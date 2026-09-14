package com.beautysalonapp.security;

import com.beautysalonapp.modules.branch.application.BranchService;
import com.beautysalonapp.modules.branch.web.BranchContextFilter;
import com.beautysalonapp.security.application.UserService;
import com.beautysalonapp.security.domain.RoleName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kullanıcı-şube ataması (§B5) — uçtan uca HTTP katmanı doğrulaması.
 * {@code X-Branch-Id} artık yalnızca "şube var mı" değil, "bu kullanıcı bu şubede
 * çalışabilir mi" sorusunu da yanıtlar. Kısıtlanmamış kullanıcı (varsayılan, boş atama)
 * için davranış birebir eskisi gibidir — {@link com.beautysalonapp.modules.branch.BranchContextFilterTest}
 * bunu zaten kapsıyor.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserBranchAssignmentTest {

    @Autowired MockMvc mvc;
    @Autowired UserService userService;
    @Autowired BranchService branchService;

    private long freshBranch() {
        return branchService.create("UBT" + Long.toString(System.nanoTime(), 36).toUpperCase(),
                "Test Şube", null, null, null).getId();
    }

    private MockHttpSession loginAs(String username, String password) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) r.getRequest().getSession(false);
    }

    @Test
    void kisitlanmamis_kullanici_her_subede_calisir() throws Exception {
        String username = "branch.free." + System.nanoTime();
        userService.create(username, "Serbest", "Parola123!", Set.of(RoleName.KASIYER));
        long other = freshBranch();

        var session = loginAs(username, "Parola123!");

        mvc.perform(get("/api/v1/auth/me").session(session)
                        .header(BranchContextFilter.HEADER, String.valueOf(other)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk());
    }

    @Test
    void kisitli_kullanici_izinli_olmayan_subeye_403_izinliye_200() throws Exception {
        String username = "branch.rest." + System.nanoTime();
        var user = userService.create(username, "Kısıtlı", "Parola123!", Set.of(RoleName.KASIYER));
        long other = freshBranch();
        userService.assignBranches(user.getId(), Set.of(1L));

        var session = loginAs(username, "Parola123!");

        mvc.perform(get("/api/v1/auth/me").session(session)
                        .header(BranchContextFilter.HEADER, String.valueOf(other)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/auth/me").session(session)
                        .header(BranchContextFilter.HEADER, "1"))
                .andExpect(status().isOk());
    }

    @Test
    void sube_listesi_kisitli_kullaniciya_yalnizca_atanmis_subeyi_gosterir() throws Exception {
        String username = "branch.list." + System.nanoTime();
        var user = userService.create(username, "Liste", "Parola123!", Set.of(RoleName.KASIYER));
        long other = freshBranch();
        userService.assignBranches(user.getId(), Set.of(other));

        var session = loginAs(username, "Parola123!");

        mvc.perform(get("/api/v1/branches").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(other));
    }

    @Test
    void atanmis_sube_sonradan_silinirse_basliksiz_istek_erisimi_reddeder() throws Exception {
        // user_branch, branch'e FK ile bağlı değil (V18) — bir şube atandıktan sonra soft-delete
        // edilebilir. Filtre, başlıksız istekte silinmiş şubeye sessizce düşmemeli.
        String username = "branch.deleted." + System.nanoTime();
        var user = userService.create(username, "Silinen Şube", "Parola123!", Set.of(RoleName.KASIYER));
        long onlyBranch = freshBranch();
        userService.assignBranches(user.getId(), Set.of(onlyBranch));

        var session = loginAs(username, "Parola123!");
        mvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isOk());

        branchService.delete(onlyBranch);

        mvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("access_denied"));
    }

    @Test
    void dashboard_izin_disi_branchid_parametresiyle_403() throws Exception {
        // RAPOR_OKUYUCU: REPORTING_VIEW var, PIN gerektirmiyor (ADMIN/MUDUR değil) — akış sade kalır.
        String username = "branch.dash." + System.nanoTime();
        var user = userService.create(username, "Rapor", "Parola123!", Set.of(RoleName.RAPOR_OKUYUCU));
        long other = freshBranch();
        userService.assignBranches(user.getId(), Set.of(1L));

        var session = loginAs(username, "Parola123!");

        mvc.perform(get("/api/v1/dashboard/today").session(session).param("branchId", String.valueOf(other)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/dashboard/today").session(session).param("branchId", "1"))
                .andExpect(status().isOk());
        // Parametresiz istek de çalışır (atanmış şubeye varsayılan).
        mvc.perform(get("/api/v1/dashboard/today").session(session))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "USER_EDIT")
    void gecersiz_subeye_atama_400_doner() throws Exception {
        var user = userService.create("branch.invalid." + System.nanoTime(), "Geçersiz", "Parola123!",
                Set.of(RoleName.KASIYER));

        mvc.perform(put("/api/v1/users/" + user.getId() + "/branches")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchIds\":[999999]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("invalid_branch"));

        assertThat(userService.assignedBranches(user.getUsername())).isEmpty();
    }

    @Test
    @WithMockUser(authorities = "USER_EDIT")
    void gecerli_subeye_atama_kaydedilir_ve_geri_bildirilir() throws Exception {
        var user = userService.create("branch.valid." + System.nanoTime(), "Geçerli", "Parola123!",
                Set.of(RoleName.KASIYER));

        mvc.perform(put("/api/v1/users/" + user.getId() + "/branches")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchIds\":[1]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedBranchIds[0]").value(1));
    }
}
