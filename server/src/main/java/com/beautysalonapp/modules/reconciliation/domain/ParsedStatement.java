package com.beautysalonapp.modules.reconciliation.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Ayrıştırılmış banka ekstresi (başlık + satırlar). Kalıcılık öncesi ara model. */
public record ParsedStatement(
        StatementFormat format,
        String statementRef,
        String accountHint,
        String currency,
        BigDecimal openingBalance,
        BigDecimal closingBalance,
        LocalDate periodStart,
        LocalDate periodEnd,
        List<ParsedLine> lines) {

    public ParsedStatement {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }

    public int lineCount() {
        return lines.size();
    }

    /** Açılış + Σ(satır tutarları) — kapanışla tutması beklenir. */
    public BigDecimal computedClosing() {
        BigDecimal sum = openingBalance == null ? BigDecimal.ZERO : openingBalance;
        for (ParsedLine l : lines) {
            sum = sum.add(l.amount());
        }
        return sum;
    }

    /** Dosyadaki kapanış ile satırlardan hesaplanan kapanış tutuyor mu? */
    public boolean balanceConsistent() {
        if (closingBalance == null || openingBalance == null) {
            return true; // doğrulanamıyor → engelleme
        }
        return computedClosing().compareTo(closingBalance) == 0;
    }
}
