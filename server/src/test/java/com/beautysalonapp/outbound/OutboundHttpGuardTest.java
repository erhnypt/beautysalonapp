package com.beautysalonapp.outbound;

import com.beautysalonapp.config.AppProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Giden HTTP allowlist'i (§2.1, CLAUDE.md #1). Varsayılan allowlist: lisans sunucusu +
 * Yahoo Finance (canlı kur). Bunun dışındaki her hedef ve https olmayan her şema RED.
 */
class OutboundHttpGuardTest {

    private final OutboundHttpGuard guard = new OutboundHttpGuard(new AppProperties());

    @Test
    void lisans_sunucusu_izinli() {
        assertThat(guard.isAllowed("https://license.beautysalonapp.com/api/v1/heartbeat")).isTrue();
    }

    @Test
    void yahoo_finance_izinli() {
        assertThat(guard.isAllowed(
                "https://query1.finance.yahoo.com/v8/finance/chart/USDTRY=X?interval=1d")).isTrue();
    }

    @Test
    void allowlist_disi_host_reddedilir() {
        assertThat(guard.isAllowed("https://evil.example.com/steal")).isFalse();
        assertThatThrownBy(() -> guard.check("https://evil.example.com/steal"))
                .isInstanceOf(OutboundBlockedException.class);
    }

    @Test
    void https_olmayan_sema_reddedilir() {
        assertThat(guard.isAllowed("http://query1.finance.yahoo.com/")).isFalse();
    }

    @Test
    void benzer_gorunumlu_host_reddedilir() {
        assertThat(guard.isAllowed("https://query1.finance.yahoo.com.evil.com/")).isFalse();
    }
}
