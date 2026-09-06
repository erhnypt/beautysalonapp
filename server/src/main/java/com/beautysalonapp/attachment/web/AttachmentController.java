package com.beautysalonapp.attachment.web;

import com.beautysalonapp.attachment.application.AttachmentService;
import com.beautysalonapp.attachment.domain.Attachment;
import com.beautysalonapp.attachment.domain.AttachmentOwnerType;
import com.beautysalonapp.core.error.BusinessRuleException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Dosya eki (plan §9.1). Yetkilendirme not: {@code ownerType} çoklu varlık türünü
 * (PARTY/CONTRACT) kapsadığından, tam ayrıntılı yetki yerine "en az bir sahip türü
 * üzerinde düzenleme/görüntüleme yetkisi" kontrol edilir — asıl sınır zaten geçerli bir
 * {@code ownerId}'ye erişimin kendisi ilgili modülün kendi ekranından geçmesidir.
 */
@RestController
@RequestMapping("/api/v1/attachments")
public class AttachmentController {

    private final AttachmentService service;

    public AttachmentController(AttachmentService service) {
        this.service = service;
    }

    public record AttachmentView(long id, AttachmentOwnerType ownerType, long ownerId, String fileName,
                                 String contentType, Long sizeBytes, String sha256) {
        static AttachmentView of(Attachment a) {
            return new AttachmentView(a.getId(), a.getOwnerType(), a.getOwnerId(), a.getFileName(),
                    a.getContentType(), a.getSizeBytes(), a.getSha256());
        }
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('PARTY_VIEW','CONTRACT_VIEW')")
    public List<AttachmentView> list(@RequestParam AttachmentOwnerType ownerType, @RequestParam long ownerId) {
        return service.list(ownerType, ownerId).stream().map(AttachmentView::of).toList();
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('PARTY_EDIT','CONTRACT_EDIT')")
    public AttachmentView upload(@RequestParam AttachmentOwnerType ownerType, @RequestParam long ownerId,
                                 @RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new BusinessRuleException("empty_file", "Dosya boş");
        }
        return AttachmentView.of(service.upload(ownerType, ownerId, file.getOriginalFilename(),
                file.getContentType(), file.getBytes()));
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasAnyAuthority('PARTY_VIEW','CONTRACT_VIEW')")
    public ResponseEntity<byte[]> download(@PathVariable long id) {
        var file = service.download(id);
        MediaType type = file.contentType() != null
                ? MediaType.parseMediaType(file.contentType()) : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .body(file.content());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PARTY_EDIT','CONTRACT_EDIT')")
    public void delete(@PathVariable long id) {
        service.delete(id);
    }
}
