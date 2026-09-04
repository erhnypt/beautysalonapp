package com.beautysalonapp.modules.reconciliation.domain;

import com.beautysalonapp.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** İçe aktarılmış banka ekstresi başlığı (§Faz 8, docs/modules/banka-mutabakat.md). */
@Entity
@Table(name = "bank_statement", indexes = {
        @Index(name = "ix_bank_stmt_account", columnList = "fin_account_id,period_end")
})
public class BankStatement extends BaseEntity {

    public enum Status { IMPORTED, RECONCILED }

    @Column(name = "fin_account_id", nullable = false)
    private Long finAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_format", nullable = false, length = 10)
    private StatementFormat sourceFormat;

    @Column(name = "statement_ref", length = 60)
    private String statementRef;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Column(name = "opening_balance", precision = 19, scale = 4)
    private BigDecimal openingBalance;

    @Column(name = "closing_balance", precision = 19, scale = 4)
    private BigDecimal closingBalance;

    @Column(name = "line_count", nullable = false)
    private int lineCount;

    @Column(name = "matched_count", nullable = false)
    private int matchedCount;

    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private Status status = Status.IMPORTED;

    protected BankStatement() {
    }

    public BankStatement(Long finAccountId, StatementFormat sourceFormat, String originalFilename) {
        this.finAccountId = finAccountId;
        this.sourceFormat = sourceFormat;
        this.originalFilename = originalFilename;
        this.importedAt = Instant.now();
    }

    public void applyParsed(ParsedStatement p) {
        this.statementRef = p.statementRef();
        this.periodStart = p.periodStart();
        this.periodEnd = p.periodEnd();
        this.openingBalance = p.openingBalance();
        this.closingBalance = p.closingBalance();
        this.lineCount = p.lineCount();
    }

    /** Her durum değişikliğinden sonra çağrılır: kalan UNMATCHED yoksa RECONCILED. */
    public void recomputeStatus(int matchedCount, int totalUnresolved) {
        this.matchedCount = matchedCount;
        this.status = totalUnresolved == 0 ? Status.RECONCILED : Status.IMPORTED;
    }

    public Long getFinAccountId() { return finAccountId; }
    public StatementFormat getSourceFormat() { return sourceFormat; }
    public String getStatementRef() { return statementRef; }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public BigDecimal getOpeningBalance() { return openingBalance; }
    public BigDecimal getClosingBalance() { return closingBalance; }
    public int getLineCount() { return lineCount; }
    public int getMatchedCount() { return matchedCount; }
    public String getOriginalFilename() { return originalFilename; }
    public Instant getImportedAt() { return importedAt; }
    public Status getStatus() { return status; }
}
