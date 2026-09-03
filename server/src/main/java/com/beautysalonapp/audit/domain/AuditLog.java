package com.beautysalonapp.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Değiştirilemez denetim kaydı (§8.1). Her mali işlem, fiyat/indirim değişikliği,
 * kasa açılışı, yedek geri yükleme, lisans işlemi buraya yazılır.
 *
 * <p>{@link com.beautysalonapp.core.domain.BaseEntity}'den türemez: audit kaydının
 * kendisi denetlenmez ve asla güncellenmez/silinmez.
 */
@Entity
@Table(name = "audit_log", indexes = {
        @Index(name = "ix_audit_at", columnList = "at"),
        @Index(name = "ix_audit_entity", columnList = "entity_type,entity_id"),
        @Index(name = "ix_audit_actor", columnList = "actor")
})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "at", nullable = false, updatable = false)
    private Instant at;

    @Column(name = "actor", nullable = false, length = 100, updatable = false)
    private String actor;

    @Column(name = "action", nullable = false, length = 60, updatable = false)
    private String action;

    @Column(name = "entity_type", length = 80, updatable = false)
    private String entityType;

    @Column(name = "entity_id", length = 40, updatable = false)
    private String entityId;

    @Column(name = "branch_id", updatable = false)
    private Long branchId;

    /** İnsan-okur özet (PII içermez). */
    @Column(name = "summary", length = 500, updatable = false)
    private String summary;

    /** Değişiklik ayrıntısı (eski/yeni JSON); PII maskelenerek yazılır. */
    @Column(name = "detail", updatable = false, columnDefinition = "text")
    private String detail;

    @Column(name = "ip", length = 45, updatable = false)
    private String ip;

    protected AuditLog() {
    }

    public AuditLog(String actor, String action, String entityType, String entityId,
                    Long branchId, String summary, String detail, String ip) {
        this.at = Instant.now();
        this.actor = actor;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.branchId = branchId;
        this.summary = summary;
        this.detail = detail;
        this.ip = ip;
    }

    public Long getId() { return id; }
    public Instant getAt() { return at; }
    public String getActor() { return actor; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public String getEntityId() { return entityId; }
    public Long getBranchId() { return branchId; }
    public String getSummary() { return summary; }
    public String getDetail() { return detail; }
    public String getIp() { return ip; }
}
