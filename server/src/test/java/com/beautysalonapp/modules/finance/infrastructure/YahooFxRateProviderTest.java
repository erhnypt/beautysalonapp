package com.beautysalonapp.modules.finance.infrastructure;

import com.beautysalonapp.config.AppProperties;
import com.beautysalonapp.core.error.BusinessRuleException;
import com.beautysalonapp.modules.finance.domain.LiveFxRate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YahooFxRateProviderTest {

    private final YahooFxRateProvider provider =
            new YahooFxRateProvider(null, new ObjectMapper(), new AppProperties());

    @Test
    void yahoo_chart_yaniti_cozumlenir() {
        String body = """
                {"chart":{"result":[{"meta":{
                  "currency":"TRY","symbol":"USDTRY=X",
                  "regularMarketPrice":41.2345,"regularMarketTime":1757491200
                }}],"error":null}}""";

        LiveFxRate r = provider.parse("USD", body);

        assertThat(r.symbol()).isEqualTo("USD/TRY");
        assertThat(r.rate()).isEqualByComparingTo("41.2345");
        assertThat(r.rate().scale()).isEqualTo(6);
        assertThat(r.source()).isEqualTo("Yahoo Finance");
        assertThat(r.asOf()).isEqualTo(Instant.ofEpochSecond(1757491200L));
    }

    @Test
    void fiyat_yoksa_fx_rate_unavailable() {
        assertThatThrownBy(() -> provider.parse("USD", "{\"chart\":{\"result\":[{\"meta\":{}}]}}"))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode()).isEqualTo("fx_rate_unavailable"));
    }

    @Test
    void bozuk_govde_fx_rate_unavailable() {
        assertThatThrownBy(() -> provider.parse("USD", "<html>rate limited</html>"))
                .isInstanceOf(BusinessRuleException.class);
    }
}
