package com.beautysalonapp.modules.stock.domain;

import com.beautysalonapp.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

/**
 * Ürünün bir birimdeki tanımı (§9.3 çapraz birim formülasyonu).
 * {@code factor} = bu birimin kaç base unit'e karşılık geldiği (1 KOLİ → 12 ADET → factor 12).
 * Base birim satırında {@code factor = 1}, {@code isBase = true}.
 */
@Entity
@Table(name = "item_unit",
        uniqueConstraints = @UniqueConstraint(name = "uq_item_unit", columnNames = {"item_id", "unit_id"}),
        indexes = @Index(name = "ix_item_unit_item", columnList = "item_id"))
public class ItemUnit extends BaseEntity {

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    @Column(name = "factor", precision = 19, scale = 6, nullable = false)
    private BigDecimal factor = BigDecimal.ONE;

    @Column(name = "sale_price", precision = 19, scale = 4)
    private BigDecimal salePrice;

    @Column(name = "is_base", nullable = false)
    private boolean isBase = false;

    protected ItemUnit() {
    }

    public ItemUnit(Long itemId, Long unitId, BigDecimal factor, boolean isBase) {
        this.itemId = itemId;
        this.unitId = unitId;
        this.factor = factor;
        this.isBase = isBase;
    }

    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
    public Long getUnitId() { return unitId; }
    public void setUnitId(Long unitId) { this.unitId = unitId; }
    public BigDecimal getFactor() { return factor; }
    public void setFactor(BigDecimal factor) { this.factor = factor; }
    public BigDecimal getSalePrice() { return salePrice; }
    public void setSalePrice(BigDecimal salePrice) { this.salePrice = salePrice; }
    public boolean isBase() { return isBase; }
    public void setBase(boolean base) { isBase = base; }
}
