package com.beautysalonapp.security;

import com.beautysalonapp.core.error.BusinessRuleException;
import com.beautysalonapp.security.application.UserService;
import com.beautysalonapp.security.domain.Permission;
import com.beautysalonapp.security.domain.Role;
import com.beautysalonapp.security.domain.RoleName;
import com.beautysalonapp.security.domain.RolePermissionCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class UserServiceTest {

    @Autowired UserService users;

    private Role role(RoleName name) {
        return this.users.listRoles().stream().filter(r -> r.getName() == name).findFirst().orElseThrow();
    }

    @Test
    void rol_yetkileri_ozellestirilir() {
        Role personel = role(RoleName.PERSONEL);
        Set<Permission> custom = EnumSet.of(Permission.PARTY_VIEW, Permission.STOCK_VIEW);

        Role updated = users.updateRolePermissions(personel.getId(), custom);

        assertThat(updated.getPermissions()).containsExactlyInAnyOrder(
                Permission.PARTY_VIEW, Permission.STOCK_VIEW);

        // fabrika ayarına döndür — sonraki testleri etkilememesi için
        users.resetRolePermissions(personel.getId());
    }

    @Test
    void admin_rolunun_yetkileri_degistirilemez() {
        Role admin = role(RoleName.ADMIN);
        assertThatThrownBy(() -> users.updateRolePermissions(admin.getId(), EnumSet.of(Permission.STOCK_VIEW)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void admin_rolunun_yetkileri_sifirlanamaz() {
        Role admin = role(RoleName.ADMIN);
        assertThatThrownBy(() -> users.resetRolePermissions(admin.getId()))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rol_yetkileri_fabrika_ayarina_dondurulur() {
        Role kasiyer = role(RoleName.KASIYER);
        users.updateRolePermissions(kasiyer.getId(), EnumSet.of(Permission.STOCK_VIEW));

        Role reset = users.resetRolePermissions(kasiyer.getId());

        assertThat(reset.getPermissions()).isEqualTo(RolePermissionCatalog.defaultsFor(RoleName.KASIYER));
    }

    @Test
    void kullanicinin_rolleri_guncellenir() {
        var u = users.create("test.rol." + System.nanoTime(), "Test Rol", "gecici123", Set.of(RoleName.KASIYER));

        var updated = users.updateRoles(u.getId(), Set.of(RoleName.PERSONEL, RoleName.RAPOR_OKUYUCU));

        assertThat(updated.getRoles()).extracting(Role::getName)
                .containsExactlyInAnyOrder(RoleName.PERSONEL, RoleName.RAPOR_OKUYUCU);
    }

    // --- PIN (§A7) -------------------------------------------------------

    private String freshUser(RoleName role) {
        String username = "pin.test." + System.nanoTime();
        users.create(username, "PIN Test", "gecici123!", Set.of(role));
        return username;
    }

    @Test
    void zayif_pin_reddedilir() {
        String u = freshUser(RoleName.MUDUR);
        assertThatThrownBy(() -> users.setPin(u, "abcd")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> users.setPin(u, "12")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void dogru_pin_dogrulanir_yanlis_pin_reddedilir() {
        String u = freshUser(RoleName.MUDUR);
        users.setPin(u, "4321");

        assertThat(users.verifyPin(u, "4321")).isTrue();
        assertThat(users.verifyPin(u, "0000")).isFalse();
    }

    @Test
    void bes_yanlis_denemeden_sonra_kilitlenir() {
        String u = freshUser(RoleName.MUDUR);
        users.setPin(u, "1111");

        for (int i = 0; i < 4; i++) {
            assertThat(users.verifyPin(u, "9999")).isFalse();
        }
        assertThat(users.isPinLocked(u)).isFalse();
        users.verifyPin(u, "9999"); // 5.
        assertThat(users.isPinLocked(u)).isTrue();
        // Kilitliyken doğru PIN bile reddedilir.
        assertThat(users.verifyPin(u, "1111")).isFalse();
    }

    @Test
    void pin_sifirlaninca_yeniden_kurulum_gerekir() {
        var u = users.create("pin.clear." + System.nanoTime(), "PIN Clear", "gecici123!", Set.of(RoleName.MUDUR));
        users.setPin(u.getUsername(), "5555");

        users.clearPin(u.getId());

        assertThat(users.get(u.getId()).getPinHash()).isNull();
    }
}
