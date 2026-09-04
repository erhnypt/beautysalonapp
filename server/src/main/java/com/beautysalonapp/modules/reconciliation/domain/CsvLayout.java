package com.beautysalonapp.modules.reconciliation.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * CSV ekstre sütun eşlemesi. Sütun indeksleri 0-tabanlı; kullanılmayan alanlar {@code -1}.
 * Tutar ya tek işaretli {@code amountCol} ile ya da ayrı {@code debitCol}/{@code creditCol}
 * ile verilir.
 */
public record CsvLayout(
        char delimiter,
        char decimalSeparator,
        String datePattern,
        int dateCol,
        int amountCol,
        int debitCol,
        int creditCol,
        int descriptionCol,
        int refCol,
        int bookingDateCol,
        boolean hasHeader) {

    public boolean usesSeparateDebitCredit() {
        return amountCol < 0 && (debitCol >= 0 || creditCol >= 0);
    }

    // --- otomatik tespit ---------------------------------------------------

    private static final List<String> DATE_KEYS = List.of("valör", "valor", "value date", "işlem tarihi",
            "islem tarihi", "tarih", "date");
    private static final List<String> BOOKING_KEYS = List.of("işlem tarihi", "islem tarihi", "booking", "muhasebe");
    private static final List<String> AMOUNT_KEYS = List.of("işlem tutarı", "islem tutari", "tutar", "amount");
    private static final List<String> DEBIT_KEYS = List.of("borç", "borc", "çıkan", "cikan", "debit", "gider");
    private static final List<String> CREDIT_KEYS = List.of("alacak", "giren", "credit", "gelir");
    private static final List<String> DESC_KEYS = List.of("açıklama", "aciklama", "description", "detay", "işlem açıklaması");
    private static final List<String> REF_KEYS = List.of("referans", "reference", "dekont", "işlem no", "islem no", "fiş no");

    /**
     * Başlık satırından yaygın Türk/İngilizce banka sütun adlarını tanıyarak bir düzen kurar.
     * Tanınamayan alan {@code -1} kalır; en azından tarih ve (tutar veya borç/alacak) gerekir.
     */
    public static CsvLayout detect(String headerLine, char delimiter) {
        String[] cols = splitRaw(headerLine, delimiter);
        String[] norm = Arrays.stream(cols)
                .map(s -> s.trim().toLowerCase(Locale.ROOT).replace("﻿", ""))
                .toArray(String[]::new);

        int date = indexOfAny(norm, DATE_KEYS);
        int booking = indexOfAny(norm, BOOKING_KEYS);
        if (booking == date) {
            booking = -1;
        }
        int amount = indexOfAny(norm, AMOUNT_KEYS);
        int debit = indexOfAny(norm, DEBIT_KEYS);
        int credit = indexOfAny(norm, CREDIT_KEYS);
        if (debit >= 0 || credit >= 0) {
            amount = -1; // ayrı borç/alacak öncelikli
        }
        int desc = indexOfAny(norm, DESC_KEYS);
        int ref = indexOfAny(norm, REF_KEYS);

        // Ondalık ayıracı: TR ekstrelerinde neredeyse her zaman virgül.
        char dec = ',';
        return new CsvLayout(delimiter, dec, "dd.MM.yyyy", date, amount, debit, credit, desc, ref, booking, true);
    }

    private static int indexOfAny(String[] cols, List<String> keys) {
        for (String key : keys) {
            for (int i = 0; i < cols.length; i++) {
                if (cols[i].contains(key)) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** Basit, tırnak-duyarlı CSV satır bölme. */
    public static String[] splitRaw(String line, char delimiter) {
        java.util.List<String> out = new java.util.ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == delimiter && !inQuotes) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out.toArray(new String[0]);
    }
}
