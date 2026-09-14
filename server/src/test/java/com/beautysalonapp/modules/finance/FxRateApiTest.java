package com.beautysalonapp.modules.finance;

import com.beautysalonapp.core.error.BusinessRuleException;
import com.beautysalonapp.modules.finance.application.FxRateProvider;
import com.beautysalonapp.modules.finance.domain.LiveFxRate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/v1/finance/fx/rate} — canlı kur önerisi. Gerçek Yahoo çağrısı yerine
 * {@code @Primary} bir stub sağlayıcı kullanılır (ağ yok).
 */
@SpringBootTest
@AutoConfigureMockMvc
class FxRateApiTest {

    @TestConfiguration
    static class StubConfig {
        @Bean
        @Primary
        FxRateProvider stubFxRateProvider() {
            return new FxRateProvider() {
                @Override
                public LiveFxRate fetch(String baseCurrency) {
                    if ("FAIL".equalsIgnoreCase(baseCurrency)) {
                        throw new BusinessRuleException("fx_rate_unavailable", "kaynak yanıt vermedi");
                    }
                    return new LiveFxRate(baseCurrency.toUpperCase() + "/TRY",
                            new BigDecimal("41.234500"), Instant.parse("2026-09-10T08:00:00Z"), "Yahoo Finance");
                }

                @Override
                public String name() {
                    return "STUB";
                }
            };
        }
    }

    @Autowired MockMvc mvc;

    @Test
    @WithMockUser(authorities = "FINANCE_VIEW")
    void yetkili_kullanici_canli_kuru_alir() throws Exception {
        mvc.perform(get("/api/v1/finance/fx/rate").param("base", "USD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("USD/TRY"))
                .andExpect(jsonPath("$.rate").value(41.2345))
                .andExpect(jsonPath("$.source").value("Yahoo Finance"));
    }

    @Test
    @WithMockUser(authorities = "PARTY_VIEW")
    void finance_view_yoksa_403() throws Exception {
        mvc.perform(get("/api/v1/finance/fx/rate")).andExpect(status().isForbidden());
    }

    @Test
    void kimlik_dogrulamasiz_401() throws Exception {
        mvc.perform(get("/api/v1/finance/fx/rate")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "FINANCE_VIEW")
    void kaynak_hatasi_422_fx_rate_unavailable() throws Exception {
        mvc.perform(get("/api/v1/finance/fx/rate").param("base", "FAIL"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("fx_rate_unavailable"));
    }
}
