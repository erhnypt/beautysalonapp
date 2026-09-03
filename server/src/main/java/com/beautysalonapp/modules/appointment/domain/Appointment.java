package com.beautysalonapp.modules.appointment.domain;

import com.beautysalonapp.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** Randevu (§9.7). Zaman değerleri UTC {@link Instant}; sunumda Europe/Istanbul. */
@Entity
@Table(name = "appointment", indexes = {
        @Index(name = "ix_appt_staff_time", columnList = "staff_party_id,start_at"),
        @Index(name = "ix_appt_resource_time", columnList = "resource_id,start_at"),
        @Index(name = "ix_appt_party", columnList = "party_id"),
        @Index(name = "ix_appt_time", columnList = "start_at")
})
public class Appointment extends BaseEntity {

    @Column(name = "party_id", nullable = false)
    private Long partyId;

    @Column(name = "staff_party_id", nullable = false)
    private Long staffPartyId;

    @Column(name = "resource_id")
    private Long resourceId;

    @Column(name = "service_id", nullable = false)
    private Long serviceId;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private AppointmentStatus status = AppointmentStatus.PLANLANDI;

    /** TELEFON | YERINDE | ONLINE */
    @Column(name = "source", nullable = false, length = 10)
    private String source = "YERINDE";

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "price_snapshot", precision = 19, scale = 4, nullable = false)
    private BigDecimal priceSnapshot = BigDecimal.ZERO;

    @Column(name = "reminder_sent_at")
    private Instant reminderSentAt;

    @Column(name = "arrived_at")
    private Instant arrivedAt;

    @Column(name = "no_show", nullable = false)
    private boolean noShow = false;

    /** Seans paketinden geliyorsa ilgili contract_line — GELDI'de session_used++. */
    @Column(name = "contract_line_id")
    private Long contractLineId;

    /** GELDI zinciri tetiklendi mi? (idempotens) */
    @Column(name = "chain_done", nullable = false)
    private boolean chainDone = false;

    protected Appointment() {
    }

    public Appointment(Long partyId, Long staffPartyId, Long resourceId, Long serviceId,
                       Instant startAt, Instant endAt, BigDecimal priceSnapshot) {
        this.partyId = partyId;
        this.staffPartyId = staffPartyId;
        this.resourceId = resourceId;
        this.serviceId = serviceId;
        this.startAt = startAt;
        this.endAt = endAt;
        this.priceSnapshot = priceSnapshot;
    }

    public TimeSlot slot() {
        return new TimeSlot(startAt, endAt);
    }

    public Long getPartyId() { return partyId; }
    public Long getStaffPartyId() { return staffPartyId; }
    public void setStaffPartyId(Long staffPartyId) { this.staffPartyId = staffPartyId; }
    public Long getResourceId() { return resourceId; }
    public void setResourceId(Long resourceId) { this.resourceId = resourceId; }
    public Long getServiceId() { return serviceId; }
    public Instant getStartAt() { return startAt; }
    public void setStartAt(Instant startAt) { this.startAt = startAt; }
    public Instant getEndAt() { return endAt; }
    public void setEndAt(Instant endAt) { this.endAt = endAt; }
    public AppointmentStatus getStatus() { return status; }
    public void setStatus(AppointmentStatus status) { this.status = status; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public BigDecimal getPriceSnapshot() { return priceSnapshot; }
    public void setPriceSnapshot(BigDecimal priceSnapshot) { this.priceSnapshot = priceSnapshot; }
    public Instant getReminderSentAt() { return reminderSentAt; }
    public void setReminderSentAt(Instant reminderSentAt) { this.reminderSentAt = reminderSentAt; }
    public Instant getArrivedAt() { return arrivedAt; }
    public void setArrivedAt(Instant arrivedAt) { this.arrivedAt = arrivedAt; }
    public boolean isNoShow() { return noShow; }
    public void setNoShow(boolean noShow) { this.noShow = noShow; }
    public Long getContractLineId() { return contractLineId; }
    public void setContractLineId(Long contractLineId) { this.contractLineId = contractLineId; }
    public boolean isChainDone() { return chainDone; }
    public void setChainDone(boolean chainDone) { this.chainDone = chainDone; }
}
