package com.beautysalonapp;

import com.beautysalonapp.licensing.application.LicenseService;
import com.beautysalonapp.licensing.domain.LicenseStatus;
import com.beautysalonapp.security.infrastructure.AppUserRepository;
import com.beautysalonapp.security.infrastructure.RoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SmokeApplicationTests {

    @Autowired RoleRepository roles;
    @Autowired AppUserRepository users;
    @Autowired LicenseService licenseService;

    @Test
    void context_yuklenir_ve_tohumlama_calisir() {
        assertThat(roles.count()).isEqualTo(5);
        assertThat(users.existsByUsernameIgnoreCase("admin")).isTrue();
    }

    @Test
    void lisans_dosyasi_yokken_gelistirme_modu_aktif() {
        var snap = licenseService.snapshot();
        assertThat(snap.devMode()).isTrue();
        assertThat(snap.status()).isEqualTo(LicenseStatus.ACTIVE);
        assertThat(snap.writesBlocked()).isFalse();
    }
}
