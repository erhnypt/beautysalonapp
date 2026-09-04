package com.beautysalonapp.modules.loyalty.domain;

import com.beautysalonapp.core.domain.BaseEntity;
import com.beautysalonapp.modules.loyalty.domain.LoyaltyEnums.CardStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "loyalty_card",
        uniqueConstraints = @UniqueConstraint(name = "uq_loyalty_card_no", columnNames = {"branch_id", "card_no"}),
        indexes = {
                @Index(name = "ix_loyalty_card_party", columnList = "party_id"),
                @Index(name = "ix_loyalty_card_mag", columnList = "magnetic_id")
        })
public class LoyaltyCard extends BaseEntity {

    @Column(name = "card_no", nullable = false, length = 40)
    private String cardNo;

    @Column(name = "magnetic_id", length = 60)
    private String magneticId;

    @Column(name = "party_id", nullable = false)
    private Long partyId;

    @Column(name = "program_id", nullable = false)
    private Long programId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private CardStatus status = CardStatus.ACTIVE;

    @Column(name = "points_balance", nullable = false)
    private int pointsBalance = 0;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt = Instant.now();

    protected LoyaltyCard() {
    }

    public LoyaltyCard(String cardNo, Long partyId, Long programId) {
        this.cardNo = cardNo;
        this.partyId = partyId;
        this.programId = programId;
    }

    public void addPoints(int delta) {
        this.pointsBalance = Math.max(0, this.pointsBalance + delta);
    }

    public String getCardNo() { return cardNo; }
    public void setCardNo(String cardNo) { this.cardNo = cardNo; }
    public String getMagneticId() { return magneticId; }
    public void setMagneticId(String magneticId) { this.magneticId = magneticId; }
    public Long getPartyId() { return partyId; }
    public Long getProgramId() { return programId; }
    public CardStatus getStatus() { return status; }
    public void setStatus(CardStatus status) { this.status = status; }
    public int getPointsBalance() { return pointsBalance; }
    public Instant getIssuedAt() { return issuedAt; }
}
