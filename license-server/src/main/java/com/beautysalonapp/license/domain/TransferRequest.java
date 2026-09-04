package com.beautysalonapp.license.domain;

import com.beautysalonapp.license.domain.Enums.TransferStatus;
import jakarta.persistence.*;

import java.time.Instant;

/** Makine değişikliği talebi (§6.3). Ayda 1 otomatik onay hakkı, fazlası manuel. */
@Entity
@Table(name = "transfer_request", indexes = @Index(name = "ix_transfer_license", columnList = "licenseId"))
public class TransferRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 24)
    private String licenseId;

    @Column(length = 128)
    private String oldFingerprint;

    @Column(nullable = false, length = 128)
    private String newFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransferStatus status = TransferStatus.PENDING;

    @Column(nullable = false)
    private boolean autoApproved = false;

    @Column(nullable = false)
    private Instant requestedAt = Instant.now();

    private Instant decidedAt;

    @Column(length = 100)
    private String decidedBy;

    protected TransferRequest() {
    }

    public TransferRequest(String licenseId, String oldFingerprint, String newFingerprint) {
        this.licenseId = licenseId;
        this.oldFingerprint = oldFingerprint;
        this.newFingerprint = newFingerprint;
    }

    public Long getId() { return id; }
    public String getLicenseId() { return licenseId; }
    public String getOldFingerprint() { return oldFingerprint; }
    public String getNewFingerprint() { return newFingerprint; }
    public TransferStatus getStatus() { return status; }
    public void setStatus(TransferStatus status) { this.status = status; }
    public boolean isAutoApproved() { return autoApproved; }
    public void setAutoApproved(boolean autoApproved) { this.autoApproved = autoApproved; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
    public String getDecidedBy() { return decidedBy; }
    public void setDecidedBy(String decidedBy) { this.decidedBy = decidedBy; }
}
