package com.beautysalonapp.modules.finance;

import com.beautysalonapp.config.AppProperties;
import com.beautysalonapp.core.error.BusinessRuleException;
import com.beautysalonapp.modules.finance.application.FxRateProvider;
import com.beautysalonapp.modules.finance.application.FxRateService;
import com.beautysalonapp.modules.finance.domain.LiveFxRate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FxRateServiceTest {

    private final LiveFxRate sample =
            new LiveFxRate("USD/TRY", new BigDecimal("41.234500"), Instant.parse("2026-09-10T08:00:00Z"), "Yahoo Finance");
    private final FxRateProvider okProvider = new FxRateProvider() {
        public LiveFxRate fetch(String baseCurrency) { return sample; }
        public String name() { return "STUB"; }
    };

    private static AppProperties props(boolean fxEnabled, boolean offline) {
        AppProperties p = new AppProperties();
        p.getFx().setEnabled(fxEnabled);
        p.setFullOfflineMode(offline);
        return p;
    }

    @Test
    void mutlu_yol_saglayicinin_sonucunu_doner() {
        LiveFxRate r = new FxRateService(okProvider, props(true, false)).liveRate("USD");
        assertThat(r.rate()).isEqualByComparingTo("41.2345");
        assertThat(r.symbol()).isEqualTo("USD/TRY");
    }

    @Test
    void canli_kur_kapaliysa_reddedilir() {
        assertThatThrownBy(() -> new FxRateService(okProvider, props(false, false)).liveRate("USD"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("kapalı");
    }

    @Test
    void tam_cevrimdisi_modda_reddedilir() {
        assertThatThrownBy(() -> new FxRateService(okProvider, props(true, true)).liveRate("USD"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("çevrimdışı");
    }

    @Test
    void gecersiz_para_birimi_reddedilir() {
        FxRateService svc = new FxRateService(okProvider, props(true, false));
        assertThatThrownBy(() -> svc.liveRate("TRY")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> svc.liveRate(" ")).isInstanceOf(BusinessRuleException.class);
    }
}
