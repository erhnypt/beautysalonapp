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

    /** H2 otomatik sunucusunun (AUTO_SERVER=TRUE) dinleyeceği adres için sistem özelliği. */
    static final String H2_BIND_ADDRESS_PROPERTY = "h2.bindAddress";

    public static void main(String[] args) {
        hardenH2BindAddress();
        SpringApplication.run(BeautySalonAppApplication.class, args);
    }

    /**
     * JDBC URL'i {@code AUTO_SERVER=TRUE} kullanıyor: H2, aynı veritabanı dosyasına ikinci
     * bir işlemin (ör. yedekleme aracı, ikinci uygulama örneği) bağlanabilmesi için bir TCP
     * dinleyici açar. Bu dinleyici varsayılan olarak makinenin çözümlenebilir adresine
     * bağlanır; yani {@code server.address: 127.0.0.1} ve {@code lan-access-enabled=false}
     * ayarlarına <b>rağmen</b> ağdan erişilebilir. Parolasız {@code sa} kullanıcısıyla bu,
     * tüm uygulama/lisans/yetki katmanını atlayan bir veri sızma yoludur (§8.1).
     *
     * <p>H2 sunucu soketini yalnızca loopback'e bağlamak için {@code h2.bindAddress} sistem
     * özelliğini H2 sınıfları yüklenmeden önce (yani Spring bağlamı ayağa kalkmadan) ayarlıyoruz.
     * Aynı makinedeki ikinci işlem hâlâ bağlanabilir; yalnızca makine dışı TCP erişimi kapanır.
     * Operatör gerçekten gerekiyorsa {@code -Dh2.bindAddress=...} ile açıkça ezebilir.
     */
    static void hardenH2BindAddress() {
        if (System.getProperty(H2_BIND_ADDRESS_PROPERTY) == null) {
            System.setProperty(H2_BIND_ADDRESS_PROPERTY, "127.0.0.1");
        }
    }
}
