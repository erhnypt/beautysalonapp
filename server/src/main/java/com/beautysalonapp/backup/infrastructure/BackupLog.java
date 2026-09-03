package com.beautysalonapp.backup.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/** Yedek/geri yükleme kayıt defteri. Değiştirilemez. */
@Entity
@Table(name = "backup_log", indexes = @Index(name = "ix_backup_log_started", columnList = "started_at"))
public class BackupLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kind", nullable = false, length = 12)
    private String kind; // BACKUP | RESTORE | VERIFY

    @Column(name = "trigger_src", length = 20)
    private String trigger; // MANUAL | SCHEDULED | SHUTDOWN | PRE_UPDATE

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "status", nullable = false, length = 10)
    private String status = "RUNNING"; // RUNNING | OK | FAILED

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "checksum", length = 64)
    private String checksum;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "error", length = 1000)
    private String error;

    @Column(name = "actor", length = 100)
    private String actor;

    protected BackupLog() {
    }

    public BackupLog(String kind, String trigger, String actor) {
        this.kind = kind;
        this.trigger = trigger;
        this.actor = actor;
        this.startedAt = Instant.now();
    }

    public void ok(String filePath, Long size, String checksum) {
        this.status = "OK";
        this.finishedAt = Instant.now();
        this.filePath = filePath;
        this.sizeBytes = size;
        this.checksum = checksum;
    }

    public void fail(String error) {
        this.status = "FAILED";
        this.finishedAt = Instant.now();
        this.error = error != null && error.length() > 1000 ? error.substring(0, 1000) : error;
    }

    public Long getId() { return id; }
    public String getKind() { return kind; }
    public String getTrigger() { return trigger; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public String getStatus() { return status; }
    public String getFilePath() { return filePath; }
    public Long getSizeBytes() { return sizeBytes; }
    public String getChecksum() { return checksum; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }
    public String getError() { return error; }
}
