package com.beautysalonapp.modules.loyalty.domain;

import com.beautysalonapp.core.domain.BaseEntity;
import com.beautysalonapp.modules.loyalty.domain.LoyaltyEnums.LoyaltyTxnType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Sadakat hareketi (§9.9). Append-only. */
@Entity
@Table(name = "loyalty_transaction", indexes = {
        @Index(name = "ix_loyalty_txn_card", columnList = "card_id,at"),
        @Index(name = "ix_loyalty_txn_expiry", columnList = "expires_at,expired")
})
public class LoyaltyTransaction extends BaseEntity {

    @Column(name = "card_id", nullable = false)
    private Long cardId;

    @Enumerated(EnumType.STRING)
    @Column(name = "txn_type", nullable = false, length = 14)
    private LoyaltyTxnType type;

    /** Kazanımda +, harcama/expire/transfer-out'ta -. */
    @Column(name = "points", nullable = false)
    private int points;

    @Column(name = "spend_amount", precision = 19, scale = 4)
    private BigDecimal spendAmount;

    @Column(name = "currency_value", precision = 19, scale = 4)
    private BigDecimal currencyValue;

    @Column(name = "source_ref", length = 60)
    private String sourceRef;

    @Column(name = "at", nullable = false)
    private Instant at = Instant.now();

    /** Yalnızca EARN satırında: bu puanların zaman aşımı tarihi. */
    @Column(name = "expires_at")
    private LocalDate expiresAt;

    @Column(name = "expired", nullable = false)
    private boolean expired = false;

    protected LoyaltyTransaction() {
    }

    public LoyaltyTransaction(Long cardId, LoyaltyTxnType type, int points, String sourceRef) {
        this.cardId = cardId;
        this.type = type;
        this.points = points;
        this.sourceRef = sourceRef;
    }

    public Long getCardId() { return cardId; }
    public LoyaltyTxnType getType() { return type; }
    public int getPoints() { return points; }
    public BigDecimal getSpendAmount() { return spendAmount; }
    public void setSpendAmount(BigDecimal spendAmount) { this.spendAmount = spendAmount; }
    public BigDecimal getCurrencyValue() { return currencyValue; }
    public void setCurrencyValue(BigDecimal currencyValue) { this.currencyValue = currencyValue; }
    public String getSourceRef() { return sourceRef; }
    public Instant getAt() { return at; }
    public LocalDate getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDate expiresAt) { this.expiresAt = expiresAt; }
    public boolean isExpired() { return expired; }
    public void setExpired(boolean expired) { this.expired = expired; }
}
