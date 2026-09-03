package com.beautysalonapp.modules.appointment;

import com.beautysalonapp.core.error.BusinessRuleException;
import com.beautysalonapp.modules.appointment.application.AppointmentService;
import com.beautysalonapp.modules.appointment.application.AppointmentService.BookCommand;
import com.beautysalonapp.modules.appointment.application.AppointmentService.StatusChange;
import com.beautysalonapp.modules.appointment.domain.Appointment;
import com.beautysalonapp.modules.appointment.domain.AppointmentStatus;
import com.beautysalonapp.modules.appointment.domain.Resource;
import com.beautysalonapp.modules.appointment.domain.ServiceDefinition;
import com.beautysalonapp.modules.finance.application.FinanceService;
import com.beautysalonapp.modules.party.application.PartyLedger;
import com.beautysalonapp.modules.party.application.PartyService;
import com.beautysalonapp.modules.party.domain.AccountKind;
import com.beautysalonapp.modules.party.domain.PartyType;
import com.beautysalonapp.modules.stock.application.StockPort;
import com.beautysalonapp.modules.stock.application.StockService;
import com.beautysalonapp.modules.stock.domain.ItemType;
import com.beautysalonapp.modules.stock.domain.MovementDirection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class AppointmentServiceTest {

    @Autowired AppointmentService appts;
    @Autowired PartyService partyService;
    @Autowired PartyLedger partyLedger;
    @Autowired FinanceService finance;
    @Autowired StockService stock;
    @Autowired StockPort stockPort;

    private long staff() {
        return partyService.create(PartyType.PERSONEL, null, "Uzman " + System.nanoTime(),
                null, null, null, null, null, null).getId();
    }

    private long customer() {
        return partyService.create(PartyType.MUSTERI, null, "Randevu Müşteri " + System.nanoTime(),
                null, null, null, null, null, null).getId();
    }

    private ServiceDefinition svc(int durationMin, String price) {
        return appts.createService("SVC-" + System.nanoTime() % 1_000_000L, "Cilt Bakımı", durationMin,
                new BigDecimal(price), 0, 0, false);
    }

    private final Instant base = Instant.parse("2026-09-15T09:00:00Z");

    @Test
    void randevu_olusturulur_bitis_hesaplanir() {
        var a = appts.book(new BookCommand(customer(), staff(), null, svc(60, "400").getId(),
                base, "TELEFON", null, null));
        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.PLANLANDI);
        assertThat(ChronoUnit.MINUTES.between(a.getStartAt(), a.getEndAt())).isEqualTo(60);
        assertThat(a.getPriceSnapshot()).isEqualByComparingTo("400");
    }

    @Test
    void ayni_personel_cakismasi_reddedilir() {
        long s = staff();
        long svcId = svc(60, "300").getId();
        appts.book(new BookCommand(customer(), s, null, svcId, base, null, null, null));
        assertThatThrownBy(() -> appts.book(new BookCommand(customer(), s, null, svcId,
                base.plus(30, ChronoUnit.MINUTES), null, null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Personel");
    }

    @Test
    void ayni_kaynak_cakismasi_reddedilir() {
        Resource room = appts.createResource("ODA-" + System.nanoTime() % 100000, "Oda 1", "ODA");
        long svcId = svc(30, "0").getId();
        appts.book(new BookCommand(customer(), staff(), room.getId(), svcId, base, null, null, null));
        assertThatThrownBy(() -> appts.book(new BookCommand(customer(), staff(), room.getId(), svcId,
                base.plus(10, ChronoUnit.MINUTES), null, null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Kaynak");
    }

    @Test
    void bitisik_randevular_cakismaz() {
        long s = staff();
        long svcId = svc(60, "0").getId();
        appts.book(new BookCommand(customer(), s, null, svcId, base, null, null, null));
        var second = appts.book(new BookCommand(customer(), s, null, svcId,
                base.plus(60, ChronoUnit.MINUTES), null, null, null));
        assertThat(second.getId()).isPositive();
    }

    @Test
    void gelmedi_no_show_sayacini_artirir() {
        long cust = customer();
        var a = appts.book(new BookCommand(cust, staff(), null, svc(30, "0").getId(), base, null, null, null));
        appts.changeStatus(a.getId(), new StatusChange(AppointmentStatus.GELMEDI, false, null));
        assertThat(appts.get(a.getId()).isNoShow()).isTrue();
        assertThat(appts.noShowCount(cust)).isEqualTo(1);
    }

    @Test
    void gecersiz_durum_gecisi_reddedilir() {
        var a = appts.book(new BookCommand(customer(), staff(), null, svc(30, "0").getId(), base, null, null, null));
        appts.changeStatus(a.getId(), new StatusChange(AppointmentStatus.GELDI, false, null));
        assertThatThrownBy(() -> appts.changeStatus(a.getId(),
                new StatusChange(AppointmentStatus.PLANLANDI, false, null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void geldi_zinciri_hizmet_bedelini_cariye_yazar_ve_tahsil_eder() {
        long cust = customer();
        long acc = partyLedger.resolveAccount(cust, AccountKind.NORMAL, "TRY");
        long kasa = finance.defaultCashAccountId();
        BigDecimal kasaBefore = finance.accountBalance(kasa).getAmount();

        var a = appts.book(new BookCommand(cust, staff(), null, svc(45, "500").getId(), base, null, null, null));
        appts.changeStatus(a.getId(), new StatusChange(AppointmentStatus.GELDI, true, null));

        assertThat(partyLedger.balance(acc).getAmount()).isEqualByComparingTo("0.0000"); // borç 500 - tahsilat 500
        assertThat(finance.accountBalance(kasa).getAmount())
                .isEqualByComparingTo(kasaBefore.add(new BigDecimal("500")));
        assertThat(appts.get(a.getId()).getStatus()).isEqualTo(AppointmentStatus.GELDI);
    }

    @Test
    void geldi_zinciri_recete_stogunu_sarf_eder() {
        // Stok: bir ürün, SARF deposuna 10 giriş
        var item = stock.createItem(null, "Maske Kremi " + System.nanoTime(), ItemType.EMTIA, "ADET",
                new BigDecimal("20"), null, null, null);
        long adet = stock.unitsOf(item.getId()).get(0).getUnitId();
        long sarf = stockPort.consumptionWarehouseId();
        stock.record(LocalDate.now(), item.getId(), sarf, MovementDirection.IN, adet,
                new BigDecimal("10"), new BigDecimal("15"), "TEST", "OPEN-" + item.getId(), "1", null);

        ServiceDefinition service = svc(30, "0");
        appts.addRecipe(service.getId(), item.getId(), adet, new BigDecimal("2"));

        var a = appts.book(new BookCommand(customer(), staff(), null, service.getId(), base, null, null, null));
        appts.changeStatus(a.getId(), new StatusChange(AppointmentStatus.GELDI, false, null));

        assertThat(stockPort.onHandBase(item.getId(), sarf)).isEqualByComparingTo("8"); // 10 - 2
    }

    @Test
    void personel_olmayan_kisiyle_randevu_reddedilir() {
        long notStaff = customer();
        assertThatThrownBy(() -> appts.book(new BookCommand(customer(), notStaff, null,
                svc(30, "0").getId(), base, null, null, null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void move_cakisma_kontrol_eder() {
        long s = staff();
        long svcId = svc(60, "0").getId();
        var a1 = appts.book(new BookCommand(customer(), s, null, svcId, base, null, null, null));
        var a2 = appts.book(new BookCommand(customer(), s, null, svcId,
                base.plus(120, ChronoUnit.MINUTES), null, null, null));
        assertThatThrownBy(() -> appts.move(a2.getId(), base.plus(30, ChronoUnit.MINUTES), null, null))
                .isInstanceOf(BusinessRuleException.class);
        // boş saate taşıma sorunsuz
        Appointment moved = appts.move(a2.getId(), base.plus(300, ChronoUnit.MINUTES), null, null);
        assertThat(moved.getStartAt()).isEqualTo(base.plus(300, ChronoUnit.MINUTES));
    }
}
