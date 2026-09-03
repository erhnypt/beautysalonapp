package com.beautysalonapp.outbound;

import com.beautysalonapp.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * Giden ağ trafiği için tek merkezî kontrol noktası (§2.1).
 *
 * <p>Uygulamadan çıkan HER HTTP isteği bu sınıftan geçmelidir. Allowlist dışına
 * çıkan istek {@link OutboundBlockedException} ile reddedilir. Yeni bir dış hedef
 * eklenecekse {@code beautysalonapp.outbound.allowlist} listesine yazılır ve PR
 * açıklamasında gerekçelendirilir.
 */
@Component
public class OutboundHttpGuard {

    private static final Logger log = LoggerFactory.getLogger(OutboundHttpGuard.class);

    private final AppProperties properties;

    public OutboundHttpGuard(AppProperties properties) {
        this.properties = properties;
    }

    public List<String> allowlist() {
        return properties.getOutbound().getAllowlist();
    }

    /**
     * Verilen hedef için isteğe izin var mı? Yoksa {@link OutboundBlockedException}.
     */
    public void check(String targetUrl) {
        if (targetUrl == null || targetUrl.isBlank()) {
            throw new OutboundBlockedException("(boş)", allowlist());
        }
        final URI uri;
        try {
            uri = URI.create(targetUrl.trim());
        } catch (IllegalArgumentException e) {
            throw new OutboundBlockedException(targetUrl, allowlist());
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("https")) {
            throw new OutboundBlockedException(targetUrl + " (yalnızca https)", allowlist());
        }
        boolean allowed = allowlist().stream().anyMatch(prefix -> matches(uri, prefix));
        if (!allowed) {
            log.warn("Giden istek REDDEDİLDİ: {}", uri.getHost());
            throw new OutboundBlockedException(targetUrl, allowlist());
        }
    }

    public boolean isAllowed(String targetUrl) {
        try {
            check(targetUrl);
            return true;
        } catch (OutboundBlockedException e) {
            return false;
        }
    }

    private static boolean matches(URI uri, String allowedPrefix) {
        try {
            URI allowed = URI.create(allowedPrefix.trim());
            if (allowed.getHost() == null) {
                return false;
            }
            boolean hostOk = uri.getHost() != null
                    && uri.getHost().equalsIgnoreCase(allowed.getHost());
            boolean pathOk = allowed.getPath() == null || allowed.getPath().isBlank()
                    || (uri.getPath() != null && uri.getPath().startsWith(allowed.getPath()));
            return hostOk && pathOk;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
