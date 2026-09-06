package com.beautysalonapp.modules.party;

import com.beautysalonapp.modules.party.application.PartyLedger;
import com.beautysalonapp.modules.party.application.PartyLedger.LedgerEntry;
import com.beautysalonapp.modules.party.application.PartyReportService;
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
class PartyReportServiceTest {

    @Autowired PartyService partyService;
    @Autowired PartyLedger ledger;
    @Autowired PartyReportService reports;

    private long newCustomer() {
        return partyService.create(PartyType.MUSTERI, null, "Yaşlandırma Müşteri " + System.nanoTime(),
                null, null, null, null, null, null).getId();
    }

    @Test
    void yaslandirma_odenmemis_borcu_islem_tarihine_gore_kovaya_koyar() {
        long partyId = newCustomer();
        long acc = ledger.resolveAccount(partyId, AccountKind.NORMAL, "TRY");
        LocalDate d45 = LocalDate.now().minusDays(45);

        ledger.post(LedgerEntry.debit(acc, d45, "TEST", "AGE-" + partyId, "45 gün önce borç",
                new BigDecimal("500"), "TRY"));

        var rows = reports.aging(AccountKind.NORMAL, null);
        var row = rows.stream().filter(r -> r.partyId() == partyId).findFirst().orElseThrow();
        assertThat(row.days31to60()).isEqualByComparingTo("500");
        assertThat(row.days0to30()).isEqualByComparingTo("0");
        assertThat(row.total()).isEqualByComparingTo("500");
    }

    @Test
    void yaslandirma_kismi_odeme_orijinal_borc_tarihini_korur() {
        long partyId = newCustomer();
        long acc = ledger.resolveAccount(partyId, AccountKind.NORMAL, "TRY");
        LocalDate d100 = LocalDate.now().minusDays(100);

        ledger.post(LedgerEntry.debit(acc, d100, "TEST", "PART-" + partyId, "100 gün önce borç",
                new BigDecimal("300"), "TRY"));
        ledger.post(new LedgerEntry(acc, LocalDate.now(), "TEST", "PART-" + partyId, "pay1",
                "Kısmi ödeme", BigDecimal.ZERO, new BigDecimal("100"), "TRY"));

        var rows = reports.aging(AccountKind.NORMAL, null);
        var row = rows.stream().filter(r -> r.partyId() == partyId).findFirst().orElseThrow();
        // Kalan 200 TL hâlâ orijinal (100 gün önceki) borç tarihiyle yaşlanır → 90+ kovası
        assertThat(row.days90plus()).isEqualByComparingTo("200");
        assertThat(row.total()).isEqualByComparingTo("200");
    }

    @Test
    void yaslandirma_tamamen_odenen_borc_listede_gorunmez() {
        long partyId = newCustomer();
        long acc = ledger.resolveAccount(partyId, AccountKind.NORMAL, "TRY");
        ledger.post(LedgerEntry.debit(acc, LocalDate.now().minusDays(10), "TEST", "FULL-" + partyId,
                "borç", new BigDecimal("50"), "TRY"));
        ledger.post(new LedgerEntry(acc, LocalDate.now(), "TEST", "FULL-" + partyId, "pay1",
                "tam ödeme", BigDecimal.ZERO, new BigDecimal("50"), "TRY"));

        var rows = reports.aging(AccountKind.NORMAL, null);
        assertThat(rows).noneMatch(r -> r.partyId() == partyId);
    }

    @Test
    void dogum_gunu_listesi_yakin_tarihli_dogum_gununu_bulur() {
        long partyId = newCustomer();
        LocalDate soon = LocalDate.now().plusDays(5);
        partyService.update(partyId, null, null, null, null, null, null, null,
                null, null, null, null, soon.minusYears(30), null);

        var rows = reports.birthdays(10, null);
        assertThat(rows).anyMatch(r -> r.partyId() == partyId && r.daysUntil() == 5);
    }

    @Test
    void dogum_gunu_listesi_araligin_disindakini_haric_tutar() {
        long partyId = newCustomer();
        LocalDate far = LocalDate.now().plusDays(60);
        partyService.update(partyId, null, null, null, null, null, null, null,
                null, null, null, null, far.minusYears(20), null);

        var rows = reports.birthdays(10, null);
        assertThat(rows).noneMatch(r -> r.partyId() == partyId);
    }
}
