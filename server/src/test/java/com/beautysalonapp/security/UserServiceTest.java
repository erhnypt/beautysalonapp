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
}
