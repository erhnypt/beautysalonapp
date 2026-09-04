package com.beautysalonapp.modules.invoice.domain;

import com.beautysalonapp.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "invoice",
        uniqueConstraints = @UniqueConstraint(name = "uq_invoice_doc_no", columnNames = {"branch_id", "doc_no"}),
        indexes = {
                @Index(name = "ix_invoice_party", columnList = "party_id"),
                @Index(name = "ix_invoice_type_date", columnList = "invoice_type,invoice_date")
        })
public class Invoice extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "invoice_type", nullable = false, length = 12)
    private InvoiceType type;

    @Column(name = "series", length = 10)
    private String series;

    @Column(name = "doc_no", nullable = false, length = 40)
    private String docNo;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate date;

    @Column(name = "party_id", nullable = false)
    private Long partyId;

    @Column(name = "party_account_id", nullable = false)
    private Long partyAccountId;

    @Column(name = "warehouse_id")
    private Long warehouseId;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "TRY";

    @Column(name = "fx_rate", precision = 19, scale = 6)
    private BigDecimal fxRate;

    /** Yazarkasa / ÖKC fiş numarası (perakende). */
    @Column(name = "cash_register_receipt_no", length = 40)
    private String cashRegisterReceiptNo;

    @Column(name = "subtotal", precision = 19, scale = 4, nullable = false)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "discount_total", precision = 19, scale = 4, nullable = false)
    private BigDecimal discountTotal = BigDecimal.ZERO;

    @Column(name = "vat_total", precision = 19, scale = 4, nullable = false)
    private BigDecimal vatTotal = BigDecimal.ZERO;

    @Column(name = "grand_total", precision = 19, scale = 4, nullable = false)
    private BigDecimal grandTotal = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private InvoiceStatus status = InvoiceStatus.CONFIRMED;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "void_reason", length = 300)
    private String voidReason;

    // v2 e-Arşiv/e-Fatura için rezerve
    @Column(name = "einvoice_uuid", length = 64)
    private String einvoiceUuid;

    @Column(name = "einvoice_status", length = 20)
    private String einvoiceStatus;

    protected Invoice() {
    }

    public Invoice(InvoiceType type, String docNo, LocalDate date, Long partyId, Long partyAccountId,
                   Long warehouseId, String currency) {
        this.type = type;
        this.docNo = docNo;
        this.date = date;
        this.partyId = partyId;
        this.partyAccountId = partyAccountId;
        this.warehouseId = warehouseId;
        this.currency = currency;
    }

    public InvoiceType getType() { return type; }
    public String getSeries() { return series; }
    public void setSeries(String series) { this.series = series; }
    public String getDocNo() { return docNo; }
    public LocalDate getDate() { return date; }
    public Long getPartyId() { return partyId; }
    public Long getPartyAccountId() { return partyAccountId; }
    public Long getWarehouseId() { return warehouseId; }
    public String getCurrency() { return currency; }
    public BigDecimal getFxRate() { return fxRate; }
    public void setFxRate(BigDecimal fxRate) { this.fxRate = fxRate; }
    public String getCashRegisterReceiptNo() { return cashRegisterReceiptNo; }
    public void setCashRegisterReceiptNo(String v) { this.cashRegisterReceiptNo = v; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    public BigDecimal getDiscountTotal() { return discountTotal; }
    public void setDiscountTotal(BigDecimal discountTotal) { this.discountTotal = discountTotal; }
    public BigDecimal getVatTotal() { return vatTotal; }
    public void setVatTotal(BigDecimal vatTotal) { this.vatTotal = vatTotal; }
    public BigDecimal getGrandTotal() { return grandTotal; }
    public void setGrandTotal(BigDecimal grandTotal) { this.grandTotal = grandTotal; }
    public InvoiceStatus getStatus() { return status; }
    public void setStatus(InvoiceStatus status) { this.status = status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getVoidReason() { return voidReason; }
    public void setVoidReason(String voidReason) { this.voidReason = voidReason; }
    public String getEinvoiceUuid() { return einvoiceUuid; }
    public void setEinvoiceUuid(String einvoiceUuid) { this.einvoiceUuid = einvoiceUuid; }
    public String getEinvoiceStatus() { return einvoiceStatus; }
    public void setEinvoiceStatus(String einvoiceStatus) { this.einvoiceStatus = einvoiceStatus; }
}
