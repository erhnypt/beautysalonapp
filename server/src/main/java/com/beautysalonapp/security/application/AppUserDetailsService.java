package com.beautysalonapp.security.application;

import com.beautysalonapp.security.domain.AppUser;
import com.beautysalonapp.security.domain.PinPolicy;
import com.beautysalonapp.security.infrastructure.AppUserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository users;

    public AppUserDetailsService(AppUserRepository users) {
        this.users = users;
    }

    /**
     * Parola doğrulamasından hemen sonra çağrılır. ADMIN/MUDUR için gerçek rol/izin
     * yetkileri yerine PIN adımını temsil eden tek bir sentetik yetki döner
     * ({@link PinPolicy}) — PIN tamamlanana kadar hiçbir iş uç noktasına erişilemez.
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser u = find(username);
        if (PinPolicy.requiredFor(u.getRoles())) {
            return toUserDetails(u, Set.of(new SimpleGrantedAuthority(pinGateAuthority(u))));
        }
        return toUserDetails(u, fullAuthorities(u));
    }

    /**
     * PIN kurulum/doğrulama adımı başarıyla tamamlandıktan sonra çağrılır — gerçek
     * rol/izin yetkileriyle {@link UserDetails} döner (PIN kapısını bilerek atlar).
     */
    @Transactional(readOnly = true)
    public UserDetails loadFullyAuthenticated(String username) {
        AppUser u = find(username);
        return toUserDetails(u, fullAuthorities(u));
    }

    private String pinGateAuthority(AppUser u) {
        if (u.isPinLocked()) {
            return PinPolicy.PIN_LOCKED;
        }
        return (u.getPinHash() == null || u.getPinHash().isBlank())
                ? PinPolicy.PIN_SETUP_REQUIRED
                : PinPolicy.PIN_VERIFICATION_REQUIRED;
    }

    private Set<GrantedAuthority> fullAuthorities(AppUser u) {
        Set<GrantedAuthority> authorities = new HashSet<>();
        u.getRoles().forEach(role -> {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getName().name()));
            role.getPermissions().forEach(p -> authorities.add(new SimpleGrantedAuthority(p.name())));
        });
        return authorities;
    }

    private UserDetails toUserDetails(AppUser u, Set<GrantedAuthority> authorities) {
        return User.withUsername(u.getUsername())
                .password(u.getPasswordHash())
                .disabled(!u.isEnabled())
                .accountLocked(u.isAccountLocked())
                .authorities(authorities)
                .build();
    }

    private AppUser find(String username) {
        return users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("Kullanıcı bulunamadı"));
    }
}
