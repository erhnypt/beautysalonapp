package com.beautysalonapp.modules.branch;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BranchApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void kimlik_dogrulamasiz_401() throws Exception {
        mvc.perform(get("/api/v1/branches")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "SETTINGS_VIEW")
    void view_yetkisiyle_liste_gorulur_ama_olusturulamaz() throws Exception {
        mvc.perform(get("/api/v1/branches")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].headquarters").exists());

        String body = json.writeValueAsString(new HashMap<>() {{
            put("code", "APIB" + System.nanoTime());
            put("title", "API Şube");
        }});
        mvc.perform(post("/api/v1/branches").with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"SETTINGS_VIEW", "SETTINGS_EDIT"})
    void edit_yetkisiyle_sube_olusturulur() throws Exception {
        String body = json.writeValueAsString(new HashMap<>() {{
            put("code", "APIB" + System.nanoTime());
            put("title", "API Şube 2");
        }});
        mvc.perform(post("/api/v1/branches").with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("API Şube 2"));
    }
}
