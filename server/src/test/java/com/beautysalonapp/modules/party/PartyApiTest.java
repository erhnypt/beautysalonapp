package com.beautysalonapp.modules.party;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PartyApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    @WithMockUser(authorities = {"PARTY_VIEW", "PARTY_ADD"})
    void cari_olusturulur_kod_otomatik_ve_liste_maskeli() throws Exception {
        String body = json.writeValueAsString(new java.util.HashMap<>() {{
            put("type", "MUSTERI");
            put("title", "Ayşe Yılmaz");
            put("phone", "05551112233");
            put("email", "ayse@example.com");
        }});

        mvc.perform(post("/api/v1/parties").with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(startsWith("MUS")))
                .andExpect(jsonPath("$.phone").value("05551112233"));

        mvc.perform(get("/api/v1/parties?type=MUSTERI&q=Ayşe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Ayşe Yılmaz"))
                .andExpect(jsonPath("$.content[0].phoneMasked").value(containsString("2233")));
    }

    @Test
    void yetkisiz_erisim_403_veya_401() throws Exception {
        mvc.perform(get("/api/v1/parties")).andExpect(status().isUnauthorized());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor csrf() {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf();
    }
}
