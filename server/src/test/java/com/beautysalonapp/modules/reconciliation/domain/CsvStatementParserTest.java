package com.beautysalonapp.modules.reconciliation.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvStatementParserTest {

    @Test
    void tek_isaretli_tutar_sutunu_tr_ondalikla_okunur() {
        String csv = """
                Tarih;Açıklama;Tutar;Referans
                05.01.2024;MUSTERI TAHSILATI;1.200,00;REF-1
                10.01.2024;BANKA MASRAFI;-75,00;REF-2
                """;
        CsvLayout layout = new CsvLayout(';', ',', "dd.MM.yyyy", 0, 2, -1, -1, 1, 3, -1, true);
        ParsedStatement s = CsvStatementParser.parse(csv, layout, "TRY");

        assertThat(s.format()).isEqualTo(StatementFormat.CSV);
        assertThat(s.lines()).hasSize(2);
        assertThat(s.lines().get(0).valueDate()).isEqualTo(LocalDate.of(2024, 1, 5));
        assertThat(s.lines().get(0).amount()).isEqualByComparingTo("1200.00");
        assertThat(s.lines().get(0).description()).isEqualTo("MUSTERI TAHSILATI");
        assertThat(s.lines().get(0).bankRef()).isEqualTo("REF-1");
        assertThat(s.lines().get(1).amount()).isEqualByComparingTo("-75.00");
    }

    @Test
    void ayri_borc_alacak_sutunlari_isarete_cevrilir() {
        String csv = """
                Tarih,Açıklama,Borç,Alacak
                01.02.2024,Kira,500.00,
                02.02.2024,Tahsilat,,1000.00
                """;
        CsvLayout layout = new CsvLayout(',', '.', "dd.MM.yyyy", 0, -1, 2, 3, 1, -1, -1, true);
        ParsedStatement s = CsvStatementParser.parse(csv, layout, "TRY");

        assertThat(s.lines()).hasSize(2);
        assertThat(s.lines().get(0).amount()).isEqualByComparingTo("-500.00");
        assertThat(s.lines().get(1).amount()).isEqualByComparingTo("1000.00");
    }

    @Test
    void baslik_tespiti_yaygin_turkce_sutunlari_taniyor() {
        String header = "İşlem Tarihi;Açıklama;Tutar;Referans";
        CsvLayout layout = CsvLayout.detect(header, ';');
        assertThat(layout.dateCol()).isEqualTo(0);
        assertThat(layout.descriptionCol()).isEqualTo(1);
        assertThat(layout.amountCol()).isEqualTo(2);
        assertThat(layout.refCol()).isEqualTo(3);
    }

    @Test
    void baslik_tespiti_borc_alacak_sutunlari() {
        String header = "Tarih,Açıklama,Borç,Alacak,Referans";
        CsvLayout layout = CsvLayout.detect(header, ',');
        assertThat(layout.amountCol()).isEqualTo(-1);
        assertThat(layout.debitCol()).isEqualTo(2);
        assertThat(layout.creditCol()).isEqualTo(3);
    }

    @Test
    void tirnakli_alanlar_ayirici_iceriyorsa_bolunmez() {
        String csv = """
                Tarih;Açıklama;Tutar
                01.03.2024;"ACME; Ltd. Şti.";100,00
                """;
        CsvLayout layout = new CsvLayout(';', ',', "dd.MM.yyyy", 0, 2, -1, -1, 1, -1, -1, true);
        ParsedStatement s = CsvStatementParser.parse(csv, layout, "TRY");
        assertThat(s.lines().get(0).description()).isEqualTo("ACME; Ltd. Şti.");
    }

    @Test
    void sifir_tutarli_satir_atlanir() {
        String csv = """
                Tarih;Açıklama;Tutar
                01.01.2024;Sıfır;0,00
                02.01.2024;Gerçek;50,00
                """;
        CsvLayout layout = new CsvLayout(';', ',', "dd.MM.yyyy", 0, 2, -1, -1, 1, -1, -1, true);
        ParsedStatement s = CsvStatementParser.parse(csv, layout, "TRY");
        assertThat(s.lines()).hasSize(1);
        assertThat(s.lines().get(0).amount()).isEqualByComparingTo("50.00");
    }

    @Test
    void eksik_duzen_reddedilir() {
        CsvLayout layout = new CsvLayout(',', ',', "dd.MM.yyyy", -1, -1, -1, -1, -1, -1, -1, true);
        assertThatThrownBy(() -> CsvStatementParser.parse("a,b\n1,2\n", layout, "TRY"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseAmount_binlik_ve_parantez_negatif() {
        assertThat(CsvStatementParser.parseAmount("1.234,56", ',')).isEqualByComparingTo("1234.56");
        assertThat(CsvStatementParser.parseAmount("(500,00)", ',')).isEqualByComparingTo(new BigDecimal("-500.00"));
        assertThat(CsvStatementParser.parseAmount("", ',')).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
