package com.beautysalonapp.modules.notification.infrastructure;

import com.beautysalonapp.config.AppProperties;
import com.beautysalonapp.modules.notification.application.SmsProvider;
import com.beautysalonapp.outbound.GuardedRestClient;
import com.beautysalonapp.outbound.OutboundBlockedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Genel HTTP SMS sağlayıcı. Giden istek {@link com.beautysalonapp.outbound.OutboundHttpGuard}
 * allowlist'inden geçer — sağlayıcı hostu {@code beautysalonapp.outbound.allowlist}'e eklenmelidir (§2.1).
 * Gönderilen tek şey: alıcı numarası + mesaj metni; hiçbir iş verisi yok.
 */
public class HttpSmsProvider implements SmsProvider {

    private static final Logger log = LoggerFactory.getLogger(HttpSmsProvider.class);

    private final AppProperties props;
    private final GuardedRestClient http;

    public HttpSmsProvider(AppProperties props, GuardedRestClient http) {
        this.props = props;
        this.http = http;
    }

    @Override
    public String name() {
        return "http-sms";
    }

    @Override
    @SuppressWarnings("unchecked")
    public void send(String toPhone, String text) {
        var n = props.getNotification();
        if (n.getSmsHttpEndpoint() == null || n.getSmsHttpEndpoint().isBlank()) {
            throw new SmsException("SMS HTTP uç noktası yapılandırılmamış");
        }
        Map<String, Object> body = Map.of(
                "to", toPhone,
                "message", text,
                "from", n.getSmsSenderName() == null ? "" : n.getSmsSenderName(),
                "apiKey", n.getSmsApiKey() == null ? "" : n.getSmsApiKey());
        try {
            Map<String, Object> resp = http.post(n.getSmsHttpEndpoint(), body, Map.class);
            log.info("SMS gönderildi ({}): {}", n.getSmsHttpEndpoint(), resp);
        } catch (OutboundBlockedException e) {
            throw new SmsException("SMS hedefi allowlist'te değil: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new SmsException("SMS gönderilemedi: " + e.getMessage(), e);
        }
    }
}
