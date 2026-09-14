package com.beautysalonapp.security.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Kritik rollerde 2. faktör (PIN) politikası (güvenlik gözden geçirme A7).
 *
 * <p>ADMIN/MUDUR rolüne sahip bir kullanıcı, parolayla girişten sonra ek olarak bir
 * PIN kurmalı/doğrulamalıdır. Bu üç sentetik yetki, gerçek rol/izin yetkileri
 * <b>yerine</b> geçici olarak oturuma tanınır ({@link com.beautysalonapp.security.application.AppUserDetailsService}) —
 * böylece PIN adımı tamamlanana kadar kullanıcı hiçbir iş uç noktasına erişemez
 * (tüm controller'lar somut yetki ister, bu üçü hiçbirine eşleşmez).
 */
public final class PinPolicy {

    private PinPolicy() {
    }

    /** PIN hiç kurulmamış (`pin_hash IS NULL`) — kullanıcı önce PIN belirlemeli. */
    public static final String PIN_SETUP_REQUIRED = "PIN_SETUP_REQUIRED";
    /** PIN kurulu — bu oturumda henüz doğrulanmadı. */
    public static final String PIN_VERIFICATION_REQUIRED = "PIN_VERIFICATION_REQUIRED";
    /** Çok sayıda hatalı PIN denemesi sonrası geçici kilit. */
    public static final String PIN_LOCKED = "PIN_LOCKED";

    private static final Set<RoleName> REQUIRES_PIN = EnumSet.of(RoleName.ADMIN, RoleName.MUDUR);

    /** PIN 4-6 haneli rakam. */
    public static final String PIN_PATTERN = "^[0-9]{4,6}$";

    public static boolean requiredFor(Set<Role> roles) {
        return roles.stream().map(Role::getName).anyMatch(REQUIRES_PIN::contains);
    }
}
