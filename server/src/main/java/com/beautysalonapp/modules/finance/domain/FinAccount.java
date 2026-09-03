package com.beautysalonapp.modules.finance.domain;

import com.beautysalonapp.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

@Entity
@Table(name = "fin_account", uniqueConstraints =
        @UniqueConstraint(name = "uq_fin_account_code", columnNames = {"branch_id", "code"}))
public class FinAccount extends BaseEntity {

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 10)
    private FinAccountKind kind;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "TRY";

    @Column(name = "opening_balance", precision = 19, scale = 4, nullable = false)
    private BigDecimal openingBalance = BigDecimal.ZERO;

    @Column(name = "is_commission_bearing", nullable = false)
    private boolean commissionBearing = false;

    @Column(name = "commission_rate", precision = 7, scale = 4)
    private BigDecimal commissionRate;

    @Column(name = "bank_info", length = 300)
    private String bankInfo;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected FinAccount() {
    }

    public FinAccount(String code, String name, FinAccountKind kind, String currency) {
        this.code = code;
        this.name = name;
        this.kind = kind;
        this.currency = currency;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public FinAccountKind getKind() { return kind; }
    public void setKind(FinAccountKind kind) { this.kind = kind; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public BigDecimal getOpeningBalance() { return openingBalance; }
    public void setOpeningBalance(BigDecimal openingBalance) { this.openingBalance = openingBalance; }
    public boolean isCommissionBearing() { return commissionBearing; }
    public void setCommissionBearing(boolean v) { this.commissionBearing = v; }
    public BigDecimal getCommissionRate() { return commissionRate; }
    public void setCommissionRate(BigDecimal commissionRate) { this.commissionRate = commissionRate; }
    public String getBankInfo() { return bankInfo; }
    public void setBankInfo(String bankInfo) { this.bankInfo = bankInfo; }
    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean aDefault) { isDefault = aDefault; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
