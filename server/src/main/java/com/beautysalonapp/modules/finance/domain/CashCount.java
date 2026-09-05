package com.beautysalonapp.modules.finance.domain;

import com.beautysalonapp.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Gün başı/gün sonu kasa sayımı (§11 plan §10.6). Fark varsa {@code reason} zorunludur
 * ve fark aynı hesaba "kasa farkı" gelir/gider kaydı olarak işlenir — bkz.
 * {@code FinanceService#countCash}.
 */
@Entity
@Table(name = "cash_count", indexes = @Index(name = "ix_cash_count_account", columnList = "account_id,count_date"))
public class CashCount extends BaseEntity {

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "count_date", nullable = false)
    private LocalDate date;

    @Column(name = "expected_amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal expectedAmount;

    @Column(name = "counted_amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal countedAmount;

    @Column(name = "difference", precision = 19, scale = 4, nullable = false)
    private BigDecimal difference;

    @Column(name = "reason", length = 300)
    private String reason;

    @Column(name = "adjustment_doc_no", length = 40)
    private String adjustmentDocNo;

    protected CashCount() {
    }

    public CashCount(Long accountId, LocalDate date, BigDecimal expectedAmount, BigDecimal countedAmount) {
        this.accountId = accountId;
        this.date = date;
        this.expectedAmount = expectedAmount;
        this.countedAmount = countedAmount;
        this.difference = countedAmount.subtract(expectedAmount);
    }

    public Long getAccountId() { return accountId; }
    public LocalDate getDate() { return date; }
    public BigDecimal getExpectedAmount() { return expectedAmount; }
    public BigDecimal getCountedAmount() { return countedAmount; }
    public BigDecimal getDifference() { return difference; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getAdjustmentDocNo() { return adjustmentDocNo; }
    public void setAdjustmentDocNo(String adjustmentDocNo) { this.adjustmentDocNo = adjustmentDocNo; }
}
