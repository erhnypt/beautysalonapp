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

    private long usdAccount() {
        return finance.createAccount("USD-" + System.nanoTime(), "Döviz Kasası", FinAccountKind.KASA,
                "USD", BigDecimal.ZERO, false).getId();
    }

    @Test
    void doviz_alimi_ortalama_kuru_gunceller_ve_tl_hesabi_borclandirir() {
        long usd = usdAccount();
        long try_ = finance.defaultCashAccountId();
        BigDecimal tryBefore = finance.accountBalance(try_).getAmount();

        finance.fxExchange(new FinanceService.FxCommand(LocalDate.now(), usd, try_, true,
                new BigDecimal("100"), new BigDecimal("30"), "İlk alım"));

        assertThat(finance.accountBalance(usd).getAmount()).isEqualByComparingTo("100");
        assertThat(finance.accountBalance(try_).getAmount()).isEqualByComparingTo(tryBefore.subtract(new BigDecimal("3000")));
        assertThat(finance.getAccount(usd).getAvgRate()).isEqualByComparingTo("30");

        // ikinci alım farklı kurdan -> ağırlıklı ortalama
        finance.fxExchange(new FinanceService.FxCommand(LocalDate.now(), usd, try_, true,
                new BigDecimal("100"), new BigDecimal("32"), "İkinci alım"));
        assertThat(finance.accountBalance(usd).getAmount()).isEqualByComparingTo("200");
        // (100*30 + 100*32) / 200 = 31
        assertThat(finance.getAccount(usd).getAvgRate()).isEqualByComparingTo("31.000000");
    }

    @Test
    void doviz_satimi_kur_kari_gelir_karti_gerektirmeden_kasaya_yazilir() {
        long usd = usdAccount();
        long try_ = finance.defaultCashAccountId();
        finance.fxExchange(new FinanceService.FxCommand(LocalDate.now(), usd, try_, true,
                new BigDecimal("100"), new BigDecimal("30"), null)); // maliyet: 30/USD
        BigDecimal tryBefore = finance.accountBalance(try_).getAmount();

        finance.fxExchange(new FinanceService.FxCommand(LocalDate.now(), usd, try_, false,
                new BigDecimal("40"), new BigDecimal("33"), "Satış"));

        assertThat(finance.accountBalance(usd).getAmount()).isEqualByComparingTo("60"); // 100-40
        // Satış hasılatı: 40*33 = 1320 (maliyet 40*30=1200 + kur karı 120)
        assertThat(finance.accountBalance(try_).getAmount()).isEqualByComparingTo(tryBefore.add(new BigDecimal("1320")));
    }

    @Test
    void doviz_satimi_kur_zarari_kasadan_dusulur() {
        long usd = usdAccount();
        long try_ = finance.defaultCashAccountId();
        finance.fxExchange(new FinanceService.FxCommand(LocalDate.now(), usd, try_, true,
                new BigDecimal("100"), new BigDecimal("30"), null));
        BigDecimal tryBefore = finance.accountBalance(try_).getAmount();

        finance.fxExchange(new FinanceService.FxCommand(LocalDate.now(), usd, try_, false,
                new BigDecimal("40"), new BigDecimal("28"), "Zararına satış"));

        // Hasılat: 40*28 = 1120 (maliyet 1200'den 80 az -> kur zararı 80 kasadan düşer)
        assertThat(finance.accountBalance(try_).getAmount()).isEqualByComparingTo(tryBefore.add(new BigDecimal("1120")));
    }

    @Test
    void kasa_sayimi_fark_yoksa_gerekce_gerekmez() {
        long kasa = finance.defaultCashAccountId();
        BigDecimal expected = finance.accountBalance(kasa).getAmount();
        var count = finance.countCash(kasa, LocalDate.now(), expected, null);
        assertThat(count.getDifference()).isEqualByComparingTo("0");
        assertThat(count.getAdjustmentDocNo()).isNull();
        assertThat(finance.accountBalance(kasa).getAmount()).isEqualByComparingTo(expected); // değişmedi
    }

    @Test
    void kasa_sayimi_fark_varsa_gerekcesiz_reddedilir() {
        long kasa = finance.defaultCashAccountId();
        BigDecimal expected = finance.accountBalance(kasa).getAmount();
        assertThatThrownBy(() -> finance.countCash(kasa, LocalDate.now(), expected.add(new BigDecimal("50")), null))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> finance.countCash(kasa, LocalDate.now(), expected.add(new BigDecimal("50")), "  "))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void kasa_sayimi_fazlaligi_kasaya_gelir_yazar_ve_bakiyeyi_esitler() {
        long kasa = finance.defaultCashAccountId();
        BigDecimal expected = finance.accountBalance(kasa).getAmount();
        BigDecimal counted = expected.add(new BigDecimal("75"));

        var count = finance.countCash(kasa, LocalDate.now(), counted, "Sayımda fazla çıktı");

        assertThat(count.getDifference()).isEqualByComparingTo("75");
        assertThat(count.getAdjustmentDocNo()).isNotNull();
        assertThat(finance.accountBalance(kasa).getAmount()).isEqualByComparingTo(counted);
        assertThat(finance.cashCounts(kasa)).extracting("id").contains(count.getId());
    }

    @Test
    void kasa_sayimi_aciginin_kasadan_dusulur_ve_bakiyeyi_esitler() {
        long kasa = finance.defaultCashAccountId();
        BigDecimal expected = finance.accountBalance(kasa).getAmount();
        BigDecimal counted = expected.subtract(new BigDecimal("40"));

        var count = finance.countCash(kasa, LocalDate.now(), counted, "Sayımda açık çıktı");

        assertThat(count.getDifference()).isEqualByComparingTo("-40");
        assertThat(finance.accountBalance(kasa).getAmount()).isEqualByComparingTo(counted);
    }

    @Test
    void ayni_para_birimiyle_doviz_islemi_reddedilir() {
        long try2 = finance.createAccount("TRY2-" + System.nanoTime(), "İkinci TL Kasa",
                FinAccountKind.KASA, "TRY", BigDecimal.ZERO, false).getId();
        long try1 = finance.defaultCashAccountId();
        assertThatThrownBy(() -> finance.fxExchange(new FinanceService.FxCommand(LocalDate.now(),
                try2, try1, true, BigDecimal.TEN, BigDecimal.ONE, null)))
                .isInstanceOf(BusinessRuleException.class);
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
    void butce_gerceklesen_hareketsiz_kartlari_da_sifir_gerceklesenle_gosterir() {
        long kasa = finance.defaultCashAccountId();
        var giderKart = finance.createCard(null, "BTC-" + System.nanoTime(), "Test Gider Kartı",
                CardDirection.EXPENSE, false);
        finance.updateCardBudget(giderKart.getId(), new BigDecimal("1000"));

        LocalDate today = LocalDate.now();
        var rowsBeforeSpend = finance.budgetVsActual(today.minusDays(1), today.plusDays(1));
        var beforeRow = rowsBeforeSpend.stream().filter(r -> r.cardId() == giderKart.getId()).findFirst().orElseThrow();
        assertThat(beforeRow.actual()).isEqualByComparingTo("0");
        assertThat(beforeRow.variance()).isEqualByComparingTo("-1000"); // hiç harcanmadı

        finance.createManual(CashTxnType.PAYMENT, today, kasa, null, null, giderKart.getId(),
                new BigDecimal("300"), "kısmi harcama");

        var rowsAfterSpend = finance.budgetVsActual(today.minusDays(1), today.plusDays(1));
        var afterRow = rowsAfterSpend.stream().filter(r -> r.cardId() == giderKart.getId()).findFirst().orElseThrow();
        assertThat(afterRow.actual()).isEqualByComparingTo("-300"); // gider raporda negatif işaretlenir
        assertThat(afterRow.variance()).isEqualByComparingTo("-1300");
        assertThat(afterRow.usagePercent()).isEqualByComparingTo("-30.0000");
    }

    @Test
    void butcesi_olmayan_kart_butce_raporunda_gorunmez() {
        var kartBudgesiz = finance.createCard(null, "NOBTC-" + System.nanoTime(), "Bütçesiz Kart",
                CardDirection.EXPENSE, false);
        var rows = finance.budgetVsActual(LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));
        assertThat(rows).noneMatch(r -> r.cardId() == kartBudgesiz.getId());
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
