package com.beautysalonapp.security;

import com.beautysalonapp.security.application.UserService;
import com.beautysalonapp.security.domain.RoleName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kritik rollerde 2. faktör (PIN) uçtan uca akışı — güvenlik gözden geçirme A7.
 * ADMIN kullanıcısı üzerinden aynı senaryo {@link AuthFlowTest} içinde de dolaylı
 * doğrulanır; burada MUDUR/KASIYER ayrımı, hatalı PIN kilidi ve kurtarma yolu test edilir.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PinAuthTest {

    @Autowired MockMvc mvc;
    @Autowired UserService userService;

    private MockHttpSession loginAs(String username, String password) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) r.getRequest().getSession(false);
    }

    private static MockHttpServletRequestBuilder pinPost(String path, MockHttpSession session, String pin) {
        return post(path)
                .session(session)
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pin\":\"%s\"}".formatted(pin));
    }

    @Test
    void mudur_pin_kurulumu_zorunlu() throws Exception {
        userService.create("mudur.pin1", "Müdür Bir", "Parola123!", Set.of(RoleName.MUDUR));

        var session = loginAs("mudur.pin1", "Parola123!");
        mvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(jsonPath("$.pinSetupRequired").value(true))
                .andExpect(jsonPath("$.roles").isEmpty());

        mvc.perform(pinPost("/api/v1/auth/pin/setup", session, "4321")).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(jsonPath("$.pinSetupRequired").value(false))
                .andExpect(jsonPath("$.roles[0]").value("MUDUR"));
    }

    @Test
    void kasiyer_pin_gerektirmez() throws Exception {
        userService.create("kasiyer.pin1", "Kasiyer Bir", "Parola123!", Set.of(RoleName.KASIYER));

        var session = loginAs("kasiyer.pin1", "Parola123!");
        mvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(jsonPath("$.pinSetupRequired").value(false))
                .andExpect(jsonPath("$.pinVerificationRequired").value(false))
                .andExpect(jsonPath("$.roles[0]").value("KASIYER"));
    }

    @Test
    void gecersiz_bicimde_pin_400() throws Exception {
        userService.create("mudur.pin2", "Müdür İki", "Parola123!", Set.of(RoleName.MUDUR));
        var session = loginAs("mudur.pin2", "Parola123!");

        mvc.perform(pinPost("/api/v1/auth/pin/setup", session, "abcd")).andExpect(status().isBadRequest());
        mvc.perform(pinPost("/api/v1/auth/pin/setup", session, "12")).andExpect(status().isBadRequest());
    }

    @Test
    void yanlis_pin_ile_dogrulama_reddedilir_sonra_kilitlenir() throws Exception {
        userService.create("mudur.pin3", "Müdür Üç", "Parola123!", Set.of(RoleName.MUDUR));
        var setup = loginAs("mudur.pin3", "Parola123!");
        mvc.perform(pinPost("/api/v1/auth/pin/setup", setup, "1111")).andExpect(status().isNoContent());

        // Yeni oturum: PIN kurulu, doğrulama bekleniyor.
        var session = loginAs("mudur.pin3", "Parola123!");
        mvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(jsonPath("$.pinVerificationRequired").value(true));

        // 4 yanlış deneme: 401
        for (int i = 0; i < 4; i++) {
            mvc.perform(pinPost("/api/v1/auth/pin/verify", session, "9999")).andExpect(status().isUnauthorized());
        }
        // 5. yanlış deneme: kilit + oturum sonlandırılır
        mvc.perform(pinPost("/api/v1/auth/pin/verify", session, "9999")).andExpect(status().isLocked());

        // Kilitli oturumla başka bir isteğe artık kimlik doğrulaması geçersiz.
        mvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isUnauthorized());

        // Regresyon: kilitliyken YENİDEN giriş yapan bir oturum PIN_LOCKED yetkisiyle kurulur;
        // /pin/verify bunu da kabul etmeli (403 değil, 423 dönüp oturumu sonlandırmalı) —
        // aksi halde kilit süresi dolsa bile bu oturum sonsuza dek 403 alır.
        var relogin = loginAs("mudur.pin3", "Parola123!");
        mvc.perform(get("/api/v1/auth/me").session(relogin))
                .andExpect(jsonPath("$.pinVerificationRequired").value(true))
                .andExpect(jsonPath("$.pinLockedUntil").exists());
        mvc.perform(pinPost("/api/v1/auth/pin/verify", relogin, "1111")).andExpect(status().isLocked());
        mvc.perform(get("/api/v1/auth/me").session(relogin)).andExpect(status().isUnauthorized());
    }

    @Test
    void dogru_pin_ile_tam_oturuma_yukseltilir() throws Exception {
        userService.create("mudur.pin4", "Müdür Dört", "Parola123!", Set.of(RoleName.MUDUR));
        var setup = loginAs("mudur.pin4", "Parola123!");
        mvc.perform(pinPost("/api/v1/auth/pin/setup", setup, "2468")).andExpect(status().isNoContent());

        var session = loginAs("mudur.pin4", "Parola123!");
        mvc.perform(get("/api/v1/settings").session(session)).andExpect(status().isForbidden());

        mvc.perform(pinPost("/api/v1/auth/pin/verify", session, "2468")).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(jsonPath("$.pinVerificationRequired").value(false))
                .andExpect(jsonPath("$.roles[0]").value("MUDUR"));
        mvc.perform(get("/api/v1/settings").session(session)).andExpect(status().isOk());
    }

    @Test
    void yonetici_pin_sifirlayabilir_kurtarma_yolu() throws Exception {
        // Paylaşılan bootstrap 'admin' hesabına bağımlı olmamak için (diğer test
        // sınıfları onun PIN durumunu da değiştiriyor) ayrı bir ADMIN kullanıcısı
        // oluşturuluyor — USER_EDIT (PIN sıfırlama) yalnızca ADMIN'de var.
        userService.create("admin.pin6", "Yönetici Altı", "Parola123!", Set.of(RoleName.ADMIN));
        var adminSetup = loginAs("admin.pin6", "Parola123!");
        mvc.perform(pinPost("/api/v1/auth/pin/setup", adminSetup, "1234")).andExpect(status().isNoContent());
        var adminSession = loginAs("admin.pin6", "Parola123!");
        mvc.perform(pinPost("/api/v1/auth/pin/verify", adminSession, "1234")).andExpect(status().isNoContent());

        var mudur = userService.create("mudur.pin5", "Müdür Beş", "Parola123!", Set.of(RoleName.MUDUR));
        var mudurSetup = loginAs("mudur.pin5", "Parola123!");
        mvc.perform(pinPost("/api/v1/auth/pin/setup", mudurSetup, "1357")).andExpect(status().isNoContent());

        mvc.perform(put("/api/v1/users/" + mudur.getId() + "/pin")
                        .session(adminSession)
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk());

        var again = loginAs("mudur.pin5", "Parola123!");
        mvc.perform(get("/api/v1/auth/me").session(again))
                .andExpect(jsonPath("$.pinSetupRequired").value(true));
    }
}
