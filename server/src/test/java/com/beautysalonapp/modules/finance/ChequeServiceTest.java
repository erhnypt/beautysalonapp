package com.beautysalonapp.modules.finance;

import com.beautysalonapp.core.error.BusinessRuleException;
import com.beautysalonapp.modules.finance.application.ChequePort;
import com.beautysalonapp.modules.finance.application.ChequeService;
import com.beautysalonapp.modules.finance.application.FinanceService;
import com.beautysalonapp.modules.finance.domain.ChequeStatus;
import com.beautysalonapp.modules.finance.domain.ChequeType;
import com.beautysalonapp.modules.party.application.PartyLedger;
import com.beautysalonapp.modules.party.application.PartyService;
import com.beautysalonapp.modules.party.domain.AccountKind;
import com.beautysalonapp.modules.party.domain.PartyType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ChequeServiceTest {

    @Autowired ChequeService cheques;
    @Autowired FinanceService finance;
    @Autowired PartyService partyService;
    @Autowired PartyLedger partyLedger;

    private long customerAccount() {
        long id = partyService.create(PartyType.MUSTERI, null, "Çek Müşteri " + System.nanoTime(),
                null, null, null, null, null, null).getId();
        return partyLedger.resolveAccount(id, AccountKind.NORMAL, "TRY");
    }

    @Test
    void musteri_ceki_kaydi_cariyi_alacaklandirir_ve_riske_yazar() {
        long acc = customerAccount();
        long id = cheques.register(new ChequePort.RegisterChequeCommand("CK-100", ChequeType.MUSTERI_CEKI,
                "Y Bank", "Ali", LocalDate.now().plusDays(30), new BigDecimal("2500"), "TRY", acc, null));

        assertThat(partyLedger.balance(acc).getAmount()).isEqualByComparingTo("-2500.0000");
        assertThat(cheques.customerRiskBalance(acc).getAmount()).isEqualByComparingTo("2500");
        assertThat(cheques.history(id)).hasSize(1);
    }

    @Test
    void tahsil_edildi_bankaya_para_girer_risk_sifirlanir() {
        long acc = customerAccount();
        var banka = finance.createAccount("BNK-CK-" + System.nanoTime() % 100000, "Çek Bankası",
                com.beautysalonapp.modules.finance.domain.FinAccountKind.BANKA, "TRY", BigDecimal.ZERO, false);
        long id = cheques.register(new ChequePort.RegisterChequeCommand("CK-101", ChequeType.MUSTERI_CEKI,
                "Y Bank", "Veli", LocalDate.now().plusDays(10), new BigDecimal("1500"), "TRY", acc, null));

        cheques.transition(id, ChequeStatus.TAHSIL_EDILDI, banka.getId(), "vadesi geldi");

        assertThat(finance.accountBalance(banka.getId()).getAmount()).isEqualByComparingTo("1500.0000");
        assertThat(cheques.customerRiskBalance(acc).getAmount()).isEqualByComparingTo("0");
        // cari kayıt anında düşmüştü, tahsilde tekrar değişmez
        assertThat(partyLedger.balance(acc).getAmount()).isEqualByComparingTo("-1500.0000");
    }

    @Test
    void karsiliksiz_cek_cariyi_yeniden_borclandirir() {
        long acc = customerAccount();
        long id = cheques.register(new ChequePort.RegisterChequeCommand("CK-102", ChequeType.MUSTERI_CEKI,
                "Z Bank", "Can", LocalDate.now().plusDays(5), new BigDecimal("800"), "TRY", acc, null));

        cheques.transition(id, ChequeStatus.KARSILIKSIZ, null, "karşılıksız çıktı");

        // -800 (kayıt) + 800 (karşılıksız geri) => 0
        assertThat(partyLedger.balance(acc).getAmount()).isEqualByComparingTo("0.0000");
        assertThat(cheques.customerRiskBalance(acc).getAmount()).isEqualByComparingTo("0");
    }

    @Test
    void gecersiz_gecis_reddedilir() {
        long acc = customerAccount();
        long id = cheques.register(new ChequePort.RegisterChequeCommand("CK-103", ChequeType.MUSTERI_CEKI,
                null, null, LocalDate.now().plusDays(5), new BigDecimal("100"), "TRY", acc, null));
        cheques.transition(id, ChequeStatus.TAHSIL_EDILDI, finance.defaultCashAccountId(), null);
        assertThatThrownBy(() -> cheques.transition(id, ChequeStatus.PORTFOYDE, null, null))
                .isInstanceOf(BusinessRuleException.class);
    }
}
