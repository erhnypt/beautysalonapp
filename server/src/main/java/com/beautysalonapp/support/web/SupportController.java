package com.beautysalonapp.support.web;

import com.beautysalonapp.support.application.SupportBundleService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Destek Paketi / Tanı ekranı (plan §13.2). "Uzaktan komut YOK" kuralı: sunucudan
 * gelen hiçbir komutu çalıştırmayız, yalnızca teşhis verisi üretiriz.
 */
@RestController
@RequestMapping("/api/v1/support")
public class SupportController {

    private final SupportBundleService service;

    public SupportController(SupportBundleService service) {
        this.service = service;
    }

    @GetMapping("/diagnostics")
    @PreAuthorize("hasAuthority('SETTINGS_VIEW')")
    public SupportBundleService.DiagnosticsSnapshot diagnostics() {
        return service.diagnostics();
    }

    @GetMapping("/bundle/manifest")
    @PreAuthorize("hasAuthority('BACKUP_RUN')")
    public List<SupportBundleService.BundleFile> manifest() {
        return service.manifest();
    }

    @GetMapping("/bundle")
    @PreAuthorize("hasAuthority('BACKUP_RUN')")
    public ResponseEntity<byte[]> bundle() {
        byte[] zip = service.buildBundle();
        String filename = "destek-paketi-" + Instant.now().toString().replace(":", "-") + ".zip";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(zip);
    }
}
