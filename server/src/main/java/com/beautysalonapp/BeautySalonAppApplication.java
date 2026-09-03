package com.beautysalonapp;

import com.beautysalonapp.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * BeautySalonApp yerel sunucu giriş noktası.
 *
 * <p>İşletme bilgisayarında bir arka plan servisi olarak çalışır; {@code 127.0.0.1:8734}
 * üzerinde REST API ve derlenmiş React arayüzünü sunar. Hiçbir iş verisi dışarı çıkmaz;
 * giden trafik {@link com.beautysalonapp.outbound.OutboundHttpGuard} ile sınırlandırılır.
 */
@SpringBootApplication
@EnableScheduling
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
@EnableConfigurationProperties(AppProperties.class)
public class BeautySalonAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(BeautySalonAppApplication.class, args);
    }
}
