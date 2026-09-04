package com.beautysalonapp.modules.branch;

import com.beautysalonapp.modules.branch.web.BranchContextFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Faz 8 tam şube izolasyonu (ADR 0006): {@link BranchContextFilter}'ın HTTP katmanındaki
 * doğrulama davranışı — geçerli/geçersiz/eksik {@code X-Branch-Id} başlığı.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BranchContextFilterTest {

    @Autowired
    private MockMvc mvc;

    private MockHttpSession login() throws Exception {
        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }

    @Test
    void baslik_gonderilmezse_normal_calisir() throws Exception {
        var session = login();
        mvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk());
    }

    @Test
    void gecerli_sube_basligiyla_normal_calisir() throws Exception {
        var session = login();
        mvc.perform(get("/api/v1/auth/me").session(session).header(BranchContextFilter.HEADER, "1"))
                .andExpect(status().isOk());
    }

    @Test
    void sayisal_olmayan_sube_basligi_400_doner() throws Exception {
        var session = login();
        mvc.perform(get("/api/v1/auth/me").session(session).header(BranchContextFilter.HEADER, "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_branch_header"));
    }

    @Test
    void olmayan_sube_kimligi_400_doner() throws Exception {
        var session = login();
        mvc.perform(get("/api/v1/auth/me").session(session).header(BranchContextFilter.HEADER, "999999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_branch_header"));
    }
}
