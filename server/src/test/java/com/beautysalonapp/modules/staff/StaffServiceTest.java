package com.beautysalonapp.modules.staff;

import com.beautysalonapp.modules.finance.application.FinanceService;
import com.beautysalonapp.modules.party.application.PartyLedger;
import com.beautysalonapp.modules.party.application.PartyService;
import com.beautysalonapp.modules.party.domain.AccountKind;
import com.beautysalonapp.modules.party.domain.PartyType;
import com.beautysalonapp.modules.staff.application.CommissionPort;
import com.beautysalonapp.modules.staff.application.StaffService;
import com.beautysalonapp.modules.staff.domain.CommissionBasis;
import com.beautysalonapp.modules.staff.domain.CommissionScope;
import com.beautysalonapp.modules.staff.domain.CommissionStatus;
import com.beautysalonapp.modules.staff.domain.Staff;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class StaffServiceTest {

    @Autowired StaffService staff;
    @Autowired PartyService partyService;
    @Autowired PartyLedger partyLedger;
    @Autowired FinanceService finance;
    @Autowired CommissionPort commissionPort;

    private static final String YM = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));

    private Staff newStaff(BigDecimal serviceRate) {
        long partyId = partyService.create(PartyType.PERSONEL, null, "Kuaför " + System.nanoTime(),
                null, null, null, null, null, null).getId();
        return staff.createStaff(partyId, "Kuaför", LocalDate.now().minusYears(1), null, serviceRate, null);
    }

    @Test
    void tahakkuk_prim_satiri_ve_cari_alacak_uretir() {
        Staff s = newStaff(new BigDecimal("10"));
        long acc = partyLedger.resolveAccount(s.getPartyId(), AccountKind.NORMAL, "TRY");

        commissionPort.accrue(new CommissionPort.AccrueCommand(s.getPartyId(), CommissionScope.SERVICE,
                new BigDecimal("400"), "APPOINTMENT", "APPT-1", LocalDate.now()));

        var lines = staff.commissions(s.getId(), YM);
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).getAmount()).isEqualByComparingTo("40.00");
        assertThat(lines.get(0).getStatus()).isEqualTo(CommissionStatus.TAHAKKUK);
        // personel cariye alacak => bakiye -40
        assertThat(partyLedger.balance(acc).getAmount()).isEqualByComparingTo("-40.0000");
    }

    @Test
    void ayni_kaynak_iki_kez_tahakkuk_etmez() {
        Staff s = newStaff(new BigDecimal("10"));
        var cmd = new CommissionPort.AccrueCommand(s.getPartyId(), CommissionScope.SERVICE,
                new BigDecimal("400"), "APPOINTMENT", "APPT-DUP", LocalDate.now());
        commissionPort.accrue(cmd);
        commissionPort.accrue(cmd);
        assertThat(staff.commissions(s.getId(), YM)).hasSize(1);
    }

    @Test
    void personel_ozel_kural_sinifi_ezer() {
        var cls = staff.createClass("Kıdemli", null, new BigDecimal("8"), null);
        long partyId = partyService.create(PartyType.PERSONEL, null, "Usta " + System.nanoTime(),
                null, null, null, null, null, null).getId();
        Staff s = staff.createStaff(partyId, "Usta", LocalDate.now(), cls.getId(), null, null);
        // personel özel kural %20
        staff.createRule(CommissionScope.SERVICE, CommissionBasis.RATE, new BigDecimal("20"),
                s.getId(), null, null);

        commissionPort.accrue(new CommissionPort.AccrueCommand(s.getPartyId(), CommissionScope.SERVICE,
                new BigDecimal("1000"), "APPOINTMENT", "APPT-2", LocalDate.now()));

        assertThat(staff.commissions(s.getId(), YM).get(0).getAmount()).isEqualByComparingTo("200.00");
    }

    @Test
    void prim_odeme_kasadan_cikar_cariyi_borclandirir_satirlari_kapatir() {
        Staff s = newStaff(new BigDecimal("10"));
        long acc = partyLedger.resolveAccount(s.getPartyId(), AccountKind.NORMAL, "TRY");
        long kasa = finance.defaultCashAccountId();
        BigDecimal kasaBefore = finance.accountBalance(kasa).getAmount();

        commissionPort.accrue(new CommissionPort.AccrueCommand(s.getPartyId(), CommissionScope.SERVICE,
                new BigDecimal("400"), "APPOINTMENT", "APPT-3", LocalDate.now()));
        commissionPort.accrue(new CommissionPort.AccrueCommand(s.getPartyId(), CommissionScope.SERVICE,
                new BigDecimal("600"), "APPOINTMENT", "APPT-4", LocalDate.now()));

        BigDecimal paid = staff.payCommissions(s.getId(), YM, null); // 40 + 60 = 100
        assertThat(paid).isEqualByComparingTo("100.00");
        assertThat(finance.accountBalance(kasa).getAmount()).isEqualByComparingTo(kasaBefore.subtract(new BigDecimal("100")));
        assertThat(partyLedger.balance(acc).getAmount()).isEqualByComparingTo("0.0000"); // -100 + 100
        assertThat(staff.commissions(s.getId(), YM))
                .allSatisfy(c -> assertThat(c.getStatus()).isEqualTo(CommissionStatus.ODENDI));
    }

    @Test
    void avans_kasadan_cikar_personeli_borclandirir() {
        Staff s = newStaff(null);
        long acc = partyLedger.resolveAccount(s.getPartyId(), AccountKind.NORMAL, "TRY");
        long kasa = finance.defaultCashAccountId();
        BigDecimal kasaBefore = finance.accountBalance(kasa).getAmount();

        staff.giveAdvance(s.getId(), new BigDecimal("500"), null);

        assertThat(finance.accountBalance(kasa).getAmount()).isEqualByComparingTo(kasaBefore.subtract(new BigDecimal("500")));
        assertThat(partyLedger.balance(acc).getAmount()).isEqualByComparingTo("500.0000"); // personel borçlu
        assertThat(staff.advances(s.getId())).hasSize(1);
    }

    @Test
    void kural_ve_varsayilan_oran_yoksa_prim_yok() {
        long partyId = partyService.create(PartyType.PERSONEL, null, "Stajyer " + System.nanoTime(),
                null, null, null, null, null, null).getId();
        Staff s = staff.createStaff(partyId, "Stajyer", LocalDate.now(), null, null, null);
        commissionPort.accrue(new CommissionPort.AccrueCommand(s.getPartyId(), CommissionScope.SERVICE,
                new BigDecimal("400"), "APPOINTMENT", "APPT-5", LocalDate.now()));
        assertThat(staff.commissions(s.getId(), YM)).isEmpty();
    }
}
