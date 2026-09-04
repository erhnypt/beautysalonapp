package com.beautysalonapp.licensing;

import com.beautysalonapp.core.error.LicenseRestrictionException;
import com.beautysalonapp.licensing.application.LicenseService;
import com.beautysalonapp.licensing.domain.LicensePayload;
import com.beautysalonapp.licensing.domain.LicensePlan;
import com.beautysalonapp.licensing.domain.LicenseStatus;
import com.beautysalonapp.licensing.domain.ModuleCode;
import com.beautysalonapp.licensing.infrastructure.LicenseState;
import com.beautysalonapp.licensing.infrastructure.LicenseStateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator;
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lisans kademeli kısıtlama merdiveninin (§6.4) tüm durum geçişleri —
 * plan §18: "Sahte saat, bozuk imza, süresi geçmiş lisans ... Tüm durum geçişleri".
 *
 * Gerçek {@code MonotonicClock} kullanılır; lisans tarihleri {@code Instant.now()}'a görelidir
 * (mock yok → Byte Buddy/JDK sürüm bağımlılığı yok). {@code LicenseVerifier} bu testin ürettiği
 * Ed25519 anahtar çiftiyle çalışsın diye public key {@code @DynamicPropertySource} ile verilir.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LicenseLifecycleTest {

    private static final Ed25519PrivateKeyParameters PRIV;
    private static final String PUB_B64;

    static {
        var gen = new Ed25519KeyPairGenerator();
        gen.init(new Ed25519KeyGenerationParameters(new SecureRandom()));
        var pair = gen.generateKeyPair();
        PRIV = (Ed25519PrivateKeyParameters) pair.getPrivate();
        PUB_B64 = Base64.getEncoder().encodeToString(
                ((Ed25519PublicKeyParameters) pair.getPublic()).getEncoded());
    }

    @DynamicPropertySource
    static void licenseKey(DynamicPropertyRegistry registry) {
        registry.add("beautysalonapp.licensing.public-key-base64", () -> PUB_B64);
    }

    @Autowired
    private LicenseService licenseService;

    @Autowired
    private LicenseStateRepository stateRepo;

    @Autowired
    private ObjectMapper objectMapper;

    /** Her testte 'şimdi' — lisans tarihleri buna göre kurulur. */
    private Instant now;

    @BeforeEach
    @Transactional
    void reset() {
        now = Instant.now();
        pristineLicenseState();
    }

    /**
     * {@code license_state} paylaşılan tekil satırdır; buradaki lisans gövdesi sızarsa
     * sonraki testler (ör. {@code AuthFlowTest.devMode=true}) bozulur. Bu yüzden her testten
     * SONRA da temizlenir.
     */
    @AfterEach
    @Transactional
    void cleanup() {
        pristineLicenseState();
    }

    private void pristineLicenseState() {
        LicenseState st = stateRepo.singleton();
        st.setLicenseBlob(null);
        st.setServerStatus("ACTIVE");
        st.setReadOnlySince(null);
        st.setTamperFlag(false);
        st.setConsecutiveHeartbeatFailures(0);
        stateRepo.save(st);
        licenseService.invalidateCache();
    }

    // ---- yardımcılar ---------------------------------------------------------

    private String buildBlob(Instant notAfter, int graceDays, boolean offline) {
        try {
            LicensePayload p = new LicensePayload(
                    1, "LIC-TEST", "Test Salon", "1234567890", LicensePlan.PRO,
                    now.minus(400, ChronoUnit.DAYS), now.minus(400, ChronoUnit.DAYS), notAfter,
                    graceDays, List.of(ModuleCode.values()), null, List.of(), null, offline);
            byte[] json = objectMapper.writeValueAsString(p).getBytes(StandardCharsets.UTF_8);
            var signer = new Ed25519Signer();
            signer.init(true, PRIV);
            signer.update(json, 0, json.length);
            byte[] sig = signer.generateSignature();
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json)
                    + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Transactional
    void install(Instant notAfter, int graceDays, boolean offline, String serverStatus, Instant readOnlySince) {
        LicenseState st = stateRepo.singleton();
        st.setLicenseBlob(buildBlob(notAfter, graceDays, offline));
        st.setServerStatus(serverStatus);
        st.setReadOnlySince(readOnlySince);
        st.setTamperFlag(false);
        stateRepo.save(st);
        licenseService.invalidateCache();
    }

    private LicenseStatus status() {
        return licenseService.snapshot().status();
    }

    // ---- durum geçişleri --------------------------------------------------

    @Test
    void gecerli_lisans_ACTIVE() {
        install(now.plus(60, ChronoUnit.DAYS), 7, false, "ACTIVE", null);
        assertThat(status()).isEqualTo(LicenseStatus.ACTIVE);
        assertThat(licenseService.snapshot().writesBlocked()).isFalse();
    }

    @Test
    void bitise_yedi_gunden_az_EXPIRING() {
        install(now.plus(5, ChronoUnit.DAYS), 7, false, "ACTIVE", null);
        assertThat(status()).isEqualTo(LicenseStatus.EXPIRING);
        assertThat(licenseService.snapshot().writesBlocked()).isFalse();
    }

    @Test
    void sure_doldu_grace_icinde_GRACE() {
        install(now.minus(2, ChronoUnit.DAYS), 7, false, "ACTIVE", null);
        assertThat(status()).isEqualTo(LicenseStatus.GRACE);
        assertThat(licenseService.snapshot().writesBlocked()).isFalse();
    }

    @Test
    void grace_bitti_READ_ONLY_ve_yazma_kilitli() {
        install(now.minus(20, ChronoUnit.DAYS), 7, false, "ACTIVE", null);
        assertThat(status()).isEqualTo(LicenseStatus.READ_ONLY);
        assertThat(licenseService.snapshot().writesBlocked()).isTrue();
        assertThatThrownBy(() -> licenseService.assertWritable())
                .isInstanceOf(LicenseRestrictionException.class);
        // READ_ONLY'de görüntüleme/rapor/dışa aktarma açık kalır
        assertThat(licenseService.snapshot().isModuleEnabled(ModuleCode.REPORTING)).isTrue();
    }

    @Test
    void read_only_suresi_asilinca_LOCKED() {
        // readOnlyDays varsayılan 30 (application.yml) — 40 gün önce READ_ONLY'e düşmüş
        install(now.minus(60, ChronoUnit.DAYS), 7, false, "ACTIVE", now.minus(40, ChronoUnit.DAYS));
        assertThat(status()).isEqualTo(LicenseStatus.LOCKED);
        assertThat(licenseService.snapshot().writesBlocked()).isTrue();
        // LOCKED'te bile modül görünürlüğü (dışa aktarma) korunur
        assertThat(licenseService.snapshot().isModuleEnabled(ModuleCode.REPORTING)).isTrue();
    }

    @Test
    void sunucu_REVOKED_LOCKED() {
        install(now.plus(60, ChronoUnit.DAYS), 7, false, "REVOKED", null);
        assertThat(status()).isEqualTo(LicenseStatus.LOCKED);
    }

    @Test
    void sunucu_SUSPENDED_READ_ONLY() {
        install(now.plus(60, ChronoUnit.DAYS), 7, false, "SUSPENDED", null);
        assertThat(status()).isEqualTo(LicenseStatus.READ_ONLY);
        assertThat(licenseService.snapshot().writesBlocked()).isTrue();
    }

    @Test
    void bozuk_imza_kurcalama_bayragini_kaldirir_ve_TAMPERED_e_gecer() {
        install(now.plus(60, ChronoUnit.DAYS), 7, false, "ACTIVE", null);
        LicenseState st = stateRepo.singleton();
        String good = st.getLicenseBlob();
        // gövdeyi boz: ilk segmentin son karakterini değiştir
        int dot = good.indexOf('.');
        char[] c = good.toCharArray();
        c[dot - 1] = (c[dot - 1] == 'A') ? 'B' : 'A';
        st.setLicenseBlob(new String(c));
        stateRepo.save(st);
        licenseService.invalidateCache();

        // İlk değerlendirme: geçerli gövde çözülemez → LOCKED, ama kurcalama bayrağı kalıcı set edilir
        assertThat(status()).isEqualTo(LicenseStatus.LOCKED);
        assertThat(stateRepo.singleton().isTamperFlag()).isTrue();

        // Sonraki değerlendirme: bayrak set olduğu için TAMPERED (zorunlu online doğrulama)
        licenseService.invalidateCache();
        assertThat(status()).isEqualTo(LicenseStatus.TAMPERED);
        assertThat(licenseService.snapshot().writesBlocked()).isTrue();
    }

    @Test
    void saat_geri_alindi_tamper_bayragi_TAMPERED() {
        install(now.plus(60, ChronoUnit.DAYS), 7, false, "ACTIVE", null);
        // MonotonicClock gerçek hayatta bu bayrağı set eder; burada onu taklit ediyoruz
        LicenseState st = stateRepo.singleton();
        st.setTamperFlag(true);
        stateRepo.save(st);
        licenseService.invalidateCache();

        assertThat(status()).isEqualTo(LicenseStatus.TAMPERED);
        assertThat(licenseService.snapshot().writesBlocked()).isTrue();
    }

    @Test
    void cevrimdisi_mod_grace_suresini_uzatir() {
        // offline=true → effectiveGraceDays en az 14; 10 gün gecikmede hâlâ GRACE
        install(now.minus(10, ChronoUnit.DAYS), 3, true, "ACTIVE", null);
        assertThat(status()).isEqualTo(LicenseStatus.GRACE);
    }

    @Test
    void yeni_gecerli_lisans_kurulumu_tamper_bayragini_temizler() {
        LicenseState st = stateRepo.singleton();
        st.setTamperFlag(true);
        stateRepo.save(st);
        licenseService.invalidateCache();
        assertThat(status()).isEqualTo(LicenseStatus.TAMPERED);

        licenseService.installLicense(buildBlob(now.plus(90, ChronoUnit.DAYS), 7, false));
        assertThat(status()).isEqualTo(LicenseStatus.ACTIVE);
        assertThat(stateRepo.singleton().isTamperFlag()).isFalse();
    }
}
