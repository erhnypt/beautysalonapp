package com.beautysalonapp.licensing.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Lisans önbelleği + kurcalama tespiti durumu (§9.1, §6.7). Tek satır (id = 1).
 *
 * <p>Yetkili kaynak imzalı {@code license.lic} dosyasıdır; bu tablo yalnızca hızlı
 * erişim önbelleği ve "görülen en büyük zaman" (monotonic clock) saklar.
 */
@Entity
@Table(name = "license_state")
public class LicenseState {

    @Id
    @Column(name = "id")
    private Long id = 1L;

    /** Son başarılı doğrulanan lisans dosyası ham içeriği (imzalı). */
    @Column(name = "license_blob", columnDefinition = "text")
    private String licenseBlob;

    /** Son heartbeat sunucu yanıtındaki durum: ACTIVE | SUSPENDED | REVOKED. */
    @Column(name = "server_status", length = 20)
    private String serverStatus = "ACTIVE";

    @Column(name = "last_successful_heartbeat_at")
    private Instant lastSuccessfulHeartbeatAt;

    @Column(name = "last_heartbeat_attempt_at")
    private Instant lastHeartbeatAttemptAt;

    @Column(name = "consecutive_heartbeat_failures")
    private int consecutiveHeartbeatFailures = 0;

    /** Monotonic clock: uygulamanın gördüğü en büyük güvenilir zaman (AES-GCM şifreli). */
    @Column(name = "max_seen_time_enc", length = 512)
    private String maxSeenTimeEnc;

    /** READ_ONLY durumuna ilk geçiş anı (LOCKED sayacı için). */
    @Column(name = "read_only_since")
    private Instant readOnlySince;

    /** true ise kurcalama tespit edildi; online doğrulama yapılana dek TAMPERED. */
    @Column(name = "tamper_flag", nullable = false)
    private boolean tamperFlag = false;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getLicenseBlob() { return licenseBlob; }
    public void setLicenseBlob(String licenseBlob) { this.licenseBlob = licenseBlob; }
    public String getServerStatus() { return serverStatus; }
    public void setServerStatus(String serverStatus) { this.serverStatus = serverStatus; }
    public Instant getLastSuccessfulHeartbeatAt() { return lastSuccessfulHeartbeatAt; }
    public void setLastSuccessfulHeartbeatAt(Instant v) { this.lastSuccessfulHeartbeatAt = v; }
    public Instant getLastHeartbeatAttemptAt() { return lastHeartbeatAttemptAt; }
    public void setLastHeartbeatAttemptAt(Instant v) { this.lastHeartbeatAttemptAt = v; }
    public int getConsecutiveHeartbeatFailures() { return consecutiveHeartbeatFailures; }
    public void setConsecutiveHeartbeatFailures(int v) { this.consecutiveHeartbeatFailures = v; }
    public String getMaxSeenTimeEnc() { return maxSeenTimeEnc; }
    public void setMaxSeenTimeEnc(String v) { this.maxSeenTimeEnc = v; }
    public Instant getReadOnlySince() { return readOnlySince; }
    public void setReadOnlySince(Instant v) { this.readOnlySince = v; }
    public boolean isTamperFlag() { return tamperFlag; }
    public void setTamperFlag(boolean v) { this.tamperFlag = v; }
}
