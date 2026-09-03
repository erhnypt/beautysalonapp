package com.beautysalonapp.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * {@code beautysalonapp.*} altındaki uygulama ayarları.
 * Kod içinde sabit yazılmış yapılandırma değerlerinden kaçınmak için tek giriş noktası.
 */
@ConfigurationProperties(prefix = "beautysalonapp")
public class AppProperties {

    /** Kurulum benzersiz kimliği (parmak izi bileşeni); ilk açılışta üretilir. */
    private String installId;

    /** Yerel veri kök dizini (DB, config, backups, logs, attachments). */
    private String dataDir = "./run-data";

    /**
     * İlk açılışta kurulum kimliğini {@code <dataDir>/config/install-id} dosyasından okur
     * veya üretir. Diğer bean'ler (MonotonicClock, FingerprintService) oluşmadan çalışır.
     */
    @PostConstruct
    void ensureInstallId() {
        if (installId != null && !installId.isBlank()) {
            return;
        }
        try {
            Path dir = Path.of(dataDir, "config");
            Files.createDirectories(dir);
            Path file = dir.resolve("install-id");
            if (Files.exists(file)) {
                installId = Files.readString(file, StandardCharsets.UTF_8).trim();
            }
            if (installId == null || installId.isBlank()) {
                installId = UUID.randomUUID().toString();
                Files.writeString(file, installId, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            // Dosya sistemi yazılamıyorsa oturum boyunca geçici kimlik
            installId = "transient-" + UUID.randomUUID();
        }
    }

    /** LAN erişimi açık mı? Varsayılan kapalı — yalnızca 127.0.0.1. */
    private boolean lanAccessEnabled = false;

    /** Tam çevrimdışı mod: SMS/e-posta kapalı, lisans dosya tabanlı. */
    private boolean fullOfflineMode = false;

    /** Sunum zaman dilimi (DB her zaman UTC). */
    private String displayZone = "Europe/Istanbul";

    /** Varsayılan para birimi. */
    private String defaultCurrency = "TRY";

    @NestedConfigurationProperty
    private Licensing licensing = new Licensing();

    @NestedConfigurationProperty
    private Outbound outbound = new Outbound();

    @NestedConfigurationProperty
    private Security security = new Security();

    public static class Licensing {
        /** Lisans dosyasının yolu. */
        private String licenseFile = "${beautysalonapp.data-dir:./run-data}/config/license.lic";
        /** Uygulamaya gömülü Ed25519 public key (Base64, ham 32 bayt). Boşsa imza doğrulaması testte gevşetilir. */
        private String publicKeyBase64 = "";
        /** Heartbeat açık mı? Tam çevrimdışı modda kapalı. */
        private boolean heartbeatEnabled = true;
        private String heartbeatEndpoint = "https://license.beautysalonapp.com/api/v1/heartbeat";
        /** READ_ONLY durumundan LOCKED durumuna geçmeden önceki gün sayısı. */
        private int readOnlyDays = 30;

        public String getLicenseFile() { return licenseFile; }
        public void setLicenseFile(String v) { this.licenseFile = v; }
        public String getPublicKeyBase64() { return publicKeyBase64; }
        public void setPublicKeyBase64(String v) { this.publicKeyBase64 = v; }
        public boolean isHeartbeatEnabled() { return heartbeatEnabled; }
        public void setHeartbeatEnabled(boolean v) { this.heartbeatEnabled = v; }
        public String getHeartbeatEndpoint() { return heartbeatEndpoint; }
        public void setHeartbeatEndpoint(String v) { this.heartbeatEndpoint = v; }
        public int getReadOnlyDays() { return readOnlyDays; }
        public void setReadOnlyDays(int v) { this.readOnlyDays = v; }
    }

    public static class Outbound {
        /** İzin verilen giden HTTP hedefleri (şema + host öneki). Bunun dışına istek RED. */
        private List<String> allowlist = List.of(
                "https://license.beautysalonapp.com"
        );
        public List<String> getAllowlist() { return allowlist; }
        public void setAllowlist(List<String> v) { this.allowlist = v; }
    }

    public static class Security {
        /** İlk açılışta oluşturulacak yönetici kullanıcı adı. */
        private String bootstrapAdminUsername = "admin";
        /** İlk açılışta oluşturulacak yönetici parolası (ilk girişte değiştirilmeli). */
        private String bootstrapAdminPassword = "admin123";
        public String getBootstrapAdminUsername() { return bootstrapAdminUsername; }
        public void setBootstrapAdminUsername(String v) { this.bootstrapAdminUsername = v; }
        public String getBootstrapAdminPassword() { return bootstrapAdminPassword; }
        public void setBootstrapAdminPassword(String v) { this.bootstrapAdminPassword = v; }
    }

    public String getInstallId() { return installId; }
    public void setInstallId(String installId) { this.installId = installId; }
    public String getDataDir() { return dataDir; }
    public void setDataDir(String dataDir) { this.dataDir = dataDir; }
    public boolean isLanAccessEnabled() { return lanAccessEnabled; }
    public void setLanAccessEnabled(boolean lanAccessEnabled) { this.lanAccessEnabled = lanAccessEnabled; }
    public boolean isFullOfflineMode() { return fullOfflineMode; }
    public void setFullOfflineMode(boolean fullOfflineMode) { this.fullOfflineMode = fullOfflineMode; }
    public String getDisplayZone() { return displayZone; }
    public void setDisplayZone(String displayZone) { this.displayZone = displayZone; }
    public String getDefaultCurrency() { return defaultCurrency; }
    public void setDefaultCurrency(String defaultCurrency) { this.defaultCurrency = defaultCurrency; }
    public Licensing getLicensing() { return licensing; }
    public void setLicensing(Licensing licensing) { this.licensing = licensing; }
    public Outbound getOutbound() { return outbound; }
    public void setOutbound(Outbound outbound) { this.outbound = outbound; }
    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }
}
