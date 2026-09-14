package com.beautysalonapp.modules.finance.infrastructure;

import com.beautysalonapp.config.AppProperties;
import com.beautysalonapp.core.error.BusinessRuleException;
import com.beautysalonapp.modules.finance.application.FxRateProvider;
import com.beautysalonapp.modules.finance.domain.LiveFxRate;
import com.beautysalonapp.outbound.GuardedRestClient;
import com.beautysalonapp.outbound.OutboundBlockedException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;

/**
 * Yahoo Finance {@code chart} uç noktasından anlık kur çeker (kullanıcı seçimi).
 *
 * <p>{@code GET https://query1.finance.yahoo.com/v8/finance/chart/USDTRY=X}. Giden istek
 * {@link GuardedRestClient} → {@link com.beautysalonapp.outbound.OutboundHttpGuard}
 * allowlist'inden geçer (host {@code application.yml}'de tanımlı). Gönderilen veri yok;
 * yalnızca sembol URL'de. Resmî/sözleşmeli bir API değildir — biçim değişirse
 * {@code fx_rate_unavailable} döner ve ekran elle girişe düşer.
 */
@Component
public class YahooFxRateProvider implements FxRateProvider {

    private static final Logger log = LoggerFactory.getLogger(YahooFxRateProvider.class);
    private static final Map<String, String> HEADERS = Map.of("User-Agent", "BeautySalonApp");

    private final GuardedRestClient http;
    private final ObjectMapper json;
    private final AppProperties props;

    public YahooFxRateProvider(GuardedRestClient http, ObjectMapper json, AppProperties props) {
        this.http = http;
        this.json = json;
        this.props = props;
    }

    @Override
    public String name() {
        return "YAHOO";
    }

    @Override
    public LiveFxRate fetch(String baseCurrency) {
        String base = baseCurrency.trim().toUpperCase(Locale.ROOT);
        String symbol = base + "TRY";
        String url = props.getFx().getYahooBaseUrl() + symbol + "=X?interval=1d&range=1d";
        String body;
        try {
            body = http.get(url, HEADERS, String.class);
        } catch (OutboundBlockedException e) {
            throw unavailable("kur kaynağı allowlist'te değil", e);
        } catch (RuntimeException e) {
            throw unavailable("kaynağa ulaşılamadı (" + e.getClass().getSimpleName() + ")", e);
        }
        return parse(base, body);
    }

    /** Yahoo {@code chart} yanıtını çözer. Test edilebilirlik için ayrı. */
    LiveFxRate parse(String base, String body) {
        try {
            JsonNode meta = json.readTree(body).path("chart").path("result").path(0).path("meta");
            JsonNode price = meta.path("regularMarketPrice");
            if (!price.isNumber()) {
                throw unavailable("yanıt beklenmeyen biçimde (regularMarketPrice yok)", null);
            }
            BigDecimal rate = new BigDecimal(price.asText()).setScale(6, RoundingMode.HALF_UP);
            if (rate.signum() <= 0) {
                throw unavailable("geçersiz kur değeri: " + rate, null);
            }
            long epoch = meta.path("regularMarketTime").asLong(0L);
            Instant asOf = epoch > 0 ? Instant.ofEpochSecond(epoch) : Instant.now();
            return new LiveFxRate(base + "/TRY", rate, asOf, "Yahoo Finance");
        } catch (BusinessRuleException e) {
            throw e;
        } catch (Exception e) {
            throw unavailable("yanıt çözümlenemedi", e);
        }
    }

    private BusinessRuleException unavailable(String detail, Throwable cause) {
        log.warn("Canlı kur alınamadı: {}", detail);
        BusinessRuleException ex = new BusinessRuleException("fx_rate_unavailable",
                "Canlı kur alınamadı (" + detail + "). Lütfen kuru elle girin.");
        if (cause != null) {
            ex.initCause(cause);
        }
        return ex;
    }
}
