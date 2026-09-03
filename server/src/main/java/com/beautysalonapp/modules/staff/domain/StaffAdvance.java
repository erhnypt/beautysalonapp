package com.beautysalonapp.modules.staff.domain;

import com.beautysalonapp.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Personel avansı (§9.4): kasadan çıkış + personel cariye borç. */
@Entity
@Table(name = "staff_advance", indexes = @Index(name = "ix_staff_advance_staff", columnList = "staff_id"))
public class StaffAdvance extends BaseEntity {

    @Column(name = "staff_id", nullable = false)
    private Long staffId;

    @Column(name = "adv_date", nullable = false)
    private LocalDate date;

    @Column(name = "amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "closed_period", length = 7)
    private String closedPeriod;

    @Column(name = "doc_no", length = 40)
    private String docNo;

    protected StaffAdvance() {
    }

    public StaffAdvance(Long staffId, LocalDate date, BigDecimal amount, Long accountId) {
        this.staffId = staffId;
        this.date = date;
        this.amount = amount;
        this.accountId = accountId;
    }

    public Long getStaffId() { return staffId; }
    public LocalDate getDate() { return date; }
    public BigDecimal getAmount() { return amount; }
    public Long getAccountId() { return accountId; }
    public String getClosedPeriod() { return closedPeriod; }
    public void setClosedPeriod(String closedPeriod) { this.closedPeriod = closedPeriod; }
    public String getDocNo() { return docNo; }
    public void setDocNo(String docNo) { this.docNo = docNo; }
}
