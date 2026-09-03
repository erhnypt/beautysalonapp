package com.beautysalonapp.core.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void olcek_dorde_sabitlenir() {
        assertThat(Money.of("10.5", "TRY").getAmount()).isEqualByComparingTo("10.5000");
    }

    @Test
    void ayni_para_birimi_toplanir() {
        Money a = Money.of("100.00", "TRY");
        Money b = Money.of("49.99", "TRY");
        assertThat(a.add(b).getAmount()).isEqualByComparingTo("149.99");
    }

    @Test
    void farkli_para_birimi_toplanamaz() {
        assertThatThrownBy(() -> Money.of("1", "TRY").add(Money.of("1", "USD")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void carpim_ve_negatiflik() {
        Money m = Money.of("10", "TRY").multiply(new BigDecimal("-2"));
        assertThat(m.isNegative()).isTrue();
        assertThat(m.getAmount()).isEqualByComparingTo("-20.0000");
    }

    @Test
    void gecersiz_para_birimi_reddedilir() {
        assertThatThrownBy(() -> Money.of("1", "TL")).isInstanceOf(IllegalArgumentException.class);
    }
}
