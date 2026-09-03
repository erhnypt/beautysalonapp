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

    @NestedConfigurationProperty
    private Crypto crypto = new Crypto();

    @NestedConfigurationProperty
    private Backup backup = new Backup();

    public static class Backup {
        /** Yedek klasörü. Kurulum dizininin İÇİNDE olmamalıdır (§11.2). */
        private String dir = "./run-data/backups";
        /** Yedek şifreleme parolası. Kaybolursa yedek açılamaz (§8.2). Boşsa kurulum kimliğinden türetilir. */
        private String password = "";
        private boolean scheduledEnabled = true;
        /** Günlük yedek cron (varsayılan 23:00). */
        private String cron = "0 0 23 * * *";
        /** GFS rotasyonu: son N günlük / N haftalık / N aylık. */
        private int retentionDaily = 7;
        private int retentionWeekly = 4;
        private int retentionMonthly = 12;
        /** İkincil hedef (ağ klasörü / USB) — boş ise yalnızca birincil. */
        private String secondaryDir = "";

        public String getDir() { return dir; }
        public void setDir(String dir) { this.dir = dir; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public boolean isScheduledEnabled() { return scheduledEnabled; }
        public void setScheduledEnabled(boolean v) { this.scheduledEnabled = v; }
        public String getCron() { return cron; }
        public void setCron(String cron) { this.cron = cron; }
        public int getRetentionDaily() { return retentionDaily; }
        public void setRetentionDaily(int v) { this.retentionDaily = v; }
        public int getRetentionWeekly() { return retentionWeekly; }
        public void setRetentionWeekly(int v) { this.retentionWeekly = v; }
        public int getRetentionMonthly() { return retentionMonthly; }
        public void setRetentionMonthly(int v) { this.retentionMonthly = v; }
        public String getSecondaryDir() { return secondaryDir; }
        public void setSecondaryDir(String v) { this.secondaryDir = v; }
    }

    public Backup getBackup() { return backup; }
    public void setBackup(Backup backup) { this.backup = backup; }

    public static class Crypto {
        /**
         * Alan şifreleme anahtarı (Base64, 32 bayt) — AES-256-GCM.
         * Üretimde OS keystore'dan (Windows DPAPI / macOS Keychain) enjekte edilir (§8.2).
         * Boşsa kurulum kimliğinden türetilir (yalnızca geliştirme).
         */
        private String keyBase64 = "";
        public String getKeyBase64() { return keyBase64; }
        public void setKeyBase64(String v) { this.keyBase64 = v; }
    }

    public Crypto getCrypto() { return crypto; }
    public void setCrypto(Crypto crypto) { this.crypto = crypto; }

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
