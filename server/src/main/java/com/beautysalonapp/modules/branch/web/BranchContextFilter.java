package com.beautysalonapp.modules.branch.web;

import com.beautysalonapp.core.context.BranchContextHolder;
import com.beautysalonapp.core.error.ApiError;
import com.beautysalonapp.modules.branch.infrastructure.BranchRepository;
import com.beautysalonapp.security.application.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final UserService userService;
    private final ObjectMapper objectMapper;

    public BranchContextFilter(BranchRepository branches, UserService userService, ObjectMapper objectMapper) {
        this.branches = branches;
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        if (!path.startsWith("/api/") || EXEMPT_PREFIXES.stream().anyMatch(path::startsWith)) {
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader(HEADER);
        Set<Long> allowed = currentUserAllowedBranches();

        if (header == null || header.isBlank()) {
            if (!allowed.isEmpty()) {
                // Kısıtlı kullanıcı başlık göndermedi (§B5): sessizce genel varsayılana
                // (branch 1) düşmek yerine kendi atanmış şubelerinden birini kullan —
                // aksi halde yeni kayıtları yanlış şubeye etiketleriz (veri bütünlüğü).
                // Atanmış şube sonradan silinmiş olabilir (user_branch, branch'e FK ile
                // bağlı değil) — header yolundaki aynı geçerlilik kontrolünü burada da
                // uyguluyoruz, aksi halde silinmiş bir şubeye sessizce yazılabilirdi.
                Long fallback = allowed.stream()
                        .sorted()
                        .filter(id -> branches.findById(id).filter(b -> !b.isDeleted()).isPresent())
                        .findFirst()
                        .orElse(null);
                if (fallback == null) {
                    writeError(response, path, HttpStatus.FORBIDDEN, "access_denied",
                            "Atanmış şube(ler) artık geçerli değil; yöneticinize başvurun.");
                    return;
                }
                proceedWithBranch(fallback, request, response, chain);
                return;
            }
            chain.doFilter(request, response);
            return;
        }

        Long branchId;
        try {
            branchId = Long.valueOf(header.trim());
        } catch (NumberFormatException e) {
            writeError(response, request.getRequestURI(), HttpStatus.BAD_REQUEST, "invalid_branch_header",
                    "Şube kimliği sayısal olmalı: " + header);
            return;
        }

        boolean valid = branches.findById(branchId).filter(b -> !b.isDeleted()).isPresent();
        if (!valid) {
            writeError(response, request.getRequestURI(), HttpStatus.BAD_REQUEST, "invalid_branch_header",
                    "Geçersiz veya silinmiş şube: " + branchId);
            return;
        }
        if (!allowed.isEmpty() && !allowed.contains(branchId)) {
            writeError(response, request.getRequestURI(), HttpStatus.FORBIDDEN, "access_denied",
                    "Bu şubeye erişim yetkiniz yok: " + branchId);
            return;
        }

        proceedWithBranch(branchId, request, response, chain);
    }

    /** Kimliği doğrulanmamış istekler için boş küme (kısıtlama yok) — 401/403 yetkilendirme aşamasında verilir. */
    private Set<Long> currentUserAllowedBranches() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return Set.of();
        }
        return userService.assignedBranches(auth.getName());
    }

    private void proceedWithBranch(Long branchId, HttpServletRequest request, HttpServletResponse response,
                                   FilterChain chain) throws ServletException, IOException {
        try {
            BranchContextHolder.set(branchId);
            chain.doFilter(request, response);
        } finally {
            BranchContextHolder.clear();
        }
    }

    private void writeError(HttpServletResponse response, String path, HttpStatus status, String code,
                            String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiError body = ApiError.of(status.value(), code, message, path);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
