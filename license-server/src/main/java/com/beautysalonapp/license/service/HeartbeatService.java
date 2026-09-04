package com.beautysalonapp.license.service;

import com.beautysalonapp.license.crypto.LicenseSigner;
import com.beautysalonapp.license.domain.Enums.LicenseStatus;
import com.beautysalonapp.license.domain.Enums.SubscriptionStatus;
import com.beautysalonapp.license.domain.HeartbeatLog;
import com.beautysalonapp.license.domain.License;
import com.beautysalonapp.license.domain.LicenseBinding;
import com.beautysalonapp.license.domain.Subscription;
import com.beautysalonapp.license.repo.AppReleaseRepository;
import com.beautysalonapp.license.repo.HeartbeatLogRepository;
import com.beautysalonapp.license.repo.LicenseBindingRepository;
import com.beautysalonapp.license.repo.LicenseRepository;
import com.beautysalonapp.license.repo.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Heartbeat protokolü (§6.5). İstek minimum veri taşır; yanıt imzalı lisans + durum döner.
 */
@Service
@Transactional
public class HeartbeatService {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatService.class);

    private final LicenseRepository licenses;
    private final SubscriptionRepository subscriptions;
    private final LicenseBindingRepository bindings;
    private final HeartbeatLogRepository hbLogs;
    private final AppReleaseRepository releases;
    private final LicenseService licenseService;
    private final LicenseSigner signer;

    public HeartbeatService(LicenseRepository licenses, SubscriptionRepository subscriptions,
                            LicenseBindingRepository bindings, HeartbeatLogRepository hbLogs,
                            AppReleaseRepository releases, LicenseService licenseService, LicenseSigner signer) {
        this.licenses = licenses;
        this.subscriptions = subscriptions;
        this.bindings = bindings;
        this.hbLogs = hbLogs;
        this.releases = releases;
        this.licenseService = licenseService;
        this.signer = signer;
    }

    public record HeartbeatRequest(String licenseId, String fingerprint, String appVersion,
                                   String os, String lastHeartbeatAt, String nonce) {}

    public record HeartbeatResponse(String status, String license, String serverTime, String message,
                                    String latestVersion, boolean mandatoryUpdate, String nonce,
                                    String responseSignature) {}

    public HeartbeatResponse process(HeartbeatRequest req, String remoteIp) {
        License lic = licenses.findByLicenseId(req.licenseId())
                .orElseThrow(() -> new IllegalArgumentException("Lisans bulunamadı: " + req.licenseId()));

        HeartbeatLog hbLog = hbLogs.save(new HeartbeatLog(req.licenseId(), req.fingerprint(),
                req.appVersion(), req.os(), req.nonce(), remoteIp));

        String message = touchBinding(lic, req.fingerprint());

        Subscription sub = subscriptions.findById(lic.getSubscriptionId()).orElse(null);
        String status = resolveStatus(lic, sub);

        if ("ACTIVE".equals(status)) {
            licenseService.renewWindow(lic);
        } else if ("SUSPENDED".equals(status) && message == null) {
            message = "Aboneliğiniz askıya alınmış. Lütfen ödemenizi yapın; yazılım kademeli olarak kısıtlanacaktır.";
        } else if ("REVOKED".equals(status) && message == null) {
            message = "Lisansınız iptal edilmiş. Verilerinizi dışa aktarabilirsiniz.";
        }

        String licenseFile = licenseService.signCurrent(lic);
        String serverTime = Instant.now().toString();
        var latest = releases.findFirstByChannelOrderByReleasedAtDesc("stable").orElse(null);

        hbLog.setResponseStatus(status);

        String sigBase = status + "|" + (req.nonce() == null ? "" : req.nonce()) + "|" + serverTime;
        return new HeartbeatResponse(status, licenseFile, serverTime, message,
                latest == null ? null : latest.getVersion(),
                latest != null && latest.isMandatory(),
                req.nonce(),
                signer.signBase64(sigBase));
    }

    /** Binding'i günceller; terminal sınırı aşıldıysa uyarı mesajı döndürür. */
    private String touchBinding(License lic, String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) {
            return null;
        }
        LicenseBinding b = bindings.findByLicenseIdAndFingerprint(lic.getLicenseId(), fingerprint).orElse(null);
        if (b != null) {
            b.setLastSeenAt(Instant.now());
            if (!b.isActive()) {
                b.setActive(true);
            }
            return null;
        }
        long active = bindings.countByLicenseIdAndActiveTrue(lic.getLicenseId());
        if (active >= lic.getMaxTerminals()) {
            LicenseBinding over = new LicenseBinding(lic.getLicenseId(), fingerprint, 2);
            over.setActive(false);
            bindings.save(over);
            log.warn("Terminal sınırı aşımı: lisans {} ({} aktif, +1 talep)", lic.getLicenseId(), active);
            return "Terminal sınırınız (" + lic.getMaxTerminals() + ") aşıldı. Ek terminal için planınızı yükseltin.";
        }
        bindings.save(new LicenseBinding(lic.getLicenseId(), fingerprint, 2));
        return null;
    }

    private String resolveStatus(License lic, Subscription sub) {
        if (lic.getStatus() == LicenseStatus.REVOKED
                || (sub != null && sub.getStatus() == SubscriptionStatus.CANCELLED)) {
            return "REVOKED";
        }
        if (sub == null) {
            return "SUSPENDED";
        }
        if (sub.getStatus() == SubscriptionStatus.ACTIVE && sub.isBillingCurrent()) {
            return "ACTIVE";
        }
        return "SUSPENDED";
    }
}
