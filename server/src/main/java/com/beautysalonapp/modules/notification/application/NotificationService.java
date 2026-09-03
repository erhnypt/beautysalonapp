package com.beautysalonapp.modules.notification.application;

import com.beautysalonapp.audit.application.AuditService;
import com.beautysalonapp.config.AppProperties;
import com.beautysalonapp.core.error.BusinessRuleException;
import com.beautysalonapp.core.error.NotFoundException;
import com.beautysalonapp.modules.notification.domain.ConsentPolicy;
import com.beautysalonapp.modules.notification.domain.NotificationChannel;
import com.beautysalonapp.modules.notification.domain.NotificationQueueItem;
import com.beautysalonapp.modules.notification.domain.NotificationStatus;
import com.beautysalonapp.modules.notification.domain.NotificationTemplate;
import com.beautysalonapp.modules.notification.domain.NotificationType;
import com.beautysalonapp.modules.notification.domain.TemplateRenderer;
import com.beautysalonapp.modules.notification.infrastructure.NotificationQueueRepository;
import com.beautysalonapp.modules.notification.infrastructure.NotificationTemplateRepository;
import com.beautysalonapp.modules.party.application.PartyDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationTemplateRepository templates;
    private final NotificationQueueRepository queue;
    private final PartyDirectory partyDirectory;
    private final SmsProvider smsProvider;
    private final EmailSender emailSender;
    private final AppProperties props;
    private final AuditService audit;

    public NotificationService(NotificationTemplateRepository templates, NotificationQueueRepository queue,
                              PartyDirectory partyDirectory, SmsProvider smsProvider, EmailSender emailSender,
                              AppProperties props, AuditService audit) {
        this.templates = templates;
        this.queue = queue;
        this.partyDirectory = partyDirectory;
        this.smsProvider = smsProvider;
        this.emailSender = emailSender;
        this.props = props;
        this.audit = audit;
    }

    // --- şablonlar ---------------------------------------------------

    @Transactional(readOnly = true)
    public List<NotificationTemplate> listTemplates() {
        return templates.findAllByDeletedFalseOrderByTypeAscChannelAsc();
    }

    public NotificationTemplate upsertTemplate(NotificationType type, NotificationChannel channel,
                                               String subject, String body, boolean active) {
        NotificationTemplate tpl = templates.findByBranchIdAndTypeAndChannel(1L, type, channel)
                .orElseGet(() -> new NotificationTemplate(type, channel, subject, body));
        tpl.setSubject(subject);
        tpl.setBody(body);
        tpl.setActive(active);
        return templates.save(tpl);
    }

    // --- kuyruğa alma ---------------------------------------------

    /**
     * Bir bildirimi kuyruğa alır. İzin/çevrimdışı/şablon kontrolleri burada yapılır;
     * gönderim {@link #processQueue()} tarafından asenkron yürütülür.
     */
    public void enqueue(NotificationType type, NotificationChannel channel, Long partyId,
                        String toOverride, Map<String, String> vars, Instant scheduledAt) {
        if (!props.getNotification().isEnabled() || props.isFullOfflineMode()) {
            log.debug("Bildirim modülü kapalı/çevrimdışı — atlandı: {}", type);
            return;
        }
        Instant when = scheduledAt == null ? Instant.now() : scheduledAt;
        Map<String, String> allVars = vars == null ? new HashMap<>() : new HashMap<>(vars);

        String to = toOverride;
        if (partyId != null) {
            var maybe = partyDirectory.contact(partyId);
            if (maybe.isEmpty()) {
                return;
            }
            var c = maybe.get();
            allVars.putIfAbsent("ad", c.displayName());
            var decision = ConsentPolicy.evaluate(type, channel, c.smsConsent(), c.emailConsent(),
                    c.iysStatus(), c.anonymized());
            if (!decision.allowed()) {
                persistSkipped(type, channel, partyId, "-", when, decision.reason());
                return;
            }
            to = channel == NotificationChannel.SMS ? c.phone() : c.email();
        }
        if (to == null || to.isBlank()) {
            return; // adres yok — sessizce atla
        }

        NotificationTemplate tpl = templates
                .findByTypeAndChannelAndActiveTrueAndDeletedFalse(type, channel)
                .orElse(null);
        if (tpl == null) {
            log.debug("Aktif şablon yok: {}/{}", type, channel);
            return;
        }
        String subject = TemplateRenderer.render(tpl.getSubject(), allVars);
        String body = TemplateRenderer.render(tpl.getBody(), allVars);

        String dedup = type + "|" + channel + "|" + (partyId != null ? partyId : to) + "|"
                + LocalDate.ofInstant(when, zone());
        if (queue.existsByDedupKey(dedup)) {
            return;
        }
        queue.save(new NotificationQueueItem(partyId, to, channel, type, subject, body, when, dedup));
    }

    private void persistSkipped(NotificationType type, NotificationChannel channel, Long partyId,
                                String to, Instant when, String reason) {
        String dedup = type + "|" + channel + "|" + (partyId != null ? partyId : to) + "|"
                + LocalDate.ofInstant(when, zone()) + "|skip";
        if (queue.existsByDedupKey(dedup)) {
            return;
        }
        NotificationQueueItem item = new NotificationQueueItem(partyId, to, channel, type,
                "", "(gönderilmedi)", when, dedup);
        item.skip(reason);
        queue.save(item);
    }

    // --- gönderim -----------------------------------------------

    @Scheduled(fixedDelayString = "PT60S", initialDelayString = "PT30S")
    public void processQueue() {
        if (!props.getNotification().isEnabled() || props.isFullOfflineMode()) {
            return;
        }
        List<NotificationQueueItem> due = queue.dueNow(Instant.now(), PageRequest.of(0, 50));
        for (NotificationQueueItem item : due) {
            deliver(item);
        }
    }

    void deliver(NotificationQueueItem item) {
        try {
            if (item.getChannel() == NotificationChannel.SMS) {
                smsProvider.send(item.getToAddress(), item.getBody());
            } else {
                emailSender.send(item.getToAddress(), item.getSubject(), item.getBody());
            }
            item.markSent();
            queue.save(item);
        } catch (RuntimeException e) {
            item.markFailure(e.getMessage(), props.getNotification().getMaxAttempts());
            queue.save(item);
            if (item.getStatus() == NotificationStatus.FAILED) {
                log.warn("Bildirim {} kalıcı olarak başarısız: {}", item.getId(), e.getMessage());
                audit.record("NOTIFICATION_FAILED", "Notification", item.getId(),
                        item.getType() + "/" + item.getChannel() + " başarısız: " + e.getMessage());
            }
        }
    }

    /** Şablonu örnek değişkenlerle işleyip doğrudan (kuyruksuz) gönderir — ayar testi. */
    public String sendTest(long templateId, String to) {
        NotificationTemplate tpl = templates.findById(templateId)
                .orElseThrow(() -> new NotFoundException("Şablon", templateId));
        Map<String, String> sample = Map.of("ad", "Örnek Müşteri", "tarih",
                LocalDate.now().toString(), "tutar", "1.234,56 TL");
        String subject = TemplateRenderer.render(tpl.getSubject(), sample);
        String body = TemplateRenderer.render(tpl.getBody(), sample);
        if (tpl.getChannel() == NotificationChannel.SMS) {
            smsProvider.send(to, body);
        } else {
            emailSender.send(to, subject, body);
        }
        audit.record("NOTIFICATION_TEST", "Notification", templateId, "Test bildirimi: " + to);
        return "Gönderildi (" + (tpl.getChannel() == NotificationChannel.SMS ? smsProvider.name() : emailSender.name()) + ")";
    }

    // --- listeleme -------------------------------------------

    @Transactional(readOnly = true)
    public Page<NotificationQueueItem> listQueue(NotificationStatus status, Pageable pageable) {
        return status == null
                ? queue.findAllByOrderByScheduledAtDesc(pageable)
                : queue.findAllByStatusOrderByScheduledAtDesc(status, pageable);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> queueStats() {
        Map<String, Long> m = new java.util.LinkedHashMap<>();
        for (NotificationStatus s : NotificationStatus.values()) {
            m.put(s.name(), queue.countByStatus(s));
        }
        return m;
    }

    /** Bakım: kuyruğu hemen işle. */
    public int processNow() {
        List<NotificationQueueItem> due = queue.dueNow(Instant.now(), PageRequest.of(0, 200));
        due.forEach(this::deliver);
        return due.size();
    }

    public Integer smsCredit() {
        try {
            return smsProvider.creditBalance();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private ZoneId zone() {
        try {
            return ZoneId.of(props.getDisplayZone());
        } catch (RuntimeException e) {
            return ZoneId.systemDefault();
        }
    }
}
