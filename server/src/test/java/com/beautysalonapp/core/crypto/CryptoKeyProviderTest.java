package com.beautysalonapp.core.crypto;

import com.beautysalonapp.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.crypto.spec.SecretKeySpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Base64;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CryptoKeyProviderTest {

    private static AppProperties props(Path dataDir, String keyBase64) {
        AppProperties p = new AppProperties();
        p.setDataDir(dataDir.toString());
        p.setInstallId("test-install-id");
        p.getCrypto().setKeyBase64(keyBase64);
        return p;
    }

    @Test
    void acik_yapilandirma_en_yuksek_oncelik(@TempDir Path dir) {
        byte[] raw = new byte[32];
        for (int i = 0; i < raw.length; i++) raw[i] = (byte) i;
        String b64 = Base64.getEncoder().encodeToString(raw);

        SecretKeySpec key = CryptoKeyProvider.resolveKey(props(dir, b64));

        assertThat(key.getEncoded()).containsExactly(raw);
        assertThat(Files.exists(dir.resolve("config").resolve("field-crypto.key"))).isFalse();
    }

    @Test
    void anahtar_dosyasi_yoksa_uretilir_ve_sonraki_acilista_ayni_kalir(@TempDir Path dir) {
        SecretKeySpec first = CryptoKeyProvider.resolveKey(props(dir, ""));
        Path keyFile = dir.resolve("config").resolve("field-crypto.key");

        assertThat(Files.exists(keyFile)).isTrue();
        assertThat(first.getEncoded()).hasSize(32);

        SecretKeySpec second = CryptoKeyProvider.resolveKey(props(dir, ""));
        assertThat(second.getEncoded()).containsExactly(first.getEncoded());
    }

    @Test
    void uretilen_anahtar_dosyasi_sahibe_ozel_izinli(@TempDir Path dir) throws Exception {
        CryptoKeyProvider.resolveKey(props(dir, ""));
        Path keyFile = dir.resolve("config").resolve("field-crypto.key");

        Set<PosixFilePermission> perms;
        try {
            perms = Files.getPosixFilePermissions(keyFile);
        } catch (UnsupportedOperationException notPosix) {
            assumeTrue(false, "POSIX olmayan dosya sistemi — izin kontrolü atlandı");
            return;
        }
        assertThat(perms).containsExactlyInAnyOrder(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
    }

    @Test
    void bozuk_uzunluktaki_anahtar_dosyasi_hata_verir(@TempDir Path dir) throws Exception {
        Path cfg = Files.createDirectories(dir.resolve("config"));
        Files.writeString(cfg.resolve("field-crypto.key"),
                Base64.getEncoder().encodeToString(new byte[16])); // 32 değil

        assertThatThrownBy(() -> CryptoKeyProvider.resolveKey(props(dir, "")))
                .isInstanceOf(IllegalStateException.class);
    }
}
