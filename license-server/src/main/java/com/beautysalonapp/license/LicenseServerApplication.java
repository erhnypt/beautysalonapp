package com.beautysalonapp.license;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * BeautySalonApp Lisans Sunucusu (ayrı deploy — kendi VPS'inizde).
 *
 * <p>Sorumluluk: müşteri/abonelik yönetimi, imzalı lisans dosyası üretimi (Ed25519),
 * günlük heartbeat ile yenileme, askıya alma/iptal, makine transferi, güncelleme kanalı.
 * Hiçbir işletme iş verisi tutmaz — yalnızca lisans metadatası (bkz. teknik plan §6).
 */
@SpringBootApplication
@EnableScheduling
public class LicenseServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LicenseServerApplication.class, args);
    }
}
