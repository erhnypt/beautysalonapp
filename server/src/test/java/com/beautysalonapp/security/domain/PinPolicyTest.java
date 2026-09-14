package com.beautysalonapp.security.domain;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PinPolicyTest {

    private static Role role(RoleName name) {
        return new Role(name, "test", Set.of());
    }

    @Test
    void admin_ve_mudur_pin_gerektirir() {
        assertThat(PinPolicy.requiredFor(Set.of(role(RoleName.ADMIN)))).isTrue();
        assertThat(PinPolicy.requiredFor(Set.of(role(RoleName.MUDUR)))).isTrue();
    }

    @Test
    void diger_roller_pin_gerektirmez() {
        assertThat(PinPolicy.requiredFor(Set.of(role(RoleName.KASIYER)))).isFalse();
        assertThat(PinPolicy.requiredFor(Set.of(role(RoleName.PERSONEL)))).isFalse();
        assertThat(PinPolicy.requiredFor(Set.of(role(RoleName.RAPOR_OKUYUCU)))).isFalse();
    }

    @Test
    void birden_fazla_rolden_biri_yeterli() {
        assertThat(PinPolicy.requiredFor(Set.of(role(RoleName.KASIYER), role(RoleName.MUDUR)))).isTrue();
    }
}
