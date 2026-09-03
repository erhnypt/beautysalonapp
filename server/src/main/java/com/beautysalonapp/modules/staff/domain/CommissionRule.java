package com.beautysalonapp.modules.staff.domain;

import com.beautysalonapp.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Prim kuralı (§10.2). Özgüllük: {@code staffId} dolu > {@code staffClassId} dolu > her ikisi null (genel).
 */
@Entity
@Table(name = "commission_rule", indexes = @Index(name = "ix_commission_rule_scope", columnList = "scope,active"))
public class CommissionRule extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 10)
    private CommissionScope scope;

    @Column(name = "staff_id")
    private Long staffId;

    @Column(name = "staff_class_id")
    private Long staffClassId;

    @Enumerated(EnumType.STRING)
    @Column(name = "basis", nullable = false, length = 8)
    private CommissionBasis basis = CommissionBasis.RATE;

    @Column(name = "rule_value", precision = 19, scale = 4, nullable = false)
    private BigDecimal value = BigDecimal.ZERO;

    /** REVENUE scope için ciro eşiği (bu tutarın üstündeki ciroya uygulanır). */
    @Column(name = "min_revenue", precision = 19, scale = 4)
    private BigDecimal minRevenue;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected CommissionRule() {
    }

    public CommissionRule(CommissionScope scope, CommissionBasis basis, BigDecimal value) {
        this.scope = scope;
        this.basis = basis;
        this.value = value;
    }

    /** Özgüllük skoru: 2 = personel özel, 1 = sınıf, 0 = genel. */
    public int specificity() {
        if (staffId != null) return 2;
        if (staffClassId != null) return 1;
        return 0;
    }

    public CommissionScope getScope() { return scope; }
    public Long getStaffId() { return staffId; }
    public void setStaffId(Long staffId) { this.staffId = staffId; }
    public Long getStaffClassId() { return staffClassId; }
    public void setStaffClassId(Long staffClassId) { this.staffClassId = staffClassId; }
    public CommissionBasis getBasis() { return basis; }
    public void setBasis(CommissionBasis basis) { this.basis = basis; }
    public BigDecimal getValue() { return value; }
    public void setValue(BigDecimal value) { this.value = value; }
    public BigDecimal getMinRevenue() { return minRevenue; }
    public void setMinRevenue(BigDecimal minRevenue) { this.minRevenue = minRevenue; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
