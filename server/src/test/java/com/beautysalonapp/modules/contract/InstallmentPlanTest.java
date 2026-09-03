package com.beautysalonapp.modules.contract;

import com.beautysalonapp.modules.contract.domain.InstallmentPeriod;
import com.beautysalonapp.modules.contract.domain.InstallmentPlan;
import com.beautysalonapp.modules.contract.domain.InstallmentPlan.PlannedInstallment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Plan §9.8: kuruş farkı son taksite, ay sonu vade düzeltmesi (31 Ocak → 28/29 Şubat).
 * Mali hesaplama — testsiz merge yasak (plan §18).
 */
class InstallmentPlanTest {

    @Test
    void esit_bolunen_tutar() {
        var plan = InstallmentPlan.generate(bd("1200.00"), bd("0"), 12,
                LocalDate.of(2026, 1, 15), InstallmentPeriod.AYLIK);
        assertThat(plan).hasSize(12);
        assertThat(plan).allSatisfy(p -> assertThat(p.amount()).isEqualByComparingTo("100.00"));
        assertThat(InstallmentPlan.sum(plan)).isEqualByComparingTo("1200.00");
    }

    @Test
    void kurus_farki_son_taksite_gider() {
        // 1000 / 3 = 333.33 ; son = 1000 - 666.66 = 333.34
        var plan = InstallmentPlan.generate(bd("1000.00"), bd("0"), 3,
                LocalDate.of(2026, 3, 10), InstallmentPeriod.AYLIK);
        assertThat(plan.get(0).amount()).isEqualByComparingTo("333.33");
        assertThat(plan.get(1).amount()).isEqualByComparingTo("333.33");
        assertThat(plan.get(2).amount()).isEqualByComparingTo("333.34");
        assertThat(InstallmentPlan.sum(plan)).isEqualByComparingTo("1000.00");
    }

    @Test
    void pesinat_dusuldukten_sonra_kalan_taksitlenir() {
        var plan = InstallmentPlan.generate(bd("5000.00"), bd("2000.00"), 6,
                LocalDate.of(2026, 6, 1), InstallmentPeriod.AYLIK);
        assertThat(InstallmentPlan.sum(plan)).isEqualByComparingTo("3000.00");
        assertThat(plan.get(0).amount()).isEqualByComparingTo("500.00");
    }

    @Test
    void yuvarlama_yukari_ve_son_taksit_asagi() {
        // 100.00 / 3 => 33.33 (HALF_UP 33.333 -> 33.33); son = 100 - 66.66 = 33.34
        var plan = InstallmentPlan.generate(bd("100.00"), bd("0"), 3,
                LocalDate.of(2026, 1, 1), InstallmentPeriod.AYLIK);
        assertThat(plan).extracting(p -> p.amount().toPlainString())
                .containsExactly("33.33", "33.33", "33.34");
    }

    @Test
    void yuvarlama_net_fazla_uretirse_son_taksit_kuculur() {
        // 10.00 / 3 => 3.33 ; son = 10 - 6.66 = 3.34  (kalan artı)
        // 20.00 / 3 => 6.67 (6.666..HALF_UP) ; ilk iki = 13.34 ; son = 20 - 13.34 = 6.66
        var plan = InstallmentPlan.generate(bd("20.00"), bd("0"), 3,
                LocalDate.of(2026, 1, 1), InstallmentPeriod.AYLIK);
        assertThat(plan).extracting(p -> p.amount().toPlainString())
                .containsExactly("6.67", "6.67", "6.66");
        assertThat(InstallmentPlan.sum(plan)).isEqualByComparingTo("20.00");
    }

    @Test
    void tek_taksit_tum_kalani_alir() {
        var plan = InstallmentPlan.generate(bd("777.77"), bd("0"), 1,
                LocalDate.of(2026, 2, 20), InstallmentPeriod.AYLIK);
        assertThat(plan).hasSize(1);
        assertThat(plan.get(0).amount()).isEqualByComparingTo("777.77");
        assertThat(plan.get(0).dueDate()).isEqualTo(LocalDate.of(2026, 2, 20));
    }

    @Test
    void ay_sonu_31_ocak_28_subata_duzeltilir_2026() {
        var plan = InstallmentPlan.generate(bd("300.00"), bd("0"), 3,
                LocalDate.of(2026, 1, 31), InstallmentPeriod.AYLIK);
        assertThat(plan.get(0).dueDate()).isEqualTo(LocalDate.of(2026, 1, 31));
        assertThat(plan.get(1).dueDate()).isEqualTo(LocalDate.of(2026, 2, 28)); // 2026 artık yıl değil
        assertThat(plan.get(2).dueDate()).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @Test
    void ay_sonu_29_subat_artik_yil_2028() {
        var plan = InstallmentPlan.generate(bd("400.00"), bd("0"), 2,
                LocalDate.of(2028, 1, 31), InstallmentPeriod.AYLIK);
        assertThat(plan.get(1).dueDate()).isEqualTo(LocalDate.of(2028, 2, 29)); // 2028 artık yıl
    }

    @Test
    void ay_sonu_30_gun_duzeltmesi() {
        var plan = InstallmentPlan.generate(bd("300.00"), bd("0"), 3,
                LocalDate.of(2026, 8, 31), InstallmentPeriod.AYLIK);
        assertThat(plan.get(1).dueDate()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(plan.get(2).dueDate()).isEqualTo(LocalDate.of(2026, 10, 31));
    }

    @Test
    void haftalik_vade() {
        var plan = InstallmentPlan.generate(bd("400.00"), bd("0"), 4,
                LocalDate.of(2026, 1, 5), InstallmentPeriod.HAFTALIK);
        assertThat(plan.get(0).dueDate()).isEqualTo(LocalDate.of(2026, 1, 5));
        assertThat(plan.get(1).dueDate()).isEqualTo(LocalDate.of(2026, 1, 12));
        assertThat(plan.get(3).dueDate()).isEqualTo(LocalDate.of(2026, 1, 26));
    }

    @Test
    void tamami_pesin_ise_bos_plan() {
        var plan = InstallmentPlan.generate(bd("1500.00"), bd("1500.00"), 6,
                LocalDate.of(2026, 1, 1), InstallmentPeriod.AYLIK);
        assertThat(plan).isEmpty();
    }

    @Test
    void gecersiz_girdiler_reddedilir() {
        assertThatThrownBy(() -> InstallmentPlan.generate(bd("0"), bd("0"), 3,
                LocalDate.of(2026, 1, 1), InstallmentPeriod.AYLIK)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InstallmentPlan.generate(bd("100"), bd("200"), 3,
                LocalDate.of(2026, 1, 1), InstallmentPeriod.AYLIK)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InstallmentPlan.generate(bd("100"), bd("0"), 0,
                LocalDate.of(2026, 1, 1), InstallmentPeriod.AYLIK)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InstallmentPlan.generate(bd("100"), bd("-1"), 3,
                LocalDate.of(2026, 1, 1), InstallmentPeriod.AYLIK)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InstallmentPlan.generate(bd("100"), bd("0"), 3,
                null, InstallmentPeriod.AYLIK)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buyuk_adet_ve_kesirli_tutar_toplam_korunur() {
        var plan = InstallmentPlan.generate(bd("9999.99"), bd("0.01"), 24,
                LocalDate.of(2026, 1, 15), InstallmentPeriod.AYLIK);
        assertThat(plan).hasSize(24);
        assertThat(InstallmentPlan.sum(plan)).isEqualByComparingTo("9999.98");
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }
}
