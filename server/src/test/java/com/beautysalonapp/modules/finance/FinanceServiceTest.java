package com.beautysalonapp.modules.finance;

import com.beautysalonapp.core.error.BusinessRuleException;
import com.beautysalonapp.modules.finance.application.FinanceService;
import com.beautysalonapp.modules.finance.domain.CardDirection;
import com.beautysalonapp.modules.finance.domain.CashTxnType;
import com.beautysalonapp.modules.finance.domain.FinAccountKind;
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
class FinanceServiceTest {

    @Autowired FinanceService finance;
    @Autowired PartyService partyService;
    @Autowired PartyLedger partyLedger;

    private long customerAccount() {
        var ref = partyService.create(PartyType.MUSTERI, null, "Fin Test " + System.nanoTime(),
                null, null, null, null, null, null);
        return partyLedger.resolveAccount(ref.getId(), AccountKind.NORMAL, "TRY");
    }

    @Test
    void varsayilan_kasa_ve_kart_plani_tohumlandi() {
        long kasa = finance.defaultCashAccountId();
        assertThat(kasa).isPositive();
        assertThat(finance.listCards()).extracting("code").contains("600.01", "700.01");
    }

    @Test
    void tahsilat_kasayi_artirir_cariyi_alacaklandirir() {
        long kasa = finance.defaultCashAccountId();
        BigDecimal before = finance.accountBalance(kasa).getAmount();
        long acc = customerAccount();

        finance.createManual(CashTxnType.COLLECTION, LocalDate.now(), kasa, null, acc, null,
                new BigDecimal("500.00"), "Peşin tahsilat");

        assertThat(finance.accountBalance(kasa).getAmount()).isEqualByComparingTo(before.add(new BigDecimal("500")));
        // Cari: tahsilat alacak yazar => bakiye -500 (borç pozitif konvansiyonu)
        assertThat(partyLedger.balance(acc).getAmount()).isEqualByComparingTo("-500.0000");
    }

    @Test
    void tediye_kasayi_azaltir_cariyi_borclandirir() {
        long kasa = finance.defaultCashAccountId();
        BigDecimal before = finance.accountBalance(kasa).getAmount();
        long acc = customerAccount();

        finance.createManual(CashTxnType.PAYMENT, LocalDate.now(), kasa, null, acc, null,
                new BigDecimal("200.00"), "İade ödemesi");

        assertThat(finance.accountBalance(kasa).getAmount()).isEqualByComparingTo(before.subtract(new BigDecimal("200")));
        assertThat(partyLedger.balance(acc).getAmount()).isEqualByComparingTo("200.0000");
    }

    @Test
    void virman_iki_hesap_arasi() {
        long kasa = finance.defaultCashAccountId();
        var banka = finance.createAccount("BNK-T-" + System.nanoTime() % 100000, "Test Banka",
                FinAccountKind.BANKA, "TRY", BigDecimal.ZERO, false);
        BigDecimal kasaBefore = finance.accountBalance(kasa).getAmount();

        // önce kasaya para koy
        finance.createManual(CashTxnType.COLLECTION, LocalDate.now(), kasa, null, null, null,
                new BigDecimal("1000"), "açılış");
        finance.createManual(CashTxnType.TRANSFER, LocalDate.now(), kasa, banka.getId(), null, null,
                new BigDecimal("400"), "bankaya yatırma");

        assertThat(finance.accountBalance(kasa).getAmount())
                .isEqualByComparingTo(kasaBefore.add(new BigDecimal("600")));
        assertThat(finance.accountBalance(banka.getId()).getAmount()).isEqualByComparingTo("400.0000");
    }

    @Test
    void iptal_bakiyeleri_geri_alir() {
        long kasa = finance.defaultCashAccountId();
        long acc = customerAccount();
        BigDecimal kasaBefore = finance.accountBalance(kasa).getAmount();

        var t = finance.createManual(CashTxnType.COLLECTION, LocalDate.now(), kasa, null, acc, null,
                new BigDecimal("300"), "yanlış tahsilat");
        finance.voidTransaction(t.getId(), "hatalı giriş");

        assertThat(finance.accountBalance(kasa).getAmount()).isEqualByComparingTo(kasaBefore);
        assertThat(partyLedger.balance(acc).getAmount()).isEqualByComparingTo("0.0000");
    }

    @Test
    void gelir_gider_raporu_kart_bazli_toplar() {
        long kasa = finance.defaultCashAccountId();
        long gelirCard = finance.listCards().stream().filter(c -> c.getCode().equals("600.02"))
                .findFirst().orElseThrow().getId();
        long giderCard = finance.listCards().stream().filter(c -> c.getCode().equals("700.01"))
                .findFirst().orElseThrow().getId();

        finance.createManual(CashTxnType.COLLECTION, LocalDate.now(), kasa, null, null, gelirCard,
                new BigDecimal("750"), "ürün satışı");
        finance.createManual(CashTxnType.PAYMENT, LocalDate.now(), kasa, null, null, giderCard,
                new BigDecimal("250"), "kira");

        var rows = finance.incomeExpenseReport(LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));
        assertThat(rows).anySatisfy(r -> {
            assertThat(r.code()).isEqualTo("600.02");
            assertThat(r.amount()).isEqualByComparingTo("750");
        });
        assertThat(rows).anySatisfy(r -> {
            assertThat(r.code()).isEqualTo("700.01");
            assertThat(r.amount()).isEqualByComparingTo("-250");
        });
    }

    @Test
    void ust_karta_hareket_yazilamaz() {
        long kasa = finance.defaultCashAccountId();
        long parentCard = finance.listCards().stream().filter(c -> c.getCode().equals("600"))
                .findFirst().orElseThrow().getId();
        assertThatThrownBy(() -> finance.createManual(CashTxnType.COLLECTION, LocalDate.now(), kasa,
                null, null, parentCard, new BigDecimal("10"), "x"))
                .isInstanceOf(BusinessRuleException.class);
    }
}
