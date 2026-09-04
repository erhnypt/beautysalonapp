package com.beautysalonapp.modules.reconciliation.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SWIFT MT940 banka ekstresi ayrıştırıcı (framework'süz).
 *
 * <p>Desteklenen alanlar: {@code :20:} referans, {@code :25:} hesap, {@code :28C:} ekstre no,
 * {@code :60F:/:60M:} açılış bakiyesi, {@code :61:} hareket satırı, {@code :86:} açıklama,
 * {@code :62F:/:62M:} kapanış bakiyesi. {@code :64: :65: :90x:} yok sayılır.
 *
 * <p>{@code :61:} işaret kuralı: {@code C}→+, {@code D}→−, {@code RC}→− (alacak iptali),
 * {@code RD}→+ (borç iptali). Tarih {@code YYMMDD} → 2000+YY. Tutar virgül ondalıklı.
 */
public final class Mt940Parser {

    private Mt940Parser() {
    }

    // :61:YYMMDD[MMDD](RC|RD|C|D)[fundsCode]amount[Ntype]refs
    private static final Pattern LINE_61 = Pattern.compile(
            "^(\\d{6})(\\d{4})?(RC|RD|C|D)([A-Z])?([0-9]+,[0-9]{0,2}|[0-9]+)(.*)$");
    // :60F:C YYMMDD CCC amount
    private static final Pattern BALANCE = Pattern.compile(
            "^([CD])(\\d{6})([A-Z]{3})([0-9]+,[0-9]{0,2}|[0-9]+)$");

    public static ParsedStatement parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Boş MT940");
        }
        List<String> tags = splitIntoTags(text);

        String ref20 = null;
        String acct25 = null;
        String stmt28 = null;
        BigDecimal opening = null;
        BigDecimal closing = null;
        LocalDate openingDate = null;
        LocalDate closingDate = null;
        String currency = null;

        List<ParsedLine> lines = new ArrayList<>();
        ParsedLine.Builder pending = null;

        for (String tag : tags) {
            int colon = tag.indexOf(':', 1);
            String id = tag.substring(1, colon);
            String body = tag.substring(colon + 1).trim();

            switch (id) {
                case "20" -> ref20 = body;
                case "25" -> acct25 = body;
                case "28C", "28" -> stmt28 = body;
                case "60F", "60M" -> {
                    Balance b = parseBalance(body);
                    opening = b.amount();
                    openingDate = b.date();
                    currency = b.currency();
                }
                case "62F", "62M" -> {
                    Balance b = parseBalance(body);
                    closing = b.amount();
                    closingDate = b.date();
                    if (currency == null) {
                        currency = b.currency();
                    }
                }
                case "61" -> {
                    if (pending != null) {
                        lines.add(pending.build());
                    }
                    pending = parse61(body, currency);
                }
                case "86" -> {
                    if (pending != null) {
                        pending.appendInfo(body);
                    }
                }
                default -> {
                    // :64: :65: :90D: :90C: vb. — yok say
                }
            }
        }
        if (pending != null) {
            lines.add(pending.build());
        }

        String cur = currency == null ? "TRY" : currency;
        List<ParsedLine> fixed = new ArrayList<>(lines.size());
        for (ParsedLine l : lines) {
            fixed.add(l.currency() == null || l.currency().isBlank()
                    ? new ParsedLine(l.valueDate(), l.bookingDate(), l.amount(), cur,
                        l.description(), l.counterparty(), l.bankRef(), l.rawLine())
                    : l);
        }
        return new ParsedStatement(StatementFormat.MT940, stmt28 != null ? stmt28 : ref20,
                acct25, cur, opening, closing, openingDate, closingDate, fixed);
    }

    // --- yardımcılar -----------------------------------------------------------

    /** Metni {@code :NN:} etiketlerine böler; etiket gövdesi sonraki etikete kadar (çok satırlı). */
    static List<String> splitIntoTags(String text) {
        String norm = text.replace("\r\n", "\n").replace("\r", "\n");
        // {1:...}{2:...}{4: ... -} sarmalı varsa blok 4 içeriğini al
        int b4 = norm.indexOf("{4:");
        if (b4 >= 0) {
            int end = norm.indexOf("-}", b4);
            norm = norm.substring(b4 + 3, end < 0 ? norm.length() : end);
        }
        List<String> tags = new ArrayList<>();
        Matcher m = Pattern.compile("(?m)^:(\\d{2}[A-Z]?):").matcher(norm);
        List<int[]> spans = new ArrayList<>();
        while (m.find()) {
            spans.add(new int[]{m.start(), m.end()});
        }
        for (int i = 0; i < spans.size(); i++) {
            int from = spans.get(i)[0];
            int to = (i + 1 < spans.size()) ? spans.get(i + 1)[0] : norm.length();
            tags.add(norm.substring(from, to).trim());
        }
        return tags;
    }

    private record Balance(BigDecimal amount, LocalDate date, String currency) {
    }

    private static Balance parseBalance(String body) {
        Matcher m = BALANCE.matcher(body.replace(" ", ""));
        if (!m.matches()) {
            return new Balance(null, null, null);
        }
        BigDecimal amt = decimal(m.group(4));
        if ("D".equals(m.group(1))) {
            amt = amt.negate();
        }
        return new Balance(amt, yymmdd(m.group(2)), m.group(3));
    }

    private static ParsedLine.Builder parse61(String body, String currency) {
        String oneLine = body.replace("\n", "").trim();
        Matcher m = LINE_61.matcher(oneLine);
        if (!m.matches()) {
            throw new IllegalArgumentException(":61: satırı çözümlenemedi: " + body);
        }
        LocalDate valueDate = yymmdd(m.group(1));
        LocalDate bookingDate = m.group(2) != null ? mmdd(m.group(2), valueDate) : valueDate;
        String mark = m.group(3);
        BigDecimal amount = decimal(m.group(5));
        if ("D".equals(mark) || "RC".equals(mark)) {
            amount = amount.negate();
        }
        String rest = m.group(6) == null ? "" : m.group(6);
        String bankRef = null;
        int slashes = rest.indexOf("//");
        if (slashes >= 0) {
            bankRef = rest.substring(slashes + 2).trim();
            int nl = bankRef.indexOf('\n');
            if (nl >= 0) {
                bankRef = bankRef.substring(0, nl).trim();
            }
        }
        return new ParsedLine.Builder(valueDate, bookingDate, amount, currency, bankRef, body.trim());
    }

    static BigDecimal decimal(String swiss) {
        String s = swiss.replace(",", ".");
        if (s.endsWith(".")) {
            s = s + "00";
        }
        return new BigDecimal(s).setScale(4, RoundingMode.HALF_UP);
    }

    private static LocalDate yymmdd(String s) {
        int yy = Integer.parseInt(s.substring(0, 2));
        int mm = Integer.parseInt(s.substring(2, 4));
        int dd = Integer.parseInt(s.substring(4, 6));
        return LocalDate.of(2000 + yy, mm, dd);
    }

    private static LocalDate mmdd(String s, LocalDate ref) {
        int mm = Integer.parseInt(s.substring(0, 2));
        int dd = Integer.parseInt(s.substring(2, 4));
        int year = ref.getYear();
        // yıl dönümü: işlem ayı, valör ayından büyükse bir önceki yıl
        if (mm > ref.getMonthValue() + 1) {
            year--;
        }
        return LocalDate.of(year, mm, dd);
    }
}
