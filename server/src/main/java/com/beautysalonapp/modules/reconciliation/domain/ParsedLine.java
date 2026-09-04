package com.beautysalonapp.modules.reconciliation.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Ayrıştırılmış tek ekstre satırı. {@code amount} <b>işaretlidir</b>: pozitif = hesaba
 * para girişi (alacak), negatif = hesaptan çıkış (borç) — bankanın bakış açısı.
 */
public record ParsedLine(
        LocalDate valueDate,
        LocalDate bookingDate,
        BigDecimal amount,
        String currency,
        String description,
        String counterparty,
        String bankRef,
        String rawLine) {

    public ParsedLine {
        if (valueDate == null) {
            throw new IllegalArgumentException("valueDate zorunlu");
        }
        if (amount == null) {
            throw new IllegalArgumentException("amount zorunlu");
        }
        amount = amount.setScale(4, java.math.RoundingMode.HALF_UP);
        if (currency == null || currency.isBlank()) {
            currency = "TRY";
        }
        if (bookingDate == null) {
            bookingDate = valueDate;
        }
    }

    public boolean isInflow() {
        return amount.signum() > 0;
    }

    /**
     * MT940 ayrıştırıcısı için değiştirilebilir ara yapı: {@code :86:} açıklama satırları
     * biriktirilir, {@link #build()} anında değişmez {@link ParsedLine}'a dönüşür.
     */
    public static final class Builder {
        private final LocalDate valueDate;
        private final LocalDate bookingDate;
        private final BigDecimal amount;
        private final String currency;
        private final String bankRef;
        private final String rawLine;
        private final StringBuilder info = new StringBuilder();

        public Builder(LocalDate valueDate, LocalDate bookingDate, BigDecimal amount,
                       String currency, String bankRef, String rawLine) {
            this.valueDate = valueDate;
            this.bookingDate = bookingDate;
            this.amount = amount;
            this.currency = currency;
            this.bankRef = bankRef;
            this.rawLine = rawLine;
        }

        public void appendInfo(String s) {
            if (s == null || s.isBlank()) {
                return;
            }
            if (info.length() > 0) {
                info.append(' ');
            }
            info.append(s.replace('\n', ' ').trim());
        }

        public ParsedLine build() {
            String raw = info.toString().trim();
            String description = cleanDescription(raw);
            String counterparty = extractCounterparty(raw);
            return new ParsedLine(valueDate, bookingDate, amount, currency,
                    description.isBlank() ? null : description,
                    counterparty, bankRef, rawLine);
        }

        /** {@code :86:} yapılandırılmış alt alanları ({@code ?20 ?21 ...}) düz metne indirger. */
        private static String cleanDescription(String raw) {
            if (raw.indexOf('?') < 0) {
                return raw;
            }
            StringBuilder out = new StringBuilder();
            for (String part : raw.split("\\?\\d{2}")) {
                String p = part.trim();
                if (!p.isEmpty()) {
                    if (out.length() > 0) {
                        out.append(' ');
                    }
                    out.append(p);
                }
            }
            return out.toString().trim();
        }

        /** {@code ?32}/{@code ?33} (karşı taraf adı) varsa çıkarır. */
        private static String extractCounterparty(String raw) {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("\\?3[23]([^?]+)").matcher(raw);
            if (m.find()) {
                return m.group(1).trim();
            }
            return null;
        }
    }
}
