package com.beautysalonapp.modules.loyalty;

import com.beautysalonapp.modules.loyalty.domain.PointsCalc;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PointsCalcTest {

    @Test
    void kazanim_taban_puan_floor() {
        // 100 TL * 0.1 = 10 puan
        assertThat(PointsCalc.earn(new BigDecimal("100"), new BigDecimal("0.1"))).isEqualTo(10);
        // 95 TL * 0.1 = 9.5 -> floor 9
        assertThat(PointsCalc.earn(new BigDecimal("95"), new BigDecimal("0.1"))).isEqualTo(9);
    }

    @Test
    void gecersiz_girdiler_sifir() {
        assertThat(PointsCalc.earn(BigDecimal.ZERO, new BigDecimal("0.1"))).isZero();
        assertThat(PointsCalc.earn(new BigDecimal("100"), BigDecimal.ZERO)).isZero();
        assertThat(PointsCalc.earn(null, null)).isZero();
    }

    @Test
    void puan_degeri() {
        // 50 puan * 0.05 = 2.50 TL
        assertThat(PointsCalc.redemptionValue(50, new BigDecimal("0.05"))).isEqualByComparingTo("2.50");
        assertThat(PointsCalc.redemptionValue(0, new BigDecimal("0.05"))).isEqualByComparingTo("0.00");
    }

    @Test
    void max_kullanilabilir_min_bakiye_ve_tutar() {
        // bakiye 100, fatura 3 TL, oran 0.05 -> tutardan max 60 puan -> min(100,60)=60
        assertThat(PointsCalc.maxRedeemable(100, new BigDecimal("3.00"), new BigDecimal("0.05"))).isEqualTo(60);
        // bakiye 20, fatura 100 TL -> min(20, 2000)=20
        assertThat(PointsCalc.maxRedeemable(20, new BigDecimal("100"), new BigDecimal("0.05"))).isEqualTo(20);
        // bakiye 0 -> 0
        assertThat(PointsCalc.maxRedeemable(0, new BigDecimal("100"), new BigDecimal("0.05"))).isZero();
    }
}
