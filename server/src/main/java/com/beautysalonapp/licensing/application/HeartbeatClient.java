package com.beautysalonapp.licensing.application;

import com.beautysalonapp.config.AppProperties;
import com.beautysalonapp.licensing.infrastructure.LicenseStateRepository;
import com.beautysalonapp.outbound.GuardedRestClient;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Heartbeat protokolü (§6.5).
 *
 * <ul>
 *   <li>Günde bir kez (jitter'lı) lisans sunucusuna minimum veri gönderir — hiçbir iş verisi yok.</li>
 *   <li>Başarısız olursa iş durmaz; yalnızca sayaç artar.</li>
 *   <li>Yanıttaki {@code nonce} istekle aynı olmalı (replay koruması).</li>
 *   <li>Tam çevrimdışı modda tamamen kapalıdır.</li>
 * </ul>
 */
@Component
public class HeartbeatClient {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatClient.class);

    private final AppProperties props;
    private final GuardedRestClient http;
    private final LicenseService licenseService;
    private final FingerprintService fingerprintService;
    private final LicenseStateRepository stateRepo;

    public HeartbeatClient(AppProperties props,
                           GuardedRestClient http,
                           LicenseService licenseService,
                           FingerprintService fingerprintService,
                           LicenseStateRepository stateRepo) {
        this.props = props;
        this.http = http;
        this.licenseService = licenseService;
        this.fingerprintService = fingerprintService;
        this.stateRepo = stateRepo;
    }

    /** Her 6 saatte bir tetiklenir; iç mantık son başarılı heartbeat'ten bu yana 24 saat geçtiyse gönderir. */
    @Scheduled(fixedDelayString = "PT6H", initialDelayString = "PT2M")
    public void scheduledHeartbeat() {
        if (props.isFullOfflineMode() || !props.getLicensing().isHeartbeatEnabled()) {
            return;
        }
        var state = stateRepo.singleton();
        Instant last = state.getLastSuccessfulHeartbeatAt();
        if (last != null && last.isAfter(Instant.now().minusSeconds(23 * 3600))) {
            return; // henüz zamanı gelmedi
        }
        // Jitter: 0–120 dk (§6.5) — burada kısa bir uyku ile taklit
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(0, 5_000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        sendOnce();
    }

    /** Tek seferlik heartbeat; test ve "şimdi doğrula" düğmesi için public. */
    public void sendOnce() {
        String endpoint = props.getLicensing().getHeartbeatEndpoint();
        String nonce = UUID.randomUUID().toString();
        Map<String, Object> body = Map.of(
                "fingerprint", fingerprintService.compute(),
                "appVersion", appVersion(),
                "os", System.getProperty("os.name") + " / " + System.getProperty("os.version"),
                "lastHeartbeatAt", nullSafe(stateRepo.singleton().getLastSuccessfulHeartbeatAt()),
                "nonce", nonce
        );
        try {
            HeartbeatResponse resp = http.post(endpoint, body, HeartbeatResponse.class);
            if (resp == null) {
                throw new IllegalStateException("Boş heartbeat yanıtı");
            }
            if (resp.nonce() != null && !nonce.equals(resp.nonce())) {
                log.warn("Heartbeat nonce uyuşmuyor — yanıt yok sayıldı (replay koruması)");
                licenseService.recordHeartbeatFailure();
                return;
            }
            licenseService.applyServerStatus(resp.status(), parseInstant(resp.serverTime()),
                    resp.license(), resp.message());
            log.info("Heartbeat başarılı: status={}", resp.status());
        } catch (Exception e) {
            licenseService.recordHeartbeatFailure();
            int fails = stateRepo.singleton().getConsecutiveHeartbeatFailures();
            if (fails >= 3) {
                log.warn("Heartbeat {} kez üst üste başarısız — kullanıcı bilgilendirilmeli", fails);
            } else {
                log.info("Heartbeat başarısız (iş durmaz): {}", e.getMessage());
            }
        }
    }

    private String appVersion() {
        Package p = getClass().getPackage();
        String v = p == null ? null : p.getImplementationVersion();
        return v != null ? v : "1.0.0-dev";
    }

    private static String nullSafe(Instant i) {
        return i == null ? "" : i.toString();
    }

    private static Instant parseInstant(String s) {
        try {
            return s == null || s.isBlank() ? null : Instant.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HeartbeatResponse(
            String status,
            String license,
            String serverTime,
            String message,
            String latestVersion,
            boolean mandatoryUpdate,
            String nonce
    ) {}
}
