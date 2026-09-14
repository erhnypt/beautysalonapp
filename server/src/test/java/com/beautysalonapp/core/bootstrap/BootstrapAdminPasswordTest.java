package com.beautysalonapp.core.bootstrap;

import com.beautysalonapp.security.domain.AppUser;
import com.beautysalonapp.security.infrastructure.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A9: üründe sabit varsayılan yönetici parolası gönderilmez. Parola ayarı boş
 * bırakılınca ilk açılışta rastgele güçlü bir parola üretilir; kullanıcı
 * {@code must_change_password=true} ile oluşturulur.
 */
class BootstrapAdminPasswordTest {

    @Test
    void uretilen_parola_politikayi_karsilar() {
        for (int i = 0; i < 200; i++) {
            String pw = DataInitializer.generateBootstrapPassword();
            assertThat(pw.length()).isGreaterThanOrEqualTo(8);
            assertThat(pw.chars().anyMatch(Character::isLetter)).isTrue();
            assertThat(pw.chars().anyMatch(Character::isDigit)).isTrue();
        }
    }

    @SpringBootTest
    @TestPropertySource(properties = {
            "beautysalonapp.security.bootstrap-admin-username=autogen-admin",
            "beautysalonapp.security.bootstrap-admin-password="
    })
    static class BlankConfigSeedsRandomPassword {

        @Autowired AppUserRepository users;

        @Test
        void bos_ayar_ile_yonetici_uretilir_ve_parola_degistirme_zorunlu() {
            AppUser u = users.findByUsernameIgnoreCase("autogen-admin").orElseThrow();
            assertThat(u.isMustChangePassword()).isTrue();
            assertThat(u.isEnabled()).isTrue();
            assertThat(u.getPasswordHash()).startsWith("$2"); // BCrypt
        }
    }
}
