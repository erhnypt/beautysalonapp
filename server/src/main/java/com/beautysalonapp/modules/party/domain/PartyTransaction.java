package com.beautysalonapp.modules.party.domain;

import com.beautysalonapp.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cari hareket (§9.2). <b>Append-only</b>: bir kez yazıldıktan sonra değiştirilmez;
 * düzeltme yeni bir ters kayıtla yapılır (CLAUDE.md #3).
 */
@Entity
@Table(name = "party_transaction", indexes = {
        @Index(name = "ix_party_txn_account", columnList = "account_id,txn_date"),
        @Index(name = "ix_party_txn_doc", columnList = "doc_type,doc_ref")
})
public class PartyTransaction extends BaseEntity {

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "txn_date", nullable = false)
    private LocalDate date;

    /** OPENING | INVOICE | PAYMENT | CONTRACT | INSTALLMENT | ADJUSTMENT | REVERSAL ... */
    @Column(name = "doc_type", nullable = false, length = 24)
    private String docType;

    /** Kaynak belge referansı (fatura no, dekont no vb.). */
    @Column(name = "doc_ref", length = 40)
    private String docRef;

    /** İdempotens anahtarı: (doc_type, doc_ref, line_key) benzersizdir. */
    @Column(name = "line_key", length = 60)
    private String lineKey;

    @Column(name = "description", length = 300)
    private String description;

    @Column(name = "debit", precision = 19, scale = 4, nullable = false)
    private BigDecimal debit = BigDecimal.ZERO;

    @Column(name = "credit", precision = 19, scale = 4, nullable = false)
    private BigDecimal credit = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "TRY";

    /** Ters kayıtsa: iptal edilen hareketin id'si. */
    @Column(name = "reverses_id")
    private Long reversesId;

    protected PartyTransaction() {
    }

    public PartyTransaction(Long accountId, LocalDate date, String docType, String docRef, String lineKey,
                            String description, BigDecimal debit, BigDecimal credit, String currency) {
        this.accountId = accountId;
        this.date = date;
        this.docType = docType;
        this.docRef = docRef;
        this.lineKey = lineKey;
        this.description = description;
        this.debit = debit == null ? BigDecimal.ZERO : debit;
        this.credit = credit == null ? BigDecimal.ZERO : credit;
        this.currency = currency;
    }

    public Long getAccountId() { return accountId; }
    public LocalDate getDate() { return date; }
    public String getDocType() { return docType; }
    public String getDocRef() { return docRef; }
    public String getLineKey() { return lineKey; }
    public String getDescription() { return description; }
    public BigDecimal getDebit() { return debit; }
    public BigDecimal getCredit() { return credit; }
    public String getCurrency() { return currency; }
    public Long getReversesId() { return reversesId; }
    public void setReversesId(Long reversesId) { this.reversesId = reversesId; }
}
