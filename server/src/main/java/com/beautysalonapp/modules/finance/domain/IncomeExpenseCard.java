package com.beautysalonapp.modules.finance.domain;

import com.beautysalonapp.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

/**
 * Gelir/gider kartı (§10.4). Ağaç yapı ({@code parentId}); her kart bir kâr-zarar kriteridir.
 * {@code isServiceCard} true ise faturada kalem olarak da kullanılabilir (hizmet alış/satış).
 */
@Entity
@Table(name = "income_expense_card", uniqueConstraints =
        @UniqueConstraint(name = "uq_ie_card_code", columnNames = {"branch_id", "code"}))
public class IncomeExpenseCard extends BaseEntity {

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 8)
    private CardDirection direction;

    @Column(name = "budget_amount", precision = 19, scale = 4)
    private BigDecimal budgetAmount;

    @Column(name = "is_service_card", nullable = false)
    private boolean serviceCard = false;

    /** Yalnızca yaprak kartlara hareket yazılabilir. */
    @Column(name = "postable", nullable = false)
    private boolean postable = true;

    protected IncomeExpenseCard() {
    }

    public IncomeExpenseCard(Long parentId, String code, String name, CardDirection direction) {
        this.parentId = parentId;
        this.code = code;
        this.name = name;
        this.direction = direction;
    }

    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public CardDirection getDirection() { return direction; }
    public void setDirection(CardDirection direction) { this.direction = direction; }
    public BigDecimal getBudgetAmount() { return budgetAmount; }
    public void setBudgetAmount(BigDecimal budgetAmount) { this.budgetAmount = budgetAmount; }
    public boolean isServiceCard() { return serviceCard; }
    public void setServiceCard(boolean serviceCard) { this.serviceCard = serviceCard; }
    public boolean isPostable() { return postable; }
    public void setPostable(boolean postable) { this.postable = postable; }
}
