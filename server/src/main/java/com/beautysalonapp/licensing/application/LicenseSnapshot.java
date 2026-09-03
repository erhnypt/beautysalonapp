package com.beautysalonapp.licensing.application;

import com.beautysalonapp.licensing.domain.LicensePayload;
import com.beautysalonapp.licensing.domain.LicenseStatus;
import com.beautysalonapp.licensing.domain.ModuleCode;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Lisans durumunun anlık, salt-okunur görüntüsü. Filtre ve arayüz bunu kullanır.
 */
public record LicenseSnapshot(
        LicenseStatus status,
        boolean devMode,
        Set<ModuleCode> enabledModules,
        Instant notAfter,
        Integer daysRemaining,
        Instant lastSuccessfulHeartbeatAt,
        int consecutiveHeartbeatFailures,
        String customerName,
        String plan,
        String message
) {
    public boolean isModuleEnabled(ModuleCode code) {
        return devMode || enabledModules.contains(code);
    }

    public boolean writesBlocked() {
        return status.isWritesBlocked();
    }

    static LicenseSnapshot devFallback() {
        return new LicenseSnapshot(
                LicenseStatus.ACTIVE, true,
                Set.of(ModuleCode.values()),
                null, null, null, 0,
                "GELİŞTİRME MODU", "DEV",
                "Lisans dosyası ve gömülü public key yok — geliştirme modunda tüm modüller açık.");
    }

    static LicenseSnapshot noLicense() {
        return new LicenseSnapshot(
                LicenseStatus.LOCKED, false,
                Set.of(),
                null, null, null, 0,
                null, null,
                "Lisans bulunamadı. Ayarlar → Lisans ekranından .lic dosyanızı yükleyin.");
    }

    static LicenseSnapshot of(LicenseStatus status, LicensePayload p, Instant notAfter, Integer daysRemaining,
                              Instant lastHb, int hbFailures, String message) {
        return new LicenseSnapshot(status, false,
                p.modules() == null ? Set.of() : Set.copyOf(p.modules()),
                notAfter, daysRemaining, lastHb, hbFailures,
                p.customerName(), p.plan() == null ? null : p.plan().name(), message);
    }

    public List<String> enabledModuleNames() {
        return enabledModules.stream().map(Enum::name).sorted().toList();
    }
}
