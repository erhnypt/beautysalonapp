package com.beautysalonapp.licensing;

import com.beautysalonapp.core.error.ApiError;
import com.beautysalonapp.licensing.application.LicenseService;
import com.beautysalonapp.licensing.application.LicenseSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Lisans kısıtlaması uygulaması (§6.4).
 *
 * <p>READ_ONLY / LOCKED / TAMPERED durumlarında veri değiştiren istekleri (POST/PUT/PATCH/DELETE)
 * HTTP 423 ile reddeder. Şu yollar HER DURUMDA açıktır: kimlik doğrulama, lisans yükleme,
 * yedekleme ve veri dışa aktarma (§6.4 etik kural — müşteri kendi verisine her zaman erişir).
 */
@Component
@Order(1)
public class LicenseEnforcementFilter extends OncePerRequestFilter {

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    /** READ_ONLY/LOCKED durumunda bile izin verilen yazma yolu önekleri. */
    private static final Set<String> ALWAYS_ALLOWED_PREFIXES = Set.of(
            "/api/v1/auth/",
            "/api/v1/license/",
            "/api/v1/backup/",
            "/api/v1/export/",
            "/actuator/"
    );

    private final LicenseService licenseService;
    private final ObjectMapper objectMapper;

    public LicenseEnforcementFilter(LicenseService licenseService, ObjectMapper objectMapper) {
        this.licenseService = licenseService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String method = request.getMethod();
        String path = request.getRequestURI();

        if (WRITE_METHODS.contains(method) && path.startsWith("/api/") && !isAlwaysAllowed(path)) {
            LicenseSnapshot snap = licenseService.snapshot();
            if (snap.writesBlocked()) {
                writeBlocked(response, path, snap);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private boolean isAlwaysAllowed(String path) {
        return ALWAYS_ALLOWED_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private void writeBlocked(HttpServletResponse response, String path, LicenseSnapshot snap) throws IOException {
        response.setStatus(HttpStatus.LOCKED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        String message = snap.message() != null ? snap.message()
                : "Lisans durumu nedeniyle yeni işlem yapılamıyor (" + snap.status() + ")";
        ApiError body = ApiError.of(HttpStatus.LOCKED.value(), "license_restricted", message, path);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
