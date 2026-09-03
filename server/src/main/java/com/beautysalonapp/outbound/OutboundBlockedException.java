package com.beautysalonapp.outbound;

import java.util.List;

/**
 * Allowlist dışına giden HTTP isteği engellendiğinde fırlatılır.
 */
public class OutboundBlockedException extends RuntimeException {

    public OutboundBlockedException(String target, List<String> allowlist) {
        super("Giden istek engellendi: " + target + " — izinli hedefler: " + allowlist);
    }
}
