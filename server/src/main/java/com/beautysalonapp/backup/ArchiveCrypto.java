package com.beautysalonapp.backup;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Yedek arşivi şifrelemesi (§8.2). PBKDF2-HMAC-SHA256 ile paroladan anahtar türetip
 * arşivi AES-256-GCM ile şifreler.
 *
 * <p>Kap biçimi: {@code "BSABKP1" (7 bayt) || salt(16) || iv(12) || ciphertext+tag}.
 * Bu, 7-Zip ile açılan standart bir şifreli ZIP değil, uygulamaya özgü bir kaptır;
 * geri yükleme yalnızca uygulama sihirbazından yapılır. (zip4j ile standart AES-ZIP
 * ileride eklenebilir — bkz. docs/adr/0004.)
 */
public final class ArchiveCrypto {

    private static final byte[] MAGIC = "BSABKP1".getBytes(StandardCharsets.US_ASCII);
    private static final int SALT_LEN = 16;
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;
    private static final int PBKDF2_ITERS = 210_000;
    private static final int KEY_BITS = 256;

    private ArchiveCrypto() {
    }

    public static byte[] encrypt(byte[] plaintext, char[] password) {
        try {
            SecureRandom rnd = new SecureRandom();
            byte[] salt = new byte[SALT_LEN];
            byte[] iv = new byte[IV_LEN];
            rnd.nextBytes(salt);
            rnd.nextBytes(iv);

            SecretKeySpec key = deriveKey(password, salt);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext);

            ByteArrayOutputStream out = new ByteArrayOutputStream(MAGIC.length + SALT_LEN + IV_LEN + ct.length);
            out.write(MAGIC);
            out.write(salt);
            out.write(iv);
            out.write(ct);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Yedek şifrelenemedi", e);
        }
    }

    public static byte[] decrypt(byte[] container, char[] password) {
        try {
            if (container.length < MAGIC.length + SALT_LEN + IV_LEN + 16
                    || !Arrays.equals(Arrays.copyOf(container, MAGIC.length), MAGIC)) {
                throw new IllegalArgumentException("Geçersiz yedek dosyası biçimi");
            }
            int p = MAGIC.length;
            byte[] salt = Arrays.copyOfRange(container, p, p + SALT_LEN);
            p += SALT_LEN;
            byte[] iv = Arrays.copyOfRange(container, p, p + IV_LEN);
            p += IV_LEN;
            byte[] ct = Arrays.copyOfRange(container, p, container.length);

            SecretKeySpec key = deriveKey(password, salt);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return cipher.doFinal(ct);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Yedek çözülemedi — parola yanlış veya dosya bozuk", e);
        }
    }

    private static SecretKeySpec deriveKey(char[] password, byte[] salt) throws Exception {
        SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        PBEKeySpec spec = new PBEKeySpec(password, salt, PBKDF2_ITERS, KEY_BITS);
        return new SecretKeySpec(f.generateSecret(spec).getEncoded(), "AES");
    }
}
