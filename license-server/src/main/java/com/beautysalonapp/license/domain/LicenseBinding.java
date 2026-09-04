package com.beautysalonapp.license.domain;

import jakarta.persistence.*;

import java.time.Instant;

/** Lisansın bağlandığı makine parmak izleri (§6.3). Aktif binding sayısı ≤ maxTerminals. */
@Entity
@Table(name = "license_binding", indexes = @Index(name = "ix_binding_license", columnList = "licenseId"))
public class LicenseBinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 24)
    private String licenseId;

    @Column(nullable = false, length = 128)
    private String fingerprint;

    @Column(nullable = false)
    private int fpVersion = 2;

    @Column(nullable = false)
    private Instant boundAt = Instant.now();

    private Instant lastSeenAt;

    @Column(nullable = false)
    private boolean active = true;

    protected LicenseBinding() {
    }

    public LicenseBinding(String licenseId, String fingerprint, int fpVersion) {
        this.licenseId = licenseId;
        this.fingerprint = fingerprint;
        this.fpVersion = fpVersion;
        this.lastSeenAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getLicenseId() { return licenseId; }
    public String getFingerprint() { return fingerprint; }
    public int getFpVersion() { return fpVersion; }
    public Instant getBoundAt() { return boundAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
