package com.beautysalonapp.audit.application;

import com.beautysalonapp.audit.domain.AuditLog;
import com.beautysalonapp.audit.infrastructure.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;

/**
 * Denetim kaydı yazma/okuma. Yazma her zaman {@code REQUIRES_NEW} ile yapılır ki
 * ana işlem geri alınsa bile "denendi" kaydı kalabilsin (çağıran karar verir).
 */
@Service
public class AuditService {

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String entityType, Object entityId, String summary, String detail) {
        String actor = currentActor();
        Long branchId = 1L;
        String ip = currentIp();
        repository.save(new AuditLog(actor, action, entityType,
                entityId == null ? null : String.valueOf(entityId),
                branchId, summary, detail, ip));
    }

    public void record(String action, String entityType, Object entityId, String summary) {
        record(action, entityType, entityId, summary, null);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> search(String actor, String entityType, Instant from, Instant to, Pageable pageable) {
        return repository.search(emptyToNull(actor), emptyToNull(entityType), from, to, pageable);
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private String currentActor() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal()))
                ? auth.getName() : "system";
    }

    private String currentIp() {
        try {
            var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs != null ? attrs.getRequest().getRemoteAddr() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
