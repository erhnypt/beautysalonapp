package com.beautysalonapp.modules.notification;

import com.beautysalonapp.modules.notification.application.NotificationService;
import com.beautysalonapp.modules.notification.domain.NotificationChannel;
import com.beautysalonapp.modules.notification.domain.NotificationStatus;
import com.beautysalonapp.modules.notification.domain.NotificationType;
import com.beautysalonapp.modules.party.application.PartyService;
import com.beautysalonapp.modules.party.domain.IysStatus;
import com.beautysalonapp.modules.party.domain.PartyType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class NotificationServiceTest {

    @Autowired NotificationService notifications;
    @Autowired PartyService partyService;

    private long consentingCustomer() {
        long id = partyService.create(PartyType.MUSTERI, null, "Bildirim Test " + System.nanoTime(),
                null, null, "05551234567", "test@example.com", null, null).getId();
        partyService.update(id, null, null, null, "05551234567", "test@example.com", null, null,
                true, true, IysStatus.IZINLI);
        return id;
    }

    private long queued(NotificationStatus status) {
        return notifications.listQueue(status, PageRequest.of(0, 500)).getTotalElements();
    }

    @Test
    void izinli_musteriye_kuyruga_alinir_ve_gonderilir() {
        long id = consentingCustomer();
        long before = queued(NotificationStatus.SENT);

        notifications.enqueue(NotificationType.RANDEVU_HATIRLATMA, NotificationChannel.SMS, id, null,
                Map.of("tarih", "2026-10-01", "saat", "10:00", "hizmet", "Bakım"), null);
        assertThat(queued(NotificationStatus.PENDING)).isGreaterThanOrEqualTo(1);

        int processed = notifications.processNow();
        assertThat(processed).isGreaterThanOrEqualTo(1);
        // Paylaşılan DB + zamanlanmış işleyici nedeniyle >= kontrolü
        assertThat(queued(NotificationStatus.SENT)).isGreaterThanOrEqualTo(before + 1);
        assertThat(queued(NotificationStatus.PENDING)).isZero();
    }

    @Test
    void iys_izni_olmayana_kampanya_SKIPPED() {
        long id = partyService.create(PartyType.MUSTERI, null, "İzinsiz " + System.nanoTime(),
                null, null, "05559876543", null, null, null).getId();
        // sms_consent true ama iys BILINMIYOR
        partyService.update(id, null, null, null, "05559876543", null, null, null, true, false, null);

        long beforeSkipped = queued(NotificationStatus.SKIPPED);
        notifications.enqueue(NotificationType.KAMPANYA, NotificationChannel.SMS, id, null,
                Map.of("kampanya", "Yaz", "tarih", "2026-09-30"), null);

        assertThat(queued(NotificationStatus.SKIPPED)).isEqualTo(beforeSkipped + 1);
        assertThat(queued(NotificationStatus.PENDING)).isZero();
    }

    @Test
    void ayni_gun_ayni_sablon_iki_kez_kuyruga_girmez() {
        long id = consentingCustomer();
        var vars = Map.of("tarih", "2026-10-02", "saat", "11:00", "hizmet", "X");
        notifications.enqueue(NotificationType.RANDEVU_HATIRLATMA, NotificationChannel.SMS, id, null, vars, null);
        notifications.enqueue(NotificationType.RANDEVU_HATIRLATMA, NotificationChannel.SMS, id, null, vars, null);

        long count = notifications.listQueue(null, PageRequest.of(0, 500)).getContent().stream()
                .filter(q -> id == (q.getPartyId() == null ? -1 : q.getPartyId()))
                .filter(q -> q.getType() == NotificationType.RANDEVU_HATIRLATMA)
                .count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void sablon_yoksa_kuyruga_girmez() {
        long id = consentingCustomer();
        long before = queued(NotificationStatus.PENDING);
        // EMAIL kanalı için TAKSIT şablonu tohumlanmadı
        notifications.enqueue(NotificationType.TAKSIT, NotificationChannel.EMAIL, id, null,
                Map.of("tutar", "100"), null);
        assertThat(queued(NotificationStatus.PENDING)).isEqualTo(before);
    }

    @Test
    void test_gonderimi_noop_saglayici_ile_calisir() {
        var tpl = notifications.listTemplates().stream()
                .filter(t -> t.getType() == NotificationType.DOGUM_GUNU && t.getChannel() == NotificationChannel.SMS)
                .findFirst().orElseThrow();
        assertThat(notifications.sendTest(tpl.getId(), "05550000000")).contains("noop");
    }
}
