package com.beautysalonapp.modules.reporting;

import com.beautysalonapp.modules.appointment.application.AppointmentService;
import com.beautysalonapp.modules.appointment.application.AppointmentService.BookCommand;
import com.beautysalonapp.modules.appointment.application.AppointmentService.StatusChange;
import com.beautysalonapp.modules.appointment.domain.AppointmentStatus;
import com.beautysalonapp.modules.appointment.domain.ServiceDefinition;
import com.beautysalonapp.modules.invoice.application.InvoiceService;
import com.beautysalonapp.modules.invoice.application.InvoiceService.CreateInvoiceCommand;
import com.beautysalonapp.modules.invoice.application.InvoiceService.NewLine;
import com.beautysalonapp.modules.invoice.application.InvoiceService.NewPayment;
import com.beautysalonapp.modules.invoice.domain.InvoiceType;
import com.beautysalonapp.modules.invoice.domain.PaymentMethod;
import com.beautysalonapp.modules.party.application.PartyService;
import com.beautysalonapp.modules.party.domain.PartyType;
import com.beautysalonapp.modules.reporting.application.ReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ReportServiceTest {

    @Autowired ReportService reports;
    @Autowired InvoiceService invoices;
    @Autowired PartyService partyService;
    @Autowired AppointmentService appts;

    @Test
    void gunluk_dashboard_ciro_ve_sayilari_toplar() {
        long cust = partyService.create(PartyType.MUSTERI, null, "Rapor Müşteri " + System.nanoTime(),
                null, null, null, null, null, null).getId();

        var before = reports.today();

        invoices.create(new CreateInvoiceCommand(InvoiceType.SATIS, LocalDate.now(), cust, null, null, null,
                java.util.List.of(new NewLine(null, true, "Hizmet", BigDecimal.ONE, null,
                        new BigDecimal("500"), BigDecimal.ZERO, new BigDecimal("20"))),
                java.util.List.of(new NewPayment(PaymentMethod.CASH, new BigDecimal("600"), null, null, null, null, null))));

        var after = reports.today();
        assertThat(after.invoiceRevenue()).isEqualByComparingTo(before.invoiceRevenue().add(new BigDecimal("600")));
        assertThat(after.collections()).isEqualByComparingTo(before.collections().add(new BigDecimal("600")));
        assertThat(after.payments().nakit()).isGreaterThanOrEqualTo(new BigDecimal("600"));
        assertThat(after.totalRevenue()).isEqualByComparingTo(after.invoiceRevenue().add(after.appointmentRevenue()));
        assertThat(after.newCustomers()).isGreaterThanOrEqualTo(1);
        assertThat(after.alerts()).extracting(ReportService.Alert::key)
                .contains("installments_due", "critical_stock", "cheques_due_week");
    }

    @Test
    void randevu_cirosu_ve_durum_dagilimi() {
        long cust = partyService.create(PartyType.MUSTERI, null, "R2 " + System.nanoTime(),
                null, null, null, null, null, null).getId();
        long staff = partyService.create(PartyType.PERSONEL, null, "R2 Personel " + System.nanoTime(),
                null, null, null, null, null, null).getId();
        ServiceDefinition svc = appts.createService("RPT-" + System.nanoTime() % 1_000_000L,
                "Rapor Hizmeti", 30, new BigDecimal("250"), 0, 0, false);

        var appt = appts.book(new BookCommand(cust, staff, null, svc.getId(),
                Instant.now().plusSeconds(3600), null, null, null));
        appts.changeStatus(appt.getId(), new StatusChange(AppointmentStatus.GELDI, false, null));

        var d = reports.today();
        assertThat(d.appointmentRevenue()).isGreaterThanOrEqualTo(new BigDecimal("250"));
        assertThat(d.appointmentsByStatus()).containsKey("GELDI");
        assertThat(reports.endOfDaySummary()).contains("GÜN SONU RAPORU");
    }
}
