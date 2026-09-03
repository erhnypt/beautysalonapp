package com.beautysalonapp.modules.notification;

import com.beautysalonapp.modules.notification.domain.TemplateRenderer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateRendererTest {

    @Test
    void degiskenleri_yerlestirir() {
        String out = TemplateRenderer.render("Sayın {ad}, {tutar} TL bakiyeniz var.",
                Map.of("ad", "Ayşe", "tutar", "1.234,56"));
        assertThat(out).isEqualTo("Sayın Ayşe, 1.234,56 TL bakiyeniz var.");
    }

    @Test
    void bilinmeyen_degisken_bos() {
        assertThat(TemplateRenderer.render("Merhaba {yok}!", Map.of())).isEqualTo("Merhaba !");
    }

    @Test
    void null_ve_bos_guvenli() {
        assertThat(TemplateRenderer.render(null, Map.of())).isEmpty();
        assertThat(TemplateRenderer.render("", null)).isEmpty();
        assertThat(TemplateRenderer.render("düz metin", null)).isEqualTo("düz metin");
    }

    @Test
    void ayni_degisken_birden_cok_kez() {
        assertThat(TemplateRenderer.render("{x}-{x}", Map.of("x", "A"))).isEqualTo("A-A");
    }

    @Test
    void degisken_listesi() {
        assertThat(TemplateRenderer.variablesIn("{ad} {tarih} {ad}")).containsExactly("ad", "tarih");
    }

    @Test
    void degistirilen_deger_dolar_isareti_bozmaz() {
        assertThat(TemplateRenderer.render("{x}", Map.of("x", "$5 & $10")))
                .isEqualTo("$5 & $10");
    }
}
