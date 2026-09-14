package com.beautysalonapp.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void kimlik_dogrulamasiz_korumali_uc_401() throws Exception {
        mvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void hatali_parola_401() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"yanlis\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void giris_sonrasi_oturumla_me_ve_users_erisilir() throws Exception {
        // ADMIN kritik rol olduğundan (§A7) parola girişi tek başına yetmez: PIN
        // kurulana kadar oturumun rol/izin listesi boştur ve /api/v1/users 403 döner.
        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.pinSetupRequired").value(true))
                .andExpect(jsonPath("$.roles").isEmpty())
                .andReturn();

        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andExpect(jsonPath("$.pinSetupRequired").value(true));

        mvc.perform(get("/api/v1/users").session(session))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/auth/pin/setup")
                        .session(session)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"1234\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pinSetupRequired").value(false))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"));

        mvc.perform(get("/api/v1/users").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("admin"));
    }

    @Test
    void lisans_durumu_kimlik_dogrulamasiz_gorulur() throws Exception {
        mvc.perform(get("/api/v1/license/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.devMode").value(true));
    }
}
