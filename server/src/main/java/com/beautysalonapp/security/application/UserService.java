package com.beautysalonapp.security.application;

import com.beautysalonapp.audit.application.AuditService;
import com.beautysalonapp.core.error.BusinessRuleException;
import com.beautysalonapp.core.error.NotFoundException;
import com.beautysalonapp.modules.branch.application.BranchPort;
import com.beautysalonapp.security.domain.AppUser;
import com.beautysalonapp.security.domain.Permission;
import com.beautysalonapp.security.domain.Role;
import com.beautysalonapp.security.domain.RoleName;
import com.beautysalonapp.security.domain.RolePermissionCatalog;
import com.beautysalonapp.security.infrastructure.AppUserRepository;
import com.beautysalonapp.security.infrastructure.RoleRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class UserService {

    private static final int MAX_FAILED_LOGINS = 5;
    private static final int LOCK_MINUTES = 15;
    private static final int MAX_FAILED_PIN = 5;
    private static final int PIN_LOCK_MINUTES = 15;

    private final AppUserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final BranchPort branches;

    public UserService(AppUserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder,
                       AuditService audit, BranchPort branches) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.branches = branches;
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

    // --- rol / yetki yönetimi (§8.1) ------------------------------------

    @Transactional(readOnly = true)
    public List<Role> listRoles() {
        return roles.findAll().stream()
                .sorted((a, b) -> a.getName().name().compareTo(b.getName().name()))
                .toList();
    }

    /**
     * Bir rolün yetki kümesini günceller. ADMIN rolü hariç — yönetici yetkisi
     * daima tamdır, aksi halde admin kendini ekranlardan kilitleyebilir (§8.1).
     */
    public Role updateRolePermissions(Long roleId, Set<Permission> permissions) {
        Role r = roles.findById(roleId).orElseThrow(() -> new NotFoundException("Rol", roleId));
        if (r.getName() == RoleName.ADMIN) {
            throw new BusinessRuleException("admin_role_fixed", "Yönetici rolünün yetkileri değiştirilemez");
        }
        r.setPermissions(new java.util.HashSet<>(permissions));
        audit.record("ROLE_PERMISSIONS", "Role", r.getId(),
                "Rol yetkileri güncellendi: " + r.getName() + " → " + permissions.size() + " yetki");
        return r;
    }

    /** Rolü fabrika (varsayılan) yetki kümesine döndürür. */
    public Role resetRolePermissions(Long roleId) {
        Role r = roles.findById(roleId).orElseThrow(() -> new NotFoundException("Rol", roleId));
        if (r.getName() == RoleName.ADMIN) {
            throw new BusinessRuleException("admin_role_fixed", "Yönetici rolünün yetkileri değiştirilemez");
        }
        r.setPermissions(new java.util.HashSet<>(RolePermissionCatalog.defaultsFor(r.getName())));
        audit.record("ROLE_PERMISSIONS_RESET", "Role", r.getId(),
                "Rol yetkileri fabrika ayarına döndürüldü: " + r.getName());
        return r;
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

    // --- Şube ataması (güvenlik gözden geçirme B5) ------------------------

    /**
     * Kullanıcıyı belirli şube(ler)e kısıtlar. Boş küme = kısıtlama kaldırılır (kullanıcı
     * her şubede çalışabilir — v1/eski davranış).
     */
    public AppUser assignBranches(Long id, Set<Long> branchIds) {
        AppUser u = get(id);
        Set<Long> clean = branchIds == null ? Set.of() : branchIds;
        for (Long branchId : clean) {
            if (branchId == null || !branches.exists(branchId)) {
                throw new BusinessRuleException("invalid_branch", "Geçersiz şube: " + branchId);
            }
        }
        u.setAssignedBranchIds(new HashSet<>(clean));
        audit.record("USER_BRANCHES", "AppUser", u.getId(),
                "Şube ataması güncellendi: " + u.getUsername() + " → "
                        + (clean.isEmpty() ? "(kısıtlama yok)" : clean));
        return u;
    }

    /** Boş küme = kısıtlama yok (her şubede çalışabilir). */
    @Transactional(readOnly = true)
    public Set<Long> assignedBranches(String username) {
        return users.findByUsernameIgnoreCase(username).map(AppUser::getAssignedBranchIds).orElse(Set.of());
    }

    /**
     * Şube parametresi alan rapor/uç noktalarda ortak kapı (§B5). Kısıtlanmamış kullanıcı
     * için isteneni aynen döner (davranış değişmez). Kısıtlanmış kullanıcı için: parametre
     * verilmemişse atanmış şubelerden biri (en küçük id) varsayılan olur; verilmişse
     * atanmış kümede değilse {@link AccessDeniedException} (→ 403) fırlatılır.
     */
    @Transactional(readOnly = true)
    public Long resolveRequestedBranch(String username, Long requestedBranchId) {
        Set<Long> allowed = assignedBranches(username);
        if (allowed.isEmpty()) {
            return requestedBranchId;
        }
        if (requestedBranchId == null) {
            return allowed.stream().min(Long::compareTo).orElse(null);
        }
        if (!allowed.contains(requestedBranchId)) {
            throw new AccessDeniedException("Bu şubeye erişim yetkiniz yok: " + requestedBranchId);
        }
        return requestedBranchId;
    }

    // --- PIN (2. faktör, güvenlik gözden geçirme A7) --------------------

    /** PIN kurulumu (ilk kez) veya değiştirme. Yalnızca oturum PIN adımındayken çağrılır. */
    public void setPin(String username, String pin) {
        AppUser u = users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new NotFoundException("Kullanıcı bulunamadı"));
        validatePin(pin);
        u.setPinHash(passwordEncoder.encode(pin));
        u.setPinFailedCount(0);
        u.setPinLockedUntil(null);
        audit.record("PIN_SETUP", "AppUser", u.getId(), "PIN belirlendi: " + u.getUsername());
    }

    /** PIN doğrular; yanlışsa deneme sayacını artırır ve gerekirse kilitler. */
    public boolean verifyPin(String username, String pin) {
        AppUser u = users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new NotFoundException("Kullanıcı bulunamadı"));
        if (u.isPinLocked()) {
            return false;
        }
        if (u.getPinHash() != null && passwordEncoder.matches(pin, u.getPinHash())) {
            u.setPinFailedCount(0);
            u.setPinLockedUntil(null);
            return true;
        }
        int n = u.getPinFailedCount() + 1;
        u.setPinFailedCount(n);
        if (n >= MAX_FAILED_PIN) {
            u.setPinLockedUntil(Instant.now().plusSeconds(PIN_LOCK_MINUTES * 60L));
            audit.record("PIN_LOCKED", "AppUser", u.getId(),
                    "Çok sayıda hatalı PIN denemesi: " + u.getUsername());
        }
        return false;
    }

    @Transactional(readOnly = true)
    public boolean isPinLocked(String username) {
        return users.findByUsernameIgnoreCase(username).map(AppUser::isPinLocked).orElse(false);
    }

    /** Yönetici tarafından PIN sıfırlama ("PIN'imi unuttum" kurtarma yolu). */
    public void clearPin(Long id) {
        AppUser u = get(id);
        u.setPinHash(null);
        u.setPinFailedCount(0);
        u.setPinLockedUntil(null);
        audit.record("PIN_CLEARED", "AppUser", u.getId(), "PIN sıfırlandı: " + u.getUsername());
    }

    private void validatePin(String pin) {
        if (pin == null || !pin.matches(com.beautysalonapp.security.domain.PinPolicy.PIN_PATTERN)) {
            throw new BusinessRuleException("weak_pin", "PIN 4-6 haneli rakamdan oluşmalıdır");
        }
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
