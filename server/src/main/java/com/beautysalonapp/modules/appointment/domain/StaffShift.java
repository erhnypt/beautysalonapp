package com.beautysalonapp.modules.appointment.domain;

import com.beautysalonapp.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalTime;

/** Personel çalışma takvimi / izin (§9.7). */
@Entity
@Table(name = "staff_shift", indexes =
        @Index(name = "ix_staff_shift", columnList = "staff_party_id,shift_date"))
public class StaffShift extends BaseEntity {

    @Column(name = "staff_party_id", nullable = false)
    private Long staffPartyId;

    @Column(name = "shift_date", nullable = false)
    private LocalDate date;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    /** WORK | LEAVE */
    @Column(name = "shift_type", nullable = false, length = 8)
    private String type = "WORK";

    @Column(name = "note", length = 200)
    private String note;

    protected StaffShift() {
    }

    public StaffShift(Long staffPartyId, LocalDate date, LocalTime startTime, LocalTime endTime, String type) {
        this.staffPartyId = staffPartyId;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.type = type;
    }

    public Long getStaffPartyId() { return staffPartyId; }
    public LocalDate getDate() { return date; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public String getType() { return type; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
