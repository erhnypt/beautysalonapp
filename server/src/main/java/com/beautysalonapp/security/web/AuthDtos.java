package com.beautysalonapp.security.web;

import com.beautysalonapp.security.domain.PinPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.Set;

/** Kimlik doğrulama uç noktalarının istek/yanıt tipleri. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank String newPassword) {
    }

    /** PIN kurulum/doğrulama isteği (güvenlik gözden geçirme A7). */
    public record PinRequest(
            @NotBlank @Pattern(regexp = PinPolicy.PIN_PATTERN, message = "PIN 4-6 haneli rakam olmalı") String pin) {
    }

    public record CurrentUser(
            String username,
            String fullName,
            boolean mustChangePassword,
            boolean pinSetupRequired,
            boolean pinVerificationRequired,
            Instant pinLockedUntil,
            Set<String> roles,
            Set<String> permissions) {
    }
}
