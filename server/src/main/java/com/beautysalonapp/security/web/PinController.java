package com.beautysalonapp.security.web;

import com.beautysalonapp.security.application.AppUserDetailsService;
import com.beautysalonapp.security.application.UserService;
import com.beautysalonapp.security.domain.PinPolicy;
import com.beautysalonapp.security.web.AuthDtos.PinRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Kritik rollerde 2. faktör (PIN) akışı — güvenlik gözden geçirme A7.
 *
 * <p>Parola girişinden sonra ADMIN/MUDUR oturumu {@link PinPolicy} sentetik yetkisiyle
 * kısıtlanır ({@link AppUserDetailsService}); bu iki uç nokta o kısıtlı oturumdan
 * çağrılabilen tek yerdir. Başarılı kurulum/doğrulama sonrası oturum gerçek rol/izin
 * yetkileriyle yeniden yazılır — ayrıca bir login gerekmez.
 */
@RestController
@RequestMapping("/api/v1/auth/pin")
public class PinController {

    private final UserService userService;
    private final AppUserDetailsService userDetailsService;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    public PinController(UserService userService, AppUserDetailsService userDetailsService) {
        this.userService = userService;
        this.userDetailsService = userDetailsService;
    }

    @PostMapping("/setup")
    @PreAuthorize("hasAuthority('" + PinPolicy.PIN_SETUP_REQUIRED + "')")
    public ResponseEntity<Void> setup(@Valid @RequestBody PinRequest req, Authentication auth,
                                      HttpServletRequest request, HttpServletResponse response) {
        userService.setPin(auth.getName(), req.pin());
        promoteToFullSession(auth.getName(), request, response);
        return ResponseEntity.noContent().build();
    }

    /**
     * {@code PIN_LOCKED} de kabul edilir (yalnızca {@code PIN_VERIFICATION_REQUIRED} değil):
     * kilitliyken oturum açan bir kullanıcının session'ı doğrudan {@code PIN_LOCKED} yetkisiyle
     * kurulur ({@link AppUserDetailsService#pinGateAuthority}) — bu uç nokta yalnızca
     * {@code PIN_VERIFICATION_REQUIRED} isteseydi böyle bir oturum hiçbir zaman buraya
     * ulaşamaz, kilit süresi dolsa bile sonsuza dek 403 alır (regresyon testi: bkz. PinAuthTest).
     */
    @PostMapping("/verify")
    @PreAuthorize("hasAnyAuthority('" + PinPolicy.PIN_VERIFICATION_REQUIRED + "', '" + PinPolicy.PIN_LOCKED + "')")
    public ResponseEntity<Void> verify(@Valid @RequestBody PinRequest req, Authentication auth,
                                       HttpServletRequest request, HttpServletResponse response) {
        boolean ok = userService.verifyPin(auth.getName(), req.pin());
        if (!ok) {
            if (userService.isPinLocked(auth.getName())) {
                // Çok sayıda hatalı deneme: oturumu sonlandır, baştan (parola ile) giriş gerekir.
                invalidateSession(request);
                return ResponseEntity.status(423).build();
            }
            return ResponseEntity.status(401).build();
        }
        promoteToFullSession(auth.getName(), request, response);
        return ResponseEntity.noContent().build();
    }

    private void promoteToFullSession(String username, HttpServletRequest request, HttpServletResponse response) {
        UserDetails full = userDetailsService.loadFullyAuthenticated(username);
        Authentication newAuth = new UsernamePasswordAuthenticationToken(full, null, full.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(newAuth);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
    }

    private void invalidateSession(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }
}
