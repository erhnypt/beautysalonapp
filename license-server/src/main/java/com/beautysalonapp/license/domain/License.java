package com.beautysalonapp.license.domain;

import com.beautysalonapp.license.domain.Enums.LicenseStatus;
import com.beautysalonapp.license.domain.Enums.Plan;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "license", uniqueConstraints = {
        @UniqueConstraint(name = "uq_license_license_id", columnNames = "licenseId"),
        @UniqueConstraint(name = "uq_license_activation_key", columnNames = "activationKey")
})
public class License {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** LIC-YYYY-NNNNNN */
    @Column(nullable = false, length = 24)
    private String licenseId;

    /** BSA-XXXX-XXXX-XXXX-XXXX — ilk aktivasyon anahtarı. */
    @Column(nullable = false, length = 32)
    private String activationKey;

    @Column(nullable = false)
    private Long customerId;

    @Column(nullable = false)
    private Long subscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Plan plan;

    /** Virgülle ayrılmış modül kodları (STOCK,STAFF,...). */
    @Column(nullable = false, length = 500)
    private String modules;

    @Column(nullable = false)
    private Integer maxTerminals = 1;

    @Column(nullable = false)
    private Integer maxBranches = 1;

    @Column(nullable = false)
    private Integer maxActiveUsers = 5;

    private Integer maxCustomers; // null = sınırsız

    @Column(nullable = false)
    private int graceDays = 7;

    @Column(nullable = false)
    private boolean offlineMode = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LicenseStatus status = LicenseStatus.UNACTIVATED;

    /** Şu anki geçerlilik penceresi bitişi — heartbeat ile ileri taşınır. */
    private Instant notAfter;

    private Instant notBefore;

    private Instant issuedAt;

    private Instant lastRenewedAt;

    private Instant activatedAt;

    @Column(length = 300)
    private String note;

    public Long getId() { return id; }
    public String getLicenseId() { return licenseId; }
    public void setLicenseId(String licenseId) { this.licenseId = licenseId; }
    public String getActivationKey() { return activationKey; }
    public void setActivationKey(String activationKey) { this.activationKey = activationKey; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public Long getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(Long subscriptionId) { this.subscriptionId = subscriptionId; }
    public Plan getPlan() { return plan; }
    public void setPlan(Plan plan) { this.plan = plan; }
    public String getModules() { return modules; }
    public void setModules(String modules) { this.modules = modules; }
    public Integer getMaxTerminals() { return maxTerminals; }
    public void setMaxTerminals(Integer maxTerminals) { this.maxTerminals = maxTerminals; }
    public Integer getMaxBranches() { return maxBranches; }
    public void setMaxBranches(Integer maxBranches) { this.maxBranches = maxBranches; }
    public Integer getMaxActiveUsers() { return maxActiveUsers; }
    public void setMaxActiveUsers(Integer maxActiveUsers) { this.maxActiveUsers = maxActiveUsers; }
    public Integer getMaxCustomers() { return maxCustomers; }
    public void setMaxCustomers(Integer maxCustomers) { this.maxCustomers = maxCustomers; }
    public int getGraceDays() { return graceDays; }
    public void setGraceDays(int graceDays) { this.graceDays = graceDays; }
    public boolean isOfflineMode() { return offlineMode; }
    public void setOfflineMode(boolean offlineMode) { this.offlineMode = offlineMode; }
    public LicenseStatus getStatus() { return status; }
    public void setStatus(LicenseStatus status) { this.status = status; }
    public Instant getNotAfter() { return notAfter; }
    public void setNotAfter(Instant notAfter) { this.notAfter = notAfter; }
    public Instant getNotBefore() { return notBefore; }
    public void setNotBefore(Instant notBefore) { this.notBefore = notBefore; }
    public Instant getIssuedAt() { return issuedAt; }
    public void setIssuedAt(Instant issuedAt) { this.issuedAt = issuedAt; }
    public Instant getLastRenewedAt() { return lastRenewedAt; }
    public void setLastRenewedAt(Instant lastRenewedAt) { this.lastRenewedAt = lastRenewedAt; }
    public Instant getActivatedAt() { return activatedAt; }
    public void setActivatedAt(Instant activatedAt) { this.activatedAt = activatedAt; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public java.util.List<String> moduleList() {
        return modules == null || modules.isBlank() ? java.util.List.of()
                : java.util.Arrays.stream(modules.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
