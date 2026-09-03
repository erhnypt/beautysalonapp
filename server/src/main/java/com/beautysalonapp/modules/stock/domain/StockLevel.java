package com.beautysalonapp.modules.stock.domain;

import com.beautysalonapp.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

/**
 * Materialized stok seviyesi: {@code (item, warehouse)} başına base-unit miktar ve
 * ağırlıklı ortalama maliyet. Her {@link StockMovement} bunu günceller.
 */
@Entity
@Table(name = "stock_level",
        uniqueConstraints = @UniqueConstraint(name = "uq_stock_level", columnNames = {"item_id", "warehouse_id"}))
public class StockLevel extends BaseEntity {

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "qty_base", precision = 19, scale = 6, nullable = false)
    private BigDecimal qtyBase = BigDecimal.ZERO;

    @Column(name = "avg_cost", precision = 19, scale = 4, nullable = false)
    private BigDecimal avgCost = BigDecimal.ZERO;

    protected StockLevel() {
    }

    public StockLevel(Long itemId, Long warehouseId) {
        this.itemId = itemId;
        this.warehouseId = warehouseId;
    }

    public Long getItemId() { return itemId; }
    public Long getWarehouseId() { return warehouseId; }
    public BigDecimal getQtyBase() { return qtyBase; }
    public void setQtyBase(BigDecimal qtyBase) { this.qtyBase = qtyBase; }
    public BigDecimal getAvgCost() { return avgCost; }
    public void setAvgCost(BigDecimal avgCost) { this.avgCost = avgCost; }
}
