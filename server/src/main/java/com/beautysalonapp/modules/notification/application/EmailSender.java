package com.beautysalonapp.modules.notification.application;

/** E-posta gönderici soyutlaması (§10.8). SMTP yapılandırılmazsa NoOp kullanılır. */
public interface EmailSender {

    String name();

    void send(String to, String subject, String body);

    class EmailException extends RuntimeException {
        public EmailException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
