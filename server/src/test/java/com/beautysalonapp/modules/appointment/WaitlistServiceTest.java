package com.beautysalonapp.modules.appointment;

import com.beautysalonapp.core.error.BusinessRuleException;
import com.beautysalonapp.modules.appointment.application.AppointmentService;
import com.beautysalonapp.modules.appointment.application.AppointmentService.AddToWaitlistCommand;
import com.beautysalonapp.modules.appointment.domain.ServiceDefinition;
import com.beautysalonapp.modules.appointment.domain.WaitlistStatus;
import com.beautysalonapp.modules.party.application.PartyService;
import com.beautysalonapp.modules.party.domain.PartyType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class WaitlistServiceTest {

    @Autowired AppointmentService appts;
    @Autowired PartyService partyService;

    private long staff() {
        return partyService.create(PartyType.PERSONEL, null, "Bekleme Uzmanı " + System.nanoTime(),
                null, null, null, null, null, null).getId();
    }

    private long customer() {
        return partyService.create(PartyType.MUSTERI, null, "Bekleme Müşteri " + System.nanoTime(),
                null, null, null, null, null, null).getId();
    }

    private ServiceDefinition svc() {
        return appts.createService("WL-" + System.nanoTime() % 1_000_000L, "Manikür", 30,
                new BigDecimal("200"), 0, 0, false);
    }

    @Test
    void bekleme_listesine_eklenir_ve_listelenir() {
        long cust = customer();
        var svc = svc();
        var w = appts.addToWaitlist(new AddToWaitlistCommand(cust, svc.getId(), null,
                Instant.now(), Instant.now().plusSeconds(3600), "Cumartesi tercih ediyor"));

        assertThat(w.getStatus()).isEqualTo(WaitlistStatus.BEKLEMEDE);
        assertThat(appts.waitlist(WaitlistStatus.BEKLEMEDE)).anyMatch(x -> x.getId() == w.getId());
    }

    @Test
    void bekleme_kaydi_iptal_edilebilir() {
        long cust = customer();
        var svc = svc();
        var w = appts.addToWaitlist(new AddToWaitlistCommand(cust, svc.getId(), null, null, null, null));

        appts.cancelWaitlistEntry(w.getId());

        assertThat(appts.waitlist(WaitlistStatus.IPTAL)).anyMatch(x -> x.getId() == w.getId());
        assertThat(appts.waitlist(WaitlistStatus.BEKLEMEDE)).noneMatch(x -> x.getId() == w.getId());
    }

    @Test
    void iptal_edilmis_kayit_tekrar_iptal_edilemez() {
        long cust = customer();
        var svc = svc();
        var w = appts.addToWaitlist(new AddToWaitlistCommand(cust, svc.getId(), null, null, null, null));
        appts.cancelWaitlistEntry(w.getId());

        assertThatThrownBy(() -> appts.cancelWaitlistEntry(w.getId()))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void bekleme_kaydi_randevuya_donusturulur() {
        long cust = customer();
        long st = staff();
        var svc = svc();
        var w = appts.addToWaitlist(new AddToWaitlistCommand(cust, svc.getId(), st, null, null, "not"));

        Instant slot = Instant.parse("2026-11-10T10:00:00Z");
        var appt = appts.convertWaitlistToAppointment(w.getId(), st, null, slot, null);

        assertThat(appt.getPartyId()).isEqualTo(cust);
        assertThat(appt.getServiceId()).isEqualTo(svc.getId());
        assertThat(appt.getStartAt()).isEqualTo(slot);

        var updated = appts.waitlist(WaitlistStatus.RANDEVUYA_DONUSTU).stream()
                .filter(x -> x.getId() == w.getId()).findFirst().orElseThrow();
        assertThat(updated.getAppointmentId()).isEqualTo(appt.getId());
    }

    @Test
    void donusturulmus_kayit_tekrar_donusturulemez() {
        long cust = customer();
        long st = staff();
        var svc = svc();
        var w = appts.addToWaitlist(new AddToWaitlistCommand(cust, svc.getId(), st, null, null, null));
        appts.convertWaitlistToAppointment(w.getId(), st, null, Instant.parse("2026-11-11T10:00:00Z"), null);

        assertThatThrownBy(() -> appts.convertWaitlistToAppointment(w.getId(), st, null,
                Instant.parse("2026-11-12T10:00:00Z"), null))
                .isInstanceOf(BusinessRuleException.class);
    }
}
