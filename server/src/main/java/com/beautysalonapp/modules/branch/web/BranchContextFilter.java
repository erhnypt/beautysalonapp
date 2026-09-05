package com.beautysalonapp.modules.branch.web;

import com.beautysalonapp.core.context.BranchContextHolder;
import com.beautysalonapp.core.error.ApiError;
import com.beautysalonapp.modules.branch.infrastructure.BranchRepository;
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
 * Faz 8 tam şube izolasyonu (ADR 0006): isteğin {@code X-Branch-Id} başlığını okuyup
 * geçerliyse istek süresince {@link BranchContextHolder}'a yazar; istek bitince mutlaka
 * temizler (thread pool'da sızıntı olmasın diye {@code finally}).
 *
 * <p>Başlık gönderilmezse davranış **tamamen değişmez**: bağlam {@code null} kalır,
 * {@link com.beautysalonapp.core.domain.BaseEntity} sınıf varsayılanı ({@code 1L}) geçerli
 * olur — eski istemciler ve arka plan işleri etkilenmez. Başlık gönderilip de geçersiz/
 * bulunamayan/silinmiş bir şubeyi işaret ediyorsa istek 400 ile reddedilir (sessizce yanlış
 * şubeye yazmaktansa açık hata tercih edilir).
 */
@Component
@Order(2)
public class BranchContextFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Branch-Id";

    /** CLAUDE.md #9: LOCKED durumda bile dışa aktarma/yedekleme çalışmalı — geçersiz/eski bir
     * X-Branch-Id başlığı bu yolları engellememeli (bkz. LicenseEnforcementFilter). */
    private static final Set<String> EXEMPT_PREFIXES = Set.of(
            "/api/v1/backup/",
            "/api/v1/export/"
    );

    private final BranchRepository branches;
    private final ObjectMapper objectMapper;

    public BranchContextFilter(BranchRepository branches, ObjectMapper objectMapper) {
        this.branches = branches;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader(HEADER);
        String path = request.getRequestURI();
        if (header == null || header.isBlank() || !path.startsWith("/api/")
                || EXEMPT_PREFIXES.stream().anyMatch(path::startsWith)) {
            chain.doFilter(request, response);
            return;
        }

        Long branchId;
        try {
            branchId = Long.valueOf(header.trim());
        } catch (NumberFormatException e) {
            writeInvalid(response, request.getRequestURI(), "Şube kimliği sayısal olmalı: " + header);
            return;
        }

        boolean valid = branches.findById(branchId).filter(b -> !b.isDeleted()).isPresent();
        if (!valid) {
            writeInvalid(response, request.getRequestURI(), "Geçersiz veya silinmiş şube: " + branchId);
            return;
        }

        try {
            BranchContextHolder.set(branchId);
            chain.doFilter(request, response);
        } finally {
            BranchContextHolder.clear();
        }
    }

    private void writeInvalid(HttpServletResponse response, String path, String message) throws IOException {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiError body = ApiError.of(HttpStatus.BAD_REQUEST.value(), "invalid_branch_header", message, path);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
