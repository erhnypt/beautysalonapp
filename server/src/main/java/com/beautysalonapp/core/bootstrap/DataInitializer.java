package com.beautysalonapp.core.bootstrap;

import com.beautysalonapp.config.AppProperties;
import com.beautysalonapp.security.domain.AppUser;
import com.beautysalonapp.security.domain.Role;
import com.beautysalonapp.security.domain.RoleName;
import com.beautysalonapp.security.domain.RolePermissionCatalog;
import com.beautysalonapp.security.infrastructure.AppUserRepository;
import com.beautysalonapp.security.infrastructure.RoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * İlk açılış tohumlaması: sistem rolleri ve bootstrap yönetici kullanıcısı.
 * Flyway şemayı kurar; bu sınıf iş verisi olmayan zorunlu kayıtları ekler.
 */
@Component
@Order(10)
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final RoleRepository roles;
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties props;

    public DataInitializer(RoleRepository roles, AppUserRepository users,
                           PasswordEncoder passwordEncoder, AppProperties props) {
        this.roles = roles;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedRoles();
        seedAdmin();
    }

    private void seedRoles() {
        for (RoleName name : RoleName.values()) {
            roles.findByName(name).ifPresentOrElse(
                    existing -> {
                        if (name == RoleName.ADMIN) {
                            // ADMIN her zaman tüm yetkilere sahip olmalı
                            existing.setPermissions(RolePermissionCatalog.defaultsFor(RoleName.ADMIN));
                        }
                    },
                    () -> {
                        Role r = new Role(name, "Sistem rolü: " + name,
                                RolePermissionCatalog.defaultsFor(name));
                        roles.save(r);
                        log.info("Rol oluşturuldu: {}", name);
                    });
        }
    }

    private void seedAdmin() {
        String username = props.getSecurity().getBootstrapAdminUsername();
        if (users.existsByUsernameIgnoreCase(username)) {
            return;
        }
        Role adminRole = roles.findByName(RoleName.ADMIN).orElseThrow();
        AppUser admin = new AppUser(username, "Sistem Yöneticisi",
                passwordEncoder.encode(props.getSecurity().getBootstrapAdminPassword()));
        admin.setMustChangePassword(true);
        admin.setRoles(Set.of(adminRole));
        users.save(admin);
        log.warn("Bootstrap yönetici oluşturuldu: kullanıcı='{}' — İLK GİRİŞTE PAROLAYI DEĞİŞTİRİN", username);
    }
}
