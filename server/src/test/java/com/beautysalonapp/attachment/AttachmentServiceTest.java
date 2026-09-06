package com.beautysalonapp.attachment;

import com.beautysalonapp.attachment.application.AttachmentService;
import com.beautysalonapp.attachment.domain.Attachment;
import com.beautysalonapp.attachment.domain.AttachmentOwnerType;
import com.beautysalonapp.core.error.BusinessRuleException;
import com.beautysalonapp.core.error.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class AttachmentServiceTest {

    @Autowired AttachmentService attachments;

    @Test
    void yukleme_indirme_ve_listeleme_tam_dongu() throws Exception {
        long ownerId = System.nanoTime();
        byte[] content = "test içerik — sözleşme taraması".getBytes(StandardCharsets.UTF_8);

        Attachment saved = attachments.upload(AttachmentOwnerType.CONTRACT, ownerId,
                "sozlesme.pdf", "application/pdf", content);

        assertThat(saved.getFileName()).isEqualTo("sozlesme.pdf");
        assertThat(saved.getSizeBytes()).isEqualTo(content.length);
        assertThat(saved.getSha256()).isEqualTo(sha256Hex(content));

        var list = attachments.list(AttachmentOwnerType.CONTRACT, ownerId);
        assertThat(list).extracting(Attachment::getId).contains(saved.getId());

        var downloaded = attachments.download(saved.getId());
        assertThat(downloaded.fileName()).isEqualTo("sozlesme.pdf");
        assertThat(downloaded.content()).isEqualTo(content);
    }

    @Test
    void dosya_adindaki_yol_gecisi_denemesi_temizlenir() {
        long ownerId = System.nanoTime();
        Attachment saved = attachments.upload(AttachmentOwnerType.PARTY, ownerId,
                "../../../../etc/passwd", "text/plain", "zararsiz".getBytes(StandardCharsets.UTF_8));

        assertThat(saved.getFileName()).doesNotContain("..").doesNotContain("/");
        assertThat(saved.getStoragePath()).doesNotContain("..");
    }

    @Test
    void windows_yol_ayiricili_dosya_adi_da_temizlenir() {
        long ownerId = System.nanoTime();
        Attachment saved = attachments.upload(AttachmentOwnerType.PARTY, ownerId,
                "..\\..\\windows\\system32\\evil.txt", "text/plain", "x".getBytes(StandardCharsets.UTF_8));

        assertThat(saved.getFileName()).isEqualTo("evil.txt");
    }

    @Test
    void bos_dosya_reddedilir() {
        assertThatThrownBy(() -> attachments.upload(AttachmentOwnerType.PARTY, 1L,
                "bos.txt", "text/plain", new byte[0]))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void silinen_ek_bir_daha_erisilemez() {
        long ownerId = System.nanoTime();
        Attachment saved = attachments.upload(AttachmentOwnerType.PARTY, ownerId,
                "foto.jpg", "image/jpeg", "img".getBytes(StandardCharsets.UTF_8));

        attachments.delete(saved.getId());

        assertThatThrownBy(() -> attachments.download(saved.getId())).isInstanceOf(NotFoundException.class);
        assertThat(attachments.list(AttachmentOwnerType.PARTY, ownerId))
                .noneMatch(a -> a.getId() == saved.getId());
    }

    private static String sha256Hex(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }
}
