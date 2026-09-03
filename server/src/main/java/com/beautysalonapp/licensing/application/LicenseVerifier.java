package com.beautysalonapp.licensing.application;

import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * {@code license.lic} imza doğrulaması (§6.2).
 *
 * <p>Dosya biçimi: {@code base64url(payloadJson) + "." + base64url(ed25519Signature)}.
 * Public key uygulamaya gömülüdür (ham 32 bayt, Base64). Private key yalnızca lisans
 * sunucusundadır.
 */
public class LicenseVerifier {

    private final byte[] publicKey; // ham 32 bayt; null → doğrulama devre dışı (yalnızca DEV)

    public LicenseVerifier(String publicKeyBase64) {
        if (publicKeyBase64 == null || publicKeyBase64.isBlank()) {
            this.publicKey = null;
        } else {
            byte[] raw = decode(publicKeyBase64.trim());
            if (raw.length != Ed25519PublicKeyParameters.KEY_SIZE) {
                throw new IllegalArgumentException("Ed25519 public key 32 bayt olmalı, gelen: " + raw.length);
            }
            this.publicKey = raw;
        }
    }

    public boolean signatureCheckEnabled() {
        return publicKey != null;
    }

    /**
     * Dosya içeriğini doğrular ve gövde JSON'unu döndürür.
     *
     * @throws LicenseFormatException biçim bozuksa
     * @throws LicenseSignatureException imza geçersizse
     */
    public String verifyAndExtractPayload(String licenseFileContent) {
        if (licenseFileContent == null) {
            throw new LicenseFormatException("Lisans dosyası boş");
        }
        String trimmed = licenseFileContent.strip();
        int dot = trimmed.indexOf('.');
        if (dot <= 0 || dot == trimmed.length() - 1) {
            throw new LicenseFormatException("Lisans dosyası biçimi geçersiz (payload.signature bekleniyor)");
        }
        String payloadB64 = trimmed.substring(0, dot);
        String signatureB64 = trimmed.substring(dot + 1);

        byte[] payloadBytes;
        byte[] signatureBytes;
        try {
            payloadBytes = decode(payloadB64);
            signatureBytes = decode(signatureB64);
        } catch (IllegalArgumentException e) {
            throw new LicenseFormatException("Base64 çözümlemesi başarısız: " + e.getMessage());
        }

        if (publicKey != null) {
            Ed25519Signer verifier = new Ed25519Signer();
            verifier.init(false, new Ed25519PublicKeyParameters(publicKey, 0));
            verifier.update(payloadBytes, 0, payloadBytes.length);
            if (!verifier.verifySignature(signatureBytes)) {
                throw new LicenseSignatureException("Lisans imzası doğrulanamadı");
            }
        }
        return new String(payloadBytes, StandardCharsets.UTF_8);
    }

    private static byte[] decode(String s) {
        // Hem standart hem url-safe Base64 kabul et
        String normalized = s.replace('-', '+').replace('_', '/');
        return Base64.getDecoder().decode(normalized);
    }

    public static class LicenseFormatException extends RuntimeException {
        public LicenseFormatException(String m) { super(m); }
    }

    public static class LicenseSignatureException extends RuntimeException {
        public LicenseSignatureException(String m) { super(m); }
    }
}
