package com.beautysalonapp.core.crypto;

import com.beautysalonapp.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Set;

/**
 * Alan şifreleme anahtarını (AES-256) çözer. Öncelik sırası (§8.2):
 *
 * <ol>
 *   <li><b>Açık yapılandırma</b> — {@code beautysalonapp.crypto.key-base64} (env / system property /
 *       yml). Üretimde OS anahtar kasası (Windows DPAPI / macOS Keychain) anahtarı bu kanaldan
 *       enjekte eder; kod tarafında yapılacak bir şey yok.</li>
 *   <li><b>Anahtar dosyası</b> — {@code <dataDir>/config/field-crypto.key} (Base64, sahibe özel izin).
 *       İlk açılışta yoksa 32 baytlık <em>rastgele</em> anahtar üretilip buraya yazılır. Böylece
 *       varsayılan kurulumda bile anahtar tam entropili olur (eski "kurulum kimliğinden türet"
 *       deseninin tahmin edilebilirliği ortadan kalkar).</li>
 *   <li><b>Son çare</b> — dosya yazılamıyorsa (salt-okunur dosya sistemi) kurulum kimliğinden
 *       türetim + <b>UYARI</b>. Yalnızca geliştirme; üretimde 1. veya 2. yol kullanılmalı.</li>
 * </ol>
 *
 * <p>Anahtar dosyası veritabanı dosyasıyla <b>aynı güven sınırındadır</b>; dosyanın işletim
 * sistemi anahtar kasasına (DPAPI/Keychain) sarmalanıp veri dizininden çıkarılması bir
 * <em>paketleme</em> adımıdır (bkz. {@code docs/04-guvenlik-kvkk.md} §2, ADR 0005). Bu sınıf
 * o adımın gireceği yeri (1. öncelik) hazır tutar ve dağıtılacak anahtarı üretir.
 */
final class CryptoKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(CryptoKeyProvider.class);
    private static final String KEY_FILE = "field-crypto.key";
    private static final int KEY_BYTES = 32;

    private CryptoKeyProvider() {
    }

    static SecretKeySpec resolveKey(AppProperties props) {
        String configured = props.getCrypto().getKeyBase64();
        if (configured != null && !configured.isBlank()) {
            return toKey(Base64.getDecoder().decode(configured.trim()), "crypto.key-base64");
        }

        Path keyFile = Path.of(props.getDataDir(), "config", KEY_FILE);
        try {
            if (Files.exists(keyFile)) {
                return readKeyFile(keyFile);
            }
            byte[] fresh = new byte[KEY_BYTES];
            new SecureRandom().nextBytes(fresh);
            try {
                writeOwnerOnly(keyFile, Base64.getEncoder().encodeToString(fresh));
            } catch (FileAlreadyExistsException race) {
                // Aynı anda başlayan ikinci örnek (AUTO_SERVER çok işlemli) dosyayı yeni yazdı.
                return readKeyFile(keyFile);
            }
            log.warn("Yeni alan şifreleme anahtarı üretildi ve yazıldı: {} — BU DOSYAYI GÜVENLİ ŞEKİLDE "
                    + "YEDEKLEYİN; kaybolursa şifreli alanlar (TC, telefon, IBAN...) açılamaz.", keyFile);
            return new SecretKeySpec(fresh, "AES");
        } catch (IOException e) {
            log.warn("Alan şifreleme anahtarı dosyası kullanılamadı ({}) — anahtar kurulum kimliğinden "
                    + "türetiliyor (YALNIZCA GELİŞTİRME; üretimde beautysalonapp.crypto.key-base64 verin).",
                    e.toString());
            return deriveFromInstallId(props.getInstallId());
        }
    }

    private static SecretKeySpec readKeyFile(Path keyFile) throws IOException {
        return toKey(Base64.getDecoder().decode(Files.readString(keyFile, StandardCharsets.UTF_8).trim()),
                keyFile.toString());
    }

    private static SecretKeySpec toKey(byte[] raw, String source) {
        if (raw.length != KEY_BYTES) {
            throw new IllegalStateException(source + " geçersiz: anahtar tam " + KEY_BYTES
                    + " bayt olmalı, gelen: " + raw.length);
        }
        return new SecretKeySpec(raw, "AES");
    }

    private static void writeOwnerOnly(Path file, String contents) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, contents, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        try {
            Set<PosixFilePermission> perms = EnumSet.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(file, perms);
        } catch (UnsupportedOperationException windows) {
            // Windows: POSIX izinleri yok. Veri dizini ACL sıkılaştırması paketleme adımıdır (I2).
        }
    }

    private static SecretKeySpec deriveFromInstallId(String installId) {
        try {
            String seed = (installId == null || installId.isBlank() ? "beautysalonapp-dev" : installId)
                    + "|field-crypto|v1";
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(seed.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(digest, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Anahtar türetilemedi", e);
        }
    }
}
