package com.beautysalonapp.modules.reconciliation.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Ekstre satırı ↔ kasa hareketi eşleştirme skoru (framework'süz, saf).
 *
 * <p>Skor (0–100): tutar tam eşleşme +50 (yoksa aday değil) · tarih tam +30 / ±1g +20 /
 * ±3g +12 / ±7g +5 · referans/{@code docNo} içerir +20 · açıklama ortak kelime +≤10.
 */
public final class StatementMatcher {

    /** Bu skorun üzerindeki öneriler otomatik uygulanabilir. */
    public static final int AUTO_THRESHOLD = 80;
    private static final int SUGGEST_LIMIT = 5;
    private static final Pattern TOKEN = Pattern.compile("[a-z0-9]{4,}");

    private StatementMatcher() {
    }

    /** Bir kasa hareketinin, mutabakat için gereken alanları (banka hesabı bakışıyla işaretli tutar). */
    public record TxnView(long id, LocalDate date, BigDecimal signedAmount, String description, String docNo) {
    }

    /**
     * {@code line} için aday hareketleri skorlar. {@code alreadyMatched} kümesindeki id'ler
     * elenir. Yalnızca tutarı (kuruşuna) eşleşen adaylar döner, skora göre azalan, en çok {@value #SUGGEST_LIMIT}.
     */
    public static List<MatchCandidate> suggest(ParsedLine line, List<TxnView> candidates, Set<Long> alreadyMatched) {
        Set<Long> used = alreadyMatched == null ? Set.of() : alreadyMatched;
        List<MatchCandidate> out = new ArrayList<>();
        Set<String> lineTokens = tokens(safe(line.description()) + " " + safe(line.counterparty()));

        for (TxnView t : candidates) {
            if (used.contains(t.id())) {
                continue;
            }
            if (line.amount().compareTo(t.signedAmount()) != 0) {
                continue; // tutar eşleşmesi zorunlu
            }
            int score = 50;
            StringBuilder why = new StringBuilder("tutar");

            long dd = Math.abs(ChronoUnit.DAYS.between(line.valueDate(), t.date()));
            if (dd == 0) {
                score += 30;
                why.append(" +tarih");
            } else if (dd <= 1) {
                score += 20;
                why.append(" +tarih(±1)");
            } else if (dd <= 3) {
                score += 12;
                why.append(" +tarih(±3)");
            } else if (dd <= 7) {
                score += 5;
                why.append(" +tarih(±7)");
            }

            if (refMatch(line.bankRef(), t.docNo())) {
                score += 20;
                why.append(" +referans");
            }

            int overlap = 0;
            Set<String> tt = tokens(safe(t.description()));
            for (String tok : tt) {
                if (lineTokens.contains(tok)) {
                    overlap++;
                }
            }
            if (overlap > 0) {
                int bonus = Math.min(10, overlap * 4);
                score += bonus;
                why.append(" +açıklama");
            }

            out.add(new MatchCandidate(t.id(), Math.min(100, score), why.toString()));
        }
        out.sort(null);
        return out.size() > SUGGEST_LIMIT ? new ArrayList<>(out.subList(0, SUGGEST_LIMIT)) : out;
    }

    /**
     * Tüm satırlar için açgözlü 1:1 otomatik eşleştirme. Satırlar en iyi skorlarına göre azalan
     * işlenir; her hareket en fazla bir kez kullanılır; yalnızca skor ≥ {@link #AUTO_THRESHOLD}
     * olanlar atanır. Dönen harita: satır indeksi → hareket id.
     */
    public static Map<Integer, Long> autoReconcile(List<ParsedLine> lines, List<TxnView> txns) {
        return autoReconcile(lines, txns, AUTO_THRESHOLD);
    }

    /** {@code threshold} özelleştirilebilir eşik ile (ayar: {@code reconciliation.autoMatchThreshold}). */
    public static Map<Integer, Long> autoReconcile(List<ParsedLine> lines, List<TxnView> txns, int threshold) {
        record Ranked(int lineIdx, MatchCandidate best) {
        }
        List<Ranked> ranked = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            List<MatchCandidate> s = suggest(lines.get(i), txns, Set.of());
            if (!s.isEmpty() && s.get(0).score() >= threshold) {
                ranked.add(new Ranked(i, s.get(0)));
            }
        }
        ranked.sort((a, b) -> Integer.compare(b.best().score(), a.best().score()));

        Map<Integer, Long> result = new LinkedHashMap<>();
        Set<Long> usedTxns = new HashSet<>();
        for (Ranked r : ranked) {
            // en iyi aday zaten kullanıldıysa bir sonraki uygun adayı dene
            List<MatchCandidate> s = suggest(lines.get(r.lineIdx()), txns, usedTxns);
            if (!s.isEmpty() && s.get(0).score() >= threshold) {
                result.put(r.lineIdx(), s.get(0).txnId());
                usedTxns.add(s.get(0).txnId());
            }
        }
        return result;
    }

    private static boolean refMatch(String bankRef, String docNo) {
        String a = normalizeRef(bankRef);
        String b = normalizeRef(docNo);
        if (a.length() < 4 || b.length() < 4) {
            return false;
        }
        return a.contains(b) || b.contains(a);
    }

    private static String normalizeRef(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static Set<String> tokens(String s) {
        Set<String> out = new HashSet<>();
        var m = TOKEN.matcher(s.toLowerCase(Locale.ROOT));
        while (m.find()) {
            out.add(m.group());
        }
        return out;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
