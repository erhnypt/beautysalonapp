package com.beautysalonapp.modules.invoice;

import com.beautysalonapp.core.error.BusinessRuleException;
import com.beautysalonapp.modules.invoice.application.EInvoiceService;
import com.beautysalonapp.modules.invoice.application.InvoiceService;
import com.beautysalonapp.modules.invoice.application.InvoiceService.CreateInvoiceCommand;
import com.beautysalonapp.modules.invoice.application.InvoiceService.NewLine;
import com.beautysalonapp.modules.invoice.application.InvoiceService.NewPayment;
import com.beautysalonapp.modules.invoice.domain.Invoice;
import com.beautysalonapp.modules.invoice.domain.InvoiceType;
import com.beautysalonapp.modules.invoice.domain.PaymentMethod;
import com.beautysalonapp.modules.party.application.PartyService;
import com.beautysalonapp.modules.party.domain.PartyType;
import com.beautysalonapp.modules.stock.application.StockPort;
import com.beautysalonapp.modules.stock.application.StockService;
import com.beautysalonapp.modules.stock.domain.ItemType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class EInvoiceServiceTest {

    @Autowired private EInvoiceService eInvoice;
    @Autowired private InvoiceService invoices;
    @Autowired private PartyService partyService;
    @Autowired private StockService stock;
    @Autowired private StockPort stockPort;

    private long customerWithTaxId(String taxId) {
        return partyService.create(PartyType.MUSTERI, null, "e-Fatura Müşteri " + System.nanoTime(),
                null, null, null, null, taxId, null).getId();
    }

    private Invoice satisFaturasi(long partyId) {
        return invoices.create(new CreateInvoiceCommand(InvoiceType.SATIS, LocalDate.now(), partyId, null, null, null,
                List.of(new NewLine(null, true, "Hizmet", BigDecimal.ONE, null,
                        new BigDecimal("500"), BigDecimal.ZERO, new BigDecimal("20"))),
                List.of(new NewPayment(PaymentMethod.CASH, new BigDecimal("600"), null, null, null, null, null))));
    }

    @Test
    void xml_uretilir_ve_uuid_atanir() {
        long cust = customerWithTaxId("1234567890");
        Invoice inv = satisFaturasi(cust);

        var r = eInvoice.generate(inv.getId());
        assertThat(r.uuid()).isNotBlank();
        assertThat(r.status()).isEqualTo("HAZIR");
        assertThat(r.filename()).startsWith("efatura-").endsWith(".xml");
        assertThat(r.xml()).contains("<Invoice").contains(r.uuid());
    }

    @Test
    void ikinci_cagri_ayni_uuid_ile_idempotent() {
        long cust = customerWithTaxId("1234567890");
        Invoice inv = satisFaturasi(cust);

        var first = eInvoice.generate(inv.getId());
        var second = eInvoice.generate(inv.getId());
        assertThat(second.uuid()).isEqualTo(first.uuid());
    }

    @Test
    void vergi_no_olmayan_musteri_reddedilir() {
        long cust = partyService.create(PartyType.MUSTERI, null, "Vergisiz Müşteri " + System.nanoTime(),
                null, null, null, null, null, null).getId();
        Invoice inv = satisFaturasi(cust);
        assertThatThrownBy(() -> eInvoice.generate(inv.getId())).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void alis_faturasi_icin_uretilemez() {
        long vendor = partyService.create(PartyType.SATICI, null, "Tedarikçi " + System.nanoTime(),
                null, null, null, null, "1122334455", null).getId();
        long item = stock.createItem(null, "e-Fatura Test Ürünü " + System.nanoTime(), ItemType.EMTIA, "ADET",
                new BigDecimal("20"), null, null, new BigDecimal("100")).getId();
        long depo = stockPort.defaultWarehouseId();

        Invoice alis = invoices.create(new CreateInvoiceCommand(InvoiceType.ALIS, LocalDate.now(), vendor, depo, null, null,
                List.of(new NewLine(item, false, "Alım", new BigDecimal("10"), null,
                        new BigDecimal("80"), BigDecimal.ZERO, new BigDecimal("20"))),
                List.of()));

        assertThatThrownBy(() -> eInvoice.generate(alis.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("satış");
    }

    @Test
    void satirsiz_veya_bulunamayan_fatura_hata_verir() {
        assertThatThrownBy(() -> eInvoice.generate(Long.MAX_VALUE))
                .isInstanceOf(com.beautysalonapp.core.error.NotFoundException.class);
    }
}
