package com.beautysalonapp.modules.finance.domain;

import com.beautysalonapp.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Çek / senet (§9.5, §10.7). */
@Entity
@Table(name = "cheque", indexes = {
        @Index(name = "ix_cheque_party", columnList = "party_account_id"),
        @Index(name = "ix_cheque_due", columnList = "due_date,status")
})
public class Cheque extends BaseEntity {

    @Column(name = "cheque_no", nullable = false, length = 40)
    private String chequeNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "cheque_type", nullable = false, length = 12)
    private ChequeType type;

    @Column(name = "bank_name", length = 120)
    private String bankName;

    @Column(name = "drawer", length = 150)
    private String drawer;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "TRY";

    /** İlişkili cari hesap (müşteri çeki → müşteri; firma çeki → satıcı). */
    @Column(name = "party_account_id")
    private Long partyAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ChequeStatus status = ChequeStatus.PORTFOYDE;

    /** TAHSIL_EDILDI'de paranın girdiği banka/kasa hesabı. */
    @Column(name = "settled_account_id")
    private Long settledAccountId;

    @Column(name = "source_doc", length = 40)
    private String sourceDoc;

    @Column(name = "note", length = 300)
    private String note;

    protected Cheque() {
    }

    public Cheque(String chequeNo, ChequeType type, LocalDate dueDate, BigDecimal amount,
                  String currency, Long partyAccountId) {
        this.chequeNo = chequeNo;
        this.type = type;
        this.dueDate = dueDate;
        this.amount = amount;
        this.currency = currency;
        this.partyAccountId = partyAccountId;
    }

    public String getChequeNo() { return chequeNo; }
    public void setChequeNo(String chequeNo) { this.chequeNo = chequeNo; }
    public ChequeType getType() { return type; }
    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }
    public String getDrawer() { return drawer; }
    public void setDrawer(String drawer) { this.drawer = drawer; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public Long getPartyAccountId() { return partyAccountId; }
    public ChequeStatus getStatus() { return status; }
    public void setStatus(ChequeStatus status) { this.status = status; }
    public Long getSettledAccountId() { return settledAccountId; }
    public void setSettledAccountId(Long settledAccountId) { this.settledAccountId = settledAccountId; }
    public String getSourceDoc() { return sourceDoc; }
    public void setSourceDoc(String sourceDoc) { this.sourceDoc = sourceDoc; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
