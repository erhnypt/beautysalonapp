package com.beautysalonapp.modules.contract;

import com.beautysalonapp.modules.contract.application.ContractService;
import com.beautysalonapp.modules.contract.application.ContractService.CreateContractCommand;
import com.beautysalonapp.modules.contract.application.ContractService.NewLine;
import com.beautysalonapp.modules.contract.domain.ContractStatus;
import com.beautysalonapp.modules.contract.domain.Installment;
import com.beautysalonapp.modules.contract.domain.InstallmentPeriod;
import com.beautysalonapp.modules.contract.domain.InstallmentStatus;
import com.beautysalonapp.modules.contract.domain.SalesContract;
import com.beautysalonapp.modules.finance.application.FinanceService;
import com.beautysalonapp.modules.party.application.PartyLedger;
import com.beautysalonapp.modules.party.application.PartyService;
import com.beautysalonapp.modules.party.domain.AccountKind;
import com.beautysalonapp.modules.party.domain.PartyType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ContractServiceTest {

    @Autowired ContractService contracts;
    @Autowired PartyService partyService;
    @Autowired PartyLedger partyLedger;
    @Autowired FinanceService finance;

    private long newCustomer() {
        return partyService.create(PartyType.MUSTERI, null, "Sözleşme Test " + System.nanoTime(),
                null, null, null, null, null, null).getId();
    }

    private CreateContractCommand cmd(long partyId, BigDecimal lineTotal, BigDecimal down, int count) {
        return new CreateContractCommand(partyId, LocalDate.now(),
                List.of(new NewLine(null, "10 Seans Cilt Bakımı", BigDecimal.ONE, 10, lineTotal)),
                down, count, LocalDate.now().plusMonths(1), InstallmentPeriod.AYLIK,
                BigDecimal.ZERO, null, null);
    }

    @Test
    void sozlesme_carisi_borclandirir_pesinati_tahsil_eder() {
        long partyId = newCustomer();
        long acc = partyLedger.resolveAccount(partyId, AccountKind.NORMAL, "TRY");
        long kasa = finance.defaultCashAccountId();
        BigDecimal kasaBefore = finance.accountBalance(kasa).getAmount();

        SalesContract c = contracts.create(cmd(partyId, new BigDecimal("3000.00"), new BigDecimal("600.00"), 6));

        // Cari: +3000 borç, -600 alacak => net 2400 borç
        assertThat(partyLedger.balance(acc).getAmount()).isEqualByComparingTo("2400.0000");
        // Kasa: +600
        assertThat(finance.accountBalance(kasa).getAmount())
                .isEqualByComparingTo(kasaBefore.add(new BigDecimal("600")));
        // 6 taksit, her biri 400
        var insts = contracts.installments(c.getId());
        assertThat(insts).hasSize(6);
        assertThat(insts).allSatisfy(i -> assertThat(i.getAmount()).isEqualByComparingTo("400.00"));
        assertThat(c.getStatus()).isEqualTo(ContractStatus.ACTIVE);
    }

    @Test
    void taksit_odeme_cariyi_alacaklandirir_ve_taksiti_kapatir() {
        long partyId = newCustomer();
        long acc = partyLedger.resolveAccount(partyId, AccountKind.NORMAL, "TRY");
        SalesContract c = contracts.create(cmd(partyId, new BigDecimal("1000.00"), BigDecimal.ZERO, 4));
        BigDecimal balAfterCreate = partyLedger.balance(acc).getAmount(); // 1000 borç

        Installment first = contracts.installments(c.getId()).get(0);
        contracts.payInstallment(first.getId(), null, null, null); // tam öde (250)

        assertThat(partyLedger.balance(acc).getAmount())
                .isEqualByComparingTo(balAfterCreate.subtract(new BigDecimal("250")));
        Installment reloaded = contracts.installments(c.getId()).get(0);
        assertThat(reloaded.getStatus()).isEqualTo(InstallmentStatus.ODENDI);
        assertThat(reloaded.getPaidAmount()).isEqualByComparingTo("250.00");
    }

    @Test
    void kismi_odeme_taksiti_acik_birakir() {
        long partyId = newCustomer();
        SalesContract c = contracts.create(cmd(partyId, new BigDecimal("900.00"), BigDecimal.ZERO, 3));
        Installment first = contracts.installments(c.getId()).get(0); // 300

        contracts.payInstallment(first.getId(), null, new BigDecimal("100.00"), null);
        Installment reloaded = contracts.installments(c.getId()).get(0);
        assertThat(reloaded.getStatus()).isEqualTo(InstallmentStatus.BEKLIYOR);
        assertThat(reloaded.remaining()).isEqualByComparingTo("200.00");
    }

    @Test
    void tum_taksitler_odenince_sozlesme_tamamlanir() {
        long partyId = newCustomer();
        SalesContract c = contracts.create(cmd(partyId, new BigDecimal("600.00"), BigDecimal.ZERO, 3));
        contracts.earlyPayoff(c.getId(), null);

        assertThat(contracts.get(c.getId()).getStatus()).isEqualTo(ContractStatus.COMPLETED);
        assertThat(contracts.installments(c.getId()))
                .allSatisfy(i -> assertThat(i.getStatus()).isEqualTo(InstallmentStatus.ODENDI));
    }

    @Test
    void iptal_bekleyen_taksitleri_iptal_eder_ve_alacagi_duser() {
        long partyId = newCustomer();
        long acc = partyLedger.resolveAccount(partyId, AccountKind.NORMAL, "TRY");
        SalesContract c = contracts.create(cmd(partyId, new BigDecimal("1200.00"), BigDecimal.ZERO, 4));
        // ilk taksiti öde (300)
        contracts.payInstallment(contracts.installments(c.getId()).get(0).getId(), null, null, null);

        contracts.cancel(c.getId(), "müşteri vazgeçti");

        assertThat(contracts.get(c.getId()).getStatus()).isEqualTo(ContractStatus.CANCELLED);
        List<Installment> insts = contracts.installments(c.getId());
        assertThat(insts.get(0).getStatus()).isEqualTo(InstallmentStatus.ODENDI);
        assertThat(insts.subList(1, 4)).allSatisfy(i ->
                assertThat(i.getStatus()).isEqualTo(InstallmentStatus.IPTAL));
        // Cari: 1200 borç - 300 ödeme - 900 iptal düşüşü = 0
        assertThat(partyLedger.balance(acc).getAmount()).isEqualByComparingTo("0.0000");
    }

    @Test
    void tamami_pesin_sozlesme_dogrudan_tamamlanir() {
        long partyId = newCustomer();
        SalesContract c = contracts.create(cmd(partyId, new BigDecimal("500.00"), new BigDecimal("500.00"), 1));
        assertThat(c.getStatus()).isEqualTo(ContractStatus.COMPLETED);
        assertThat(contracts.installments(c.getId())).isEmpty();
    }

    @Test
    void gecikmis_taksit_effective_status() {
        long partyId = newCustomer();
        var command = new CreateContractCommand(partyId, LocalDate.now().minusMonths(3),
                List.of(new NewLine(null, "Paket", BigDecimal.ONE, 5, new BigDecimal("500.00"))),
                BigDecimal.ZERO, 5, LocalDate.now().minusMonths(2), InstallmentPeriod.AYLIK,
                BigDecimal.ZERO, null, null);
        SalesContract c = contracts.create(command);
        Installment overdue = contracts.installments(c.getId()).get(0);
        assertThat(overdue.effectiveStatus(LocalDate.now())).isEqualTo(InstallmentStatus.GECIKMIS);
        assertThat(contracts.dueSchedule(LocalDate.now())).isNotEmpty();
    }
}
