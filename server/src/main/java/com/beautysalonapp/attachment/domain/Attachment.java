package com.beautysalonapp.attachment.domain;

import com.beautysalonapp.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * Dosya eki (plan §9.1): müşteri fotoğrafı, taranmış imzalı sözleşme nüshası vb.
 * Dosyanın kendisi veritabanında değil {@code attachments/} dizininde durur; burada
 * yalnızca referans (yol, boyut, sha256) tutulur (bkz. plan §8.2, güvenlik notu).
 */
@Entity
@Table(name = "attachment", indexes = @Index(name = "ix_attachment_owner", columnList = "owner_type,owner_id"))
public class Attachment extends BaseEntity {

    @Column(name = "owner_type", nullable = false, length = 60)
    private String ownerType;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    /** Diskteki gerçek yol — kullanıcı girdisinden değil, sunucu tarafında üretilir. */
    @Column(name = "storage_path", nullable = false, length = 500)
    private String storagePath;

    @Column(name = "sha256", length = 64)
    private String sha256;

    protected Attachment() {
    }

    public Attachment(AttachmentOwnerType ownerType, Long ownerId, String fileName,
                      String contentType, Long sizeBytes, String storagePath, String sha256) {
        this.ownerType = ownerType.name();
        this.ownerId = ownerId;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storagePath = storagePath;
        this.sha256 = sha256;
    }

    public AttachmentOwnerType getOwnerType() { return AttachmentOwnerType.valueOf(ownerType); }
    public Long getOwnerId() { return ownerId; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public Long getSizeBytes() { return sizeBytes; }
    public String getStoragePath() { return storagePath; }
    public String getSha256() { return sha256; }
}
