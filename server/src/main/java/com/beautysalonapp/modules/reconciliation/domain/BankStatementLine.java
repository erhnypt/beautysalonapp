package com.beautysalonapp.modules.reconciliation.domain;

import com.beautysalonapp.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Tek bir ekstre satırı ve mutabakat durumu (§Faz 8). */
@Entity
@Table(name = "bank_statement_line",
        uniqueConstraints = @UniqueConstraint(name = "uq_bank_line_no", columnNames = {"statement_id", "line_no"}),
        indexes = {
                @Index(name = "ix_bank_line_stmt", columnList = "statement_id,match_status"),
                @Index(name = "ix_bank_line_txn", columnList = "matched_txn_id")
        })
public class BankStatementLine extends BaseEntity {

    @Column(name = "statement_id", nullable = false)
    private Long statementId;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "value_date", nullable = false)
    private LocalDate valueDate;

    @Column(name = "booking_date")
    private LocalDate bookingDate;

    /** İşaretli: + hesaba giriş, − çıkış (banka bakışı). */
    @Column(name = "amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "TRY";

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "counterparty", length = 200)
    private String counterparty;

    @Column(name = "bank_ref", length = 80)
    private String bankRef;

    @Column(name = "raw_line", length = 1000)
    private String rawLine;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_status", nullable = false, length = 12)
    private MatchStatus matchStatus = MatchStatus.UNMATCHED;

    @Column(name = "matched_txn_id")
    private Long matchedTxnId;

    @Column(name = "match_score")
    private Integer matchScore;

    @Column(name = "note", length = 300)
    private String note;

    protected BankStatementLine() {
    }

    public BankStatementLine(Long statementId, int lineNo, ParsedLine p) {
        this.statementId = statementId;
        this.lineNo = lineNo;
        this.valueDate = p.valueDate();
        this.bookingDate = p.bookingDate();
        this.amount = p.amount();
        this.currency = p.currency();
        this.description = trunc(p.description(), 500);
        this.counterparty = trunc(p.counterparty(), 200);
        this.bankRef = trunc(p.bankRef(), 80);
        this.rawLine = trunc(p.rawLine(), 1000);
    }

    public void markMatched(long txnId, Integer score) {
        this.matchStatus = MatchStatus.MATCHED;
        this.matchedTxnId = txnId;
        this.matchScore = score;
        this.note = null;
    }

    public void markCreated(long txnId) {
        this.matchStatus = MatchStatus.CREATED;
        this.matchedTxnId = txnId;
        this.matchScore = null;
    }

    public void markIgnored(String note) {
        this.matchStatus = MatchStatus.IGNORED;
        this.matchedTxnId = null;
        this.matchScore = null;
        this.note = note;
    }

    public void clearMatch() {
        this.matchStatus = MatchStatus.UNMATCHED;
        this.matchedTxnId = null;
        this.matchScore = null;
        this.note = null;
    }

    private static String trunc(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }

    public Long getStatementId() { return statementId; }
    public int getLineNo() { return lineNo; }
    public LocalDate getValueDate() { return valueDate; }
    public LocalDate getBookingDate() { return bookingDate; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getDescription() { return description; }
    public String getCounterparty() { return counterparty; }
    public String getBankRef() { return bankRef; }
    public String getRawLine() { return rawLine; }
    public MatchStatus getMatchStatus() { return matchStatus; }
    public Long getMatchedTxnId() { return matchedTxnId; }
    public Integer getMatchScore() { return matchScore; }
    public String getNote() { return note; }
}
