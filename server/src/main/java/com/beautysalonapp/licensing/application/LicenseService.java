package com.beautysalonapp.licensing.application;

import com.beautysalonapp.config.AppProperties;
import com.beautysalonapp.core.error.LicenseRestrictionException;
import com.beautysalonapp.licensing.domain.LicensePayload;
import com.beautysalonapp.licensing.domain.LicenseStatus;
import com.beautysalonapp.licensing.domain.ModuleCode;
import com.beautysalonapp.licensing.infrastructure.LicenseState;
import com.beautysalonapp.licensing.infrastructure.LicenseStateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Lisans motoru (§6). Yetkili kaynak imzalı {@code license.lic} dosyasıdır; DB yalnızca önbellek.
 *
 * <ul>
 *   <li>Açılışta dosyayı doğrular ve önbelleğe alır.</li>
 *   <li>{@link #snapshot()} kademeli kısıtlama merdivenini (§6.4) uygular; sonucu ~60 sn önbelleğe alır.</li>
 *   <li>{@link #assertWritable()} READ_ONLY/LOCKED/TAMPERED durumunda yazma işlemlerini reddeder.</li>
 * </ul>
 */
@Service
public class LicenseService {

    private static final Logger log = LoggerFactory.getLogger(LicenseService.class);
    private static final Duration CACHE_TTL = Duration.ofSeconds(60);

    private final AppProperties props;
    private final LicenseStateRepository stateRepo;
    private final ObjectMapper objectMapper;
    private final MonotonicClock clock;
    private final LicenseVerifier verifier;

    private volatile LicenseSnapshot cachedSnapshot;
    private volatile Instant cachedAt = Instant.EPOCH;

    public LicenseService(AppProperties props,
                          LicenseStateRepository stateRepo,
                          ObjectMapper objectMapper,
                          MonotonicClock clock) {
        this.props = props;
        this.stateRepo = stateRepo;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.verifier = new LicenseVerifier(props.getLicensing().getPublicKeyBase64());
    }

    @PostConstruct
    @Transactional
    public void loadOnStartup() {
        try {
            Path file = Path.of(resolveLicensePath());
            if (Files.exists(file)) {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                cacheLicenseBlob(content);
                log.info("Lisans dosyası yüklendi: {}", file);
            } else if (verifier.signatureCheckEnabled()) {
                log.warn("Lisans dosyası yok: {} — uygulama LOCKED durumda açılacak", file);
            } else {
                log.warn("Lisans dosyası ve gömülü public key yok — GELİŞTİRME MODU (tüm modüller açık)");
            }
        } catch (Exception e) {
            log.error("Lisans dosyası yüklenemedi", e);
        }
        invalidateCache();
    }

    /** Yüklenen .lic içeriğini doğrular ve önbelleğe alır (Ayarlar → Lisans → Dosyadan Yükle). */
    @Transactional
    public LicenseSnapshot installLicense(String licenseFileContent) {
        String payloadJson = verifier.verifyAndExtractPayload(licenseFileContent); // imza/biçim hatası fırlatır
        parsePayload(payloadJson); // JSON hatası fırlatır
        cacheLicenseBlob(licenseFileContent.strip());
        LicenseState st = stateRepo.singleton();
        st.setTamperFlag(false); // geçerli yeni lisans kurcalama bayrağını temizler
        st.setReadOnlySince(null);
        stateRepo.save(st);
        invalidateCache();
        return snapshot();
    }

    @Transactional
    public LicenseSnapshot snapshot() {
        LicenseSnapshot cached = cachedSnapshot;
        if (cached != null && Instant.now().isBefore(cachedAt.plus(CACHE_TTL))) {
            return cached;
        }
        LicenseSnapshot fresh = compute();
        cachedSnapshot = fresh;
        cachedAt = Instant.now();
        return fresh;
    }

    public void invalidateCache() {
        cachedAt = Instant.EPOCH;
    }

    public boolean isModuleEnabled(ModuleCode code) {
        return snapshot().isModuleEnabled(code);
    }

    public void assertModuleEnabled(ModuleCode code) {
        if (!isModuleEnabled(code)) {
            throw new LicenseRestrictionException("Modül lisansınızda kapalı: " + code);
        }
    }

    /** Yazma (POST/PUT/PATCH/DELETE) işlemleri için çağrılır. */
    public void assertWritable() {
        LicenseSnapshot s = snapshot();
        if (s.writesBlocked()) {
            throw new LicenseRestrictionException(
                    "Lisans durumu (" + s.status() + ") nedeniyle yeni kayıt/işlem yapılamıyor. "
                            + "Görüntüleme, raporlama, yedekleme ve veri dışa aktarma çalışmaya devam eder.");
        }
    }

    // --- iç mantık -------------------------------------------------------------

    private LicenseSnapshot compute() {
        LicenseState state = stateRepo.singleton();
        Instant now = clock.now();

        if (state.isTamperFlag()) {
            return LicenseSnapshot.of(LicenseStatus.TAMPERED, safePayload(state),
                    payloadOrNull(state) == null ? null : payloadOrNull(state).notAfter(), null,
                    state.getLastSuccessfulHeartbeatAt(), state.getConsecutiveHeartbeatFailures(),
                    "Sistem saati tutarsızlığı tespit edildi. Lütfen internete bağlanıp lisansı doğrulayın.");
        }

        LicensePayload p = payloadOrNull(state);
        if (p == null) {
            return verifier.signatureCheckEnabled()
                    ? LicenseSnapshot.noLicense()
                    : LicenseSnapshot.devFallback();
        }

        String serverStatus = state.getServerStatus() == null ? "ACTIVE" : state.getServerStatus();
        if ("REVOKED".equalsIgnoreCase(serverStatus)) {
            return LicenseSnapshot.of(LicenseStatus.LOCKED, p, p.notAfter(), null,
                    state.getLastSuccessfulHeartbeatAt(), state.getConsecutiveHeartbeatFailures(),
                    "Lisansınız iptal edilmiş. Verilerinizi dışa aktarabilirsiniz; devam için bizimle iletişime geçin.");
        }

        Instant notAfter = p.notAfter();
        Instant graceEnd = notAfter.plus(p.effectiveGraceDays(), ChronoUnit.DAYS);

        if ("SUSPENDED".equalsIgnoreCase(serverStatus)) {
            markReadOnlySince(state, now);
            return maybeLocked(state, now, p, LicenseStatus.READ_ONLY,
                    "Aboneliğiniz askıya alınmış. Yeni işlem yapılamaz; verilerinizi dışa aktarabilirsiniz.");
        }

        if (!now.isAfter(notAfter)) {
            clearReadOnlySince(state);
            long daysLeft = ChronoUnit.DAYS.between(now, notAfter);
            if (daysLeft <= 7) {
                return LicenseSnapshot.of(LicenseStatus.EXPIRING, p, notAfter, (int) daysLeft,
                        state.getLastSuccessfulHeartbeatAt(), state.getConsecutiveHeartbeatFailures(),
                        "Lisansınızın bitmesine " + daysLeft + " gün kaldı.");
            }
            return LicenseSnapshot.of(LicenseStatus.ACTIVE, p, notAfter, (int) daysLeft,
                    state.getLastSuccessfulHeartbeatAt(), state.getConsecutiveHeartbeatFailures(), null);
        }

        if (!now.isAfter(graceEnd)) {
            clearReadOnlySince(state);
            long overdue = ChronoUnit.DAYS.between(notAfter, now);
            return LicenseSnapshot.of(LicenseStatus.GRACE, p, notAfter, (int) -overdue,
                    state.getLastSuccessfulHeartbeatAt(), state.getConsecutiveHeartbeatFailures(),
                    "Lisans süresi doldu, ödemesiz kullanım süresi (grace) devam ediyor. Lütfen ödemenizi yapın.");
        }

        markReadOnlySince(state, now);
        return maybeLocked(state, now, p, LicenseStatus.READ_ONLY,
                "Lisans süresi ve ek süre doldu. Yeni işlem yapılamaz; verilerinizi dışa aktarabilirsiniz.");
    }

    private LicenseSnapshot maybeLocked(LicenseState state, Instant now, LicensePayload p,
                                        LicenseStatus readOnly, String roMessage) {
        Instant since = state.getReadOnlySince();
        long lockedDays = props.getLicensing().getReadOnlyDays();
        if (since != null && ChronoUnit.DAYS.between(since, now) >= lockedDays) {
            return LicenseSnapshot.of(LicenseStatus.LOCKED, p, p.notAfter(), null,
                    state.getLastSuccessfulHeartbeatAt(), state.getConsecutiveHeartbeatFailures(),
                    "Uzun süredir ödeme alınamadı. Yalnızca lisans girişi ve tam veri dışa aktarma açık.");
        }
        return LicenseSnapshot.of(readOnly, p, p.notAfter(), null,
                state.getLastSuccessfulHeartbeatAt(), state.getConsecutiveHeartbeatFailures(), roMessage);
    }

    private void markReadOnlySince(LicenseState state, Instant now) {
        if (state.getReadOnlySince() == null) {
            state.setReadOnlySince(now);
            stateRepo.save(state);
        }
    }

    private void clearReadOnlySince(LicenseState state) {
        if (state.getReadOnlySince() != null) {
            state.setReadOnlySince(null);
            stateRepo.save(state);
        }
    }

    private void cacheLicenseBlob(String content) {
        LicenseState st = stateRepo.singleton();
        st.setLicenseBlob(content);
        stateRepo.save(st);
    }

    private LicensePayload payloadOrNull(LicenseState state) {
        if (state.getLicenseBlob() == null || state.getLicenseBlob().isBlank()) {
            return null;
        }
        try {
            String json = verifier.verifyAndExtractPayload(state.getLicenseBlob());
            return parsePayload(json);
        } catch (RuntimeException e) {
            log.error("Önbellekteki lisans doğrulanamadı: {}", e.getMessage());
            state.setTamperFlag(true);
            stateRepo.save(state);
            return null;
        }
    }

    private LicensePayload safePayload(LicenseState state) {
        LicensePayload p = payloadOrNull(state);
        if (p != null) return p;
        // TAMPERED durumunda bile bir taban gerekiyor
        Instant nowTs = Instant.now();
        return new LicensePayload(1, "?", "?", "?", null, nowTs, nowTs, nowTs, 7,
                java.util.List.of(), null, java.util.List.of(), null, false);
    }

    private LicensePayload parsePayload(String json) {
        try {
            return objectMapper.readValue(json, LicensePayload.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Lisans gövdesi çözümlenemedi: " + e.getMessage(), e);
        }
    }

    private String resolveLicensePath() {
        String configured = props.getLicensing().getLicenseFile();
        if (configured != null && configured.contains("${")) {
            return Path.of(props.getDataDir(), "config", "license.lic").toString();
        }
        return configured;
    }

    // Heartbeat istemcisi tarafından çağrılır
    @Transactional
    public void applyServerStatus(String status, Instant serverTime, String newLicenseBlob, String message) {
        LicenseState st = stateRepo.singleton();
        if (status != null) {
            st.setServerStatus(status.toUpperCase());
        }
        if (newLicenseBlob != null && !newLicenseBlob.isBlank()) {
            try {
                verifier.verifyAndExtractPayload(newLicenseBlob);
                st.setLicenseBlob(newLicenseBlob.strip());
            } catch (RuntimeException e) {
                log.error("Sunucudan gelen yeni lisans reddedildi: {}", e.getMessage());
            }
        }
        st.setLastSuccessfulHeartbeatAt(Instant.now());
        st.setLastHeartbeatAttemptAt(Instant.now());
        st.setConsecutiveHeartbeatFailures(0);
        stateRepo.save(st);
        clock.syncWithServerTime(serverTime);
        invalidateCache();
    }

    @Transactional
    public void recordHeartbeatFailure() {
        LicenseState st = stateRepo.singleton();
        st.setLastHeartbeatAttemptAt(Instant.now());
        st.setConsecutiveHeartbeatFailures(st.getConsecutiveHeartbeatFailures() + 1);
        stateRepo.save(st);
    }
}
