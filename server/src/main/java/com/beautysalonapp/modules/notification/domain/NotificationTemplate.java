package com.beautysalonapp.modules.notification.domain;

import com.beautysalonapp.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "notification_template", uniqueConstraints =
        @UniqueConstraint(name = "uq_notif_template", columnNames = {"branch_id", "notif_type", "channel"}))
public class NotificationTemplate extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "notif_type", nullable = false, length = 24)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 8)
    private NotificationChannel channel;

    @Column(name = "subject", length = 200)
    private String subject;

    @Column(name = "body", nullable = false, length = 2000)
    private String body;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected NotificationTemplate() {
    }

    public NotificationTemplate(NotificationType type, NotificationChannel channel, String subject, String body) {
        this.type = type;
        this.channel = channel;
        this.subject = subject;
        this.body = body;
    }

    public NotificationType getType() { return type; }
    public NotificationChannel getChannel() { return channel; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
