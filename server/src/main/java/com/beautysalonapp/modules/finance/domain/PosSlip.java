package com.beautysalonapp.modules.finance.domain;

import com.beautysalonapp.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/** POS slibi (§10.7): komisyon ve valör takibi. */
@Entity
@Table(name = "pos_slip", indexes = @Index(name = "ix_pos_slip_settled", columnList = "settled,value_date"))
public class PosSlip extends BaseEntity {

    @Column(name = "pos_account_id", nullable = false)
    private Long posAccountId;

    /** Komisyon düşülmüş net tutarın aktarılacağı banka hesabı. */
    @Column(name = "bank_account_id")
    private Long bankAccountId;

    @Column(name = "slip_no", length = 40)
    private String slipNo;

    @Column(name = "slip_date", nullable = false)
    private LocalDate slipDate;

    @Column(name = "amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    @Column(name = "installment_count", nullable = false)
    private int installmentCount = 1;

    @Column(name = "commission_rate", precision = 7, scale = 4, nullable = false)
    private BigDecimal commissionRate = BigDecimal.ZERO;

    @Column(name = "commission_amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal commissionAmount = BigDecimal.ZERO;

    @Column(name = "net_amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal netAmount = BigDecimal.ZERO;

    @Column(name = "value_date")
    private LocalDate valueDate;

    @Column(name = "settled", nullable = false)
    private boolean settled = false;

    @Column(name = "source_doc", length = 40)
    private String sourceDoc;

    protected PosSlip() {
    }

    public PosSlip(Long posAccountId, LocalDate slipDate, BigDecimal amount, int installmentCount,
                   BigDecimal commissionRate) {
        this.posAccountId = posAccountId;
        this.slipDate = slipDate;
        this.amount = amount;
        this.installmentCount = Math.max(1, installmentCount);
        this.commissionRate = commissionRate == null ? BigDecimal.ZERO : commissionRate;
        recompute();
    }

    public void recompute() {
        this.commissionAmount = amount.multiply(commissionRate)
                .divide(new BigDecimal("100"), 4, java.math.RoundingMode.HALF_UP);
        this.netAmount = amount.subtract(commissionAmount);
    }

    public Long getPosAccountId() { return posAccountId; }
    public Long getBankAccountId() { return bankAccountId; }
    public void setBankAccountId(Long bankAccountId) { this.bankAccountId = bankAccountId; }
    public String getSlipNo() { return slipNo; }
    public void setSlipNo(String slipNo) { this.slipNo = slipNo; }
    public LocalDate getSlipDate() { return slipDate; }
    public BigDecimal getAmount() { return amount; }
    public int getInstallmentCount() { return installmentCount; }
    public BigDecimal getCommissionRate() { return commissionRate; }
    public BigDecimal getCommissionAmount() { return commissionAmount; }
    public BigDecimal getNetAmount() { return netAmount; }
    public LocalDate getValueDate() { return valueDate; }
    public void setValueDate(LocalDate valueDate) { this.valueDate = valueDate; }
    public boolean isSettled() { return settled; }
    public void setSettled(boolean settled) { this.settled = settled; }
    public String getSourceDoc() { return sourceDoc; }
    public void setSourceDoc(String sourceDoc) { this.sourceDoc = sourceDoc; }
}
