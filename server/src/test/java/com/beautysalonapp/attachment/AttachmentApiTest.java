package com.beautysalonapp.attachment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AttachmentApiTest {

    @Autowired MockMvc mvc;

    @Test
    void kimlik_dogrulamasiz_401() throws Exception {
        mvc.perform(get("/api/v1/attachments").param("ownerType", "PARTY").param("ownerId", "1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "STOCK_VIEW")
    void ilgisiz_yetkiyle_403() throws Exception {
        mvc.perform(get("/api/v1/attachments").param("ownerType", "PARTY").param("ownerId", "1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "PARTY_EDIT")
    void party_edit_ile_dosya_yuklenir_ve_listelenir() throws Exception {
        long ownerId = System.nanoTime();
        MockMultipartFile file = new MockMultipartFile("file", "profil.jpg", "image/jpeg", "x".getBytes());

        mvc.perform(multipart("/api/v1/attachments")
                        .file(file)
                        .param("ownerType", "PARTY")
                        .param("ownerId", String.valueOf(ownerId))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("profil.jpg"));

        mvc.perform(get("/api/v1/attachments").param("ownerType", "PARTY").param("ownerId", String.valueOf(ownerId))
                        .with(csrf()))
                .andExpect(status().isForbidden()); // PARTY_EDIT var ama PARTY_VIEW/CONTRACT_VIEW yok
    }
}
