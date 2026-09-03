package com.beautysalonapp.security.application;

import com.beautysalonapp.core.error.BusinessRuleException;
import com.beautysalonapp.core.error.NotFoundException;
import com.beautysalonapp.security.domain.AppUser;
import com.beautysalonapp.security.domain.Role;
import com.beautysalonapp.security.domain.RoleName;
import com.beautysalonapp.security.infrastructure.AppUserRepository;
import com.beautysalonapp.security.infrastructure.RoleRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class UserService {

    private static final int MAX_FAILED_LOGINS = 5;
    private static final int LOCK_MINUTES = 15;

    private final AppUserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;

    public UserService(AppUserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<AppUser> list() {
        return users.findAllByDeletedFalseOrderByUsername();
    }

    @Transactional(readOnly = true)
    public AppUser get(Long id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("Kullanıcı", id));
    }

    public AppUser create(String username, String fullName, String rawPassword, Set<RoleName> roleNames) {
        if (users.existsByUsernameIgnoreCase(username)) {
            throw new BusinessRuleException("username_taken", "Bu kullanıcı adı zaten kullanılıyor");
        }
        validatePasswordStrength(rawPassword);
        AppUser u = new AppUser(username.trim(), fullName.trim(), passwordEncoder.encode(rawPassword));
        u.setRoles(resolveRoles(roleNames));
        return users.save(u);
    }

    public AppUser updateRoles(Long id, Set<RoleName> roleNames) {
        AppUser u = get(id);
        u.setRoles(resolveRoles(roleNames));
        return u;
    }

    public AppUser setEnabled(Long id, boolean enabled) {
        AppUser u = get(id);
        if (!enabled && u.getRoles().stream().anyMatch(r -> r.getName() == RoleName.ADMIN)
                && countEnabledAdmins() <= 1) {
            throw new BusinessRuleException("last_admin", "Son etkin yönetici devre dışı bırakılamaz");
        }
        u.setEnabled(enabled);
        return u;
    }

    /** Kullanıcının kendi parolasını değiştirmesi (eski parola doğrulaması ile). */
    public void changeOwnPassword(String username, String currentPassword, String newPassword) {
        AppUser u = users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new NotFoundException("Kullanıcı bulunamadı"));
        if (!passwordEncoder.matches(currentPassword, u.getPasswordHash())) {
            throw new BusinessRuleException("bad_credentials", "Mevcut parola hatalı");
        }
        validatePasswordStrength(newPassword);
        u.setPasswordHash(passwordEncoder.encode(newPassword));
        u.setMustChangePassword(false);
    }

    /** Yönetici tarafından parola sıfırlama. */
    public void resetPassword(Long id, String newPassword) {
        AppUser u = get(id);
        validatePasswordStrength(newPassword);
        u.setPasswordHash(passwordEncoder.encode(newPassword));
        u.setMustChangePassword(true);
        u.setFailedLoginCount(0);
        u.setLockedUntil(null);
    }

    public void recordSuccessfulLogin(String username) {
        users.findByUsernameIgnoreCase(username).ifPresent(u -> {
            u.setFailedLoginCount(0);
            u.setLockedUntil(null);
            u.setLastLoginAt(Instant.now());
        });
    }

    public void recordFailedLogin(String username) {
        users.findByUsernameIgnoreCase(username).ifPresent(u -> {
            int n = u.getFailedLoginCount() + 1;
            u.setFailedLoginCount(n);
            if (n >= MAX_FAILED_LOGINS) {
                u.setLockedUntil(Instant.now().plusSeconds(LOCK_MINUTES * 60L));
            }
        });
    }

    private long countEnabledAdmins() {
        return users.findAllByDeletedFalseOrderByUsername().stream()
                .filter(AppUser::isEnabled)
                .filter(u -> u.getRoles().stream().anyMatch(r -> r.getName() == RoleName.ADMIN))
                .count();
    }

    private Set<Role> resolveRoles(Set<RoleName> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) {
            throw new BusinessRuleException("no_role", "En az bir rol seçilmelidir");
        }
        return roleNames.stream()
                .map(rn -> roles.findByName(rn)
                        .orElseThrow(() -> new NotFoundException("Rol", rn)))
                .collect(Collectors.toSet());
    }

    private void validatePasswordStrength(String pw) {
        if (pw == null || pw.length() < 8
                || pw.chars().noneMatch(Character::isDigit)
                || pw.chars().noneMatch(Character::isLetter)) {
            throw new BusinessRuleException("weak_password",
                    "Parola en az 8 karakter olmalı ve harf ile rakam içermelidir");
        }
    }
}
