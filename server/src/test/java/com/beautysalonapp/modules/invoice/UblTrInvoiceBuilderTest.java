package com.beautysalonapp.modules.invoice;

import com.beautysalonapp.modules.invoice.domain.UblTrInvoiceBuilder;
import com.beautysalonapp.modules.invoice.domain.UblTrInvoiceData;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UblTrInvoiceBuilderTest {

    private static final String NS_CBC = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
    private static final String NS_CAC = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
    private static final String NS_INVOICE = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";

    private UblTrInvoiceData.Party seller() {
        return new UblTrInvoiceData.Party("1234567890", null, "Ana İşletme A.Ş.",
                "Örnek Cad. No:1", "İstanbul", "Kadıköy", "34710");
    }

    private UblTrInvoiceData.Party buyerCompany() {
        return new UblTrInvoiceData.Party("9876543210", null, "Müşteri A.Ş.",
                "Test Sok. No:2", "Ankara", "Çankaya", "06510");
    }

    private UblTrInvoiceData.Line line(int no, String desc, String qty, String price, String vat, String net, String vatAmt, String total) {
        return new UblTrInvoiceData.Line(no, desc, new BigDecimal(qty), "C62",
                new BigDecimal(price), new BigDecimal(vat), new BigDecimal(net), new BigDecimal(vatAmt), new BigDecimal(total));
    }

    private UblTrInvoiceData sample() {
        return new UblTrInvoiceData(
                "550e8400-e29b-41d4-a716-446655440000", "SATIS2026-000001",
                LocalDate.of(2026, 9, 4), LocalTime.of(14, 30, 0), "SATIS", "TRY",
                seller(), buyerCompany(),
                List.of(line(1, "Saç Kesimi", "1.0000", "500.00", "20.00", "500.00", "100.00", "600.00")),
                new BigDecimal("500.00"), new BigDecimal("0.00"), new BigDecimal("500.00"),
                new BigDecimal("100.00"), new BigDecimal("600.00"));
    }

    private Document parse(String xml) throws Exception {
        var dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        return dbf.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private String cbcText(Element parent, String name) {
        NodeList nl = parent.getElementsByTagNameNS(NS_CBC, name);
        return nl.getLength() == 0 ? null : nl.item(0).getTextContent();
    }

    @Test
    void gecerli_ve_iyi_bicimli_xml_uretir() throws Exception {
        String xml = UblTrInvoiceBuilder.build(sample());
        assertThat(xml).startsWith("<?xml");
        Document doc = parse(xml); // parse hatasız geçmeli
        assertThat(doc.getDocumentElement().getNamespaceURI()).isEqualTo(NS_INVOICE);
        assertThat(doc.getDocumentElement().getLocalName()).isEqualTo("Invoice");
    }

    @Test
    void baslik_alanlari_dogru() throws Exception {
        Document doc = parse(UblTrInvoiceBuilder.build(sample()));
        Element root = doc.getDocumentElement();
        assertThat(cbcText(root, "ID")).isEqualTo("SATIS2026-000001");
        assertThat(cbcText(root, "UUID")).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
        assertThat(cbcText(root, "IssueDate")).isEqualTo("2026-09-04");
        assertThat(cbcText(root, "IssueTime")).isEqualTo("14:30:00");
        assertThat(cbcText(root, "InvoiceTypeCode")).isEqualTo("SATIS");
        assertThat(cbcText(root, "DocumentCurrencyCode")).isEqualTo("TRY");
        assertThat(cbcText(root, "LineCountNumeric")).isEqualTo("1");
    }

    @Test
    void satici_ve_alici_taraflari_doldurulur() throws Exception {
        Document doc = parse(UblTrInvoiceBuilder.build(sample()));
        Element supplier = (Element) doc.getElementsByTagNameNS(NS_CAC, "AccountingSupplierParty").item(0);
        Element customer = (Element) doc.getElementsByTagNameNS(NS_CAC, "AccountingCustomerParty").item(0);

        assertThat(cbcText(supplier, "Name")).isEqualTo("Ana İşletme A.Ş.");
        Element supplierId = (Element) supplier.getElementsByTagNameNS(NS_CBC, "ID").item(0);
        assertThat(supplierId.getAttribute("schemeID")).isEqualTo("VKN");
        assertThat(supplierId.getTextContent()).isEqualTo("1234567890");

        assertThat(cbcText(customer, "Name")).isEqualTo("Müşteri A.Ş.");
        assertThat(cbcText(customer, "CityName")).isEqualTo("Ankara");
    }

    @Test
    void bireysel_alici_tckn_ile_isaretlenir() throws Exception {
        UblTrInvoiceData data = new UblTrInvoiceData(
                "uuid-2", "SATIS2026-000002", LocalDate.now(), LocalTime.now(), "SATIS", "TRY",
                seller(), new UblTrInvoiceData.Party(null, "12345678901", "Ayşe Yılmaz", "Ev adresi", "İstanbul", null, null),
                List.of(line(1, "Hizmet", "1", "100.00", "20.00", "100.00", "20.00", "120.00")),
                new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("100.00"),
                new BigDecimal("20.00"), new BigDecimal("120.00"));
        Document doc = parse(UblTrInvoiceBuilder.build(data));
        Element customer = (Element) doc.getElementsByTagNameNS(NS_CAC, "AccountingCustomerParty").item(0);
        Element id = (Element) customer.getElementsByTagNameNS(NS_CBC, "ID").item(0);
        assertThat(id.getAttribute("schemeID")).isEqualTo("TCKN");
        assertThat(id.getTextContent()).isEqualTo("12345678901");
    }

    @Test
    void tutarlar_iki_ondalikla_bicimlenir() throws Exception {
        Document doc = parse(UblTrInvoiceBuilder.build(sample()));
        Element total = (Element) doc.getElementsByTagNameNS(NS_CAC, "LegalMonetaryTotal").item(0);
        assertThat(cbcText(total, "PayableAmount")).isEqualTo("600.00");
        assertThat(cbcText(total, "TaxInclusiveAmount")).isEqualTo("600.00");
        assertThat(cbcText(total, "TaxExclusiveAmount")).isEqualTo("500.00");
        assertThat(cbcText(total, "LineExtensionAmount")).isEqualTo("500.00");
    }

    @Test
    void satir_sayisi_ve_icerigi_dogru() throws Exception {
        Document doc = parse(UblTrInvoiceBuilder.build(sample()));
        NodeList lines = doc.getElementsByTagNameNS(NS_CAC, "InvoiceLine");
        assertThat(lines.getLength()).isEqualTo(1);
        Element l = (Element) lines.item(0);
        assertThat(cbcText(l, "ID")).isEqualTo("1");
        assertThat(cbcText(l, "LineExtensionAmount")).isEqualTo("500.00");
        Element item = (Element) l.getElementsByTagNameNS(NS_CAC, "Item").item(0);
        assertThat(cbcText(item, "Name")).isEqualTo("Saç Kesimi");
    }

    @Test
    void ozel_karakterler_kacisla_yazilir() throws Exception {
        UblTrInvoiceData base = sample();
        UblTrInvoiceData.Party trickySeller = new UblTrInvoiceData.Party(
                base.seller().taxId(), null, "A & B <Güzellik> \"Salonu\"",
                base.seller().address(), base.seller().city(), base.seller().district(), base.seller().postcode());
        UblTrInvoiceData data = new UblTrInvoiceData(base.uuid(), base.docNo(), base.issueDate(), base.issueTime(),
                base.invoiceTypeCode(), base.currency(), trickySeller, base.buyer(), base.lines(),
                base.lineExtensionTotal(), base.allowanceTotal(), base.taxExclusiveTotal(), base.taxTotal(), base.payableTotal());

        String xml = UblTrInvoiceBuilder.build(data);
        Document doc = parse(xml); // XML parser kabul ederse kaçış doğru yapılmış demektir
        Element supplier = (Element) doc.getElementsByTagNameNS(NS_CAC, "AccountingSupplierParty").item(0);
        assertThat(cbcText(supplier, "Name")).isEqualTo("A & B <Güzellik> \"Salonu\"");
    }

    @Test
    void satirsiz_fatura_reddedilir() {
        UblTrInvoiceData data = new UblTrInvoiceData("u", "D1", LocalDate.now(), LocalTime.now(),
                "SATIS", "TRY", seller(), buyerCompany(), List.of(),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        assertThatThrownBy(() -> UblTrInvoiceBuilder.build(data)).isInstanceOf(IllegalArgumentException.class);
    }
}
