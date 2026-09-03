package com.beautysalonapp.licensing.web;

import com.beautysalonapp.audit.application.AuditService;
import com.beautysalonapp.licensing.application.FingerprintService;
import com.beautysalonapp.licensing.application.HeartbeatClient;
import com.beautysalonapp.licensing.application.LicenseService;
import com.beautysalonapp.licensing.application.LicenseSnapshot;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Lisans durumu ve yönetimi. {@code /status} kimlik doğrulaması gerektirmez
 * (giriş ekranında lisans bandı göstermek için); diğer işlemler LICENSE_MANAGE ister.
 */
@RestController
@RequestMapping("/api/v1/license")
public class LicenseController {

    private final LicenseService licenseService;
    private final HeartbeatClient heartbeatClient;
    private final FingerprintService fingerprintService;
    private final AuditService auditService;

    public LicenseController(LicenseService licenseService,
                             HeartbeatClient heartbeatClient,
                             FingerprintService fingerprintService,
                             AuditService auditService) {
        this.licenseService = licenseService;
        this.heartbeatClient = heartbeatClient;
        this.fingerprintService = fingerprintService;
        this.auditService = auditService;
    }

    @GetMapping("/status")
    public LicenseSnapshot status() {
        return licenseService.snapshot();
    }

    @GetMapping("/fingerprint")
    @PreAuthorize("hasAuthority('LICENSE_MANAGE')")
    public FingerprintService.FingerprintComponents fingerprint() {
        return fingerprintService.components();
    }

    @PostMapping("/upload")
    @PreAuthorize("hasAuthority('LICENSE_MANAGE')")
    public ResponseEntity<LicenseSnapshot> upload(@RequestParam("file") MultipartFile file) throws IOException {
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        LicenseSnapshot snap = licenseService.installLicense(content);
        auditService.record("LICENSE_INSTALL", "License", snap.plan(),
                "Lisans dosyası yüklendi: " + snap.customerName() + " (" + snap.status() + ")");
        return ResponseEntity.ok(snap);
    }

    @PostMapping("/heartbeat/now")
    @PreAuthorize("hasAuthority('LICENSE_MANAGE')")
    public LicenseSnapshot heartbeatNow() {
        heartbeatClient.sendOnce();
        auditService.record("LICENSE_HEARTBEAT", "License", null, "Elle heartbeat tetiklendi");
        return licenseService.snapshot();
    }
}
