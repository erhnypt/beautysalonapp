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

/**
 * Kasa/banka hareketi (§9.5). Silme yok: iptal ters kayıt üretir ({@code reversesId}).
 */
@Entity
@Table(name = "cash_transaction", indexes = {
        @Index(name = "ix_cash_txn_account", columnList = "account_id,txn_date"),
        @Index(name = "ix_cash_txn_doc", columnList = "doc_type,doc_ref"),
        @Index(name = "ix_cash_txn_card", columnList = "income_expense_card_id")
})
public class CashTransaction extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "txn_type", nullable = false, length = 12)
    private CashTxnType type;

    @Column(name = "txn_date", nullable = false)
    private LocalDate date;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    /** Virman hedefi. */
    @Column(name = "counter_account_id")
    private Long counterAccountId;

    /** Tahsilat/tediyede müşteri/satıcı cari hesabı. */
    @Column(name = "party_account_id")
    private Long partyAccountId;

    @Column(name = "income_expense_card_id")
    private Long incomeExpenseCardId;

    @Column(name = "amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "TRY";

    @Column(name = "fx_rate", precision = 19, scale = 6)
    private BigDecimal fxRate;

    @Column(name = "description", length = 300)
    private String description;

    @Column(name = "doc_no", length = 40)
    private String docNo;

    /** Kaynak belge (fatura/sözleşme/randevu). */
    @Column(name = "doc_type", length = 20)
    private String docType;

    @Column(name = "doc_ref", length = 40)
    private String docRef;

    @Column(name = "line_key", length = 60)
    private String lineKey;

    @Column(name = "voided", nullable = false)
    private boolean voided = false;

    @Column(name = "void_reason", length = 300)
    private String voidReason;

    @Column(name = "reverses_id")
    private Long reversesId;

    protected CashTransaction() {
    }

    public CashTransaction(CashTxnType type, LocalDate date, Long accountId, BigDecimal amount, String currency) {
        this.type = type;
        this.date = date;
        this.accountId = accountId;
        this.amount = amount;
        this.currency = currency;
    }

    public CashTxnType getType() { return type; }
    public LocalDate getDate() { return date; }
    public Long getAccountId() { return accountId; }
    public Long getCounterAccountId() { return counterAccountId; }
    public void setCounterAccountId(Long counterAccountId) { this.counterAccountId = counterAccountId; }
    public Long getPartyAccountId() { return partyAccountId; }
    public void setPartyAccountId(Long partyAccountId) { this.partyAccountId = partyAccountId; }
    public Long getIncomeExpenseCardId() { return incomeExpenseCardId; }
    public void setIncomeExpenseCardId(Long incomeExpenseCardId) { this.incomeExpenseCardId = incomeExpenseCardId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public BigDecimal getFxRate() { return fxRate; }
    public void setFxRate(BigDecimal fxRate) { this.fxRate = fxRate; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getDocNo() { return docNo; }
    public void setDocNo(String docNo) { this.docNo = docNo; }
    public String getDocType() { return docType; }
    public void setDocType(String docType) { this.docType = docType; }
    public String getDocRef() { return docRef; }
    public void setDocRef(String docRef) { this.docRef = docRef; }
    public String getLineKey() { return lineKey; }
    public void setLineKey(String lineKey) { this.lineKey = lineKey; }
    public boolean isVoided() { return voided; }
    public void setVoided(boolean voided) { this.voided = voided; }
    public String getVoidReason() { return voidReason; }
    public void setVoidReason(String voidReason) { this.voidReason = voidReason; }
    public Long getReversesId() { return reversesId; }
    public void setReversesId(Long reversesId) { this.reversesId = reversesId; }

    /** Bu hareketin {@code accountId} bakiyesine net etkisi (+ giren, − çıkan). */
    public BigDecimal signedEffectOnAccount(Long forAccountId) {
        if (voided) {
            return BigDecimal.ZERO;
        }
        if (forAccountId.equals(accountId)) {
            return switch (type) {
                case COLLECTION, FX_BUY -> amount;
                case PAYMENT, FX_SELL, TRANSFER -> amount.negate();
            };
        }
        if (forAccountId.equals(counterAccountId) && type == CashTxnType.TRANSFER) {
            return amount;
        }
        return BigDecimal.ZERO;
    }
}
