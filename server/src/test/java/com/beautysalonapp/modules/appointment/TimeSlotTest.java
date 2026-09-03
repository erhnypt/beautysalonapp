package com.beautysalonapp.modules.appointment;

import com.beautysalonapp.modules.appointment.domain.TimeSlot;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeSlotTest {

    private static Instant t(String iso) {
        return Instant.parse(iso);
    }

    @Test
    void kesisen_araliklar() {
        var a = new TimeSlot(t("2026-09-10T10:00:00Z"), t("2026-09-10T11:00:00Z"));
        var b = new TimeSlot(t("2026-09-10T10:30:00Z"), t("2026-09-10T11:30:00Z"));
        assertThat(a.overlaps(b)).isTrue();
        assertThat(b.overlaps(a)).isTrue();
    }

    @Test
    void bitisik_araliklar_cakismaz() {
        var a = new TimeSlot(t("2026-09-10T10:00:00Z"), t("2026-09-10T11:00:00Z"));
        var b = new TimeSlot(t("2026-09-10T11:00:00Z"), t("2026-09-10T12:00:00Z"));
        assertThat(a.overlaps(b)).isFalse();
    }

    @Test
    void ic_ice_araliklar_cakisir() {
        var outer = new TimeSlot(t("2026-09-10T09:00:00Z"), t("2026-09-10T12:00:00Z"));
        var inner = new TimeSlot(t("2026-09-10T10:00:00Z"), t("2026-09-10T10:15:00Z"));
        assertThat(outer.overlaps(inner)).isTrue();
    }

    @Test
    void buffer_bitisik_randevuyu_cakistirir() {
        var a = new TimeSlot(t("2026-09-10T10:00:00Z"), t("2026-09-10T11:00:00Z"))
                .withBuffers(0, 15);
        var b = new TimeSlot(t("2026-09-10T11:10:00Z"), t("2026-09-10T11:40:00Z"));
        assertThat(a.overlaps(b)).isTrue();
    }

    @Test
    void of_sure_ile_bitis_hesaplar() {
        var s = TimeSlot.of(t("2026-09-10T10:00:00Z"), 45);
        assertThat(s.end()).isEqualTo(t("2026-09-10T10:45:00Z"));
        assertThat(s.minutes()).isEqualTo(45);
    }

    @Test
    void gecersiz_aralik_reddedilir() {
        assertThatThrownBy(() -> new TimeSlot(t("2026-09-10T11:00:00Z"), t("2026-09-10T10:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TimeSlot(null, t("2026-09-10T10:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
