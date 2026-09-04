package com.beautysalonapp.license.service;

import com.beautysalonapp.license.crypto.LicenseSigner;
import com.beautysalonapp.license.domain.Customer;
import com.beautysalonapp.license.domain.Enums.LicenseStatus;
import com.beautysalonapp.license.domain.Enums.Plan;
import com.beautysalonapp.license.domain.Enums.SubscriptionStatus;
import com.beautysalonapp.license.domain.Enums.TransferStatus;
import com.beautysalonapp.license.domain.License;
import com.beautysalonapp.license.domain.LicenseBinding;
import com.beautysalonapp.license.domain.PaymentRecord;
import com.beautysalonapp.license.domain.Subscription;
import com.beautysalonapp.license.domain.TransferRequest;
import com.beautysalonapp.license.repo.CustomerRepository;
import com.beautysalonapp.license.repo.LicenseBindingRepository;
import com.beautysalonapp.license.repo.LicenseRepository;
import com.beautysalonapp.license.repo.PaymentRecordRepository;
import com.beautysalonapp.license.repo.SubscriptionRepository;
import com.beautysalonapp.license.repo.TransferRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@Transactional
public class LicenseService {

    private static final Logger log = LoggerFactory.getLogger(LicenseService.class);
    private static final int RENEWAL_WINDOW_DAYS = 35;
    private static final SecureRandom RND = new SecureRandom();
    private static final String KEY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final CustomerRepository customers;
    private final SubscriptionRepository subscriptions;
    private final LicenseRepository licenses;
    private final LicenseBindingRepository bindings;
    private final TransferRequestRepository transfers;
    private final PaymentRecordRepository payments;
    private final LicenseSigner signer;

    public LicenseService(CustomerRepository customers, SubscriptionRepository subscriptions,
                          LicenseRepository licenses, LicenseBindingRepository bindings,
                          TransferRequestRepository transfers, PaymentRecordRepository payments,
                          LicenseSigner signer) {
        this.customers = customers;
        this.subscriptions = subscriptions;
        this.licenses = licenses;
        this.bindings = bindings;
        this.transfers = transfers;
        this.payments = payments;
        this.signer = signer;
    }

    // --- sağlama (admin) --------------------------------------------

    public record ProvisionCommand(Long customerId, Plan plan, String modulesCsv, int maxTerminals,
                                   int maxBranches, int maxActiveUsers, Integer maxCustomers,
                                   int graceDays, boolean offlineMode, BigDecimal monthlyFee,
                                   int initialMonths) {}

    public License provision(ProvisionCommand c) {
        customers.findById(c.customerId()).orElseThrow(() ->
                new IllegalArgumentException("Müşteri bulunamadı: " + c.customerId()));

        Subscription sub = new Subscription();
        sub.setCustomerId(c.customerId());
        sub.setPlan(c.plan());
        sub.setMonthlyFee(c.monthlyFee() == null ? BigDecimal.ZERO : c.monthlyFee());
        sub.setGraceDays(c.graceDays());
        sub.setStatus(c.initialMonths() > 0 ? SubscriptionStatus.ACTIVE : SubscriptionStatus.PENDING_PAYMENT);
        sub.setPaidThrough(LocalDate.now().plusMonths(Math.max(0, c.initialMonths())));
        subscriptions.save(sub);

        License lic = new License();
        lic.setLicenseId(uniqueLicenseId());
        lic.setActivationKey(newActivationKey());
        lic.setCustomerId(c.customerId());
        lic.setSubscriptionId(sub.getId());
        lic.setPlan(c.plan());
        lic.setModules(c.modulesCsv());
        lic.setMaxTerminals(c.maxTerminals());
        lic.setMaxBranches(c.maxBranches());
        lic.setMaxActiveUsers(c.maxActiveUsers());
        lic.setMaxCustomers(c.maxCustomers());
        lic.setGraceDays(c.graceDays());
        lic.setOfflineMode(c.offlineMode());
        lic.setStatus(LicenseStatus.UNACTIVATED);
        lic.setIssuedAt(Instant.now());
        licenses.save(lic);
        log.info("Lisans sağlandı: {} / key {}", lic.getLicenseId(), lic.getActivationKey());
        return lic;
    }

