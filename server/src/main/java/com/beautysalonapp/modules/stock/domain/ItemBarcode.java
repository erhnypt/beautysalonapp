package com.beautysalonapp.modules.stock.domain;

import com.beautysalonapp.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Ürün barkodu (§9.3 çoklu barkod). Her barkod bir birime bağlıdır:
 * koli barkodu ≠ adet barkodu. Okutulduğunda {@code (item, unit)} çözülür.
 */
@Entity
@Table(name = "item_barcode",
        uniqueConstraints = @UniqueConstraint(name = "uq_item_barcode", columnNames = {"branch_id", "barcode"}),
        indexes = @Index(name = "ix_item_barcode_item", columnList = "item_id"))
public class ItemBarcode extends BaseEntity {

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "barcode", nullable = false, length = 64)
    private String barcode;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    @Column(name = "is_primary", nullable = false)
    private boolean primary = false;

    protected ItemBarcode() {
    }

    public ItemBarcode(Long itemId, String barcode, Long unitId, boolean primary) {
        this.itemId = itemId;
        this.barcode = barcode;
        this.unitId = unitId;
        this.primary = primary;
    }

    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
    public Long getUnitId() { return unitId; }
    public void setUnitId(Long unitId) { this.unitId = unitId; }
    public boolean isPrimary() { return primary; }
    public void setPrimary(boolean primary) { this.primary = primary; }
}
