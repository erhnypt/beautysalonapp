package com.beautysalonapp.licensing;

import com.beautysalonapp.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code packaged} profilinde gömülü lisans public key'i yoksa açılış durmalı — aksi
 * halde uygulama sessizce "tüm modüller açık" geliştirme moduna düşer (§7 G5).
 */
class PackagedLicenseKeyGuardTest {

    private static PackagedLicenseKeyGuard guard(String publicKeyBase64) {
        AppProperties p = new AppProperties();
        p.getLicensing().setPublicKeyBase64(publicKeyBase64);
        return new PackagedLicenseKeyGuard(p);
    }

    @Test
    void bos_key_ile_acilis_durur() {
        assertThatThrownBy(() -> guard("").verifyEmbeddedKeyPresent())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void degistirilmemis_token_ile_acilis_durur() {
        assertThatThrownBy(() -> guard(PackagedLicenseKeyGuard.UNSUBSTITUTED_TOKEN).verifyEmbeddedKeyPresent())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void gercek_key_ile_sorun_yok() {
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        assertThatCode(() -> guard(key).verifyEmbeddedKeyPresent()).doesNotThrowAnyException();
    }
}
