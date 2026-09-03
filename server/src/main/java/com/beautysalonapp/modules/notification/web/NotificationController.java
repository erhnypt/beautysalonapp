package com.beautysalonapp.modules.notification.web;

import com.beautysalonapp.modules.notification.NotificationTriggers;
import com.beautysalonapp.modules.notification.application.NotificationService;
import com.beautysalonapp.modules.notification.domain.NotificationChannel;
import com.beautysalonapp.modules.notification.domain.NotificationQueueItem;
import com.beautysalonapp.modules.notification.domain.NotificationStatus;
import com.beautysalonapp.modules.notification.domain.NotificationTemplate;
import com.beautysalonapp.modules.notification.domain.NotificationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@PreAuthorize("hasAuthority('NOTIFICATION_VIEW')")
public class NotificationController {

    private final NotificationService service;
    private final NotificationTriggers triggers;

    public NotificationController(NotificationService service, NotificationTriggers triggers) {
        this.service = service;
        this.triggers = triggers;
    }

    public record TemplateView(long id, NotificationType type, NotificationChannel channel,
                               String subject, String body, boolean active) {
        static TemplateView of(NotificationTemplate t) {
            return new TemplateView(t.getId(), t.getType(), t.getChannel(), t.getSubject(), t.getBody(), t.isActive());
        }
    }

    public record UpsertTemplateRequest(@NotNull NotificationType type, @NotNull NotificationChannel channel,
                                        String subject, @NotBlank String body, boolean active) {}

    public record QueueView(long id, Long partyId, String toAddress, NotificationChannel channel,
                            NotificationType type, NotificationStatus status, int attempts,
                            Instant scheduledAt, Instant sentAt, String lastError) {
        static QueueView of(NotificationQueueItem q) {
            return new QueueView(q.getId(), q.getPartyId(), mask(q.getToAddress()), q.getChannel(), q.getType(),
                    q.getStatus(), q.getAttempts(), q.getScheduledAt(), q.getSentAt(), q.getLastError());
        }

        private static String mask(String s) {
            if (s == null || s.length() < 4) return "***";
            return s.contains("@")
                    ? s.charAt(0) + "***" + s.substring(s.indexOf('@'))
                    : "***" + s.substring(s.length() - 4);
        }
    }

    public record TestRequest(@NotNull Long templateId, @NotBlank String to) {}

    public record CampaignRequest(@NotNull Long templateId, String scheduledAt) {}

    @GetMapping("/templates")
    public List<TemplateView> templates() {
        return service.listTemplates().stream().map(TemplateView::of).toList();
    }

    @PostMapping("/templates")
    @PreAuthorize("hasAuthority('NOTIFICATION_TEMPLATE_EDIT')")
    public TemplateView upsertTemplate(@Valid @RequestBody UpsertTemplateRequest r) {
        return TemplateView.of(service.upsertTemplate(r.type(), r.channel(), r.subject(), r.body(), r.active()));
    }

    @GetMapping("/queue")
    public Page<QueueView> queue(@RequestParam(required = false) NotificationStatus status,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "50") int size) {
        return service.listQueue(status, PageRequest.of(page, Math.min(size, 200))).map(QueueView::of);
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return Map.of("queue", service.queueStats(), "smsCredit", String.valueOf(service.smsCredit()));
    }

    @PostMapping("/test")
    @PreAuthorize("hasAuthority('NOTIFICATION_SEND')")
    public String test(@Valid @RequestBody TestRequest r) {
        return service.sendTest(r.templateId(), r.to());
    }

    @PostMapping("/process-now")
    @PreAuthorize("hasAuthority('NOTIFICATION_SEND')")
    public Map<String, Integer> processNow() {
        return Map.of("processed", service.processNow());
    }

    @GetMapping("/reminder-list")
    public String reminderList(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return triggers.reminderList(date == null ? LocalDate.now() : date);
    }
}
