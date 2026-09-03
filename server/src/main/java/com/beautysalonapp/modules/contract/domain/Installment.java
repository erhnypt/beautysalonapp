package com.beautysalonapp.modules.contract.domain;

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
import java.time.LocalDate;

@Entity
@Table(name = "installment",
        uniqueConstraints = @UniqueConstraint(name = "uq_installment_seq", columnNames = {"contract_id", "seq"}),
        indexes = @Index(name = "ix_installment_due", columnList = "due_date,status"))
public class Installment extends BaseEntity {

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(name = "seq", nullable = false)
    private int seq;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    @Column(name = "paid_amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private InstallmentStatus status = InstallmentStatus.BEKLIYOR;

    protected Installment() {
    }

    public Installment(Long contractId, int seq, LocalDate dueDate, BigDecimal amount) {
        this.contractId = contractId;
        this.seq = seq;
        this.dueDate = dueDate;
        this.amount = amount;
    }

    public BigDecimal remaining() {
        return amount.subtract(paidAmount);
    }

    /** Kayıtlı durum IPTAL/ODENDI değilse ve vade geçmişse GECIKMIS döndürür (görüntüleme için). */
    public InstallmentStatus effectiveStatus(LocalDate today) {
        if (status == InstallmentStatus.ODENDI || status == InstallmentStatus.IPTAL) {
            return status;
        }
        return dueDate.isBefore(today) ? InstallmentStatus.GECIKMIS : InstallmentStatus.BEKLIYOR;
    }

    public void applyPayment(BigDecimal payAmount) {
        this.paidAmount = this.paidAmount.add(payAmount);
        if (this.paidAmount.compareTo(this.amount) >= 0) {
            this.status = InstallmentStatus.ODENDI;
            this.paidAt = Instant.now();
        }
    }

    public Long getContractId() { return contractId; }
    public int getSeq() { return seq; }
    public LocalDate getDueDate() { return dueDate; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public Instant getPaidAt() { return paidAt; }
    public InstallmentStatus getStatus() { return status; }
    public void setStatus(InstallmentStatus status) { this.status = status; }
}
