package com.beautysalonapp.security.web;

import jakarta.validation.constraints.NotBlank;

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

    public record CurrentUser(
            String username,
            String fullName,
            boolean mustChangePassword,
            Set<String> roles,
            Set<String> permissions) {
    }
}
