package com.beautysalonapp.modules.notification;

import com.beautysalonapp.modules.notification.domain.ConsentPolicy;
import com.beautysalonapp.modules.notification.domain.NotificationChannel;
import com.beautysalonapp.modules.notification.domain.NotificationType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConsentPolicyTest {

    @Test
    void kampanya_iys_izinli_ve_kanal_onayi_gerekir() {
        // İYS IZINLI + SMS onayı → izin
        assertThat(ConsentPolicy.evaluate(NotificationType.KAMPANYA, NotificationChannel.SMS,
                true, false, "IZINLI", false).allowed()).isTrue();
        // İYS BILINMIYOR → red
        assertThat(ConsentPolicy.evaluate(NotificationType.KAMPANYA, NotificationChannel.SMS,
                true, false, "BILINMIYOR", false).allowed()).isFalse();
        // SMS onayı yok → red
        assertThat(ConsentPolicy.evaluate(NotificationType.KAMPANYA, NotificationChannel.SMS,
                false, true, "IZINLI", false).allowed()).isFalse();
    }

    @Test
    void bilgilendirme_iys_aramaz_ama_kanal_onayi_arar() {
        assertThat(ConsentPolicy.evaluate(NotificationType.RANDEVU_HATIRLATMA, NotificationChannel.SMS,
                true, false, "BILINMIYOR", false).allowed()).isTrue();
        assertThat(ConsentPolicy.evaluate(NotificationType.TAKSIT, NotificationChannel.SMS,
                false, false, "IZINLI", false).allowed()).isFalse();
    }

    @Test
    void gunluk_rapor_ic_kullanici_onay_aramaz() {
        assertThat(ConsentPolicy.evaluate(NotificationType.GUNLUK_RAPOR, NotificationChannel.EMAIL,
                false, false, "IZINSIZ", false).allowed()).isTrue();
    }

    @Test
    void anonim_kayda_gonderilmez() {
        assertThat(ConsentPolicy.evaluate(NotificationType.RANDEVU_HATIRLATMA, NotificationChannel.SMS,
                true, true, "IZINLI", true).allowed()).isFalse();
    }

    @Test
    void email_kanali_email_onayina_bakar() {
        assertThat(ConsentPolicy.evaluate(NotificationType.DOGUM_GUNU, NotificationChannel.EMAIL,
                true, false, "IZINLI", false).allowed()).isFalse();
        assertThat(ConsentPolicy.evaluate(NotificationType.DOGUM_GUNU, NotificationChannel.EMAIL,
                false, true, "IZINLI", false).allowed()).isTrue();
    }
}
