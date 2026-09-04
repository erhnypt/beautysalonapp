package com.beautysalonapp.modules.reconciliation.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Serbest CSV banka ekstresi ayrıştırıcı (framework'süz). Sütun eşlemesi {@link CsvLayout}
 * ile verilir; başlıktan otomatik kurmak için {@link CsvLayout#detect}.
 *
 * <p>Tutar iki biçimde olabilir:
 * <ul>
 *   <li>tek işaretli sütun ({@code amountCol}) — "−1.234,56" gibi,</li>
 *   <li>ayrı borç/alacak sütunları — borç negatife çevrilir, alacak pozitif.</li>
 * </ul>
 * Binlik ayıracı ({@code decimalSeparator}'ın tersi) temizlenir.
 */
public final class CsvStatementParser {

    private CsvStatementParser() {
    }

    public static ParsedStatement parse(String csv, CsvLayout layout, String currency) {
        if (csv == null || csv.isBlank()) {
            throw new IllegalArgumentException("Boş CSV");
        }
        if (layout.dateCol() < 0 || (layout.amountCol() < 0 && layout.debitCol() < 0 && layout.creditCol() < 0)) {
            throw new IllegalArgumentException("CSV düzeni eksik: tarih ve tutar (veya borç/alacak) sütunu gerekli");
        }
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern(layout.datePattern());
        String[] rows = csv.replace("\r\n", "\n").replace("\r", "\n").split("\n");

        List<ParsedLine> lines = new ArrayList<>();
        int start = layout.hasHeader() ? 1 : 0;
        for (int r = start; r < rows.length; r++) {
            String raw = rows[r];
            if (raw.isBlank()) {
                continue;
            }
            String[] c = CsvLayout.splitRaw(raw, layout.delimiter());
            LocalDate valueDate = parseDate(get(c, layout.dateCol()), fmt, r + 1);
            LocalDate bookingDate = layout.bookingDateCol() >= 0
                    ? tryDate(get(c, layout.bookingDateCol()), fmt) : valueDate;
            BigDecimal amount = layout.usesSeparateDebitCredit()
                    ? debitCredit(get(c, layout.debitCol()), get(c, layout.creditCol()), layout.decimalSeparator())
                    : parseAmount(get(c, layout.amountCol()), layout.decimalSeparator());
            if (amount.signum() == 0) {
                continue; // 0 tutarlı satır atlanır
            }
            String desc = layout.descriptionCol() >= 0 ? get(c, layout.descriptionCol()).trim() : null;
            String ref = layout.refCol() >= 0 ? get(c, layout.refCol()).trim() : null;
            lines.add(new ParsedLine(valueDate, bookingDate, amount, currency, desc, null, ref, raw.trim()));
        }
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("CSV'den hiç geçerli satır çıkarılamadı");
        }
        LocalDate min = lines.stream().map(ParsedLine::valueDate).min(LocalDate::compareTo).orElse(null);
        LocalDate max = lines.stream().map(ParsedLine::valueDate).max(LocalDate::compareTo).orElse(null);
        return new ParsedStatement(StatementFormat.CSV, null, null, currency,
                null, null, min, max, lines);
    }

    private static String get(String[] arr, int i) {
        return (i >= 0 && i < arr.length) ? arr[i] : "";
    }

    private static LocalDate parseDate(String s, DateTimeFormatter fmt, int rowNo) {
        LocalDate d = tryDate(s, fmt);
        if (d == null) {
            throw new IllegalArgumentException("Satır " + rowNo + ": tarih çözümlenemedi: '" + s + "'");
        }
        return d;
    }

    private static LocalDate tryDate(String s, DateTimeFormatter fmt) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim();
        try {
            return LocalDate.parse(t, fmt);
        } catch (DateTimeParseException ignored) {
            // yaygın alternatifler
            for (String p : new String[]{"yyyy-MM-dd", "dd/MM/yyyy", "dd.MM.yyyy", "MM/dd/yyyy", "yyyy/MM/dd"}) {
                try {
                    return LocalDate.parse(t, DateTimeFormatter.ofPattern(p));
                } catch (DateTimeParseException ignored2) {
                    // dene
                }
            }
            return null;
        }
    }

    private static BigDecimal debitCredit(String debit, String credit, char dec) {
        BigDecimal d = parseAmount(debit, dec).abs();
        BigDecimal c = parseAmount(credit, dec).abs();
        return c.subtract(d); // alacak +, borç −
    }

    static BigDecimal parseAmount(String s, char dec) {
        if (s == null || s.isBlank()) {
            return BigDecimal.ZERO;
        }
        String t = s.trim().replace(" ", " ").replace(" ", "");
        boolean negative = t.startsWith("-") || t.endsWith("-")
                || (t.startsWith("(") && t.endsWith(")"));
        t = t.replace("(", "").replace(")", "").replace("+", "");
        if (t.startsWith("-")) {
            t = t.substring(1);
        }
        if (t.endsWith("-")) {
            t = t.substring(0, t.length() - 1);
        }
        // para birimi harfleri / sembolleri temizle
        t = t.replaceAll("[^0-9.,]", "");
        char thousands = (dec == ',') ? '.' : ',';
        t = t.replace(String.valueOf(thousands), "");
        t = t.replace(dec, '.');
        if (t.isBlank() || t.equals(".")) {
            return BigDecimal.ZERO;
        }
        BigDecimal v = new BigDecimal(t).setScale(4, java.math.RoundingMode.HALF_UP);
        return negative ? v.negate() : v;
    }
}
