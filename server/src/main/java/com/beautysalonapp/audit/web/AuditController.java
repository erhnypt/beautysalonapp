package com.beautysalonapp.audit.web;

import com.beautysalonapp.audit.application.AuditService;
import com.beautysalonapp.audit.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/audit")
@PreAuthorize("hasAuthority('AUDIT_VIEW')")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    public record AuditView(Long id, Instant at, String actor, String action,
                            String entityType, String entityId, String summary) {
        static AuditView of(AuditLog a) {
            return new AuditView(a.getId(), a.getAt(), a.getActor(), a.getAction(),
                    a.getEntityType(), a.getEntityId(), a.getSummary());
        }
    }

    @GetMapping
    public Page<AuditView> search(
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return auditService.search(actor, entityType, from, to, PageRequest.of(page, Math.min(size, 200)))
                .map(AuditView::of);
    }
}
