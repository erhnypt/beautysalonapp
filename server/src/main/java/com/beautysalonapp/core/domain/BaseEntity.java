package com.beautysalonapp.core.domain;

import com.beautysalonapp.core.context.BranchContextHolder;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Version;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Tüm kalıcı varlıkların ortak tabanı (§9).
 *
 * <p>Ortak alanlar: {@code id}, denetim damgaları, iyimser kilit {@code version},
 * çok şubeli yapı için {@code branchId} ve mantıksal silme için {@code deleted}.
 * Mali kayıtlarda fiziksel silme yasaktır; {@code deleted} yalnızca mali olmayan
 * ana kartlar (kategori, birim vb.) için kullanılır.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private long version;

    /** Çok şubeli konsolidasyon için; v1'de tek şube = 1. */
    @Column(name = "branch_id", nullable = false)
    private Long branchId = 1L;

    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false, length = 100)
    private String createdBy;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public Long getBranchId() { return branchId; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }
    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }

    public boolean isNew() { return id == null; }

    /**
     * Faz 8 tam şube izolasyonu (ADR 0006): kayıt anında bir "aktif şube" bağlamı varsa
     * (ör. {@code X-Branch-Id} isteği), {@code branchId} bununla etiketlenir. Bağlam yoksa
     * (arka plan işi, test, başlık göndermeyen eski istemci) alan sınıf varsayılanında
     * ({@code 1L}) kalır — v1 tek şube davranışı **değişmez**.
     */
    @PrePersist
    void assignActiveBranch() {
        Long active = BranchContextHolder.get();
        if (active != null) {
            this.branchId = active;
        }
    }
}
