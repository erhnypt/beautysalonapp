package com.beautysalonapp.modules.appointment.domain;

import com.beautysalonapp.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Bekleme listesi (plan §10.10): istenen zaman diliminde uygun slot yokken müşterinin
 * talebi kaydedilir; bir slot açıldığında bir randevuya dönüştürülür.
 */
@Entity
@Table(name = "waitlist", indexes = {
        @Index(name = "ix_waitlist_status", columnList = "status,created_at"),
        @Index(name = "ix_waitlist_party", columnList = "party_id")
})
public class Waitlist extends BaseEntity {

    @Column(name = "party_id", nullable = false)
    private Long partyId;

    @Column(name = "service_id", nullable = false)
    private Long serviceId;

    /** Tercih edilen personel; null ise fark etmez. */
    @Column(name = "staff_party_id")
    private Long staffPartyId;

    @Column(name = "preferred_from")
    private Instant preferredFrom;

    @Column(name = "preferred_to")
    private Instant preferredTo;

    @Column(name = "notes", length = 500)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private WaitlistStatus status = WaitlistStatus.BEKLEMEDE;

    /** Dönüştürüldüyse hangi randevuya. */
    @Column(name = "appointment_id")
    private Long appointmentId;

    protected Waitlist() {
    }

    public Waitlist(Long partyId, Long serviceId, Long staffPartyId,
                    Instant preferredFrom, Instant preferredTo, String notes) {
        this.partyId = partyId;
        this.serviceId = serviceId;
        this.staffPartyId = staffPartyId;
        this.preferredFrom = preferredFrom;
        this.preferredTo = preferredTo;
        this.notes = notes;
    }

    public Long getPartyId() { return partyId; }
    public Long getServiceId() { return serviceId; }
    public Long getStaffPartyId() { return staffPartyId; }
    public Instant getPreferredFrom() { return preferredFrom; }
    public Instant getPreferredTo() { return preferredTo; }
    public String getNotes() { return notes; }
    public WaitlistStatus getStatus() { return status; }
    public Long getAppointmentId() { return appointmentId; }

    public void markConverted(long appointmentId) {
        this.status = WaitlistStatus.RANDEVUYA_DONUSTU;
        this.appointmentId = appointmentId;
    }

    public void cancel() {
        this.status = WaitlistStatus.IPTAL;
    }
}
