package com.beautysalonapp.core.error;

import java.time.Instant;
import java.util.List;

/**
 * Tüm hata yanıtlarının tek biçimi. {@code code} istemci i18n anahtarı,
 * {@code message} geliştirici/log içindir (PII içermez).
 */
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        List<FieldViolation> violations
) {
    public record FieldViolation(String field, String message) {}

    public static ApiError of(int status, String code, String message, String path) {
        return new ApiError(Instant.now(), status, code, message, path, List.of());
    }

    public static ApiError of(int status, String code, String message, String path, List<FieldViolation> violations) {
        return new ApiError(Instant.now(), status, code, message, path, violations);
    }
}
