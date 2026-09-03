package com.beautysalonapp.modules.reporting.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Raporlama / Günlük Analiz (§14). Modüller arası kuplajı önlemek için doğrudan
 * tablolara {@link JdbcTemplate} ile native sorgu atar (plan: "raporlar okuma
 * modelinden çalışsın; jOOQ veya JdbcTemplate"). Tüm tarih aritmetiği Java'da
 * yapılır ki H2/PostgreSQL farkı olmasın.
 */
@Service
public class ReportService {

    private final JdbcTemplate jdbc;

    public ReportService(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public record PaymentBreakdown(BigDecimal nakit, BigDecimal kart, BigDecimal havale) {}

    public record Alert(String key, int count, BigDecimal amount) {}

    public record TrendPoint(LocalDate date, BigDecimal amount) {}

    public record NameCount(String name, long count) {}

    public record DailyDashboard(
            LocalDate date,
            BigDecimal invoiceRevenue,
            BigDecimal appointmentRevenue,
            BigDecimal totalRevenue,
            PaymentBreakdown payments,
            BigDecimal collections,
            BigDecimal expenses,
            Map<String, Integer> appointmentsByStatus,
            int newCustomers,
            List<Alert> alerts,
            List<TrendPoint> revenueTrend30d,
            List<NameCount> serviceDistribution30d,
            List<NameCount> staffOccupancy30d) {
    }

    public DailyDashboard today() {
        LocalDate today = LocalDate.now();
        Date sqlToday = Date.valueOf(today);
        Date from30 = Date.valueOf(today.minusDays(30));
        java.sql.Timestamp fromTs30 = java.sql.Timestamp.from(
                today.minusDays(30).atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date weekAhead = Date.valueOf(today.plusDays(7));

        BigDecimal invoiceRevenue = nz(jdbc.queryForObject("""
                select coalesce(sum(case
                    when invoice_type in ('SATIS','PERAKENDE') then grand_total
                    when invoice_type = 'IADE_SATIS' then -grand_total
                    else 0 end), 0)
                from invoice
                where status = 'CONFIRMED' and invoice_date = ?
                """, BigDecimal.class, sqlToday));

        BigDecimal apptRevenue = nz(jdbc.queryForObject("""
                select coalesce(sum(price_snapshot), 0)
                from appointment
                where status = 'GELDI' and contract_line_id is null
                  and cast(arrived_at as date) = ?
                """, BigDecimal.class, sqlToday));

        // Ödeme türü dağılımı (bugün, iptal olmayan)
        var payMap = new java.util.HashMap<String, BigDecimal>();
        jdbc.query("""
                select a.kind, coalesce(sum(case
                    when c.txn_type = 'COLLECTION' then c.amount
                    when c.txn_type = 'PAYMENT' then -c.amount else 0 end), 0) as net
                from cash_transaction c join fin_account a on a.id = c.account_id
                where c.voided = false and c.txn_date = ?
                group by a.kind
                """, (org.springframework.jdbc.core.RowCallbackHandler) rs -> { payMap.put(rs.getString("kind"), rs.getBigDecimal("net")); }, sqlToday);
        var payments = new PaymentBreakdown(
                payMap.getOrDefault("KASA", BigDecimal.ZERO),
                payMap.getOrDefault("POS", BigDecimal.ZERO),
                payMap.getOrDefault("BANKA", BigDecimal.ZERO));

        BigDecimal collections = nz(jdbc.queryForObject("""
                select coalesce(sum(amount), 0) from cash_transaction
                where txn_type = 'COLLECTION' and voided = false and txn_date = ?
                """, BigDecimal.class, sqlToday));

        BigDecimal expenses = nz(jdbc.queryForObject("""
                select coalesce(sum(c.amount), 0)
                from cash_transaction c join income_expense_card ie on ie.id = c.income_expense_card_id
                where ie.direction = 'EXPENSE' and c.voided = false and c.txn_date = ?
                """, BigDecimal.class, sqlToday));

        Map<String, Integer> apptStatus = new LinkedHashMap<>();
        jdbc.query("""
                select status, count(*) as cnt from appointment
                where cast(start_at as date) = ? group by status
                """, (org.springframework.jdbc.core.RowCallbackHandler) rs -> { apptStatus.put(rs.getString("status"), rs.getInt("cnt")); }, sqlToday);

        int newCustomers = nzInt(jdbc.queryForObject("""
                select count(*) from party
                where party_type = 'MUSTERI' and deleted = false and cast(created_at as date) = ?
                """, Integer.class, sqlToday));

        var alerts = new java.util.ArrayList<Alert>();
        jdbc.query("""
                select count(*) as cnt, coalesce(sum(amount - paid_amount), 0) as amt
                from installment
                where status in ('BEKLIYOR','GECIKMIS') and due_date <= ?
                """, (org.springframework.jdbc.core.RowCallbackHandler) rs -> alerts.add(new Alert("installments_due", rs.getInt("cnt"), rs.getBigDecimal("amt"))),
                sqlToday);
        int criticalStock = nzInt(jdbc.queryForObject("""
                select count(*) from (
                  select i.id from item i join stock_level sl on sl.item_id = i.id
                  where i.reorder_level is not null and i.deleted = false
                  group by i.id, i.reorder_level
                  having sum(sl.qty_base) <= i.reorder_level
                ) x
                """, Integer.class));
        alerts.add(new Alert("critical_stock", criticalStock, BigDecimal.ZERO));
        int chequesDue = nzInt(jdbc.queryForObject("""
                select count(*) from cheque
                where status in ('PORTFOYDE','BANKAYA_TAHSILE') and due_date <= ?
                """, Integer.class, weekAhead));
        alerts.add(new Alert("cheques_due_week", chequesDue, BigDecimal.ZERO));

        var trend = jdbc.query("""
                select invoice_date as d, sum(case
                    when invoice_type in ('SATIS','PERAKENDE') then grand_total
                    when invoice_type = 'IADE_SATIS' then -grand_total else 0 end) as amt
                from invoice
                where status = 'CONFIRMED' and invoice_date >= ?
                group by invoice_date order by invoice_date
                """, (rs, i) -> new TrendPoint(rs.getDate("d").toLocalDate(), nz(rs.getBigDecimal("amt"))),
                from30);

        var serviceDist = jdbc.query("""
                select sd.name as n, count(*) as c
                from appointment a join service_definition sd on sd.id = a.service_id
                where a.status = 'GELDI' and a.start_at >= ?
                group by sd.name order by c desc
                """, (rs, i) -> new NameCount(rs.getString("n"), rs.getLong("c")), fromTs30);

        var staffOcc = jdbc.query("""
                select s.title as n, count(*) as c
                from appointment a join staff s on s.party_id = a.staff_party_id
                where a.start_at >= ?
                group by s.title order by c desc
                """, (rs, i) -> new NameCount(rs.getString("n"), rs.getLong("c")), fromTs30);

        return new DailyDashboard(today, invoiceRevenue, apptRevenue,
                invoiceRevenue.add(apptRevenue), payments, collections, expenses,
                apptStatus, newCustomers, alerts, trend, serviceDist, staffOcc);
    }

    /** Gün sonu özeti (yöneticiye e-posta metni için, §14.2). */
    public String endOfDaySummary() {
        DailyDashboard d = today();
        StringBuilder sb = new StringBuilder();
        sb.append("GÜN SONU RAPORU — ").append(d.date()).append("\n\n");
        sb.append("Ciro (fatura): ").append(d.invoiceRevenue()).append(" TL\n");
        sb.append("Ciro (randevu): ").append(d.appointmentRevenue()).append(" TL\n");
        sb.append("Tahsilat: ").append(d.collections()).append(" TL   Gider: ").append(d.expenses()).append(" TL\n");
        sb.append("Nakit: ").append(d.payments().nakit())
          .append("  Kart: ").append(d.payments().kart())
          .append("  Havale: ").append(d.payments().havale()).append("\n");
        sb.append("Randevu: ").append(d.appointmentsByStatus()).append("\n");
        sb.append("Yeni müşteri: ").append(d.newCustomers()).append("\n\n");
        sb.append("UYARILAR:\n");
        for (Alert a : d.alerts()) {
            sb.append(" - ").append(a.key()).append(": ").append(a.count());
            if (a.amount() != null && a.amount().signum() > 0) sb.append(" (").append(a.amount()).append(" TL)");
            sb.append("\n");
        }
        return sb.toString();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static int nzInt(Integer v) {
        return v == null ? 0 : v;
    }
}
