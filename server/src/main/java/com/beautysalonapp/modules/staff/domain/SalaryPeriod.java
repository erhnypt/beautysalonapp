package com.beautysalonapp.modules.staff.domain;

import com.beautysalonapp.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Maaş dönemi (§9.4). Kapatılan (locked=true) döneme personel avans/prim hareketi
 * girilemez (plan §10.2: "Maaş dönemi kapandıktan sonra o döneme hareket girilemez (kilit)").
 */
@Entity
@Table(name = "salary_period", uniqueConstraints =
        @UniqueConstraint(name = "uq_salary_period", columnNames = {"staff_id", "period_ym"}))
public class SalaryPeriod extends BaseEntity {

    @Column(name = "staff_id", nullable = false)
    private Long staffId;

    @Column(name = "period_ym", nullable = false, length = 7)
    private String periodYm;

    @Column(name = "gross", precision = 19, scale = 4, nullable = false)
    private BigDecimal gross = BigDecimal.ZERO;

    @Column(name = "deductions", precision = 19, scale = 4, nullable = false)
    private BigDecimal deductions = BigDecimal.ZERO;

    @Column(name = "net", precision = 19, scale = 4, nullable = false)
    private BigDecimal net = BigDecimal.ZERO;

    @Column(name = "locked", nullable = false)
    private boolean locked = false;

    @Column(name = "paid_at")
    private Instant paidAt;

    protected SalaryPeriod() {
    }

    public SalaryPeriod(Long staffId, String periodYm) {
        this.staffId = staffId;
        this.periodYm = periodYm;
    }

    public Long getStaffId() { return staffId; }
    public String getPeriodYm() { return periodYm; }
    public BigDecimal getGross() { return gross; }
    public void setGross(BigDecimal gross) { this.gross = gross; }
    public BigDecimal getDeductions() { return deductions; }
    public void setDeductions(BigDecimal deductions) { this.deductions = deductions; }
    public BigDecimal getNet() { return net; }
    public void setNet(BigDecimal net) { this.net = net; }
    public boolean isLocked() { return locked; }
    public void lock() { this.locked = true; }
    public Instant getPaidAt() { return paidAt; }
    public void markPaid(Instant at) { this.paidAt = at; }
}
