package com.beautysalonapp.modules.finance.web;

import com.beautysalonapp.modules.finance.application.FxRateService;
import com.beautysalonapp.modules.finance.domain.LiveFxRate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Döviz işlemi ekranındaki "Kur" alanına canlı kur önerisi (kullanıcı isteği).
 * Yazma değil — {@code FINANCE_VIEW} yeterli; kur kalıcı değildir, operatör {@code /fx}
 * ile onaylar. Kaynak hatası/çevrimdışı mod → {@code 4xx fx_rate_unavailable}, ekran
 * elle girişe döner.
 */
@RestController
@RequestMapping("/api/v1/finance/fx")
@PreAuthorize("hasAuthority('FINANCE_VIEW')")
public class FxRateController {

    private final FxRateService service;

    public FxRateController(FxRateService service) {
        this.service = service;
    }

    public record LiveRateView(String symbol, BigDecimal rate, Instant asOf, String source) {
        static LiveRateView of(LiveFxRate r) {
            return new LiveRateView(r.symbol(), r.rate(), r.asOf(), r.source());
        }
    }

    @GetMapping("/rate")
    public LiveRateView rate(@RequestParam(defaultValue = "USD") String base) {
        return LiveRateView.of(service.liveRate(base));
    }
}
