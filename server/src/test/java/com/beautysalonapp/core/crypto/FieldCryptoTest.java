package com.beautysalonapp.core.crypto;

import com.beautysalonapp.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class FieldCryptoTest {

    private FieldCrypto crypto() {
        AppProperties p = new AppProperties();
        byte[] key = new byte[32];
        for (int i = 0; i < 32; i++) key[i] = (byte) i;
        p.getCrypto().setKeyBase64(Base64.getEncoder().encodeToString(key));
        return new FieldCrypto(p);
    }

    @Test
    void sifrele_coz_turu_ayni_deger() {
        FieldCrypto c = crypto();
        String enc = c.encrypt("05551234567");
        assertThat(enc).startsWith("enc:v1:").isNotEqualTo("05551234567");
        assertThat(c.decrypt(enc)).isEqualTo("05551234567");
    }

    @Test
    void ayni_metin_iki_kez_farkli_ciphertext_uretir() {
        FieldCrypto c = crypto();
        assertThat(c.encrypt("ali@example.com")).isNotEqualTo(c.encrypt("ali@example.com"));
    }

    @Test
    void zaten_sifreli_metin_tekrar_sifrelenmez() {
        FieldCrypto c = crypto();
        String enc = c.encrypt("12345678901");
        assertThat(c.encrypt(enc)).isEqualTo(enc);
    }

    @Test
    void null_guvenli() {
        FieldCrypto c = crypto();
        assertThat(c.encrypt(null)).isNull();
        assertThat(c.decrypt(null)).isNull();
    }

    @Test
    void blind_index_belirleyici_ve_normalize() {
        FieldCrypto c = crypto();
        assertThat(c.blindIndex("  0555 000  ")).isEqualTo(c.blindIndex("0555 000"));
        assertThat(c.blindIndex("A@b.com")).isEqualTo(c.blindIndex("a@b.com"));
        assertThat(c.blindIndex("x")).isNotEqualTo(c.blindIndex("y"));
    }

    @Test
    void sifresiz_eski_veri_oldugu_gibi_donulur() {
        FieldCrypto c = crypto();
        assertThat(c.decrypt("düz-metin")).isEqualTo("düz-metin");
    }
}
