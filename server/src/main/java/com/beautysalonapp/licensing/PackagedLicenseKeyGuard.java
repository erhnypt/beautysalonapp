package com.beautysalonapp.licensing;

import com.beautysalonapp.config.AppProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Paketlenmiş üründe ({@code packaged} profili) lisans imza doğrulaması KAPALI olamaz.
 *
 * <p>{@link com.beautysalonapp.licensing.application.LicenseVerifier}, gömülü Ed25519
 * public key boş/geçersizse imza doğrulamasını atlar ve {@code LicenseService} "tüm
 * modüller açık" geliştirme moduna düşer. Bu, geliştirmede kasıtlıdır; ancak paketlenmiş
 * bir kurulumda güvenlik açığıdır (güvenlik gözden geçirme §7 G5).
 *
 * <p>Bu guard, {@code packaged} profilinde public key yoksa veya paketleme token'ı
 * ({@code @LICENSE_PUBLIC_KEY@}) gerçek anahtarla değiştirilmemişse uygulamayı açılışta
 * durdurur. Geçerli (32 bayt) bir anahtar için {@code LicenseVerifier} zaten kendi
 * biçim kontrolünü yapar; buradaki amaç "sessizce geliştirme moduna düşme"yi önlemektir.
 */
@Component
@Profile("packaged")
public class PackagedLicenseKeyGuard {

    static final String UNSUBSTITUTED_TOKEN = "@LICENSE_PUBLIC_KEY@";

    private final AppProperties props;

    public PackagedLicenseKeyGuard(AppProperties props) {
        this.props = props;
    }

    @PostConstruct
    void verifyEmbeddedKeyPresent() {
        String key = props.getLicensing().getPublicKeyBase64();
        if (key == null || key.isBlank() || UNSUBSTITUTED_TOKEN.equals(key.trim())) {
            throw new IllegalStateException(
                    "Paketlenmiş üründe gömülü lisans public key'i yok (packaged profili). "
                            + "Paketleme adımı '@LICENSE_PUBLIC_KEY@' token'ını gerçek Ed25519 anahtarıyla "
                            + "değiştirmelidir; aksi halde lisans denetimi devre dışı kalır. Açılış durduruldu.");
        }
    }
}
