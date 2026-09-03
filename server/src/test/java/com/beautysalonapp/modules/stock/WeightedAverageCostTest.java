package com.beautysalonapp.modules.stock;

import com.beautysalonapp.modules.stock.domain.WeightedAverageCost;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class WeightedAverageCostTest {

    @Test
    void bos_baslangic() {
        WeightedAverageCost w = WeightedAverageCost.empty();
        assertThat(w.quantityBase()).isEqualByComparingTo("0");
        assertThat(w.avgUnitCost()).isEqualByComparingTo("0");
        assertThat(w.value()).isEqualByComparingTo("0");
    }

    @Test
    void ilk_giris_ortalamayi_belirler() {
        WeightedAverageCost w = WeightedAverageCost.empty()
                .receive(new BigDecimal("10"), new BigDecimal("25.00"));
        assertThat(w.quantityBase()).isEqualByComparingTo("10");
        assertThat(w.avgUnitCost()).isEqualByComparingTo("25.0000");
        assertThat(w.value()).isEqualByComparingTo("250.0000");
    }

    @Test
    void ikinci_giris_agirlikli_ortalama() {
        // 10 adet @ 25 + 30 adet @ 30 => (250 + 900) / 40 = 28.75
        WeightedAverageCost w = WeightedAverageCost.empty()
                .receive(new BigDecimal("10"), new BigDecimal("25"))
                .receive(new BigDecimal("30"), new BigDecimal("30"));
        assertThat(w.quantityBase()).isEqualByComparingTo("40");
        assertThat(w.avgUnitCost()).isEqualByComparingTo("28.7500");
    }

    @Test
    void cikis_ortalamayi_degistirmez() {
        WeightedAverageCost w = WeightedAverageCost.empty()
                .receive(new BigDecimal("40"), new BigDecimal("28.75"))
                .issue(new BigDecimal("15"));
        assertThat(w.quantityBase()).isEqualByComparingTo("25");
        assertThat(w.avgUnitCost()).isEqualByComparingTo("28.7500");
        assertThat(w.value()).isEqualByComparingTo("718.7500");
    }

    @Test
    void cikis_sonrasi_yeni_giris_dogru_ortalama() {
        // 25 @ 28.75 kalan, +25 @ 40 => (718.75 + 1000) / 50 = 34.375
        WeightedAverageCost w = new WeightedAverageCost(new BigDecimal("25"), new BigDecimal("28.75"))
                .receive(new BigDecimal("25"), new BigDecimal("40"));
        assertThat(w.avgUnitCost()).isEqualByComparingTo("34.3750");
        assertThat(w.quantityBase()).isEqualByComparingTo("50");
    }

    @Test
    void negatif_stoga_dusen_cikis_miktari_dusurur_ama_ortalama_korur() {
        WeightedAverageCost w = new WeightedAverageCost(new BigDecimal("5"), new BigDecimal("10"))
                .issue(new BigDecimal("8"));
        assertThat(w.quantityBase()).isEqualByComparingTo("-3");
        assertThat(w.avgUnitCost()).isEqualByComparingTo("10.0000");
        assertThat(w.isNegative()).isTrue();
    }

    @Test
    void sifir_veya_negatif_miktarli_giris_cikis_etkisiz() {
        WeightedAverageCost base = new WeightedAverageCost(new BigDecimal("10"), new BigDecimal("5"));
        assertThat(base.receive(BigDecimal.ZERO, new BigDecimal("99"))).isEqualTo(base);
        assertThat(base.receive(new BigDecimal("-1"), new BigDecimal("99"))).isEqualTo(base);
        assertThat(base.issue(BigDecimal.ZERO)).isEqualTo(base);
        assertThat(base.issue(new BigDecimal("-1"))).isEqualTo(base);
    }

    @Test
    void kesirli_miktar_ve_yuvarlama() {
        // 3.333333 @ 12.00 + 6.666667 @ 15.00 => (40.000 + 100.00005) / 10 = 14.0000
        WeightedAverageCost w = WeightedAverageCost.empty()
                .receive(new BigDecimal("3.333333"), new BigDecimal("12.00"))
                .receive(new BigDecimal("6.666667"), new BigDecimal("15.00"));
        assertThat(w.quantityBase()).isEqualByComparingTo("10.000000");
        assertThat(w.avgUnitCost()).isEqualByComparingTo("14.0000");
    }
}
