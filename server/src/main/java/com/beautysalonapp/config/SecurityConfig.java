package com.beautysalonapp.config;

import com.beautysalonapp.licensing.LicenseEnforcementFilter;
import com.beautysalonapp.modules.branch.web.BranchContextFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;

/**
 * Uygulama güvenliği (§8.1).
 *
 * <ul>
 *   <li>Oturum tabanlı kimlik doğrulama, çerez {@code HttpOnly; SameSite=Strict}.</li>
 *   <li>SPA için token tabanlı CSRF (çerezden okunur, header ile geri gönderilir).</li>
 *   <li>Parola karması BCrypt cost 12.</li>
 *   <li>Lisans kısıtlaması: {@link LicenseEnforcementFilter} yazma isteklerini READ_ONLY/LOCKED'te reddeder.</li>
 *   <li>Şube bağlamı: {@link BranchContextFilter} {@code X-Branch-Id} başlığını doğrulayıp
 *       istek süresince aktif şubeyi taşır (Faz 8 tam şube izolasyonu, ADR 0006).</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           LicenseEnforcementFilter licenseFilter,
                                           BranchContextFilter branchContextFilter) throws Exception {
        var csrfHandler = new CsrfTokenRequestAttributeHandler();

        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(csrfHandler)
                .ignoringRequestMatchers("/api/v1/auth/login", "/api/v1/auth/logout"))
            // Güvenlik başlıkları (§8.1, Faz 7 sertleştirme). X-Content-Type-Options,
            // X-Frame-Options, Cache-Control zaten varsayılan; CSP + Referrer/Permissions
            // Policy elle eklenir. Uygulama SPA'yı kendi origin'inden sunar.
            .headers(headers -> headers
                .frameOptions(fo -> fo.deny())
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; "
                    + "font-src 'self' data:; connect-src 'self'; object-src 'none'; "
                    + "frame-ancestors 'none'; base-uri 'self'; form-action 'self'"))
                .referrerPolicy(rp -> rp.policy(ReferrerPolicy.NO_REFERRER))
                .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy",
                    "geolocation=(), camera=(), microphone=(), payment=(), usb=()")))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/v1/auth/**",
                    "/api/v1/license/status",
                    "/actuator/health",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html").permitAll()
                // Statik SPA varlıkları
                .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico", "/*.svg", "/*.png").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll())
            .exceptionHandling(eh -> eh
                .authenticationEntryPoint((req, res, ex) -> res.sendError(HttpStatus.UNAUTHORIZED.value())))
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .logout(logout -> logout.disable())
            .addFilterAfter(licenseFilter, BasicAuthenticationFilter.class)
            .addFilterAfter(branchContextFilter, LicenseEnforcementFilter.class);

        return http.build();
    }
}
