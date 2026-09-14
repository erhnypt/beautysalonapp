package com.beautysalonapp.modules.finance.application;

import com.beautysalonapp.config.AppProperties;
import com.beautysalonapp.core.error.BusinessRuleException;
import com.beautysalonapp.modules.finance.domain.LiveFxRate;
import org.springframework.stereotype.Service;

/**
 * Döviz işlemi ekranı için anlık kur önerisi. Kalıcılık yok; yalnızca dış kaynağı
 * (allowlist'li) çağırır ve sonucu döner. Operatör kuru onaylayıp {@code /fx} ile işler.
 *
 * <p>Şu durumlarda {@code fx_rate_unavailable} ({@link BusinessRuleException}) döner:
 * canlı kur kapalı ({@code beautysalonapp.fx.enabled=false}), tam çevrimdışı mod açık,
 * geçersiz para birimi, ya da kaynak hatası (bkz. {@code YahooFxRateProvider}).
 */
@Service
public class FxRateService {

    private final FxRateProvider provider;
    private final AppProperties props;

    public FxRateService(FxRateProvider provider, AppProperties props) {
        this.provider = provider;
        this.props = props;
    }

    public LiveFxRate liveRate(String baseCurrency) {
        if (!props.getFx().isEnabled()) {
            throw new BusinessRuleException("fx_rate_unavailable",
                    "Canlı kur çekme kapalı. Kuru elle girin.");
        }
        if (props.isFullOfflineMode()) {
            throw new BusinessRuleException("fx_rate_unavailable",
                    "Tam çevrimdışı mod açık — canlı kur alınamaz. Kuru elle girin.");
        }
        if (baseCurrency == null || baseCurrency.isBlank()
                || baseCurrency.trim().equalsIgnoreCase("TRY")) {
            throw new BusinessRuleException("fx_rate_invalid_currency",
                    "Geçersiz para birimi: " + baseCurrency);
        }
        return provider.fetch(baseCurrency);
    }
}
