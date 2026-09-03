package com.beautysalonapp.modules.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/** Çek durum geçişi geçmişi (§9.5). Değiştirilemez. */
@Entity
@Table(name = "cheque_movement", indexes = @Index(name = "ix_cheque_mv", columnList = "cheque_id,at"))
public class ChequeMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cheque_id", nullable = false, updatable = false)
    private Long chequeId;

    @Column(name = "at", nullable = false, updatable = false)
    private Instant at;

    @Column(name = "from_status", length = 16, updatable = false)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 16, updatable = false)
    private String toStatus;

    @Column(name = "actor", length = 100, updatable = false)
    private String actor;

    @Column(name = "note", length = 300, updatable = false)
    private String note;

    protected ChequeMovement() {
    }

    public ChequeMovement(Long chequeId, String fromStatus, String toStatus, String actor, String note) {
        this.chequeId = chequeId;
        this.at = Instant.now();
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actor = actor;
        this.note = note;
    }

    public Long getId() { return id; }
    public Long getChequeId() { return chequeId; }
    public Instant getAt() { return at; }
    public String getFromStatus() { return fromStatus; }
    public String getToStatus() { return toStatus; }
    public String getNote() { return note; }
}
