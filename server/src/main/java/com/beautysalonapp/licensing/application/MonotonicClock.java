package com.beautysalonapp.licensing.application;

import com.beautysalonapp.licensing.infrastructure.LicenseState;
import com.beautysalonapp.licensing.infrastructure.LicenseStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

/**
 * Saat geri alma tespiti (§6.7).
 *
 * <p>Gördüğü en büyük güvenilir zamanı ({@code maxSeenTime}) DB'de AES-256-GCM ile
 * şifreli saklar. Sistem saati bu değerden {@code TOLERANCE} kadar geriye giderse
 * kurcalama sayılır. Heartbeat cevabındaki {@code serverTime} ile de senkronlanır.
 */
@Component
public class MonotonicClock {

    private static final Logger log = LoggerFactory.getLogger(MonotonicClock.class);
    private static final Duration BACKWARD_TOLERANCE = Duration.ofHours(6);
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_LEN = 12;

    private final LicenseStateRepository repository;
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public MonotonicClock(LicenseStateRepository repository,
                          com.beautysalonapp.config.AppProperties props) {
        this.repository = repository;
        this.key = deriveKey(props.getInstallId());
    }

    /** Şu anki güvenilir zamanı döndürür ve monotonic değeri günceller. */
    @Transactional
    public Instant now() {
        Instant sys = Instant.now();
        LicenseState state = repository.singleton();
        Instant seen = decrypt(state.getMaxSeenTimeEnc());

        if (seen != null && sys.isBefore(seen.minus(BACKWARD_TOLERANCE))) {
            if (!state.isTamperFlag()) {
                log.warn("Saat geri alma tespiti: sistem={} < görülen={}", sys, seen);
                state.setTamperFlag(true);
                repository.save(state);
            }
            return seen;
        }

        Instant best = (seen == null || sys.isAfter(seen)) ? sys : seen;
        state.setMaxSeenTimeEnc(encrypt(best));
        repository.save(state);
        return best;
    }

    /** Heartbeat sunucu zamanıyla ileri senkron (sunucu asla geri götürmez). */
    @Transactional
    public void syncWithServerTime(Instant serverTime) {
        if (serverTime == null) return;
        LicenseState state = repository.singleton();
        Instant seen = decrypt(state.getMaxSeenTimeEnc());
        if (seen == null || serverTime.isAfter(seen)) {
            state.setMaxSeenTimeEnc(encrypt(serverTime));
            repository.save(state);
        }
    }

    private SecretKeySpec deriveKey(String installId) {
        try {
            String seed = (installId == null || installId.isBlank() ? "beautysalonapp-dev-seed" : installId)
                    + "|monotonic-clock|v1";
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(seed.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(digest, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Anahtar türetilemedi", e);
        }
    }

    private String encrypt(Instant value) {
        try {
            byte[] iv = new byte[IV_LEN];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ct = cipher.doFinal(Long.toString(value.toEpochMilli()).getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("Şifreleme başarısız", e);
        }
    }

    private Instant decrypt(String encoded) {
        if (encoded == null || encoded.isBlank()) return null;
        try {
            byte[] all = Base64.getDecoder().decode(encoded);
            byte[] iv = Arrays.copyOfRange(all, 0, IV_LEN);
            byte[] ct = Arrays.copyOfRange(all, IV_LEN, all.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            String millis = new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
            return Instant.ofEpochMilli(Long.parseLong(millis));
        } catch (Exception e) {
            log.warn("Monotonic clock çözümlenemedi (bozulma?); sıfırlanıyor");
            return null;
        }
    }
}
