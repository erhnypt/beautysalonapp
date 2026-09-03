package com.beautysalonapp.modules.invoice;

import com.beautysalonapp.core.error.BusinessRuleException;
import com.beautysalonapp.modules.finance.application.FinanceService;
import com.beautysalonapp.modules.invoice.application.InvoiceService;
import com.beautysalonapp.modules.invoice.application.InvoiceService.CreateInvoiceCommand;
import com.beautysalonapp.modules.invoice.application.InvoiceService.NewLine;
import com.beautysalonapp.modules.invoice.application.InvoiceService.NewPayment;
import com.beautysalonapp.modules.invoice.domain.Invoice;
import com.beautysalonapp.modules.invoice.domain.InvoiceStatus;
import com.beautysalonapp.modules.invoice.domain.InvoiceType;
import com.beautysalonapp.modules.invoice.domain.PaymentMethod;
import com.beautysalonapp.modules.party.application.PartyLedger;
import com.beautysalonapp.modules.party.application.PartyService;
import com.beautysalonapp.modules.party.domain.AccountKind;
import com.beautysalonapp.modules.party.domain.PartyType;
import com.beautysalonapp.modules.stock.application.StockPort;
import com.beautysalonapp.modules.stock.application.StockService;
import com.beautysalonapp.modules.stock.domain.ItemType;
import com.beautysalonapp.settings.application.SettingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class InvoiceServiceTest {

    @Autowired InvoiceService invoices;
    @Autowired PartyService partyService;
    @Autowired PartyLedger partyLedger;
    @Autowired FinanceService finance;
    @Autowired StockService stock;
    @Autowired StockPort stockPort;
    @Autowired SettingService settings;

    private long customer() {
        return partyService.create(PartyType.MUSTERI, null, "Fatura Müşteri " + System.nanoTime(),
                null, null, null, null, null, null).getId();
    }

    private long emtiaItem() {
        return stock.createItem(null, "Ürün " + System.nanoTime(), ItemType.EMTIA, "ADET",
                new BigDecimal("20"), null, null, new BigDecimal("100")).getId();
    }

    @Test
    void satis_faturasi_stok_cikarir_cariyi_borclandirir_nakit_tahsil_eder() {
        long cust = customer();
        long acc = partyLedger.resolveAccount(cust, AccountKind.NORMAL, "TRY");
        long item = emtiaItem();
        long depo = stockPort.defaultWarehouseId();
        // önce stok girişi
        long unit = stock.baseUnitId(item);
        stock.record(LocalDate.now(), item, depo, com.beautysalonapp.modules.stock.domain.MovementDirection.IN,
                unit, new BigDecimal("50"), new BigDecimal("60"), "TEST", "OPEN-" + item, "1", null);
        long kasa = finance.defaultCashAccountId();
        BigDecimal kasaBefore = finance.accountBalance(kasa).getAmount();

        Invoice inv = invoices.create(new CreateInvoiceCommand(InvoiceType.SATIS, LocalDate.now(), cust, depo,
                null, null,
                List.of(new NewLine(item, false, "Ürün satışı", new BigDecimal("2"), null,
                        new BigDecimal("100"), BigDecimal.ZERO, new BigDecimal("20"))),
                List.of(new NewPayment(PaymentMethod.CASH, new BigDecimal("240"), null, null, null, null, null))));

        assertThat(inv.getGrandTotal()).isEqualByComparingTo("240.00");
        assertThat(stockPort.onHandBase(item, depo)).isEqualByComparingTo("48"); // 50 - 2
        assertThat(finance.accountBalance(kasa).getAmount()).isEqualByComparingTo(kasaBefore.add(new BigDecimal("240")));
        assertThat(partyLedger.balance(acc).getAmount()).isEqualByComparingTo("0.0000"); // borç 240 - tahsilat 240
    }

    @Test
    void vadeli_satis_cari_borcta_kalir() {
        long cust = customer();
        long acc = partyLedger.resolveAccount(cust, AccountKind.NORMAL, "TRY");
        Invoice inv = invoices.create(new CreateInvoiceCommand(InvoiceType.SATIS, LocalDate.now(), cust, null,
                null, null,
                List.of(new NewLine(null, true, "Danışmanlık", BigDecimal.ONE, null,
                        new BigDecimal("500"), BigDecimal.ZERO, new BigDecimal("20"))),
                List.of()));
        assertThat(inv.getGrandTotal()).isEqualByComparingTo("600.00");
        assertThat(partyLedger.balance(acc).getAmount()).isEqualByComparingTo("600.0000");
    }

    @Test
    void alis_faturasi_stok_girer_maliyet_ile_cari_alacaklanir() {
        long vendor = partyService.create(PartyType.SATICI, null, "Tedarikçi " + System.nanoTime(),
                null, null, null, null, null, null).getId();
        long acc = partyLedger.resolveAccount(vendor, AccountKind.NORMAL, "TRY");
        long item = emtiaItem();
        long depo = stockPort.defaultWarehouseId();

        invoices.create(new CreateInvoiceCommand(InvoiceType.ALIS, LocalDate.now(), vendor, depo, null, null,
                List.of(new NewLine(item, false, "Alım", new BigDecimal("10"), null,
                        new BigDecimal("80"), BigDecimal.ZERO, new BigDecimal("20"))),
                List.of()));

        assertThat(stockPort.onHandBase(item, depo)).isEqualByComparingTo("10");
        var level = stock.levelsOf(item).stream().filter(l -> l.getWarehouseId() == depo).findFirst().orElseThrow();
        assertThat(level.getAvgCost()).isEqualByComparingTo("80.0000"); // net birim maliyet
        assertThat(partyLedger.balance(acc).getAmount()).isEqualByComparingTo("-960.0000"); // 10*80*1.2 alacak
    }

    @Test
    void perakende_fis_no_zorunlulugu_ayarla() {
        settings.put("invoice.retail.requireReceiptNo", "true");
        try {
            long cust = customer();
            var cmd = new CreateInvoiceCommand(InvoiceType.PERAKENDE, LocalDate.now(), cust, null, null, null,
                    List.of(new NewLine(null, true, "Ürün", BigDecimal.ONE, null,
                            new BigDecimal("100"), BigDecimal.ZERO, new BigDecimal("20"))),
                    List.of());
            assertThatThrownBy(() -> invoices.create(cmd)).isInstanceOf(BusinessRuleException.class);

            var withNo = new CreateInvoiceCommand(InvoiceType.PERAKENDE, LocalDate.now(), cust, null, "FIS-001", null,
                    List.of(new NewLine(null, true, "Ürün", BigDecimal.ONE, null,
                            new BigDecimal("100"), BigDecimal.ZERO, new BigDecimal("20"))),
                    List.of());
            assertThat(invoices.create(withNo).getCashRegisterReceiptNo()).isEqualTo("FIS-001");
        } finally {
            settings.put("invoice.retail.requireReceiptNo", "false");
        }
    }

    @Test
    void iptal_stok_cari_ve_nakiti_geri_alir() {
        long cust = customer();
        long acc = partyLedger.resolveAccount(cust, AccountKind.NORMAL, "TRY");
        long item = emtiaItem();
        long depo = stockPort.defaultWarehouseId();
        long unit = stock.baseUnitId(item);
        stock.record(LocalDate.now(), item, depo, com.beautysalonapp.modules.stock.domain.MovementDirection.IN,
                unit, new BigDecimal("20"), new BigDecimal("50"), "TEST", "OPEN2-" + item, "1", null);
        long kasa = finance.defaultCashAccountId();
        BigDecimal kasaBefore = finance.accountBalance(kasa).getAmount();

        Invoice inv = invoices.create(new CreateInvoiceCommand(InvoiceType.SATIS, LocalDate.now(), cust, depo,
                null, null,
                List.of(new NewLine(item, false, "Satış", new BigDecimal("5"), null,
                        new BigDecimal("100"), BigDecimal.ZERO, new BigDecimal("20"))),
                List.of(new NewPayment(PaymentMethod.CASH, new BigDecimal("600"), null, null, null, null, null))));

        invoices.voidInvoice(inv.getId(), "yanlış fatura");

        assertThat(invoices.get(inv.getId()).getStatus()).isEqualTo(InvoiceStatus.VOIDED);
        assertThat(stockPort.onHandBase(item, depo)).isEqualByComparingTo("20"); // geri geldi
        assertThat(finance.accountBalance(kasa).getAmount()).isEqualByComparingTo(kasaBefore);
        assertThat(partyLedger.balance(acc).getAmount()).isEqualByComparingTo("0.0000");
    }

    @Test
    void cek_ile_odeme_cek_portfoye_alinir_ve_cari_azalir() {
        long cust = customer();
        long acc = partyLedger.resolveAccount(cust, AccountKind.NORMAL, "TRY");
        Invoice inv = invoices.create(new CreateInvoiceCommand(InvoiceType.SATIS, LocalDate.now(), cust, null,
                null, null,
                List.of(new NewLine(null, true, "Hizmet", BigDecimal.ONE, null,
                        new BigDecimal("1000"), BigDecimal.ZERO, BigDecimal.ZERO)),
                List.of(new NewPayment(PaymentMethod.CHEQUE, new BigDecimal("1000"), null, "CK-1", "X Bank",
                        LocalDate.now().plusDays(45), null))));

        assertThat(inv.getGrandTotal()).isEqualByComparingTo("1000.00");
        // fatura borç 1000 - çek alacak 1000 => 0
        assertThat(partyLedger.balance(acc).getAmount()).isEqualByComparingTo("0.0000");
        assertThat(invoices.payments(inv.getId())).anySatisfy(p -> assertThat(p.getChequeId()).isNotNull());
    }

    @Test
    void odeme_fatura_tutarini_asamaz() {
        long cust = customer();
        var cmd = new CreateInvoiceCommand(InvoiceType.SATIS, LocalDate.now(), cust, null, null, null,
                List.of(new NewLine(null, true, "X", BigDecimal.ONE, null, new BigDecimal("100"),
                        BigDecimal.ZERO, BigDecimal.ZERO)),
                List.of(new NewPayment(PaymentMethod.CASH, new BigDecimal("150"), null, null, null, null, null)));
        assertThatThrownBy(() -> invoices.create(cmd)).isInstanceOf(BusinessRuleException.class);
    }
}
