package com.beautysalonapp.backup;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArchiveCryptoTest {

    private final byte[] payload = "BeautySalonApp yedek içeriği — 1234567890 çğıöşü".getBytes(StandardCharsets.UTF_8);

    @Test
    void sifrele_coz_ayni_veri() {
        byte[] c = ArchiveCrypto.encrypt(payload, "parola123".toCharArray());
        assertThat(c).isNotEqualTo(payload);
        assertThat(ArchiveCrypto.decrypt(c, "parola123".toCharArray())).isEqualTo(payload);
    }

    @Test
    void yanlis_parola_basarisiz() {
        byte[] c = ArchiveCrypto.encrypt(payload, "dogru".toCharArray());
        assertThatThrownBy(() -> ArchiveCrypto.decrypt(c, "yanlis".toCharArray()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void bozuk_kap_reddedilir() {
        byte[] c = ArchiveCrypto.encrypt(payload, "p".toCharArray());
        c[c.length - 1] ^= 0x7F;
        assertThatThrownBy(() -> ArchiveCrypto.decrypt(c, "p".toCharArray()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void magic_yoksa_reddedilir() {
        byte[] junk = Arrays.copyOf(payload, 100);
        assertThatThrownBy(() -> ArchiveCrypto.decrypt(junk, "p".toCharArray()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void her_sifrelemede_farkli_kap() {
        assertThat(ArchiveCrypto.encrypt(payload, "p".toCharArray()))
                .isNotEqualTo(ArchiveCrypto.encrypt(payload, "p".toCharArray()));
    }
}
