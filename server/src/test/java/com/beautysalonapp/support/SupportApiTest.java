package com.beautysalonapp.support;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SupportApiTest {

    @Autowired MockMvc mvc;

    @Test
    void kimlik_dogrulamasiz_401() throws Exception {
        mvc.perform(get("/api/v1/support/diagnostics")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "SETTINGS_VIEW")
    void settings_view_ile_tani_ekrani_gorulur() throws Exception {
        mvc.perform(get("/api/v1/support/diagnostics")).andExpect(status().isOk())
                .andExpect(jsonPath("$.appVersion").exists())
                .andExpect(jsonPath("$.dbConnected").value(true));
    }

    @Test
    @WithMockUser(authorities = "SETTINGS_VIEW")
    void settings_view_yetkisi_destek_paketine_yetmez() throws Exception {
        mvc.perform(get("/api/v1/support/bundle")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "BACKUP_RUN")
    void backup_run_ile_destek_paketi_indirilir() throws Exception {
        mvc.perform(get("/api/v1/support/bundle"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    byte[] body = result.getResponse().getContentAsByteArray();
                    org.assertj.core.api.Assertions.assertThat(body).isNotEmpty();
                });
    }

    @Test
    @WithMockUser(authorities = "BACKUP_RUN")
    void manifest_dosya_listesini_doner() throws Exception {
        mvc.perform(get("/api/v1/support/bundle/manifest")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").exists());
    }
}
