package com.beautysalonapp.perf;

import com.beautysalonapp.modules.reporting.application.ReportService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Faz 7 — performans bütçesi (plan §18: "Liste ekranları &lt; 300 ms").
 *
 * <p>Bu test varsayılan {@code mvn test} çalışmasından {@code perf} etiketiyle DIŞLANIR
 * (bkz. pom.xml {@code excludedGroups}). Elle çalıştırma:
 * <pre>
 *   ./mvnw -Pperf test -Dtest=PerformanceBudgetTest
 * </pre>
 *
 * <p>{@link PerfDataGenerator} indirgenmiş ölçekte (~120k stok hareketi) izole bir
 * bellek-içi H2'ye tohumlanır; ardından en ağır rapor/liste sorguları ölçülür.
 * CI donanımı değişken olduğundan eşik gevşektir ({@value #BUDGET_MS} ms) ve
 * gerçek süreler her zaman loglanır. Gerçek 500k doğrulaması {@code docs/perf/README.md}.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:perfbudget;DB_CLOSE_DELAY=-1;MODE=LEGACY",
        "spring.flyway.locations=classpath:db/migration/common,classpath:db/migration/h2",
        "beautysalonapp.perf.seed=true",
        "beautysalonapp.perf.years=10",
        "beautysalonapp.perf.customers=2000",
        "beautysalonapp.perf.items=150",
        "beautysalonapp.perf.staff=12",
        "beautysalonapp.perf.services=25",
        "beautysalonapp.perf.movements=120000",
        "beautysalonapp.perf.appointments=40000",
        "beautysalonapp.perf.invoices=15000",
        "beautysalonapp.perf.cashTxns=30000",
        "beautysalonapp.perf.partyTxns=40000",
        "beautysalonapp.perf.contracts=1500",
        "beautysalonapp.perf.cheques=800"
})
@Tag("perf")
class PerformanceBudgetTest {

    private static final Logger log = LoggerFactory.getLogger(PerformanceBudgetTest.class);

    /** Gevşek CI eşiği. Gerçek hedef 300 ms'dir; asıl değerlendirme loglanan sürelerdir. */
    private static final long BUDGET_MS = 2_000;

    @Autowired
    private ReportService reportService;

    @Autowired
    private DataSource dataSource;

    @Test
    void liste_ve_rapor_sorgulari_butce_icinde() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        long movements = jdbc.queryForObject("select count(*) from stock_movement", Long.class);
        assertThat(movements)
                .as("PerfDataGenerator tohumlaması çalışmış olmalı")
                .isGreaterThan(100_000);

        record Case(String name, LongSupplier op) {}

        List<Case> cases = new ArrayList<>();
        cases.add(new Case("günlük dashboard (ReportService.today)",
                () -> time(() -> reportService.today())));
        cases.add(new Case("cari liste sayfası (title sıralı, 50)",
                () -> time(() -> jdbc.queryForList(
                        "select id, code, title, party_type from party "
                        + "where deleted = false order by title limit 50"))));
        cases.add(new Case("stok hareket son 50",
                () -> time(() -> jdbc.queryForList(
                        "select id, mv_date, item_id, direction, base_qty from stock_movement "
                        + "order by mv_date desc, id desc limit 50"))));
        cases.add(new Case("fatura liste (tarih sıralı, 50)",
                () -> time(() -> jdbc.queryForList(
                        "select id, doc_no, invoice_date, grand_total, status from invoice "
                        + "order by invoice_date desc limit 50"))));
        cases.add(new Case("bir müşterinin cari ekstresi",
                () -> time(() -> jdbc.queryForList(
                        "select pt.txn_date, pt.debit, pt.credit from party_transaction pt "
                        + "join party_account pa on pa.id = pt.account_id "
                        + "where pa.party_id = (select min(id) from party where party_type = 'MUSTERI') "
                        + "order by pt.txn_date"))));
        cases.add(new Case("geciken taksitler (dashboard uyarısı)",
                () -> time(() -> jdbc.queryForList(
                        "select count(*) c, coalesce(sum(amount - paid_amount),0) a from installment "
                        + "where status in ('BEKLIYOR','GECIKMIS') and due_date <= current_date"))));

        StringBuilder report = new StringBuilder("\n=== Faz 7 performans bütçesi (").append(movements)
                .append(" stok hareketi) ===\n");
        List<String> breaches = new ArrayList<>();

        for (Case c : cases) {
            c.op().getAsLong();                       // ısıtma (JIT + plan cache)
            long best = Long.MAX_VALUE;
            long sum = 0;
            int runs = 5;
            for (int i = 0; i < runs; i++) {
                long ms = c.op().getAsLong();
                best = Math.min(best, ms);
                sum += ms;
            }
            long avg = sum / runs;
            report.append(String.format("  %-42s  en iyi %4d ms   ort %4d ms%n", c.name(), best, avg));
            if (best > BUDGET_MS) {
                breaches.add(c.name() + " = " + best + " ms");
            }
        }
        log.info(report.toString());

        assertThat(breaches)
                .as("Bütçeyi (%d ms) aşan sorgular — %s", BUDGET_MS, report)
                .isEmpty();
    }

    private static long time(Runnable r) {
        long t0 = System.nanoTime();
        r.run();
        return (System.nanoTime() - t0) / 1_000_000;
    }
}
