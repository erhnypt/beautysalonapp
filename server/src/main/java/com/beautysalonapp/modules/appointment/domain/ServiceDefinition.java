package com.beautysalonapp.modules.appointment.domain;

import com.beautysalonapp.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

/** Hizmet tanımı (§9.7): süre, buffer, fiyat, reçete. */
@Entity
@Table(name = "service_definition", uniqueConstraints =
        @UniqueConstraint(name = "uq_service_code", columnNames = {"branch_id", "code"}))
public class ServiceDefinition extends BaseEntity {

    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "duration_min", nullable = false)
    private int durationMin = 30;

    @Column(name = "buffer_before_min", nullable = false)
    private int bufferBeforeMin = 0;

    @Column(name = "buffer_after_min", nullable = false)
    private int bufferAfterMin = 0;

    @Column(name = "price", precision = 19, scale = 4, nullable = false)
    private BigDecimal price = BigDecimal.ZERO;

    @Column(name = "vat_rate", precision = 5, scale = 2, nullable = false)
    private BigDecimal vatRate = new BigDecimal("20.00");

    @Column(name = "resource_required", nullable = false)
    private boolean resourceRequired = false;

    /** Hizmet aynı zamanda bir stok (HIZMET) kartıysa satış/gelir eşlemesi için. */
    @Column(name = "stock_item_id")
    private Long stockItemId;

    @Column(name = "income_card_id")
    private Long incomeCardId;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected ServiceDefinition() {
    }

    public ServiceDefinition(String code, String name, int durationMin, BigDecimal price) {
        this.code = code;
        this.name = name;
        this.durationMin = durationMin;
        this.price = price;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getDurationMin() { return durationMin; }
    public void setDurationMin(int durationMin) { this.durationMin = durationMin; }
    public int getBufferBeforeMin() { return bufferBeforeMin; }
    public void setBufferBeforeMin(int v) { this.bufferBeforeMin = v; }
    public int getBufferAfterMin() { return bufferAfterMin; }
    public void setBufferAfterMin(int v) { this.bufferAfterMin = v; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public BigDecimal getVatRate() { return vatRate; }
    public void setVatRate(BigDecimal vatRate) { this.vatRate = vatRate; }
    public boolean isResourceRequired() { return resourceRequired; }
    public void setResourceRequired(boolean resourceRequired) { this.resourceRequired = resourceRequired; }
    public Long getStockItemId() { return stockItemId; }
    public void setStockItemId(Long stockItemId) { this.stockItemId = stockItemId; }
    public Long getIncomeCardId() { return incomeCardId; }
    public void setIncomeCardId(Long incomeCardId) { this.incomeCardId = incomeCardId; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
