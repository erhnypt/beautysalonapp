package com.beautysalonapp.modules.branch;

import com.beautysalonapp.core.error.BusinessRuleException;
import com.beautysalonapp.modules.branch.application.BranchService;
import com.beautysalonapp.modules.branch.domain.Branch;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class BranchServiceTest {

    @Autowired
    private BranchService branches;

    private String code() {
        return "S" + Long.toString(System.nanoTime(), 36).toUpperCase();
    }

    @Test
    void ilk_kurulumdaki_merkez_sube_zaten_var() {
        assertThat(branches.list()).isNotEmpty();
        assertThat(branches.list()).anyMatch(Branch::isHeadquarters);
    }

    @Test
    void yeni_sube_olusturulur_ve_merkez_olmaz() {
        Branch b = branches.create(code(), "Şube A", null, "Adres", "0212 000 00 00");
        assertThat(b.getId()).isPositive();
        assertThat(b.isHeadquarters()).isFalse(); // zaten bir HQ var
        assertThat(branches.get(b.getId()).getTitle()).isEqualTo("Şube A");
    }

    @Test
    void mukerrer_kod_reddedilir() {
        String c = code();
        branches.create(c, "Şube B", null, null, null);
        assertThatThrownBy(() -> branches.create(c, "Şube B2", null, null, null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void merkez_degistirme_yalnizca_bir_merkez_birakir() {
        Branch b = branches.create(code(), "Şube C", null, null, null);
        branches.setHeadquarters(b.getId());

        var all = branches.list();
        assertThat(all.stream().filter(Branch::isHeadquarters).count()).isEqualTo(1);
        assertThat(branches.get(b.getId()).isHeadquarters()).isTrue();
    }

    @Test
    void merkez_sube_silinemez() {
        Branch b = branches.create(code(), "Şube D", null, null, null);
        branches.setHeadquarters(b.getId());
        assertThatThrownBy(() -> branches.delete(b.getId())).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void merkez_olmayan_sube_silinebilir() {
        Branch b = branches.create(code(), "Şube E", null, null, null);
        branches.delete(b.getId());
        assertThatThrownBy(() -> branches.get(b.getId())).isInstanceOf(com.beautysalonapp.core.error.NotFoundException.class);
    }

    @Test
    void bos_baslik_reddedilir() {
        assertThatThrownBy(() -> branches.create(code(), " ", null, null, null))
                .isInstanceOf(BusinessRuleException.class);
    }
}
