package com.beautysalonapp.modules.notification;

import com.beautysalonapp.modules.notification.domain.NotificationChannel;
import com.beautysalonapp.modules.notification.domain.NotificationTemplate;
import com.beautysalonapp.modules.notification.domain.NotificationType;
import com.beautysalonapp.modules.notification.infrastructure.NotificationTemplateRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** İlk açılışta varsayılan bildirim şablonları (Türkçe, değişkenli). */
@Component
@Order(40)
public class NotificationDefaults implements ApplicationRunner {

    private final NotificationTemplateRepository templates;

    public NotificationDefaults(NotificationTemplateRepository templates) {
        this.templates = templates;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seed(NotificationType.DOGUM_GUNU, NotificationChannel.SMS, null,
                "Sevgili {ad}, doğum gününüz kutlu olsun! Bu ay size özel indirimlerimizden yararlanmayı unutmayın.");
        seed(NotificationType.DOGUM_GUNU, NotificationChannel.EMAIL, "Doğum Gününüz Kutlu Olsun",
                "Sevgili {ad},\n\nDoğum gününüzü en içten dileklerimizle kutlarız.\n\nSağlıkla kalın.");
        seed(NotificationType.YILDONUMU, NotificationChannel.SMS, null,
                "Sevgili {ad}, evlilik yıldönümünüz kutlu olsun!");
        seed(NotificationType.RANDEVU_HATIRLATMA, NotificationChannel.SMS, null,
                "{ad} için hatırlatma: {tarih} {saat} - {hizmet} randevunuz var. İyi günler dileriz.");
        seed(NotificationType.RANDEVU_HATIRLATMA, NotificationChannel.EMAIL, "Randevu Hatırlatması",
                "Sayın {ad},\n\n{tarih} {saat} tarihinde {hizmet} randevunuz bulunmaktadır.");
        seed(NotificationType.TAKSIT, NotificationChannel.SMS, null,
                "Sayın {ad}, {sozlesme} sözleşmenizin {taksitNo}. taksiti ({tutar} TL) {vade} tarihinde ödenmelidir.");
        seed(NotificationType.BORC, NotificationChannel.SMS, null,
                "Sayın {ad}, güncel bakiyeniz {tutar} TL'dir. Bilginize sunarız.");
        seed(NotificationType.KAMPANYA, NotificationChannel.SMS, null,
                "Sevgili {ad}, {kampanya} kampanyamızdan {tarih} tarihine kadar yararlanabilirsiniz!");
        seed(NotificationType.GUNLUK_RAPOR, NotificationChannel.EMAIL, "Gün Sonu Raporu - {tarih}",
                "{rapor}");
    }

    private void seed(NotificationType type, NotificationChannel channel, String subject, String body) {
        if (templates.findByBranchIdAndTypeAndChannel(1L, type, channel).isEmpty()) {
            templates.save(new NotificationTemplate(type, channel, subject, body));
        }
    }
}
