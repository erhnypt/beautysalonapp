package com.beautysalonapp.core.crypto;

import com.beautysalonapp.config.AppProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Hassas alan (PII) şifrelemesi (§8.2). AES-256-GCM; çıktı biçimi:
 * {@code enc:v1:<base64(iv||ciphertext||tag)>}.
 *
 * <p>Anahtar çözümü {@link CryptoKeyProvider}'a devredilir: açık yapılandırma
 * ({@code beautysalonapp.crypto.key-base64}) → {@code <dataDir>/config/field-crypto.key}
 * (yoksa rastgele üretilir) → son çare kurulum kimliğinden türetim.
 */
@Component
public class FieldCrypto {

    private static final String PREFIX = "enc:v1:";
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public FieldCrypto(AppProperties props) {
        this.key = CryptoKeyProvider.resolveKey(props);
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        if (plaintext.startsWith(PREFIX)) {
            return plaintext; // zaten şifreli
        }
        try {
            byte[] iv = new byte[IV_LEN];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("Alan şifrelemesi başarısız", e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null) {
            return null;
        }
        if (!stored.startsWith(PREFIX)) {
            return stored; // eski/şifresiz veri — olduğu gibi döndür
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] iv = Arrays.copyOfRange(all, 0, IV_LEN);
            byte[] ct = Arrays.copyOfRange(all, IV_LEN, all.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Alan çözme başarısız (anahtar değişmiş olabilir)", e);
        }
    }

    /** Aramada kullanmak için belirleyici arama anahtarı (HMAC benzeri hash). PII değil. */
    public String blindIndex(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(key.getEncoded());
            md.update((byte) '|');
            byte[] digest = md.digest(plaintext.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(Arrays.copyOf(digest, 16));
        } catch (Exception e) {
            return null;
        }
    }
}
