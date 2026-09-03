package com.beautysalonapp.modules.notification.application;

/**
 * SMS sağlayıcı soyutlaması (§10.9). NetGSM / İletimerkezi / Twilio adaptörleri
 * bu arayüzü uygular; değiştirilebilir olmalıdır.
 */
public interface SmsProvider {

    String name();

    /** Tek bir SMS gönderir. Başarısızlıkta {@link SmsException} fırlatır. */
    void send(String toPhone, String text);

    /** Sağlayıcı bakiyesi (kredi). Bilinmiyorsa null. */
    default Integer creditBalance() {
        return null;
    }

    class SmsException extends RuntimeException {
        public SmsException(String message) {
            super(message);
        }

        public SmsException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
