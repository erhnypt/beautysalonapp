package com.beautysalonapp.modules.party;

import com.beautysalonapp.modules.party.application.PartyLedger;
import com.beautysalonapp.modules.party.application.PartyLedger.LedgerEntry;
import com.beautysalonapp.modules.party.application.PartyService;
import com.beautysalonapp.modules.party.domain.AccountKind;
import com.beautysalonapp.modules.party.domain.PartyType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PartyLedgerServiceTest {

    @Autowired PartyService partyService;
    @Autowired PartyLedger ledger;

    private long newAccount() {
        var ref = partyService.create(PartyType.MUSTERI, null, "Test Müşteri " + System.nanoTime(),
                null, null, null, null, null, null);
        return ledger.resolveAccount(ref.getId(), AccountKind.NORMAL, "TRY");
    }

    @Test
    void borc_ve_alacak_bakiyeyi_dogru_hesaplar() {
        long acc = newAccount();
        ledger.post(LedgerEntry.debit(acc, LocalDate.now(), "INVOICE", "A-1", "Satış", new BigDecimal("150.00"), "TRY"));
        ledger.post(LedgerEntry.credit(acc, LocalDate.now(), "PAYMENT", "T-1", "Tahsilat", new BigDecimal("100.00"), "TRY"));

        var bal = ledger.balance(acc);
        assertThat(bal.getAmount()).isEqualByComparingTo("50.0000");
        assertThat(bal.getCurrency()).isEqualTo("TRY");
    }

    @Test
    void ayni_belge_ayni_line_key_iki_kez_yazilmaz() {
        long acc = newAccount();
        LedgerEntry e = new LedgerEntry(acc, LocalDate.now(), "INVOICE", "A-2", "L1", "Satır",
                new BigDecimal("40.00"), BigDecimal.ZERO, "TRY");
        ledger.post(e);
        ledger.post(e);
        assertThat(ledger.balance(acc).getAmount()).isEqualByComparingTo("40.0000");
    }

    @Test
    void ters_kayit_belgeyi_sifirlar() {
        long acc = newAccount();
        ledger.post(LedgerEntry.debit(acc, LocalDate.now(), "INVOICE", "A-3", "Satış", new BigDecimal("250.00"), "TRY"));
        assertThat(ledger.balance(acc).getAmount()).isEqualByComparingTo("250.0000");

        ledger.reverse("INVOICE", "A-3", "hatalı fatura");
        assertThat(ledger.balance(acc).getAmount()).isEqualByComparingTo("0.0000");
    }

    @Test
    void ekstre_yuruyen_bakiye_uretir() {
        long acc = newAccount();
        ledger.post(LedgerEntry.debit(acc, LocalDate.now().minusDays(2), "INVOICE", "A-4", "1", new BigDecimal("100.00"), "TRY"));
        ledger.post(LedgerEntry.credit(acc, LocalDate.now(), "PAYMENT", "T-4", "2", new BigDecimal("30.00"), "TRY"));

        var lines = ledger.statement(acc, null, null);
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).runningBalance()).isEqualByComparingTo("100.0000");
        assertThat(lines.get(1).runningBalance()).isEqualByComparingTo("70.0000");
    }

    @Test
    void negatif_tutar_reddedilir() {
        long acc = newAccount();
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                ledger.post(new LedgerEntry(acc, LocalDate.now(), "X", "X-1", null, "neg",
                        new BigDecimal("-5"), BigDecimal.ZERO, "TRY")));
    }
}
