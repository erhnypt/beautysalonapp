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
import java.time.LocalDate;

/** Satış sözleşmesi (§9.8). */
@Entity
@Table(name = "sales_contract",
        uniqueConstraints = @UniqueConstraint(name = "uq_contract_doc_no", columnNames = {"branch_id", "doc_no"}),
        indexes = @Index(name = "ix_contract_party", columnList = "party_id"))
public class SalesContract extends BaseEntity {

    @Column(name = "doc_no", nullable = false, length = 40)
    private String docNo;

    @Column(name = "party_id", nullable = false)
    private Long partyId;

    @Column(name = "party_account_id", nullable = false)
    private Long partyAccountId;

    @Column(name = "contract_date", nullable = false)
    private LocalDate contractDate;

    @Column(name = "total_amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "down_payment", precision = 19, scale = 4, nullable = false)
    private BigDecimal downPayment = BigDecimal.ZERO;

    @Column(name = "installment_count", nullable = false)
    private int installmentCount;

    @Column(name = "first_due_date", nullable = false)
    private LocalDate firstDueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "period", nullable = false, length = 10)
    private InstallmentPeriod period = InstallmentPeriod.AYLIK;

    @Column(name = "interest_rate", precision = 7, scale = 4, nullable = false)
    private BigDecimal interestRate = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "TRY";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private ContractStatus status = ContractStatus.ACTIVE;

    @Column(name = "signed_document_id")
    private Long signedDocumentId;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "cancel_reason", length = 300)
    private String cancelReason;

    protected SalesContract() {
    }

    public SalesContract(String docNo, Long partyId, Long partyAccountId, LocalDate contractDate,
                         BigDecimal totalAmount, BigDecimal downPayment, int installmentCount,
                         LocalDate firstDueDate, InstallmentPeriod period) {
        this.docNo = docNo;
        this.partyId = partyId;
        this.partyAccountId = partyAccountId;
        this.contractDate = contractDate;
        this.totalAmount = totalAmount;
        this.downPayment = downPayment;
        this.installmentCount = installmentCount;
        this.firstDueDate = firstDueDate;
        this.period = period;
    }

    public String getDocNo() { return docNo; }
    public Long getPartyId() { return partyId; }
    public Long getPartyAccountId() { return partyAccountId; }
    public LocalDate getContractDate() { return contractDate; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public BigDecimal getDownPayment() { return downPayment; }
    public void setDownPayment(BigDecimal downPayment) { this.downPayment = downPayment; }
    public int getInstallmentCount() { return installmentCount; }
    public LocalDate getFirstDueDate() { return firstDueDate; }
    public InstallmentPeriod getPeriod() { return period; }
    public BigDecimal getInterestRate() { return interestRate; }
    public void setInterestRate(BigDecimal interestRate) { this.interestRate = interestRate; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public ContractStatus getStatus() { return status; }
    public void setStatus(ContractStatus status) { this.status = status; }
    public Long getSignedDocumentId() { return signedDocumentId; }
    public void setSignedDocumentId(Long signedDocumentId) { this.signedDocumentId = signedDocumentId; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
}
