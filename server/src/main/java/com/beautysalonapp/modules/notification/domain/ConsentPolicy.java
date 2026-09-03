package com.beautysalonapp.modules.notification.domain;

/**
 * KVKK / İYS izin kontrolü (§8.3, §10.8). Saf domain — hangi bildirimin hangi
 * alıcıya gönderilebileceğine karar verir. Sonuç {@link Decision}.
 */
public final class ConsentPolicy {

    private ConsentPolicy() {
    }

    /** {@code iysStatus} değerleri party modülüyle aynı: IZINLI | IZINSIZ | BILINMIYOR. */
    public static Decision evaluate(NotificationType type, NotificationChannel channel,
                                    boolean smsConsent, boolean emailConsent,
                                    String iysStatus, boolean anonymized) {
        if (anonymized) {
            return Decision.deny("Anonimleştirilmiş kayda bildirim gönderilemez");
        }
        if (type.internalOnly()) {
            return Decision.allow(); // yönetici e-postası — müşteri onayı aranmaz
        }
        boolean channelConsent = channel == NotificationChannel.SMS ? smsConsent : emailConsent;
        if (!channelConsent) {
            return Decision.deny(channel + " kanalı için onay yok");
        }
        if (type.commercial() && !"IZINLI".equalsIgnoreCase(iysStatus)) {
            return Decision.deny("Ticari ileti için İYS izni yok (iys_status=" + iysStatus + ")");
        }
        return Decision.allow();
    }

    public record Decision(boolean allowed, String reason) {
        static Decision allow() {
            return new Decision(true, null);
        }

        static Decision deny(String reason) {
            return new Decision(false, reason);
        }
    }
}