    // --- aktivasyon (istemci) --------------------------------------

    public String activate(String activationKey, String fingerprint, int fpVersion) {
        License lic = licenses.findByActivationKey(activationKey.trim())
                .orElseThrow(() -> new IllegalArgumentException("Geçersiz aktivasyon anahtarı"));
        if (lic.getStatus() == LicenseStatus.REVOKED) {
            throw new IllegalStateException("Lisans iptal edilmiş");
        }

        LicenseBinding binding = bindings.findByLicenseIdAndFingerprint(lic.getLicenseId(), fingerprint)
                .orElse(null);
        if (binding == null) {
            long active = bindings.countByLicenseIdAndActiveTrue(lic.getLicenseId());
            if (active >= lic.getMaxTerminals()) {
                throw new IllegalStateException("Terminal sınırı doldu (" + lic.getMaxTerminals()
                        + "). Makine transferi talep edin.");
            }
            binding = bindings.save(new LicenseBinding(lic.getLicenseId(), fingerprint, fpVersion));
        } else {
            binding.setActive(true);
            binding.setLastSeenAt(Instant.now());
        }

        lic.setStatus(LicenseStatus.ACTIVE);
        lic.setActivatedAt(Instant.now());
        renewWindow(lic);
        return signCurrent(lic);
    }

    // --- transfer -------------------------------------------------

    public record TransferResult(boolean autoApproved, String licenseFileOrNull, Long pendingRequestId) {}

    public TransferResult requestTransfer(String licenseId, String oldFingerprint, String newFingerprint) {
        License lic = licenses.findByLicenseId(licenseId)
                .orElseThrow(() -> new IllegalArgumentException("Lisans bulunamadı"));
        Instant monthAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        long autoThisMonth = transfers.countByLicenseIdAndAutoApprovedTrueAndRequestedAtAfter(licenseId, monthAgo);

        TransferRequest tr = new TransferRequest(licenseId, oldFingerprint, newFingerprint);
        if (autoThisMonth < 1) {
            applyTransfer(lic, oldFingerprint, newFingerprint);
            tr.setAutoApproved(true);
            tr.setStatus(TransferStatus.APPROVED);
            tr.setDecidedAt(Instant.now());
            tr.setDecidedBy("auto");
            transfers.save(tr);
            return new TransferResult(true, signCurrent(lic), null);
        }
        transfers.save(tr);
        return new TransferResult(false, null, tr.getId());
    }

    public String approveTransfer(long requestId, String admin) {
        TransferRequest tr = transfers.findById(requestId).orElseThrow();
        if (tr.getStatus() != TransferStatus.PENDING) {
            throw new IllegalStateException("Talep zaten karara bağlanmış");
        }
        License lic = licenses.findByLicenseId(tr.getLicenseId()).orElseThrow();
        applyTransfer(lic, tr.getOldFingerprint(), tr.getNewFingerprint());
        tr.setStatus(TransferStatus.APPROVED);
        tr.setDecidedAt(Instant.now());
        tr.setDecidedBy(admin);
        return signCurrent(lic);
    }

    public void rejectTransfer(long requestId, String admin) {
        TransferRequest tr = transfers.findById(requestId).orElseThrow();
        tr.setStatus(TransferStatus.REJECTED);
        tr.setDecidedAt(Instant.now());
        tr.setDecidedBy(admin);
    }

    private void applyTransfer(License lic, String oldFp, String newFp) {
        if (oldFp != null) {
            bindings.findByLicenseIdAndFingerprint(lic.getLicenseId(), oldFp)
                    .ifPresent(b -> b.setActive(false));
        }
        LicenseBinding neu = bindings.findByLicenseIdAndFingerprint(lic.getLicenseId(), newFp)
                .orElseGet(() -> bindings.save(new LicenseBinding(lic.getLicenseId(), newFp, 2)));
        neu.setActive(true);
        neu.setLastSeenAt(Instant.now());
    }

