package com.beautysalonapp.modules.notification.infrastructure;

import com.beautysalonapp.modules.notification.application.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Varsayılan e-posta gönderici: yalnızca loglar. {@code spring.mail.host} ayarlanınca SMTP devreye girer. */
public class NoOpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(NoOpEmailSender.class);

    @Override
    public String name() {
        return "noop-email";
    }

    @Override
    public void send(String to, String subject, String body) {
        log.info("[EMAIL-NOOP] → {} | {} | {} karakter", to, subject, body == null ? 0 : body.length());
    }
}
