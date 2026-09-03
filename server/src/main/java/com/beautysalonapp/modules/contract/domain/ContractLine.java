package com.beautysalonapp.modules.contract.domain;

import com.beautysalonapp.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** Sözleşme satırı: satılan hizmet/paket/ürün (seans adedi dahil). */
@Entity
@Table(name = "contract_line", indexes = @Index(name = "ix_contract_line_contract", columnList = "contract_id"))
public class ContractLine extends BaseEntity {

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(name = "item_id")
    private Long itemId;

    @Column(name = "description", nullable = false, length = 300)
    private String description;

    @Column(name = "quantity", precision = 19, scale = 4, nullable = false)
    private BigDecimal quantity = BigDecimal.ONE;

    /** Paket ise toplam seans adedi (randevu modülü tüketir). */
    @Column(name = "session_count")
    private Integer sessionCount;

    @Column(name = "session_used", nullable = false)
    private int sessionUsed = 0;

    @Column(name = "unit_price", precision = 19, scale = 4, nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "line_total", precision = 19, scale = 4, nullable = false)
    private BigDecimal lineTotal;

    protected ContractLine() {
    }

    public ContractLine(Long contractId, Long itemId, String description, BigDecimal quantity,
                        Integer sessionCount, BigDecimal unitPrice) {
        this.contractId = contractId;
        this.itemId = itemId;
        this.description = description;
        this.quantity = quantity;
        this.sessionCount = sessionCount;
        this.unitPrice = unitPrice;
        this.lineTotal = quantity.multiply(unitPrice);
    }

    public Long getContractId() { return contractId; }
    public Long getItemId() { return itemId; }
    public String getDescription() { return description; }
    public BigDecimal getQuantity() { return quantity; }
    public Integer getSessionCount() { return sessionCount; }
    public int getSessionUsed() { return sessionUsed; }
    public void setSessionUsed(int sessionUsed) { this.sessionUsed = sessionUsed; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getLineTotal() { return lineTotal; }
}
