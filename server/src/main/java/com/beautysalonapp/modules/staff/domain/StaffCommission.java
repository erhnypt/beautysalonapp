package com.beautysalonapp.modules.staff.domain;

import com.beautysalonapp.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "staff_commission",
        uniqueConstraints = @UniqueConstraint(name = "uq_staff_commission_src",
                columnNames = {"staff_id", "source_type", "source_ref"}),
        indexes = @Index(name = "ix_staff_commission_period", columnList = "staff_id,period_ym,status"))
public class StaffCommission extends BaseEntity {

    @Column(name = "staff_id", nullable = false)
    private Long staffId;

    @Column(name = "period_ym", nullable = false, length = 7)
    private String periodYm;

    @Column(name = "source_type", nullable = false, length = 16)
    private String sourceType;

    @Column(name = "source_ref", nullable = false, length = 60)
    private String sourceRef;

    @Column(name = "base_amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal baseAmount;

    @Column(name = "rate", precision = 19, scale = 4)
    private BigDecimal rate;

    @Column(name = "amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private CommissionStatus status = CommissionStatus.TAHAKKUK;

    @Column(name = "accrued_at", nullable = false)
    private Instant accruedAt = Instant.now();

    @Column(name = "paid_at")
    private Instant paidAt;

    protected StaffCommission() {
    }

    public StaffCommission(Long staffId, String periodYm, String sourceType, String sourceRef,
                           BigDecimal baseAmount, BigDecimal rate, BigDecimal amount) {
        this.staffId = staffId;
        this.periodYm = periodYm;
        this.sourceType = sourceType;
        this.sourceRef = sourceRef;
        this.baseAmount = baseAmount;
        this.rate = rate;
        this.amount = amount;
    }

    public void markPaid() {
        this.status = CommissionStatus.ODENDI;
        this.paidAt = Instant.now();
    }

    public Long getStaffId() { return staffId; }
    public String getPeriodYm() { return periodYm; }
    public String getSourceType() { return sourceType; }
    public String getSourceRef() { return sourceRef; }
    public BigDecimal getBaseAmount() { return baseAmount; }
    public BigDecimal getRate() { return rate; }
    public BigDecimal getAmount() { return amount; }
    public CommissionStatus getStatus() { return status; }
    public void setStatus(CommissionStatus status) { this.status = status; }
    public Instant getAccruedAt() { return accruedAt; }
    public Instant getPaidAt() { return paidAt; }
}
