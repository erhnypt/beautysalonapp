package com.beautysalonapp.modules.notification.domain;

import com.beautysalonapp.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/** Bildirim kuyruğu satırı (§9.10). Kuyruklu + idempotent ({@code dedup_key}). */
@Entity
@Table(name = "notification_queue",
        uniqueConstraints = @UniqueConstraint(name = "uq_notif_dedup", columnNames = "dedup_key"),
        indexes = @Index(name = "ix_notif_status_sched", columnList = "status,scheduled_at"))
public class NotificationQueueItem extends BaseEntity {

    @Column(name = "party_id")
    private Long partyId;

    @Column(name = "to_address", nullable = false, length = 200)
    private String toAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 8)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "notif_type", nullable = false, length = 24)
    private NotificationType type;

    @Column(name = "subject", length = 200)
    private String subject;

    @Column(name = "body", nullable = false, length = 2000)
    private String body;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private NotificationStatus status = NotificationStatus.PENDING;

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "dedup_key", nullable = false, length = 120)
    private String dedupKey;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected NotificationQueueItem() {
    }

    public NotificationQueueItem(Long partyId, String toAddress, NotificationChannel channel,
                                 NotificationType type, String subject, String body,
                                 Instant scheduledAt, String dedupKey) {
        this.partyId = partyId;
        this.toAddress = toAddress;
        this.channel = channel;
        this.type = type;
        this.subject = subject;
        this.body = body;
        this.scheduledAt = scheduledAt;
        this.nextAttemptAt = scheduledAt;
        this.dedupKey = dedupKey;
    }

    public void markSent() {
        this.status = NotificationStatus.SENT;
        this.sentAt = Instant.now();
        this.lastError = null;
    }

    public void markFailure(String error, int maxAttempts) {
        this.attempts++;
        this.lastError = error != null && error.length() > 500 ? error.substring(0, 500) : error;
        if (this.attempts >= maxAttempts) {
            this.status = NotificationStatus.FAILED;
        } else {
            // exponential backoff: 2^attempts dakika
            long delayMin = (long) Math.pow(2, this.attempts);
            this.nextAttemptAt = Instant.now().plusSeconds(delayMin * 60);
        }
    }

    public void skip(String reason) {
        this.status = NotificationStatus.SKIPPED;
        this.lastError = reason;
    }

    public Long getPartyId() { return partyId; }
    public String getToAddress() { return toAddress; }
    public NotificationChannel getChannel() { return channel; }
    public NotificationType getType() { return type; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public Instant getScheduledAt() { return scheduledAt; }
    public NotificationStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public String getLastError() { return lastError; }
    public String getDedupKey() { return dedupKey; }
    public Instant getSentAt() { return sentAt; }
}
