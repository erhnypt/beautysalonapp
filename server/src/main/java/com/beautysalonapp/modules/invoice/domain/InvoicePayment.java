package com.beautysalonapp.modules.invoice.domain;

import com.beautysalonapp.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "invoice_payment", indexes = @Index(name = "ix_invoice_payment_invoice", columnList = "invoice_id"))
public class InvoicePayment extends BaseEntity {

    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 10)
    private PaymentMethod method;

    @Column(name = "amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    /** CASH → kasa hesabı, CARD → POS hesabı. */
    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "cheque_id")
    private Long chequeId;

    @Column(name = "pos_slip_id")
    private Long posSlipId;

    protected InvoicePayment() {
    }

    public InvoicePayment(Long invoiceId, PaymentMethod method, BigDecimal amount, Long accountId) {
        this.invoiceId = invoiceId;
        this.method = method;
        this.amount = amount;
        this.accountId = accountId;
    }

    public Long getInvoiceId() { return invoiceId; }
    public PaymentMethod getMethod() { return method; }
    public BigDecimal getAmount() { return amount; }
    public Long getAccountId() { return accountId; }
    public Long getChequeId() { return chequeId; }
    public void setChequeId(Long chequeId) { this.chequeId = chequeId; }
    public Long getPosSlipId() { return posSlipId; }
    public void setPosSlipId(Long posSlipId) { this.posSlipId = posSlipId; }
}
