package com.beautysalonapp.security.web;

import com.beautysalonapp.security.application.UserService;
import com.beautysalonapp.security.domain.AppUser;
import com.beautysalonapp.security.infrastructure.AppUserRepository;
import com.beautysalonapp.security.web.AuthDtos.ChangePasswordRequest;
import com.beautysalonapp.security.web.AuthDtos.CurrentUser;
import com.beautysalonapp.security.web.AuthDtos.LoginRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Oturum tabanlı kimlik doğrulama. Başarılı girişte {@link SecurityContext} HTTP oturumuna
 * yazılır; sonraki istekler {@code JSESSIONID} çerezi ile doğrulanır.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();
    private final UserService userService;
    private final AppUserRepository users;

    public AuthController(AuthenticationManager authenticationManager,
                          UserService userService,
                          AppUserRepository users) {
        this.authenticationManager = authenticationManager;
        this.userService = userService;
        this.users = users;
    }

    @PostMapping("/login")
    public ResponseEntity<CurrentUser> login(@Valid @RequestBody LoginRequest req,
                                             HttpServletRequest request,
                                             HttpServletResponse response) {
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.username(), req.password()));

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);
            // Oturum sabitleme saldırısına karşı: önceden bir oturum varsa kimliğini değiştir
            if (request.getSession(false) != null) {
                request.changeSessionId();
            }
            contextRepository.saveContext(context, request, response);

            userService.recordSuccessfulLogin(auth.getName());
            return ResponseEntity.ok(toCurrentUser(auth.getName(), authorities(auth)));
        } catch (LockedException e) {
            return ResponseEntity.status(423).build();
        } catch (BadCredentialsException e) {
            userService.recordFailedLogin(req.username());
            return ResponseEntity.status(401).build();
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<CurrentUser> me(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(toCurrentUser(auth.getName(), authorities(auth)));
    }

    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf() {
        // CsrfToken çözümlemesi çerezi yazar; gövdeye gerek yok.
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req, Authentication auth) {
        if (auth == null) {
            return ResponseEntity.status(401).build();
        }
        userService.changeOwnPassword(auth.getName(), req.currentPassword(), req.newPassword());
        return ResponseEntity.noContent().build();
    }

    private Set<String> authorities(Authentication auth) {
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }

    private CurrentUser toCurrentUser(String username, Set<String> authorities) {
        Set<String> roles = authorities.stream()
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .collect(Collectors.toSet());
        Set<String> permissions = authorities.stream()
                .filter(a -> !a.startsWith("ROLE_"))
                .collect(Collectors.toSet());
        boolean mustChange = users.findByUsernameIgnoreCase(username)
                .map(AppUser::isMustChangePassword)
                .orElse(false);
        String fullName = users.findByUsernameIgnoreCase(username)
                .map(AppUser::getFullName)
                .orElse(username);
        return new CurrentUser(username, fullName, mustChange, roles, permissions);
    }
}
