package com.beautysalonapp.license.crypto;

import jakarta.annotation.PostConstruct;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Ed25519 anahtar çifti sağlayıcı (§6.8).
 *
 * <p>Private key diskte <b>düz tutulmaz</b>: PBKDF2 + AES-256-GCM ile paroladan türetilen
 * anahtarla şifreli bir dosyada saklanır. Parola {@code beautysalonapp.license.key-password}
 * (ortam değişkeni / vault) ile verilir. Üretimde KMS/HSM tercih edilmelidir.
 *
 * <p>Dosya yoksa ve {@code beautysalonapp.license.allow-generate=true} ise (yalnızca dev)
 * yeni bir çift üretilip kaydedilir.
 */
@Component
public class Ed25519KeyProvider {

    private static final Logger log = LoggerFactory.getLogger(Ed25519KeyProvider.class);
    private static final byte[] MAGIC = "BSAKEY1".getBytes();
    private static final int SALT_LEN = 16;
    private static final int IV_LEN = 12;
    private static final int PBKDF2_ITERS = 210_000;

    @Value("${beautysalonapp.license.key-file:./data/ed25519.key}")
    private String keyFile;

    @Value("${beautysalonapp.license.key-password:}")
    private String keyPassword;

    @Value("${beautysalonapp.license.allow-generate:false}")
    private boolean allowGenerate;

    private Ed25519PrivateKeyParameters privateKey;
    private Ed25519PublicKeyParameters publicKey;

    @PostConstruct
    void init() throws Exception {
        if (keyPassword == null || keyPassword.isBlank()) {
            if (!allowGenerate) {
                throw new IllegalStateException(
                        "beautysalonapp.license.key-password ayarlı değil ve allow-generate kapalı");
            }
            keyPassword = "dev-insecure-password";
            log.warn("KEY PASSWORD AYARLI DEĞİL — geçici dev parolası kullanılıyor. ÜRETİMDE KULLANMAYIN.");
        }
        Path path = Path.of(keyFile);
        if (Files.exists(path)) {
            byte[] seed = decryptSeed(Files.readAllBytes(path), keyPassword.toCharArray());
            this.privateKey = new Ed25519PrivateKeyParameters(seed, 0);
            log.info("Ed25519 private key yüklendi: {}", path);
        } else if (allowGenerate) {
            byte[] seed = new byte[Ed25519PrivateKeyParameters.KEY_SIZE];
            new SecureRandom().nextBytes(seed);
            Files.createDirectories(path.toAbsolutePath().getParent());
            Files.write(path, encryptSeed(seed, keyPassword.toCharArray()));
            this.privateKey = new Ed25519PrivateKeyParameters(seed, 0);
            log.warn("YENİ Ed25519 anahtar çifti üretildi ve {} dosyasına yazıldı (dev). "
                    + "Public key: {}", path, publicKeyBase64Lazy());
        } else {
            throw new IllegalStateException("Anahtar dosyası yok: " + path
                    + " (allow-generate kapalı). Anahtarı sağlayın.");
        }
        this.publicKey = privateKey.generatePublicKey();
        log.info("Lisans public key (istemciye gömülecek): {}", publicKeyBase64());
    }

    /** İstemciye gömülecek public key — Base64 (ham 32 bayt). */
    public String publicKeyBase64() {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    /** Verilen baytları Ed25519 ile imzalar. */
    public byte[] sign(byte[] data) {
        Ed25519Signer signer = new Ed25519Signer();
        signer.init(true, privateKey);
        signer.update(data, 0, data.length);
        return signer.generateSignature();
    }

    private String publicKeyBase64Lazy() {
        return Base64.getEncoder().encodeToString(privateKey.generatePublicKey().getEncoded());
    }

    // --- şifreli seed kabı ---------------------------------------

    private static byte[] encryptSeed(byte[] seed, char[] password) throws Exception {
        SecureRandom rnd = new SecureRandom();
        byte[] salt = new byte[SALT_LEN];
        byte[] iv = new byte[IV_LEN];
        rnd.nextBytes(salt);
        rnd.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), new GCMParameterSpec(128, iv));
        byte[] ct = cipher.doFinal(seed);
        byte[] out = new byte[MAGIC.length + SALT_LEN + IV_LEN + ct.length];
        System.arraycopy(MAGIC, 0, out, 0, MAGIC.length);
        System.arraycopy(salt, 0, out, MAGIC.length, SALT_LEN);
        System.arraycopy(iv, 0, out, MAGIC.length + SALT_LEN, IV_LEN);
        System.arraycopy(ct, 0, out, MAGIC.length + SALT_LEN + IV_LEN, ct.length);
        return out;
    }

    private static byte[] decryptSeed(byte[] container, char[] password) throws Exception {
        if (container.length < MAGIC.length + SALT_LEN + IV_LEN + 16
                || !Arrays.equals(Arrays.copyOf(container, MAGIC.length), MAGIC)) {
            throw new IllegalStateException("Geçersiz anahtar dosyası biçimi");
        }
        int p = MAGIC.length;
        byte[] salt = Arrays.copyOfRange(container, p, p + SALT_LEN);
        p += SALT_LEN;
        byte[] iv = Arrays.copyOfRange(container, p, p + IV_LEN);
        p += IV_LEN;
        byte[] ct = Arrays.copyOfRange(container, p, container.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), new GCMParameterSpec(128, iv));
        return cipher.doFinal(ct);
    }

    private static SecretKeySpec deriveKey(char[] password, byte[] salt) throws Exception {
        SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        return new SecretKeySpec(
                f.generateSecret(new PBEKeySpec(password, salt, PBKDF2_ITERS, 256)).getEncoded(), "AES");
    }
}
