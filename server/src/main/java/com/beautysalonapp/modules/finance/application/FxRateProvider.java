package com.beautysalonapp.modules.finance.application;

import com.beautysalonapp.modules.finance.domain.LiveFxRate;

/**
 * Anlık döviz kuru kaynağı. Tek uygulama: {@code YahooFxRateProvider}.
 * Başarısızlıkta {@link com.beautysalonapp.core.error.BusinessRuleException} fırlatır
 * (kod: {@code fx_rate_unavailable}) — çağıran ekran elle girişe döner.
 */
public interface FxRateProvider {

    /** @param baseCurrency 3 harfli yabancı para kodu, ör. {@code "USD"} (karşı birim daima TRY). */
    LiveFxRate fetch(String baseCurrency);

    String name();
}
