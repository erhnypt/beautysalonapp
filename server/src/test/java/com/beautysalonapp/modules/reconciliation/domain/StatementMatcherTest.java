package com.beautysalonapp.modules.reconciliation.domain;

import com.beautysalonapp.modules.reconciliation.domain.StatementMatcher.TxnView;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StatementMatcherTest {

    private static ParsedLine line(String date, String amount, String desc, String ref) {
        return new ParsedLine(LocalDate.parse(date), LocalDate.parse(date),
                new BigDecimal(amount), "TRY", desc, null, ref, "");
    }

    @Test
    void tutar_ve_tarih_tam_eslesirse_en_yuksek_skor() {
        ParsedLine l = line("2024-01-05", "1200.00", "MUSTERI TAHSILATI", "REF-1");
        TxnView t = new TxnView(1L, LocalDate.parse("2024-01-05"), new BigDecimal("1200.00"), "Fatura #9", "REF-1");

        List<MatchCandidate> s = StatementMatcher.suggest(l, List.of(t), Set.of());
        assertThat(s).hasSize(1);
        assertThat(s.get(0).txnId()).isEqualTo(1L);
        assertThat(s.get(0).score()).isEqualTo(100); // 50+30+20 kırpılmış (referans da eşleşti)
    }

    @Test
    void tutar_uymuyorsa_aday_degildir() {
        ParsedLine l = line("2024-01-05", "1200.00", "x", null);
        TxnView t = new TxnView(1L, LocalDate.parse("2024-01-05"), new BigDecimal("1199.00"), "x", null);
        assertThat(StatementMatcher.suggest(l, List.of(t), Set.of())).isEmpty();
    }

    @Test
    void tarih_toleransi_azalan_puan_verir() {
        ParsedLine l = line("2024-01-10", "500.00", null, null);
        TxnView exact = new TxnView(1L, LocalDate.parse("2024-01-10"), new BigDecimal("500.00"), null, null);
        TxnView oneDay = new TxnView(2L, LocalDate.parse("2024-01-11"), new BigDecimal("500.00"), null, null);
        TxnView threeDay = new TxnView(3L, LocalDate.parse("2024-01-13"), new BigDecimal("500.00"), null, null);
        TxnView farAway = new TxnView(4L, LocalDate.parse("2024-02-01"), new BigDecimal("500.00"), null, null);

        List<MatchCandidate> s = StatementMatcher.suggest(l, List.of(farAway, threeDay, oneDay, exact), Set.of());
        assertThat(s).extracting(MatchCandidate::txnId).containsExactly(1L, 2L, 3L, 4L); // skora göre azalan
        assertThat(s.get(0).score()).isEqualTo(80);  // 50+30
        assertThat(s.get(1).score()).isEqualTo(70);  // 50+20
        assertThat(s.get(2).score()).isEqualTo(62);  // 50+12
        assertThat(s.get(3).score()).isEqualTo(50);  // yalnızca tutar
    }

    @Test
    void referans_alt_dize_olarak_eslesirse_bonus_alir() {
        ParsedLine l = line("2024-01-05", "300.00", null, "HAVALE-REF-9988");
        TxnView t = new TxnView(1L, LocalDate.parse("2024-06-01"), new BigDecimal("300.00"), null, "REF-9988");
        List<MatchCandidate> s = StatementMatcher.suggest(l, List.of(t), Set.of());
        assertThat(s.get(0).score()).isEqualTo(70); // 50 tutar + 20 referans (tarih çok uzak, 0)
    }

    @Test
    void aciklama_ortak_kelimeler_bonus_verir() {
        ParsedLine l = line("2024-03-01", "80.00", "KIRA ODEMESI EMLAK OFIS", null);
        TxnView t = new TxnView(1L, LocalDate.parse("2024-03-01"), new BigDecimal("80.00"), "AYLIK KIRA EMLAK", null);
        List<MatchCandidate> s = StatementMatcher.suggest(l, List.of(t), Set.of());
        // 50 tutar + 30 tarih + açıklama ortak "kira","emlak" -> +8
        assertThat(s.get(0).score()).isGreaterThanOrEqualTo(88);
    }

    @Test
    void zaten_eslesmis_hareket_aday_kumesinden_cikarilir() {
        ParsedLine l = line("2024-01-05", "1200.00", null, null);
        TxnView t = new TxnView(1L, LocalDate.parse("2024-01-05"), new BigDecimal("1200.00"), null, null);
        assertThat(StatementMatcher.suggest(l, List.of(t), Set.of(1L))).isEmpty();
    }

    @Test
    void oneri_en_fazla_bes_aday_dondurur() {
        ParsedLine l = line("2024-01-05", "10.00", null, null);
        List<TxnView> many = java.util.stream.IntStream.range(0, 9)
                .mapToObj(i -> new TxnView(i, LocalDate.parse("2024-01-05"), new BigDecimal("10.00"), null, null))
                .toList();
        assertThat(StatementMatcher.suggest(l, many, Set.of())).hasSize(5);
    }

    @Test
    void otomatik_mutabakat_esik_altini_atamaz_ve_hareketi_iki_kez_kullanmaz() {
        ParsedLine strong = line("2024-01-05", "1000.00", "MUSTERI ABC", "REF-1");
        ParsedLine weak = line("2024-01-05", "1000.00", null, null); // aynı tutar, zayıf eşleşme

        TxnView good = new TxnView(1L, LocalDate.parse("2024-01-05"), new BigDecimal("1000.00"), "MUSTERI ABC odeme", "REF-1");
        TxnView farDate = new TxnView(2L, LocalDate.parse("2024-03-01"), new BigDecimal("1000.00"), null, null);

        Map<Integer, Long> result = StatementMatcher.autoReconcile(List.of(strong, weak), List.of(good, farDate));

        assertThat(result).containsEntry(0, 1L);   // güçlü eşleşme #1'i alır
        assertThat(result).doesNotContainKey(1);   // weak (yalnızca 50 puan) eşiğin altında kalır, atanmaz
    }

    @Test
    void ayni_tutarli_iki_satir_farkli_hareketlere_atanir() {
        ParsedLine l1 = line("2024-01-05", "500.00", "A", "REF-A");
        ParsedLine l2 = line("2024-01-06", "500.00", "B", "REF-B");
        TxnView t1 = new TxnView(1L, LocalDate.parse("2024-01-05"), new BigDecimal("500.00"), "A", "REF-A");
        TxnView t2 = new TxnView(2L, LocalDate.parse("2024-01-06"), new BigDecimal("500.00"), "B", "REF-B");

        Map<Integer, Long> result = StatementMatcher.autoReconcile(List.of(l1, l2), List.of(t1, t2));
        assertThat(result).containsEntry(0, 1L).containsEntry(1, 2L);
    }
}
