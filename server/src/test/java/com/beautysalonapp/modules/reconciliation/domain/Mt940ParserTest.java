package com.beautysalonapp.modules.reconciliation.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Mt940ParserTest {

    private static final String SAMPLE = """
            :20:STMT240115
            :25:TR330006100519786457841326
            :28C:00012/001
            :60F:C240101TRY10000,00
            :61:2401030103DR250,50NTRFFATURA123//BREF0001
            :86:?20KIRA ODEMESI ?32EMLAK A.S.
            :61:2401050105CR1200,00NTRFTHS456//BREF0002
            :86:MUSTERI TAHSILATI ORNEK
            :61:2401100110D75,00NCHGBANKA MASRAFI//BREF0003
            :86:HESAP ISLETIM UCRETI
            :61:2401120112RD75,00NRTIIADE//BREF0004
            :86:MASRAF IADESI
            :62F:C240131TRY10949,50
            :64:C240131TRY10949,50
            """;

    @Test
    void baslik_ve_bakiyeler_okunur() {
        ParsedStatement s = Mt940Parser.parse(SAMPLE);
        assertThat(s.format()).isEqualTo(StatementFormat.MT940);
        assertThat(s.statementRef()).isEqualTo("00012/001");
        assertThat(s.currency()).isEqualTo("TRY");
        assertThat(s.openingBalance()).isEqualByComparingTo("10000.00");
        assertThat(s.closingBalance()).isEqualByComparingTo("10949.50");
        assertThat(s.periodStart()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(s.periodEnd()).isEqualTo(LocalDate.of(2024, 1, 31));
    }

    @Test
    void satirlar_isaretli_tutarla_cozulur() {
        ParsedStatement s = Mt940Parser.parse(SAMPLE);
        assertThat(s.lines()).hasSize(4);

        ParsedLine l1 = s.lines().get(0); // D → −
        assertThat(l1.valueDate()).isEqualTo(LocalDate.of(2024, 1, 3));
        assertThat(l1.amount()).isEqualByComparingTo("-250.50");
        assertThat(l1.bankRef()).isEqualTo("BREF0001");
        assertThat(l1.description()).contains("KIRA ODEMESI");
        assertThat(l1.counterparty()).isEqualTo("EMLAK A.S.");

        ParsedLine l2 = s.lines().get(1); // C → +
        assertThat(l2.amount()).isEqualByComparingTo("1200.00");
        assertThat(l2.isInflow()).isTrue();

        ParsedLine l3 = s.lines().get(2); // D → −
        assertThat(l3.amount()).isEqualByComparingTo("-75.00");

        ParsedLine l4 = s.lines().get(3); // RD → + (borç iptali)
        assertThat(l4.amount()).isEqualByComparingTo("75.00");
    }

    @Test
    void satirlardan_hesaplanan_kapanis_dosyayla_tutar() {
        ParsedStatement s = Mt940Parser.parse(SAMPLE);
        assertThat(s.computedClosing()).isEqualByComparingTo("10949.50");
        assertThat(s.balanceConsistent()).isTrue();
    }

    @Test
    void blok4_sarmali_de_calisir() {
        String wrapped = "{1:F01BANKTR00XXX0000000000}{2:O9401200...}{4:\n" + SAMPLE + "\n-}";
        ParsedStatement s = Mt940Parser.parse(wrapped);
        assertThat(s.lines()).hasSize(4);
        assertThat(s.closingBalance()).isEqualByComparingTo("10949.50");
    }

    @Test
    void bos_girdi_reddedilir() {
        assertThatThrownBy(() -> Mt940Parser.parse("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void bozuk_61_satiri_hata_verir() {
        String bad = ":20:X\n:60F:C240101TRY0,00\n:61:BOZUK\n:62F:C240101TRY0,00\n";
        assertThatThrownBy(() -> Mt940Parser.parse(bad))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(":61:");
    }

    @Test
    void ondalik_kirpma_dogru() {
        assertThat(Mt940Parser.decimal("1234,5")).isEqualByComparingTo("1234.50");
        assertThat(Mt940Parser.decimal("1234,")).isEqualByComparingTo("1234.00");
        assertThat(Mt940Parser.decimal("0,05")).isEqualByComparingTo("0.05");
    }
}
