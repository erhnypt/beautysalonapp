package com.beautysalonapp.modules.notification;

import com.beautysalonapp.config.AppProperties;
import com.beautysalonapp.modules.notification.application.NotificationService;
import com.beautysalonapp.modules.notification.domain.NotificationChannel;
import com.beautysalonapp.modules.notification.domain.NotificationType;
import com.beautysalonapp.modules.reporting.application.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * Zamanlanmış bildirim tetikleyicileri (§10.8). Modüller arası kuplajı önlemek için
 * tarama sorguları {@link JdbcTemplate} ile yapılır; kuyruğa alma {@link NotificationService}.
 */
@Component
public class NotificationTriggers {

    private static final Logger log = LoggerFactory.getLogger(NotificationTriggers.class);

    private final JdbcTemplate jdbc;
    private final NotificationService notifications;
    private final ReportService reports;
    private final AppProperties props;

    public NotificationTriggers(DataSource ds, NotificationService notifications,
                                ReportService reports, AppProperties props) {
        this.jdbc = new JdbcTemplate(ds);
        this.notifications = notifications;
        this.reports = reports;
        this.props = props;
    }

    private boolean disabled() {
        return !props.getNotification().isEnabled() || props.isFullOfflineMode();
    }

    /** Doğum günü + evlilik yıldönümü — her sabah 09:00. */
    @Scheduled(cron = "0 0 9 * * *")
    public void celebrations() {
        if (disabled()) return;
        LocalDate today = LocalDate.now();
        int m = today.getMonthValue();
        int d = today.getDayOfMonth();

        for (var row : jdbc.queryForList("""
                select id, title from party
                where deleted = false and party_type = 'MUSTERI' and birth_date is not null
                  and extract(month from birth_date) = ? and extract(day from birth_date) = ?
                """, m, d)) {
            enqueueBoth(NotificationType.DOGUM_GUNU, ((Number) row.get("id")).longValue(),
                    Map.of("ad", String.valueOf(row.get("title"))));
        }
        for (var row : jdbc.queryForList("""
                select id, title from party
                where deleted = false and party_type = 'MUSTERI' and wedding_anniversary is not null
                  and extract(month from wedding_anniversary) = ? and extract(day from wedding_anniversary) = ?
                """, m, d)) {
            enqueueBoth(NotificationType.YILDONUMU, ((Number) row.get("id")).longValue(),
                    Map.of("ad", String.valueOf(row.get("title"))));
        }
    }

    /** Randevu hatırlatma — saatlik; başlangıcı [now+H, now+H+1s) penceresinde olanlar. */
    @Scheduled(cron = "0 5 * * * *")
    public void appointmentReminders() {
        if (disabled()) return;
        int h = props.getNotification().getReminderHours();
        Instant from = Instant.now().plusSeconds(h * 3600L);
        Instant to = from.plusSeconds(3600);
        for (var row : jdbc.queryForList("""
                select a.party_id as pid, a.start_at as st, sd.name as svc
                from appointment a join service_definition sd on sd.id = a.service_id
                where a.status in ('PLANLANDI','ONAYLANDI')
                  and a.start_at >= ? and a.start_at < ?
                """, java.sql.Timestamp.from(from), java.sql.Timestamp.from(to))) {
            long pid = ((Number) row.get("pid")).longValue();
            var st = ((java.sql.Timestamp) row.get("st")).toInstant().atZone(displayZone());
            var vars = Map.of("tarih", st.toLocalDate().toString(),
                    "saat", String.format("%02d:%02d", st.getHour(), st.getMinute()),
                    "hizmet", String.valueOf(row.get("svc")));
            notifications.enqueue(NotificationType.RANDEVU_HATIRLATMA, NotificationChannel.SMS, pid, null, vars, null);
        }
    }

    /** Taksit hatırlatma — her gün 09:15. Vadeden 3 gün önce, vade günü ve gecikmiş taksitler. */
    @Scheduled(cron = "0 15 9 * * *")
    public void installmentReminders() {
        if (disabled()) return;
        LocalDate today = LocalDate.now();
        for (var row : jdbc.queryForList("""
                select c.party_id as pid, i.seq as seq, i.due_date as dd,
                       (i.amount - i.paid_amount) as remaining, c.doc_no as doc
                from installment i join sales_contract c on c.id = i.contract_id
                where i.status in ('BEKLIYOR','GECIKMIS')
                  and (i.due_date = ? or i.due_date = ? or i.due_date < ?)
                """, java.sql.Date.valueOf(today.plusDays(3)),
                java.sql.Date.valueOf(today), java.sql.Date.valueOf(today))) {
            long pid = ((Number) row.get("pid")).longValue();
            var vars = Map.of(
                    "taksitNo", String.valueOf(row.get("seq")),
                    "vade", row.get("dd").toString(),
                    "tutar", String.valueOf(row.get("remaining")),
                    "sozlesme", String.valueOf(row.get("doc")));
            notifications.enqueue(NotificationType.TAKSIT, NotificationChannel.SMS, pid, null, vars, null);
        }
    }

