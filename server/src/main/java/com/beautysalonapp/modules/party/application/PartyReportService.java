package com.beautysalonapp.modules.party.application;

import com.beautysalonapp.modules.party.domain.AccountKind;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

/**
 * Cari raporları (plan §10.3): yaşlandırma (0-30-60-90+) ve doğum günü listesi.
 * "Kayıp müşteri" ve "risk bakiyesi" başka yerlerde karşılanıyor — bkz.
 * {@code ReportService#lostCustomers} (randevu+fatura'yı da okuduğu için orada) ve
 * {@code GET /api/v1/finance/cheques/risk/{partyAccountId}} (ChequePort, sırasıyla
 * finance→party bağımlılığı zaten var; ters yönde bağımlılık eklemekten kaçınıldı).
 */
@Service
public class PartyReportService {

    private final JdbcTemplate jdbc;

    public PartyReportService(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public record AgingRow(long partyId, String code, String title,
                           BigDecimal days0to30, BigDecimal days31to60, BigDecimal days61to90,
                           BigDecimal days90plus, BigDecimal total) {}

    private record TxnRow(long accountId, LocalDate date, BigDecimal debit, BigDecimal credit) {}

    private static final class OpenDebit {
        final LocalDate date;
        BigDecimal amount;
        OpenDebit(LocalDate date, BigDecimal amount) { this.date = date; this.amount = amount; }
    }

    /**
     * Yaşlandırma: her hesabın hareketleri FIFO mantığıyla netleştirilir (en eski borç,
     * en eski alacakla kapanır) — kalan açık borç tutarları işlem tarihine göre
     * yaşlandırılır. Yalnızca net bakiyesi pozitif (borçlu) taraflar listelenir.
     */
    public List<AgingRow> aging(AccountKind kind, Long branchId) {
        record AccountInfo(long accountId, long partyId, String code, String title) {}
        List<AccountInfo> accounts = jdbc.query("""
                select pa.id account_id, p.id party_id, p.code, p.title
                from party_account pa
                  join party p on p.id = pa.party_id
                where pa.account_kind = ? and pa.deleted = false and p.deleted = false
                  and p.anonymized = false""" + bf("p.branch_id", branchId) + """
                """,
                (rs, n) -> new AccountInfo(rs.getLong("account_id"), rs.getLong("party_id"),
                        rs.getString("code"), rs.getString("title")),
                kind.name());

        LocalDate today = LocalDate.now();
        List<AgingRow> result = new ArrayList<>();
        for (AccountInfo acc : accounts) {
            List<TxnRow> txns = jdbc.query("""
                    select account_id, txn_date, debit, credit from party_transaction
                    where account_id = ? and deleted = false
                    order by txn_date, id
                    """,
                    (rs, n) -> new TxnRow(rs.getLong("account_id"), rs.getDate("txn_date").toLocalDate(),
                            rs.getBigDecimal("debit"), rs.getBigDecimal("credit")),
                    acc.accountId());

            Deque<OpenDebit> openDebits = new ArrayDeque<>();
            for (TxnRow t : txns) {
                if (t.debit().signum() > 0) {
                    openDebits.addLast(new OpenDebit(t.date(), t.debit()));
                }
                BigDecimal toConsume = t.credit();
                while (toConsume.signum() > 0 && !openDebits.isEmpty()) {
                    OpenDebit oldest = openDebits.peekFirst();
                    BigDecimal applied = toConsume.min(oldest.amount);
                    oldest.amount = oldest.amount.subtract(applied);
                    toConsume = toConsume.subtract(applied);
                    if (oldest.amount.signum() == 0) {
                        openDebits.pollFirst();
                    }
                }
            }

            BigDecimal b0 = BigDecimal.ZERO, b30 = BigDecimal.ZERO, b60 = BigDecimal.ZERO, b90 = BigDecimal.ZERO;
            for (OpenDebit od : openDebits) {
                long age = ChronoUnit.DAYS.between(od.date, today);
                if (age <= 30) b0 = b0.add(od.amount);
                else if (age <= 60) b30 = b30.add(od.amount);
                else if (age <= 90) b60 = b60.add(od.amount);
                else b90 = b90.add(od.amount);
            }
            BigDecimal total = b0.add(b30).add(b60).add(b90);
            if (total.signum() > 0) {
                result.add(new AgingRow(acc.partyId(), acc.code(), acc.title(), b0, b30, b60, b90, total));
            }
        }
        result.sort(Comparator.comparing(AgingRow::total).reversed());
        return result;
    }

    public record BirthdayRow(long partyId, String code, String title, LocalDate birthDate, long daysUntil) {}

    /** Doğum günü listesi: bugünden itibaren {@code withinDays} gün içinde doğum günü olanlar. */
    public List<BirthdayRow> birthdays(int withinDays, Long branchId) {
        record Raw(long id, String code, String title, Date birthDate) {}
        List<Raw> raws = jdbc.query("""
                select id, code, title, birth_date from party
                where birth_date is not null and deleted = false and anonymized = false""" + bf("branch_id", branchId) + """
                """,
                (rs, n) -> new Raw(rs.getLong("id"), rs.getString("code"), rs.getString("title"),
                        rs.getDate("birth_date")));

        LocalDate today = LocalDate.now();
        List<BirthdayRow> result = new ArrayList<>();
        for (Raw r : raws) {
            LocalDate bday = r.birthDate().toLocalDate();
            MonthDay md = MonthDay.from(bday);
            LocalDate nextOccurrence = md.atYear(today.getYear());
            if (nextOccurrence.isBefore(today)) {
                nextOccurrence = md.atYear(today.getYear() + 1);
            }
            long days = ChronoUnit.DAYS.between(today, nextOccurrence);
            if (days <= withinDays) {
                result.add(new BirthdayRow(r.id(), r.code(), r.title(), bday, days));
            }
        }
        result.sort(Comparator.comparingLong(BirthdayRow::daysUntil));
        return result;
    }

    private static String bf(String column, Long branchId) {
        return branchId == null ? "" : " and " + column + " = " + branchId;
    }
}
