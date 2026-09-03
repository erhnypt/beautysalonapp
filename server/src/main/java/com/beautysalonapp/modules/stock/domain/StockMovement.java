package com.beautysalonapp.modules.stock.domain;

import com.beautysalonapp.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Stok hareketi (§9.3). <b>Append-only.</b> Miktar her zaman base unit cinsinden
 * ({@code baseQty}); girilen birim ve miktar bilgi amaçlı saklanır.
 */
@Entity
@Table(name = "stock_movement", indexes = {
        @Index(name = "ix_stock_mv_item_wh", columnList = "item_id,warehouse_id,mv_date"),
        @Index(name = "ix_stock_mv_doc", columnList = "doc_type,doc_ref")
})
public class StockMovement extends BaseEntity {

    @Column(name = "mv_date", nullable = false)
    private LocalDate date;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 3)
    private MovementDirection direction;

    @Column(name = "base_qty", precision = 19, scale = 6, nullable = false)
    private BigDecimal baseQty;

    @Column(name = "entered_unit_id", nullable = false)
    private Long enteredUnitId;

    @Column(name = "entered_qty", precision = 19, scale = 6, nullable = false)
    private BigDecimal enteredQty;

    /** Base birim başına maliyet (IN hareketlerde dolu; OUT'ta o anki ortalama). */
    @Column(name = "unit_cost", precision = 19, scale = 4, nullable = false)
    private BigDecimal unitCost = BigDecimal.ZERO;

    /** INVOICE | APPOINTMENT | TRANSFER | COUNT | ADJUSTMENT | OPENING | REVERSAL */
    @Column(name = "doc_type", nullable = false, length = 20)
    private String docType;

    @Column(name = "doc_ref", length = 40)
    private String docRef;

    @Column(name = "line_key", length = 60)
    private String lineKey;

    @Column(name = "note", length = 300)
    private String note;

    protected StockMovement() {
    }

    public StockMovement(LocalDate date, Long itemId, Long warehouseId, MovementDirection direction,
                         BigDecimal baseQty, Long enteredUnitId, BigDecimal enteredQty, BigDecimal unitCost,
                         String docType, String docRef, String lineKey, String note) {
        this.date = date;
        this.itemId = itemId;
        this.warehouseId = warehouseId;
        this.direction = direction;
        this.baseQty = baseQty;
        this.enteredUnitId = enteredUnitId;
        this.enteredQty = enteredQty;
        this.unitCost = unitCost == null ? BigDecimal.ZERO : unitCost;
        this.docType = docType;
        this.docRef = docRef;
        this.lineKey = lineKey;
        this.note = note;
    }

    public LocalDate getDate() { return date; }
    public Long getItemId() { return itemId; }
    public Long getWarehouseId() { return warehouseId; }
    public MovementDirection getDirection() { return direction; }
    public BigDecimal getBaseQty() { return baseQty; }
    public Long getEnteredUnitId() { return enteredUnitId; }
    public BigDecimal getEnteredQty() { return enteredQty; }
    public BigDecimal getUnitCost() { return unitCost; }
    public String getDocType() { return docType; }
    public String getDocRef() { return docRef; }
    public String getLineKey() { return lineKey; }
    public String getNote() { return note; }
}
