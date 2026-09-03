package com.beautysalonapp.modules.stock.domain;

import com.beautysalonapp.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

/** Ürün/hizmet kartı (§9.3). */
@Entity
@Table(name = "item",
        uniqueConstraints = @UniqueConstraint(name = "uq_item_code", columnNames = {"branch_id", "code"}),
        indexes = {
                @Index(name = "ix_item_name", columnList = "name"),
                @Index(name = "ix_item_category", columnList = "category_id")
        })
public class Item extends BaseEntity {

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 10)
    private ItemType type = ItemType.EMTIA;

    @Column(name = "vat_rate", precision = 5, scale = 2, nullable = false)
    private BigDecimal vatRate = new BigDecimal("20.00");

    @Column(name = "base_unit_id", nullable = false)
    private Long baseUnitId;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "brand", length = 100)
    private String brand;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** Kritik stok eşiği (base unit); altına düşünce uyarı raporunda. */
    @Column(name = "reorder_level", precision = 19, scale = 6)
    private BigDecimal reorderLevel;

    protected Item() {
    }

    public Item(String code, String name, ItemType type, Long baseUnitId) {
        this.code = code;
        this.name = name;
        this.type = type;
        this.baseUnitId = baseUnitId;
    }

    public boolean tracksStock() {
        return type == ItemType.EMTIA;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public ItemType getType() { return type; }
    public void setType(ItemType type) { this.type = type; }
    public BigDecimal getVatRate() { return vatRate; }
    public void setVatRate(BigDecimal vatRate) { this.vatRate = vatRate; }
    public Long getBaseUnitId() { return baseUnitId; }
    public void setBaseUnitId(Long baseUnitId) { this.baseUnitId = baseUnitId; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public BigDecimal getReorderLevel() { return reorderLevel; }
    public void setReorderLevel(BigDecimal reorderLevel) { this.reorderLevel = reorderLevel; }
}
