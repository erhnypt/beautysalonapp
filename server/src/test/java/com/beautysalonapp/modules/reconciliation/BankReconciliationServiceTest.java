package com.beautysalonapp.modules.reconciliation;

import com.beautysalonapp.core.error.BusinessRuleException;
import com.beautysalonapp.modules.finance.application.FinanceService;
import com.beautysalonapp.modules.finance.domain.CashTxnType;
import com.beautysalonapp.modules.finance.domain.FinAccountKind;
import com.beautysalonapp.modules.reconciliation.application.BankReconciliationService;
import com.beautysalonapp.modules.reconciliation.domain.BankStatement;
import com.beautysalonapp.modules.reconciliation.domain.BankStatementLine;
import com.beautysalonapp.modules.reconciliation.domain.MatchStatus;
import com.beautysalonapp.modules.reconciliation.domain.StatementFormat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class BankReconciliationServiceTest {

    @Autowired
    private BankReconciliationService reconciliation;

    @Autowired
    private FinanceService finance;

    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private long freshBankAccount() {
        var acc = finance.createAccount("BNK" + Long.toString(System.nanoTime(), 36), "Test Banka",
                FinAccountKind.BANKA, "TRY", BigDecimal.ZERO, false);
        return acc.getId();
    }

    private byte[] csv(String... dataLines) {
        StringBuilder sb = new StringBuilder("Tarih;Açıklama;Tutar;Referans\n");
        for (String l : dataLines) {
            sb.append(l).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void ice_aktarma_satirlari_ve_ozeti_uretir() {
        long acc = freshBankAccount();
        LocalDate today = LocalDate.now();
        byte[] file = csv(today.format(D) + ";Bilinmeyen hareket;250,00;X1");

        BankStatement stmt = reconciliation.importStatement(acc, StatementFormat.CSV, "test.csv", file);

        assertThat(stmt.getId()).isPositive();
        assertThat(stmt.getLineCount()).isEqualTo(1);
        assertThat(stmt.getSourceFormat()).isEqualTo(StatementFormat.CSV);
        List<BankStatementLine> lines = reconciliation.linesOf(stmt.getId());
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).getMatchStatus()).isEqualTo(MatchStatus.UNMATCHED);
        assertThat(stmt.getStatus().name()).isEqualTo("IMPORTED");
    }

    @Test
    void var_olan_hareketle_otomatik_eslesir() {
        long acc = freshBankAccount();
        LocalDate today = LocalDate.now();
        var txn = finance.createManual(CashTxnType.COLLECTION, today, acc, null, null, null,
                new BigDecimal("1000.00"), "Musteri X odemesi");

        byte[] file = csv(today.format(D) + ";MUSTERI X ODEMESI;1000,00;REF-1");
        BankStatement stmt = reconciliation.importStatement(acc, StatementFormat.CSV, "s.csv", file);

        List<BankStatementLine> lines = reconciliation.linesOf(stmt.getId());
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).getMatchStatus()).isEqualTo(MatchStatus.MATCHED);
        assertThat(lines.get(0).getMatchedTxnId()).isEqualTo(txn.getId());

        BankStatement refreshed = reconciliation.get(stmt.getId());
        assertThat(refreshed.getStatus().name()).isEqualTo("RECONCILED");
    }

    @Test
    void elle_eslestirme_ve_geri_alma() {
        long acc = freshBankAccount();
        LocalDate today = LocalDate.now();
        var txn = finance.createManual(CashTxnType.PAYMENT, today.minusDays(20), acc, null, null, null,
                new BigDecimal("300.00"), "Eski masraf");

        byte[] file = csv(today.format(D) + ";Alakasiz aciklama;-300,00;ZZZ");
        BankStatement stmt = reconciliation.importStatement(acc, StatementFormat.CSV, "s.csv", file);
        BankStatementLine line = reconciliation.linesOf(stmt.getId()).get(0);
        assertThat(line.getMatchStatus()).isEqualTo(MatchStatus.UNMATCHED); // tarih farkı skoru eşiğin altında bırakabilir

        reconciliation.confirmMatch(line.getId(), txn.getId());
        BankStatementLine matched = reconciliation.linesOf(stmt.getId()).get(0);
        assertThat(matched.getMatchStatus()).isEqualTo(MatchStatus.MATCHED);

        reconciliation.unmatch(line.getId());
        BankStatementLine cleared = reconciliation.linesOf(stmt.getId()).get(0);
        assertThat(cleared.getMatchStatus()).isEqualTo(MatchStatus.UNMATCHED);
    }

    @Test
    void satirdan_hareket_uretme_ve_geri_alma_hareketi_iptal_eder() {
        long acc = freshBankAccount();
        LocalDate today = LocalDate.now();
        BigDecimal before = finance.accountBalance(acc).getAmount();

        byte[] file = csv(today.format(D) + ";Banka masrafi;-45,00;M1");
        BankStatement stmt = reconciliation.importStatement(acc, StatementFormat.CSV, "s.csv", file);
        BankStatementLine line = reconciliation.linesOf(stmt.getId()).get(0);

        long txnId = reconciliation.createTransaction(line.getId(), null, "Banka masrafı", null);
        assertThat(finance.accountBalance(acc).getAmount()).isEqualByComparingTo(before.subtract(new BigDecimal("45.00")));

        BankStatementLine created = reconciliation.linesOf(stmt.getId()).get(0);
        assertThat(created.getMatchStatus()).isEqualTo(MatchStatus.CREATED);
        assertThat(created.getMatchedTxnId()).isEqualTo(txnId);
        assertThat(reconciliation.get(stmt.getId()).getStatus().name()).isEqualTo("RECONCILED");

        reconciliation.unmatch(line.getId());
        assertThat(finance.accountBalance(acc).getAmount()).isEqualByComparingTo(before); // hareket iptal edildi
        assertThat(reconciliation.linesOf(stmt.getId()).get(0).getMatchStatus()).isEqualTo(MatchStatus.UNMATCHED);
    }

    @Test
    void yok_sayma_not_zorunlu_ve_durumu_cozer() {
        long acc = freshBankAccount();
        byte[] file = csv(LocalDate.now().format(D) + ";Aciliş kaydi;0,01;OPEN");
        BankStatement stmt = reconciliation.importStatement(acc, StatementFormat.CSV, "s.csv", file);
        long lineId = reconciliation.linesOf(stmt.getId()).get(0).getId();

        assertThatThrownBy(() -> reconciliation.ignore(lineId, " "))
                .isInstanceOf(BusinessRuleException.class);

        reconciliation.ignore(lineId, "Açılış kaydı, mükerrer");
        assertThat(reconciliation.linesOf(stmt.getId()).get(0).getMatchStatus()).isEqualTo(MatchStatus.IGNORED);
        assertThat(reconciliation.get(stmt.getId()).getStatus().name()).isEqualTo("RECONCILED");
    }

    @Test
    void otomatik_esik_altindaki_oneri_uygulanmaz() {
        long acc = freshBankAccount();
        LocalDate today = LocalDate.now();
        // Tarih çok uzak (skor: yalnızca tutar = 50 < eşik 80) -> otomatik eşleşmemeli
        finance.createManual(CashTxnType.COLLECTION, today.minusMonths(6), acc, null, null, null,
                new BigDecimal("77.00"), "Alakasiz");

        byte[] file = csv(today.format(D) + ";Farkli aciklama;77,00;NOMATCH");
        BankStatement stmt = reconciliation.importStatement(acc, StatementFormat.CSV, "s.csv", file);
        assertThat(reconciliation.linesOf(stmt.getId()).get(0).getMatchStatus()).isEqualTo(MatchStatus.UNMATCHED);
    }

    @Test
    void bos_ekstre_reddedilir() {
        // Başlık dışında satır yok -> CsvStatementParser.parse zaten IllegalArgumentException fırlatır
        // (GlobalExceptionHandler bunu 400'e çevirir); servis ayrıca boş ParsedStatement'ı da reddeder.
        long acc = freshBankAccount();
        byte[] file = "Tarih;Açıklama;Tutar\n".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> reconciliation.importStatement(acc, StatementFormat.CSV, "e.csv", file))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
