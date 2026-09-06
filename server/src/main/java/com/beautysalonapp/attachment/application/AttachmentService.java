package com.beautysalonapp.attachment.application;

import com.beautysalonapp.attachment.domain.Attachment;
import com.beautysalonapp.attachment.domain.AttachmentOwnerType;
import com.beautysalonapp.attachment.infrastructure.AttachmentRepository;
import com.beautysalonapp.audit.application.AuditService;
import com.beautysalonapp.config.AppProperties;
import com.beautysalonapp.core.error.BusinessRuleException;
import com.beautysalonapp.core.error.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Dosya eki yönetimi (plan §9.1, §10.12 taranmış sözleşme, §8.3 KVKK). Dosyanın kendisi
 * DB'de değil {@code <dataDir>/attachments/} altında durur; burada yalnızca referans
 * tutulur ve yedeklemeye {@code BackupService} tarafından dahil edilir.
 *
 * <p><b>Yol geçişi (path traversal) önlemi</b> (güvenlik gözden geçirme, madde C5):
 * diskteki gerçek dosya adı kullanıcı girdisinden asla doğrudan üretilmez — yalnızca
 * sunucu tarafında üretilen bir {@link UUID} kullanılır. Kullanıcının verdiği orijinal
 * dosya adı yalnızca DB'de görüntüleme amaçlı saklanır ({@code sha256} ile birlikte
 * bütünlük referansı) ve indirirken {@code Content-Disposition} başlığında (sanitize
 * edilmiş biçimde) geri verilir.
 */
@Service
public class AttachmentService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);
    private static final long MAX_SIZE_BYTES = 100L * 1024 * 1024; // application.yml multipart limitiyle aynı

    private final AttachmentRepository attachments;
    private final AppProperties props;
    private final AuditService audit;

    public AttachmentService(AttachmentRepository attachments, AppProperties props, AuditService audit) {
        this.attachments = attachments;
        this.props = props;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<Attachment> list(AttachmentOwnerType ownerType, long ownerId) {
        return attachments.findAllFor(ownerType, ownerId);
    }

    @Transactional
    public Attachment upload(AttachmentOwnerType ownerType, long ownerId, String originalFilename,
                             String contentType, byte[] content) {
        if (content == null || content.length == 0) {
            throw new BusinessRuleException("empty_file", "Dosya boş olamaz");
        }
        if (content.length > MAX_SIZE_BYTES) {
            throw new BusinessRuleException("file_too_large", "Dosya 100 MB sınırını aşıyor");
        }
        String safeName = sanitizeFilename(originalFilename);
        String storedName = UUID.randomUUID() + "_" + safeName;

        Path ownerDir = attachmentsRoot().resolve(ownerType.name()).resolve(String.valueOf(ownerId));
        Path target = ownerDir.resolve(storedName);
        // Savunma amaçlı ikinci kontrol: üretilen yolun gerçekten kök dizin altında kaldığını doğrula.
        Path root = attachmentsRoot().normalize();
        if (!target.normalize().startsWith(root)) {
            throw new BusinessRuleException("invalid_path", "Geçersiz dosya yolu");
        }

        try {
            Files.createDirectories(ownerDir);
            Files.write(target, content);
        } catch (IOException e) {
            throw new UncheckedIOException("Dosya kaydedilemedi", e);
        }

        Attachment a = new Attachment(ownerType, ownerId, safeName, contentType, (long) content.length,
                root.relativize(target.normalize()).toString(), sha256(content));
        attachments.save(a);
        audit.record("ATTACHMENT_UPLOAD", "Attachment", a.getId(),
                ownerType + " #" + ownerId + " için dosya eklendi: " + safeName);
        return a;
    }

    public record DownloadedFile(String fileName, String contentType, byte[] content) {}

    @Transactional(readOnly = true)
    public DownloadedFile download(long attachmentId) {
        Attachment a = get(attachmentId);
        Path root = attachmentsRoot().normalize();
        Path file = root.resolve(a.getStoragePath()).normalize();
        if (!file.startsWith(root)) {
            // storage_path yalnızca sunucu tarafında üretilir; bu asla olmamalı — savunma amaçlı.
            throw new BusinessRuleException("invalid_path", "Geçersiz dosya yolu");
        }
        try {
            byte[] content = Files.readAllBytes(file);
            return new DownloadedFile(a.getFileName(), a.getContentType(), content);
        } catch (IOException e) {
            throw new UncheckedIOException("Dosya okunamadı: " + a.getFileName(), e);
        }
    }

    @Transactional
    public void delete(long attachmentId) {
        Attachment a = get(attachmentId);
        a.setDeleted(true);
        audit.record("ATTACHMENT_DELETE", "Attachment", attachmentId,
                a.getOwnerType() + " #" + a.getOwnerId() + " ekinden kaldırıldı: " + a.getFileName());
    }

    private Attachment get(long id) {
        return attachments.findById(id).filter(a -> !a.isDeleted())
                .orElseThrow(() -> new NotFoundException("Ek dosya", id));
    }

    private Path attachmentsRoot() {
        return Path.of(props.getDataDir(), "attachments");
    }

    /**
     * Orijinal dosya adından yalnızca güvenli karakterleri (harf/rakam/nokta/tire/alt çizgi)
     * bırakır; yol ayırıcıları ve ".." dizilerini tamamen atar. Sonuç asla boş bırakılmaz.
     */
    private static String sanitizeFilename(String original) {
        if (original == null) {
            return "dosya";
        }
        String base = original.replace('\\', '/');
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        String cleaned = base.replaceAll("[^A-Za-z0-9._-]", "_").replaceAll("^\\.+", "");
        if (cleaned.isBlank()) {
            cleaned = "dosya";
        }
        return cleaned.length() > 150 ? cleaned.substring(cleaned.length() - 150) : cleaned;
    }

    private static String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 kullanılamıyor", e);
        }
    }
}
