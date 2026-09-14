package com.beautysalonapp.core.crypto;

import com.beautysalonapp.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
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
 *       Yoksa:
 *       <ul>
 *         <li><b>Var olan kurulum</b> (veri dizininde H2 veritabanı dosyası zaten varsa) — eski
 *             sürümün anahtarı kurulum kimliğinden türettiği varsayılır; AYNI türetim yapılıp
 *             sonucu dosyaya <em>taşınır</em>. Aksi halde daha önce şifrelenmiş alanlar (TC,
 *             telefon, IBAN...) yeni rastgele bir anahtarla asla çözülemez hâle gelirdi.</li>
 *         <li><b>Yepyeni kurulum</b> — 32 baytlık <em>rastgele</em> anahtar üretilip dosyaya yazılır;
 *             tam entropili (eski "kurulum kimliğinden türet" deseninin tahmin edilebilirliği yok).</li>
 *       </ul>
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

            byte[] keyBytes;
            if (hasExistingDatabase(props.getDataDir())) {
                // Yükseltme senaryosu: bu veri dizini daha önce çalışmış. Önceki sürümlerde
                // (bu dosya hiç yoktu) anahtar her açılışta kurulum kimliğinden türetiliyordu —
                // yeni rastgele bir anahtar üretmek, o eski anahtarla şifrelenmiş her alanı
                // (TC, telefon, IBAN...) kalıcı olarak çözülemez yapardı. Aynı türetimi bir kez
                // yapıp sonucu dosyaya taşıyoruz; bundan sonra dosyadan okunur.
                keyBytes = deriveFromInstallId(props.getInstallId()).getEncoded();
                log.warn("Var olan bir kurulum tespit edildi (veri dizininde veritabanı dosyası var) — "
                        + "uyumluluk için anahtar kurulum kimliğinden türetilip {} dosyasına taşındı.",
                        keyFile);
            } else {
                keyBytes = new byte[KEY_BYTES];
                new SecureRandom().nextBytes(keyBytes);
            }

            try {
                writeOwnerOnly(keyFile, Base64.getEncoder().encodeToString(keyBytes));
            } catch (FileAlreadyExistsException race) {
                // Aynı anda başlayan ikinci örnek (AUTO_SERVER çok işlemli) dosyayı yeni yazdı.
                return readKeyFile(keyFile);
            }
            log.warn("Alan şifreleme anahtarı dosyası yazıldı: {} — BU DOSYAYI GÜVENLİ ŞEKİLDE "
                    + "YEDEKLEYİN; kaybolursa şifreli alanlar (TC, telefon, IBAN...) açılamaz.", keyFile);
            return new SecretKeySpec(keyBytes, "AES");
        } catch (IOException e) {
            log.warn("Alan şifreleme anahtarı dosyası kullanılamadı ({}) — anahtar kurulum kimliğinden "
                    + "türetiliyor (YALNIZCA GELİŞTİRME; üretimde beautysalonapp.crypto.key-base64 verin).",
                    e.toString());
            return deriveFromInstallId(props.getInstallId());
        }
    }

    /** {@code <dataDir>/data} altında bir H2 dosyası (*.mv.db) var mı — varsa kurulum daha önce çalışmış demektir. */
    private static boolean hasExistingDatabase(String dataDir) {
        Path dataSubdir = Path.of(dataDir, "data");
        if (!Files.isDirectory(dataSubdir)) {
            return false;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dataSubdir, "*.mv.db")) {
            return stream.iterator().hasNext();
        } catch (IOException e) {
            return false;
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

    /**
     * Dosyayı sahibe özel izinle ATOMİK olarak oluşturur (izin, oluşturma anında geçerli olur —
     * "önce herkese açık yaz, sonra kısıtla" penceresi yok).
     */
    private static void writeOwnerOnly(Path file, String contents) throws IOException {
        Files.createDirectories(file.getParent());
        byte[] bytes = contents.getBytes(StandardCharsets.UTF_8);
        Set<OpenOption> options = Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        try {
            Set<PosixFilePermission> perms = EnumSet.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
            FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions.asFileAttribute(perms);
            try (var channel = Files.newByteChannel(file, options, attr)) {
                channel.write(ByteBuffer.wrap(bytes));
            }
        } catch (UnsupportedOperationException windows) {
            // Windows: POSIX izin özniteliği yok. Veri dizini ACL sıkılaştırması paketleme adımıdır (I2).
            try (var channel = Files.newByteChannel(file, options)) {
                channel.write(ByteBuffer.wrap(bytes));
            }
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
