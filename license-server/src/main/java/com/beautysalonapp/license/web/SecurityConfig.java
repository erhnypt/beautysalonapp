package com.beautysalonapp.license.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * İstemci API'si ({@code /api/v1/**}) açıktır — kimlik anahtarla doğrulanır.
 * Admin paneli form-login arkasındadır (tek yönetici, ortam değişkeninden).
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public UserDetailsService adminUser(
            @Value("${beautysalonapp.license.admin-username:admin}") String username,
            @Value("${beautysalonapp.license.admin-password:admin123}") String password,
            PasswordEncoder encoder) {
        return new InMemoryUserDetailsManager(
                User.withUsername(username).password(encoder.encode(password)).roles("ADMIN").build());
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/v1/**"))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/**", "/actuator/health", "/css/**", "/login", "/error").permitAll()
                .anyRequest().hasRole("ADMIN"))
            .formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/admin", true).permitAll())
            .logout(logout -> logout.logoutSuccessUrl("/login?logout").permitAll());
        return http.build();
    }
}
