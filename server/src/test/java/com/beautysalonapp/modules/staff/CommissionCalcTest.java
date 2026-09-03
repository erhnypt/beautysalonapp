package com.beautysalonapp.modules.staff;

import com.beautysalonapp.modules.staff.domain.CommissionBasis;
import com.beautysalonapp.modules.staff.domain.CommissionCalc;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommissionCalcTest {

    @Test
    void oran_bazli() {
        assertThat(CommissionCalc.amount(CommissionBasis.RATE, new BigDecimal("10"), new BigDecimal("500")))
                .isEqualByComparingTo("50.00");
    }

    @Test
    void oran_yuvarlama() {
        // 333.33 * 15% = 49.9995 -> 50.00
        assertThat(CommissionCalc.amount(CommissionBasis.RATE, new BigDecimal("15"), new BigDecimal("333.33")))
                .isEqualByComparingTo("50.00");
    }

    @Test
    void sabit_tutar() {
        assertThat(CommissionCalc.amount(CommissionBasis.AMOUNT, new BigDecimal("75"), new BigDecimal("9999")))
                .isEqualByComparingTo("75.00");
    }

    @Test
    void sifir_baz() {
        assertThat(CommissionCalc.amount(CommissionBasis.RATE, new BigDecimal("20"), BigDecimal.ZERO))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void negatif_deger_reddedilir() {
        assertThatThrownBy(() -> CommissionCalc.amount(CommissionBasis.RATE, new BigDecimal("-1"), BigDecimal.TEN))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
