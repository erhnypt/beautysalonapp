package com.beautysalonapp.licensing;

import com.beautysalonapp.licensing.application.LicenseVerifier;
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator;
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LicenseVerifierTest {

    private record Keys(String publicB64, Ed25519PrivateKeyParameters priv) {}

    private Keys generate() {
        var gen = new Ed25519KeyPairGenerator();
        gen.init(new Ed25519KeyGenerationParameters(new SecureRandom()));
        var pair = gen.generateKeyPair();
        var pub = (Ed25519PublicKeyParameters) pair.getPublic();
        var priv = (Ed25519PrivateKeyParameters) pair.getPrivate();
        return new Keys(Base64.getEncoder().encodeToString(pub.getEncoded()), priv);
    }

    private String sign(String payloadJson, Ed25519PrivateKeyParameters priv) {
        byte[] payload = payloadJson.getBytes(StandardCharsets.UTF_8);
        var signer = new Ed25519Signer();
        signer.init(true, priv);
        signer.update(payload, 0, payload.length);
        byte[] sig = signer.generateSignature();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload)
                + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
    }

    @Test
    void gecerli_imza_dogrulanir_ve_govde_donulur() {
        Keys k = generate();
        String json = "{\"v\":1,\"licenseId\":\"LIC-1\",\"plan\":\"PRO\"}";
        String licFile = sign(json, k.priv());

        LicenseVerifier verifier = new LicenseVerifier(k.publicB64());
        assertThat(verifier.verifyAndExtractPayload(licFile)).isEqualTo(json);
    }

    @Test
    void bozuk_imza_reddedilir() {
        Keys k = generate();
        Keys other = generate();
        String json = "{\"v\":1}";
        String licFile = sign(json, other.priv());

        LicenseVerifier verifier = new LicenseVerifier(k.publicB64());
        assertThatThrownBy(() -> verifier.verifyAndExtractPayload(licFile))
                .isInstanceOf(LicenseVerifier.LicenseSignatureException.class);
    }

    @Test
    void bicimi_bozuk_dosya_reddedilir() {
        LicenseVerifier verifier = new LicenseVerifier(generate().publicB64());
        assertThatThrownBy(() -> verifier.verifyAndExtractPayload("imzasiz-icerik"))
                .isInstanceOf(LicenseVerifier.LicenseFormatException.class);
    }

    @Test
    void public_key_yoksa_imza_kontrolu_devre_disi() {
        LicenseVerifier verifier = new LicenseVerifier("");
        assertThat(verifier.signatureCheckEnabled()).isFalse();
        String json = "{\"v\":1}";
        String licFile = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8)) + ".AA";
        assertThat(verifier.verifyAndExtractPayload(licFile)).isEqualTo(json);
    }
}
