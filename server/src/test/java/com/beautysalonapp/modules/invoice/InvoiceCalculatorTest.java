package com.beautysalonapp.modules.invoice;

import com.beautysalonapp.modules.invoice.domain.InvoiceCalculator;
import com.beautysalonapp.modules.invoice.domain.InvoiceCalculator.LineInput;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvoiceCalculatorTest {

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    @Test
    void basit_satir_kdv_dahil() {
        var r = InvoiceCalculator.line(new LineInput(bd("2"), bd("100"), bd("0"), bd("20")));
        assertThat(r.net()).isEqualByComparingTo("200.00");
        assertThat(r.vat()).isEqualByComparingTo("40.00");
        assertThat(r.total()).isEqualByComparingTo("240.00");
    }

    @Test
    void indirimli_satir() {
        // 3 * 50 = 150 ; %10 indirim => net 135 ; %20 kdv => 27 ; total 162
        var r = InvoiceCalculator.line(new LineInput(bd("3"), bd("50"), bd("10"), bd("20")));
        assertThat(r.net()).isEqualByComparingTo("135.00");
        assertThat(r.vat()).isEqualByComparingTo("27.00");
        assertThat(r.total()).isEqualByComparingTo("162.00");
    }

    @Test
    void kdv_sifir() {
        var r = InvoiceCalculator.line(new LineInput(bd("1"), bd("99.99"), bd("0"), bd("0")));
        assertThat(r.vat()).isEqualByComparingTo("0.00");
        assertThat(r.total()).isEqualByComparingTo("99.99");
    }

    @Test
    void yuvarlama_kdv() {
        // net 33.33 * 10% = 3.333 -> 3.33
        var r = InvoiceCalculator.line(new LineInput(bd("1"), bd("33.33"), bd("0"), bd("10")));
        assertThat(r.vat()).isEqualByComparingTo("3.33");
        assertThat(r.total()).isEqualByComparingTo("36.66");
    }

    @Test
    void coklu_satir_toplamlari() {
        var totals = InvoiceCalculator.totals(List.of(
                new LineInput(bd("2"), bd("100"), bd("0"), bd("20")),   // net 200 vat 40
                new LineInput(bd("1"), bd("50"), bd("10"), bd("10"))    // net 45 vat 4.5
        ));
        assertThat(totals.subtotal()).isEqualByComparingTo("250.00");     // 200 + 50 brüt
        assertThat(totals.discountTotal()).isEqualByComparingTo("5.00");  // 50 - 45
        assertThat(totals.vatTotal()).isEqualByComparingTo("44.50");
        assertThat(totals.grandTotal()).isEqualByComparingTo("289.50");   // 240 + 49.5
        assertThat(totals.lines()).hasSize(2);
    }

    @Test
    void kesirli_miktar() {
        // 1.5 * 40 = 60 ; net 60 ; %8 kdv = 4.80
        var r = InvoiceCalculator.line(new LineInput(bd("1.5"), bd("40"), bd("0"), bd("8")));
        assertThat(r.net()).isEqualByComparingTo("60.00");
        assertThat(r.vat()).isEqualByComparingTo("4.80");
    }

    @Test
    void gecersiz_girdiler() {
        assertThatThrownBy(() -> InvoiceCalculator.line(new LineInput(bd("0"), bd("10"), bd("0"), bd("20"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InvoiceCalculator.line(new LineInput(bd("1"), bd("10"), bd("120"), bd("20"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InvoiceCalculator.line(new LineInput(null, bd("10"), bd("0"), bd("20"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InvoiceCalculator.totals(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tam_indirim_net_sifir() {
        var r = InvoiceCalculator.line(new LineInput(bd("1"), bd("100"), bd("100"), bd("20")));
        assertThat(r.net()).isEqualByComparingTo("0.00");
        assertThat(r.total()).isEqualByComparingTo("0.00");
    }
}
