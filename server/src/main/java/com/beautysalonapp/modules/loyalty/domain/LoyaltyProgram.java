package com.beautysalonapp.modules.loyalty.domain;

import com.beautysalonapp.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "loyalty_program")
public class LoyaltyProgram extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** 1 TL harcama başına kazanılan puan (ör. 0.1 → 100 TL = 10 puan). */
    @Column(name = "earn_rate", precision = 12, scale = 4, nullable = false)
    private BigDecimal earnRate = new BigDecimal("0.1000");

    /** 1 puanın TL karşılığı (ör. 0.05). */
    @Column(name = "point_to_currency", precision = 12, scale = 4, nullable = false)
    private BigDecimal pointToCurrency = new BigDecimal("0.0500");

    @Column(name = "expiry_months", nullable = false)
    private int expiryMonths = 12;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected LoyaltyProgram() {
    }

    public LoyaltyProgram(String name) {
        this.name = name;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getEarnRate() { return earnRate; }
    public void setEarnRate(BigDecimal earnRate) { this.earnRate = earnRate; }
    public BigDecimal getPointToCurrency() { return pointToCurrency; }
    public void setPointToCurrency(BigDecimal v) { this.pointToCurrency = v; }
    public int getExpiryMonths() { return expiryMonths; }
    public void setExpiryMonths(int expiryMonths) { this.expiryMonths = expiryMonths; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
