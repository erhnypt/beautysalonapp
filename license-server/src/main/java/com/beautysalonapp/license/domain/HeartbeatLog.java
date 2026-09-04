package com.beautysalonapp.license.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "heartbeat_log", indexes = @Index(name = "ix_hb_license", columnList = "licenseId,receivedAt"))
public class HeartbeatLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 24)
    private String licenseId;

    @Column(length = 128)
    private String fingerprint;

    @Column(length = 40)
    private String appVersion;

    @Column(length = 120)
    private String os;

    @Column(nullable = false)
    private Instant receivedAt = Instant.now();

    @Column(length = 16)
    private String responseStatus;

    @Column(length = 64)
    private String nonce;

    @Column(length = 200)
    private String remoteIp;

    protected HeartbeatLog() {
    }

    public HeartbeatLog(String licenseId, String fingerprint, String appVersion, String os,
                        String nonce, String remoteIp) {
        this.licenseId = licenseId;
        this.fingerprint = fingerprint;
        this.appVersion = appVersion;
        this.os = os;
        this.nonce = nonce;
        this.remoteIp = remoteIp;
    }

    public Long getId() { return id; }
    public String getLicenseId() { return licenseId; }
    public String getAppVersion() { return appVersion; }
    public String getOs() { return os; }
    public Instant getReceivedAt() { return receivedAt; }
    public String getResponseStatus() { return responseStatus; }
    public void setResponseStatus(String responseStatus) { this.responseStatus = responseStatus; }
}
