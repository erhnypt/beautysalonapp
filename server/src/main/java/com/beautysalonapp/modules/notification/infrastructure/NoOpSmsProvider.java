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
        log.info("[SMS-NOOP] → {} : {}", mask(toPhone), text);
    }

    private static String mask(String phone) {
        if (phone == null || phone.length() < 4) {
            return "***";
        }
        return "***" + phone.substring(phone.length() - 4);
    }
}
