package com.beautysalonapp.modules.reporting;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /api/v1/dashboard/today} mali özet döndürür (ciro, tahsilat, gider, ödeme
 * dağılımı). REPORTING_VIEW yetkisi olmadan erişilememelidir — aksi halde her oturum
 * açan kullanıcı işletmenin günlük mali tablosunu görür.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DashboardApiTest {

    @Autowired MockMvc mvc;

    @Test
    void today_kimlik_dogrulamasiz_401() throws Exception {
        mvc.perform(get("/api/v1/dashboard/today")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = {"APPOINTMENT_VIEW", "PARTY_VIEW", "STOCK_VIEW"})
    void today_raporlama_yetkisi_olmayan_kullanici_403() throws Exception {
        mvc.perform(get("/api/v1/dashboard/today")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"REPORTING_VIEW"})
    void today_raporlama_yetkisiyle_200() throws Exception {
        mvc.perform(get("/api/v1/dashboard/today")).andExpect(status().isOk());
    }
}
