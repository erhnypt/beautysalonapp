package com.beautysalonapp.modules.invoice.domain;

import com.beautysalonapp.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "invoice_line", indexes = @Index(name = "ix_invoice_line_invoice", columnList = "invoice_id"))
public class InvoiceLine extends BaseEntity {

    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "item_id")
    private Long itemId;

    @Column(name = "is_service", nullable = false)
    private boolean service = false;

    @Column(name = "description", nullable = false, length = 300)
    private String description;

    @Column(name = "quantity", precision = 19, scale = 6, nullable = false)
    private BigDecimal quantity;

    @Column(name = "unit_id")
    private Long unitId;

    @Column(name = "unit_price", precision = 19, scale = 4, nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "discount_rate", precision = 7, scale = 4, nullable = false)
    private BigDecimal discountRate = BigDecimal.ZERO;

    @Column(name = "vat_rate", precision = 5, scale = 2, nullable = false)
    private BigDecimal vatRate = BigDecimal.ZERO;

    @Column(name = "line_net", precision = 19, scale = 4, nullable = false)
    private BigDecimal lineNet;

    @Column(name = "line_vat", precision = 19, scale = 4, nullable = false)
    private BigDecimal lineVat;

    @Column(name = "line_total", precision = 19, scale = 4, nullable = false)
    private BigDecimal lineTotal;

    protected InvoiceLine() {
    }

    public InvoiceLine(Long invoiceId, int lineNo, Long itemId, boolean service, String description,
                       BigDecimal quantity, Long unitId, BigDecimal unitPrice, BigDecimal discountRate,
                       BigDecimal vatRate, BigDecimal lineNet, BigDecimal lineVat, BigDecimal lineTotal) {
        this.invoiceId = invoiceId;
        this.lineNo = lineNo;
        this.itemId = itemId;
        this.service = service;
        this.description = description;
        this.quantity = quantity;
        this.unitId = unitId;
        this.unitPrice = unitPrice;
        this.discountRate = discountRate;
        this.vatRate = vatRate;
        this.lineNet = lineNet;
        this.lineVat = lineVat;
        this.lineTotal = lineTotal;
    }

    public Long getInvoiceId() { return invoiceId; }
    public int getLineNo() { return lineNo; }
    public Long getItemId() { return itemId; }
    public boolean isService() { return service; }
    public String getDescription() { return description; }
    public BigDecimal getQuantity() { return quantity; }
    public Long getUnitId() { return unitId; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getDiscountRate() { return discountRate; }
    public BigDecimal getVatRate() { return vatRate; }
    public BigDecimal getLineNet() { return lineNet; }
    public BigDecimal getLineVat() { return lineVat; }
    public BigDecimal getLineTotal() { return lineTotal; }
}
