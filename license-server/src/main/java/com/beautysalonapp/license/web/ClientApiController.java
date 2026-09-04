package com.beautysalonapp.license.web;

import com.beautysalonapp.license.crypto.Ed25519KeyProvider;
import com.beautysalonapp.license.repo.AppReleaseRepository;
import com.beautysalonapp.license.service.HeartbeatService;
import com.beautysalonapp.license.service.HeartbeatService.HeartbeatRequest;
import com.beautysalonapp.license.service.HeartbeatService.HeartbeatResponse;
import com.beautysalonapp.license.service.LicenseService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * İstemci (kurulum) API'si. Kimlik: aktivasyon anahtarı / lisans no. Oturum yok.
 */
@RestController
@RequestMapping("/api/v1")
public class ClientApiController {

    private final LicenseService licenseService;
    private final HeartbeatService heartbeatService;
    private final Ed25519KeyProvider keys;
    private final AppReleaseRepository releases;

    public ClientApiController(LicenseService licenseService, HeartbeatService heartbeatService,
                               Ed25519KeyProvider keys, AppReleaseRepository releases) {
        this.licenseService = licenseService;
        this.heartbeatService = heartbeatService;
        this.keys = keys;
        this.releases = releases;
    }

    public record ActivateRequest(@NotBlank String activationKey, @NotBlank String fingerprint,
                                  Integer fpVersion) {}

    /** İlk aktivasyon: anahtar → imzalı lisans dosyası. */
    @PostMapping("/activate")
    public Map<String, String> activate(@RequestBody ActivateRequest req) {
        String lic = licenseService.activate(req.activationKey(),
                req.fingerprint(), req.fpVersion() == null ? 2 : req.fpVersion());
        return Map.of("license", lic);
    }

    @PostMapping("/heartbeat")
    public HeartbeatResponse heartbeat(@RequestBody HeartbeatRequest req, HttpServletRequest http) {
        return heartbeatService.process(req, http.getRemoteAddr());
    }

    public record TransferReq(@NotBlank String licenseId, String oldFingerprint,
                              @NotBlank String newFingerprint) {}

    /** Makine değişikliği talebi. Ayda 1 otomatik onay; fazlası manuel. */
    @PostMapping("/transfer")
    public Map<String, Object> transfer(@RequestBody TransferReq req) {
        var r = licenseService.requestTransfer(req.licenseId(), req.oldFingerprint(), req.newFingerprint());
        if (r.autoApproved()) {
            return Map.of("autoApproved", true, "license", r.licenseFileOrNull());
        }
        return Map.of("autoApproved", false, "pendingRequestId", r.pendingRequestId(),
                "message", "Talebiniz alındı, elle onay bekliyor.");
    }

    @GetMapping("/updates/latest")
    public ResponseEntity<Map<String, Object>> latest() {
        return releases.findFirstByChannelOrderByReleasedAtDesc("stable")
                .<ResponseEntity<Map<String, Object>>>map(r -> ResponseEntity.ok(Map.of(
                        "latestVersion", r.getVersion(),
                        "updateUrl", r.getUrl(),
                        "checksum", r.getChecksum() == null ? "" : r.getChecksum(),
                        "mandatory", r.isMandatory())))
                .orElse(ResponseEntity.noContent().build());
    }

    /** İstemciye gömülecek public key (dev kolaylığı; üretimde build'e gömülür). */
    @GetMapping("/public-key")
    public Map<String, String> publicKey() {
        return Map.of("algorithm", "Ed25519", "publicKeyBase64", keys.publicKeyBase64());
    }
}
