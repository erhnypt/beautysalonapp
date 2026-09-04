package com.beautysalonapp.license.crypto;

import com.beautysalonapp.license.domain.Customer;
import com.beautysalonapp.license.domain.License;
import com.beautysalonapp.license.domain.LicenseBinding;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * İmzalı {@code license.lic} içeriği üretir (§6.2).
 *
 * <p>Biçim: {@code base64url(payloadJson) + "." + base64url(ed25519Signature)} — istemcideki
 * {@code LicenseVerifier} ile birebir uyumludur.
 */
@Component
public class LicenseSigner {

    private final Ed25519KeyProvider keys;
    private final ObjectMapper json;
    private final String heartbeatEndpoint;

    public LicenseSigner(Ed25519KeyProvider keys,
                         @Value("${beautysalonapp.license.heartbeat-endpoint:https://license.beautysalonapp.com/api/v1/heartbeat}")
                         String heartbeatEndpoint) {
        this.keys = keys;
        this.heartbeatEndpoint = heartbeatEndpoint;
        this.json = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public String buildAndSign(License lic, Customer customer, List<LicenseBinding> activeBindings) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("v", 1);
        payload.put("licenseId", lic.getLicenseId());
        payload.put("customerName", customer == null ? "" : customer.getName());
        payload.put("taxId", customer == null ? null : customer.getTaxId());
        payload.put("plan", lic.getPlan().name());
        payload.put("issuedAt", lic.getIssuedAt());
        payload.put("notBefore", lic.getNotBefore());
        payload.put("notAfter", lic.getNotAfter());
        payload.put("graceDays", lic.getGraceDays());
        payload.put("modules", lic.moduleList());

        Map<String, Object> limits = new LinkedHashMap<>();
        limits.put("maxTerminals", lic.getMaxTerminals());
        limits.put("maxBranches", lic.getMaxBranches());
        limits.put("maxActiveUsers", lic.getMaxActiveUsers());
        limits.put("maxCustomers", lic.getMaxCustomers());
        payload.put("limits", limits);

        List<Map<String, Object>> mb = new ArrayList<>();
        for (LicenseBinding b : activeBindings) {
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("fpVersion", b.getFpVersion());
            one.put("hash", b.getFingerprint());
            one.put("boundAt", b.getBoundAt());
            mb.add(one);
        }
        payload.put("machineBinding", mb);

        Map<String, Object> hb = new LinkedHashMap<>();
        hb.put("required", !lic.isOfflineMode());
        hb.put("intervalHours", 24);
        hb.put("endpoint", heartbeatEndpoint);
        payload.put("heartbeat", hb);

        payload.put("offlineMode", lic.isOfflineMode());

        byte[] payloadBytes;
        try {
            payloadBytes = json.writeValueAsBytes(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Lisans gövdesi serileştirilemedi", e);
        }
        byte[] signature = keys.sign(payloadBytes);

        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        return enc.encodeToString(payloadBytes) + "." + enc.encodeToString(signature);
    }

    /** Genel amaçlı imza (heartbeat yanıtı vb. için). */
    public String signBase64(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(keys.sign(data));
    }

    public String signBase64(String data) {
        return signBase64(data.getBytes(StandardCharsets.UTF_8));
    }
}