    /** Borç bakiyesi — her ayın 1'i saat 10:00, eşiği aşanlara. */
    @Scheduled(cron = "0 0 10 1 * *")
    public void debtReminders() {
        if (disabled()) return;
        var threshold = props.getNotification().getDebtThreshold();
        for (var row : jdbc.queryForList("""
                select pa.party_id as pid,
                       coalesce(sum(pt.debit),0) - coalesce(sum(pt.credit),0) + max(pa.opening_balance) as bal
                from party_account pa
                left join party_transaction pt on pt.account_id = pa.id
                where pa.account_kind = 'NORMAL'
                group by pa.party_id
                having coalesce(sum(pt.debit),0) - coalesce(sum(pt.credit),0) + max(pa.opening_balance) >= ?
                """, threshold)) {
            long pid = ((Number) row.get("pid")).longValue();
            notifications.enqueue(NotificationType.BORC, NotificationChannel.SMS, pid, null,
                    Map.of("tutar", String.valueOf(row.get("bal"))), null);
        }
    }

    /** Gün sonu yönetici raporu — her gece 23:30. */
    @Scheduled(cron = "0 30 23 * * *")
    public void managerDailyReport() {
        if (disabled()) return;
        String to = props.getNotification().getManagerEmail();
        if (to == null || to.isBlank()) {
            return;
        }
        notifications.enqueue(NotificationType.GUNLUK_RAPOR, NotificationChannel.EMAIL, null, to,
                Map.of("rapor", reports.endOfDaySummary(), "tarih", LocalDate.now().toString()), null);
    }

    private void enqueueBoth(NotificationType type, long partyId, Map<String, String> vars) {
        notifications.enqueue(type, NotificationChannel.SMS, partyId, null, vars, null);
        notifications.enqueue(type, NotificationChannel.EMAIL, partyId, null, vars, null);
    }

    private ZoneId displayZone() {
        try {
            return ZoneId.of(props.getDisplayZone());
        } catch (RuntimeException e) {
            return ZoneId.systemDefault();
        }
    }

    /** Çevrimdışı kurulumlar için yazdırılabilir hatırlatma listesi (§10.8). */
    public String reminderList(LocalDate date) {
        StringBuilder sb = new StringBuilder("HATIRLATMA LİSTESİ — ").append(date).append("\n\n");

        sb.append("DOĞUM GÜNLERİ:\n");
        for (var r : jdbc.queryForList("""
                select title, phone from party where deleted=false and party_type='MUSTERI'
                  and birth_date is not null and extract(month from birth_date)=? and extract(day from birth_date)=?
                """, date.getMonthValue(), date.getDayOfMonth())) {
            sb.append(" - ").append(r.get("title")).append("\n");
        }

        sb.append("\nRANDEVULAR:\n");
        for (var r : jdbc.queryForList("""
                select a.start_at as st, p.title as cust, sd.name as svc
                from appointment a join party p on p.id = a.party_id
                join service_definition sd on sd.id = a.service_id
                where cast(a.start_at as date) = ? and a.status in ('PLANLANDI','ONAYLANDI')
                order by a.start_at
                """, java.sql.Date.valueOf(date))) {
            sb.append(" - ").append(r.get("st")).append("  ").append(r.get("cust"))
              .append(" · ").append(r.get("svc")).append("\n");
        }

        sb.append("\nVADESİ GELEN TAKSİTLER:\n");
        for (var r : jdbc.queryForList("""
                select p.title as cust, i.seq as seq, (i.amount - i.paid_amount) as rem
                from installment i join sales_contract c on c.id = i.contract_id
                join party p on p.id = c.party_id
                where i.status in ('BEKLIYOR','GECIKMIS') and i.due_date <= ?
                """, java.sql.Date.valueOf(date))) {
            sb.append(" - ").append(r.get("cust")).append("  taksit ").append(r.get("seq"))
              .append("  ").append(r.get("rem")).append(" TL\n");
        }
        return sb.toString();
    }
}