    // --- abonelik yönetimi (admin) --------------------------------

    public void suspend(long subscriptionId) {
        setSubStatus(subscriptionId, SubscriptionStatus.SUSPENDED);
        forEachLicense(subscriptionId, l -> l.setStatus(LicenseStatus.SUSPENDED));
    }

    public void reactivate(long subscriptionId) {
        setSubStatus(subscriptionId, SubscriptionStatus.ACTIVE);
        forEachLicense(subscriptionId, l -> l.setStatus(LicenseStatus.ACTIVE));
    }

    public void cancel(long subscriptionId) {
        setSubStatus(subscriptionId, SubscriptionStatus.CANCELLED);
        forEachLicense(subscriptionId, l -> l.setStatus(LicenseStatus.REVOKED));
    }

    public void updatePlanAndModules(String licenseId, Plan plan, String modulesCsv,
                                     Integer maxTerminals, Integer maxActiveUsers) {
        License lic = licenses.findByLicenseId(licenseId).orElseThrow();
        lic.setPlan(plan);
        lic.setModules(modulesCsv);
        if (maxTerminals != null) lic.setMaxTerminals(maxTerminals);
        if (maxActiveUsers != null) lic.setMaxActiveUsers(maxActiveUsers);
        subscriptions.findById(lic.getSubscriptionId()).ifPresent(s -> s.setPlan(plan));
    }

    public PaymentRecord recordPayment(long subscriptionId, BigDecimal amount, int months, String admin) {
        Subscription sub = subscriptions.findById(subscriptionId).orElseThrow();
        LocalDate start = sub.getPaidThrough().isBefore(LocalDate.now()) ? LocalDate.now() : sub.getPaidThrough();
        LocalDate end = start.plusMonths(Math.max(1, months));
        sub.setPaidThrough(end);
        if (sub.getStatus() != SubscriptionStatus.CANCELLED) {
            sub.setStatus(SubscriptionStatus.ACTIVE);
            forEachLicense(subscriptionId, l -> {
                if (l.getStatus() == LicenseStatus.SUSPENDED) l.setStatus(LicenseStatus.ACTIVE);
            });
        }
        return payments.save(new PaymentRecord(subscriptionId, amount, start, end, "MANUEL", admin));
    }

    // --- yardımcılar ---------------------------------------------

    void renewWindow(License lic) {
        lic.setNotBefore(lic.getNotBefore() == null ? Instant.now() : lic.getNotBefore());
        lic.setNotAfter(Instant.now().plus(RENEWAL_WINDOW_DAYS, ChronoUnit.DAYS));
        lic.setLastRenewedAt(Instant.now());
    }

    String signCurrent(License lic) {
        Customer customer = customers.findById(lic.getCustomerId()).orElse(null);
        List<LicenseBinding> active = bindings.findAllByLicenseIdAndActiveTrue(lic.getLicenseId());
        return signer.buildAndSign(lic, customer, active);
    }

    private void setSubStatus(long id, SubscriptionStatus s) {
        subscriptions.findById(id).orElseThrow().setStatus(s);
    }

    private void forEachLicense(long subscriptionId, java.util.function.Consumer<License> fn) {
        licenses.findAllBySubscriptionId(subscriptionId).forEach(fn);
    }

    private String uniqueLicenseId() {
        for (int i = 0; i < 20; i++) {
            String id = "LIC-" + LocalDate.now().getYear() + "-" + String.format("%06d", RND.nextInt(1_000_000));
            if (!licenses.existsByLicenseId(id)) {
                return id;
            }
        }
        throw new IllegalStateException("Benzersiz lisans no üretilemedi");
    }

    private String newActivationKey() {
        StringBuilder sb = new StringBuilder("BSA");
        for (int g = 0; g < 4; g++) {
            sb.append('-');
            for (int i = 0; i < 4; i++) {
                sb.append(KEY_ALPHABET.charAt(RND.nextInt(KEY_ALPHABET.length())));
            }
        }
        return sb.toString();
    }
}
