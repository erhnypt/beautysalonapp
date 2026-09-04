package com.beautysalonapp.license.domain;

import com.beautysalonapp.license.domain.Enums.Plan;
import com.beautysalonapp.license.domain.Enums.SubscriptionStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "subscription")
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Plan plan = Plan.PRO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SubscriptionStatus status = SubscriptionStatus.PENDING_PAYMENT;

    @Column(precision = 12, scale = 2)
    private BigDecimal monthlyFee = BigDecimal.ZERO;

    @Column(nullable = false)
    private LocalDate startDate = LocalDate.now();

    /** Ödemenin karşıladığı dönemin bitişi. Heartbeat yenilemesi bu tarihe göre yapılır. */
    @Column(nullable = false)
    private LocalDate paidThrough = LocalDate.now();

    @Column(nullable = false)
    private int graceDays = 7;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public boolean isBillingCurrent() {
        return status == SubscriptionStatus.ACTIVE
                && !LocalDate.now().isAfter(paidThrough.plusDays(graceDays));
    }

    public Long getId() { return id; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public Plan getPlan() { return plan; }
    public void setPlan(Plan plan) { this.plan = plan; }
    public SubscriptionStatus getStatus() { return status; }
    public void setStatus(SubscriptionStatus status) { this.status = status; }
    public BigDecimal getMonthlyFee() { return monthlyFee; }
    public void setMonthlyFee(BigDecimal monthlyFee) { this.monthlyFee = monthlyFee; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getPaidThrough() { return paidThrough; }
    public void setPaidThrough(LocalDate paidThrough) { this.paidThrough = paidThrough; }
    public int getGraceDays() { return graceDays; }
    public void setGraceDays(int graceDays) { this.graceDays = graceDays; }
    public Instant getCreatedAt() { return createdAt; }
}
