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
        // KVKK (CLAUDE.md #10): e-posta adresi PII — INFO'da maskeli.
        log.info("[EMAIL-NOOP] → {} | {} | {} karakter", mask(to), subject, body == null ? 0 : body.length());
        log.debug("[EMAIL-NOOP] tam alıcı={} gövde={}", to, body);
    }

    private static String mask(String addr) {
        if (addr == null) {
            return "***";
        }
        int at = addr.indexOf('@');
        if (at <= 1) {
            return "***" + (at >= 0 ? addr.substring(at) : "");
        }
        return addr.charAt(0) + "***" + addr.substring(at);
    }
}
