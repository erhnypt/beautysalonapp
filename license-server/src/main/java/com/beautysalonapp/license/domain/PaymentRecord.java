package com.beautysalonapp.license.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Ödeme kaydı (§6.8 — manuel işaretleme yeterli; ileride sanal POS). */
@Entity
@Table(name = "payment_record", indexes = @Index(name = "ix_payment_sub", columnList = "subscriptionId"))
public class PaymentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long subscriptionId;

    @Column(precision = 12, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate periodStart;

    @Column(nullable = false)
    private LocalDate periodEnd;

    @Column(length = 40)
    private String method = "MANUEL";

    @Column(nullable = false)
    private Instant paidAt = Instant.now();

    @Column(length = 100)
    private String markedBy;

    protected PaymentRecord() {
    }

    public PaymentRecord(Long subscriptionId, BigDecimal amount, LocalDate periodStart, LocalDate periodEnd,
                         String method, String markedBy) {
        this.subscriptionId = subscriptionId;
        this.amount = amount;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.method = method;
        this.markedBy = markedBy;
    }

    public Long getId() { return id; }
    public Long getSubscriptionId() { return subscriptionId; }
    public BigDecimal getAmount() { return amount; }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public String getMethod() { return method; }
    public Instant getPaidAt() { return paidAt; }
    public String getMarkedBy() { return markedBy; }
}
