package com.beautysalonapp.modules.notification.infrastructure;

import com.beautysalonapp.modules.notification.application.SmsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Varsayılan SMS sağlayıcı: gerçekten göndermez, yalnızca loglar.
 * Çevrimdışı kurulumlar ve geliştirme için. Gerçek sağlayıcı
 * {@code beautysalonapp.notification.sms-provider=HTTP} ile devreye alınır.
 */
public class NoOpSmsProvider implements SmsProvider {

    private static final Logger log = LoggerFactory.getLogger(NoOpSmsProvider.class);

    @Override
    public String name() {
        return "noop-sms";
    }

    @Override
    public void send(String toPhone, String text) {
        // KVKK (CLAUDE.md #10): mesaj gövdesi müşteri adı içerebilir — INFO'da yalnızca
        // maskeli numara + uzunluk; tam gövde yalnızca DEBUG'da.
        log.info("[SMS-NOOP] → {} ({} karakter)", mask(toPhone), text == null ? 0 : text.length());
        log.debug("[SMS-NOOP] gövde → {} : {}", mask(toPhone), text);
    }

    private static String mask(String phone) {
        if (phone == null || phone.length() < 4) {
            return "***";
        }
        return "***" + phone.substring(phone.length() - 4);
    }
}
