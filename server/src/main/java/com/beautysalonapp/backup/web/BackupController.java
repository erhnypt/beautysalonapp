package com.beautysalonapp.backup.web;

import com.beautysalonapp.backup.application.BackupService;
import com.beautysalonapp.backup.application.BackupService.BackupFileInfo;
import com.beautysalonapp.backup.application.BackupService.BackupStatus;
import com.beautysalonapp.backup.application.BackupService.VerifyResult;
import com.beautysalonapp.backup.infrastructure.BackupLog;
import com.beautysalonapp.backup.infrastructure.BackupLogRepository;
import com.beautysalonapp.core.error.BusinessRuleException;
import com.beautysalonapp.security.infrastructure.AppUserRepository;
import jakarta.validation.constraints.NotBlank;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * Yedekleme uçları. {@code /api/v1/backup/**} lisans kısıtlamasından muaftır (§6.4):
 * LOCKED durumda dahi müşteri yedek alıp verisini dışa aktarabilir.
 */
@RestController
@RequestMapping("/api/v1/backup")
public class BackupController {

    private final BackupService backup;
    private final BackupLogRepository logs;
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;

    public BackupController(BackupService backup, BackupLogRepository logs,
                           AppUserRepository users, PasswordEncoder passwordEncoder) {
        this.backup = backup;
        this.logs = logs;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    public record LogView(long id, String kind, String trigger, Instant startedAt, Instant finishedAt,
                          String status, String fileName, Long sizeBytes, Instant verifiedAt, String error) {
        static LogView of(BackupLog l) {
            String fn = l.getFilePath() == null ? null
                    : l.getFilePath().substring(l.getFilePath().replace('\\', '/').lastIndexOf('/') + 1);
            return new LogView(l.getId(), l.getKind(), l.getTrigger(), l.getStartedAt(), l.getFinishedAt(),
                    l.getStatus(), fn, l.getSizeBytes(), l.getVerifiedAt(), l.getError());
        }
    }

    @GetMapping("/status")
    @PreAuthorize("hasAnyAuthority('BACKUP_RUN','BACKUP_RESTORE','SETTINGS_VIEW')")
    public BackupStatus status() {
        return backup.status();
    }

    @GetMapping("/list")
    @PreAuthorize("hasAnyAuthority('BACKUP_RUN','BACKUP_RESTORE')")
    public List<BackupFileInfo> list() {
        return backup.listBackups();
    }

    @GetMapping("/history")
    @PreAuthorize("hasAnyAuthority('BACKUP_RUN','BACKUP_RESTORE','AUDIT_VIEW')")
    public List<LogView> history() {
        return logs.findTop50ByOrderByStartedAtDesc().stream().map(LogView::of).toList();
    }

    @PostMapping("/run")
    @PreAuthorize("hasAuthority('BACKUP_RUN')")
    public LogView run(Authentication auth) {
        return LogView.of(backup.createBackup("MANUAL", auth == null ? "system" : auth.getName()));
    }

    @PostMapping("/verify/{name}")
    @PreAuthorize("hasAnyAuthority('BACKUP_RUN','BACKUP_RESTORE')")
    public VerifyResult verify(@PathVariable String name) {
        return backup.verify(name);
    }

    @GetMapping("/download/{name}")
    @PreAuthorize("hasAnyAuthority('BACKUP_RUN','BACKUP_RESTORE','DATA_EXPORT')")
    public ResponseEntity<Resource> download(@PathVariable String name) {
        byte[] data = backup.readBackup(name);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header("Content-Disposition", ContentDisposition.attachment().filename(name).build().toString())
                .body(new ByteArrayResource(data));
    }

    @PostMapping("/restore")
    @PreAuthorize("hasAuthority('BACKUP_RESTORE')")
    public void restore(@RequestParam("file") MultipartFile file,
                        @RequestParam("adminPassword") @NotBlank String adminPassword,
                        Authentication auth) throws IOException {
        // Yönetici parolası doğrulaması (§11.2)
        String username = auth.getName();
        boolean valid = users.findByUsernameIgnoreCase(username)
                .map(u -> passwordEncoder.matches(adminPassword, u.getPasswordHash()))
                .orElse(false);
        if (!valid) {
            throw new BusinessRuleException("bad_admin_password", "Yönetici parolası hatalı");
        }
        backup.restore(file.getBytes(), username);
    }
}
