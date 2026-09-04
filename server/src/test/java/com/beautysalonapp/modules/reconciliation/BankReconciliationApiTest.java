package com.beautysalonapp.modules.reconciliation;

import com.beautysalonapp.modules.finance.application.FinanceService;
import com.beautysalonapp.modules.finance.domain.FinAccountKind;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BankReconciliationApiTest {

    @Autowired MockMvc mvc;
    @Autowired FinanceService finance;

    @Test
    void kimlik_dogrulamasiz_erisim_401() throws Exception {
        mvc.perform(get("/api/v1/bank-reconciliation")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "FINANCE_VIEW")
    void yalnizca_view_yetkisiyle_ice_aktarma_yasak() throws Exception {
        long acc = finance.createAccount("BK" + Long.toString(System.nanoTime(), 36), "API Test Banka",
                FinAccountKind.BANKA, "TRY", BigDecimal.ZERO, false).getId();
        var file = new MockMultipartFile("file", "s.csv", "text/csv",
                "Tarih;Açıklama;Tutar\n01.01.2024;x;10,00\n".getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/v1/bank-reconciliation/import")
                        .file(file)
                        .param("finAccountId", String.valueOf(acc))
                        .param("format", "CSV")
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"FINANCE_VIEW", "FINANCE_EDIT"})
    void ice_aktarma_ve_liste_calisir() throws Exception {
        long acc = finance.createAccount("BK" + Long.toString(System.nanoTime(), 36), "API Test Banka",
                FinAccountKind.BANKA, "TRY", BigDecimal.ZERO, false).getId();
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        var file = new MockMultipartFile("file", "ekstre.csv", "text/csv",
                (("Tarih;Açıklama;Tutar;Referans\n" + today + ";Test hareketi;150,00;R1\n"))
                        .getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/v1/bank-reconciliation/import")
                        .file(file)
                        .param("finAccountId", String.valueOf(acc))
                        .param("format", "CSV")
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lineCount").value(1))
                .andExpect(jsonPath("$.sourceFormat").value("CSV"))
                .andExpect(jsonPath("$.status").value("IMPORTED"));

        mvc.perform(get("/api/v1/bank-reconciliation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].finAccountId").value(acc));
    }

    @Test
    @WithMockUser(authorities = "FINANCE_VIEW")
    void banka_hesap_listesi_gorulur() throws Exception {
        mvc.perform(get("/api/v1/bank-reconciliation/accounts"))
                .andExpect(status().isOk());
    }
}
