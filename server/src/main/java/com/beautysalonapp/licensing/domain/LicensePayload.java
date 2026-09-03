package com.beautysalonapp.licensing.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * {@code license.lic} içindeki imzalı JSON gövdesi (§6.2). Alanlar lisans
 * sunucusu tarafından üretilir; istemci yalnızca doğrular ve okur.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LicensePayload(
        int v,
        String licenseId,
        String customerName,
        String taxId,
        LicensePlan plan,
        Instant issuedAt,
        Instant notBefore,
        Instant notAfter,
        int graceDays,
        List<ModuleCode> modules,
        Limits limits,
        List<MachineBinding> machineBinding,
        Heartbeat heartbeat,
        boolean offlineMode
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Limits(
            Integer maxTerminals,
            Integer maxBranches,
            Integer maxActiveUsers,
            Integer maxCustomers
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MachineBinding(
            int fpVersion,
            String hash,
            Instant boundAt
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Heartbeat(
            boolean required,
            int intervalHours,
            String endpoint
    ) {}

    public int effectiveGraceDays() {
        // Çevrimdışı modda dosya gecikmesi için daha uzun tolerans (§6.6)
        return offlineMode ? Math.max(graceDays, 14) : Math.max(graceDays, 0);
    }

    public boolean allowsModule(ModuleCode code) {
        return modules != null && modules.contains(code);
    }

    public Map<String, Object> toPublicSummary() {
        return Map.of(
                "licenseId", licenseId == null ? "" : licenseId,
                "customerName", customerName == null ? "" : customerName,
                "plan", plan == null ? "" : plan.name(),
                "notAfter", notAfter == null ? "" : notAfter.toString(),
                "graceDays", effectiveGraceDays(),
                "offlineMode", offlineMode,
                "modules", modules == null ? List.of() : modules
        );
    }
}
