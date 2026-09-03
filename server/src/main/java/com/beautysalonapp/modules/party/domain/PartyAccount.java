package com.beautysalonapp.modules.party.domain;

import com.beautysalonapp.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

/**
 * Cari hesap başlığı (§9.2). Bir taraf birden çok hesap taşıyabilir
 * (ör. NORMAL TL + RETAIL TL + döviz hesabı).
 */
@Entity
@Table(name = "party_account",
        uniqueConstraints = @UniqueConstraint(name = "uq_party_account",
                columnNames = {"party_id", "account_kind", "currency"}),
        indexes = @Index(name = "ix_party_account_party", columnList = "party_id"))
public class PartyAccount extends BaseEntity {

    @Column(name = "party_id", nullable = false)
    private Long partyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_kind", nullable = false, length = 10)
    private AccountKind kind = AccountKind.NORMAL;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "TRY";

    /** Açılış bakiyesi (borç pozitif). İlk hareket olarak da yazılır. */
    @Column(name = "opening_balance", precision = 19, scale = 4, nullable = false)
    private BigDecimal openingBalance = BigDecimal.ZERO;

    protected PartyAccount() {
    }

    public PartyAccount(Long partyId, AccountKind kind, String currency) {
        this.partyId = partyId;
        this.kind = kind;
        this.currency = currency;
    }

    public Long getPartyId() { return partyId; }
    public void setPartyId(Long partyId) { this.partyId = partyId; }
    public AccountKind getKind() { return kind; }
    public void setKind(AccountKind kind) { this.kind = kind; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public BigDecimal getOpeningBalance() { return openingBalance; }
    public void setOpeningBalance(BigDecimal openingBalance) { this.openingBalance = openingBalance; }
}
