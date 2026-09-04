package com.beautysalonapp.modules.invoice.domain;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;

/**
 * UBL-TR 1.2 profiline uygun (GİB e-Fatura/e-Arşiv temel şeması) Invoice XML'i üretir.
 * Framework'süz, saf: dışarıya hiçbir HTTP çağrısı yapmaz, imzalamaz, göndermez
 * (CLAUDE.md #1). Çıktı, işletmenin seçtiği bir özel entegratöre elle/API ile
 * yüklenmek üzere **hazırlık** amaçlıdır — bkz. docs/modules/e-fatura.md.
 */
public final class UblTrInvoiceBuilder {

    private static final String NS_INVOICE = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";
    private static final String NS_CAC = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
    private static final String NS_CBC = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";

    private UblTrInvoiceBuilder() {
    }

    public static String build(UblTrInvoiceData data) {
        if (data.lines().isEmpty()) {
            throw new IllegalArgumentException("En az bir fatura satırı gerekli");
        }
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setXIncludeAware(false);
            dbf.setExpandEntityReferences(false);
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.newDocument();

            Element root = doc.createElementNS(NS_INVOICE, "Invoice");
            root.setAttribute("xmlns:cac", NS_CAC);
            root.setAttribute("xmlns:cbc", NS_CBC);
            doc.appendChild(root);

            cbc(doc, root, "UBLVersionID", "2.1");
            cbc(doc, root, "CustomizationID", "TR1.2");
            cbc(doc, root, "ProfileID", "TEMELFATURA");
            cbc(doc, root, "ID", data.docNo());
            cbc(doc, root, "UUID", data.uuid());
            cbc(doc, root, "IssueDate", data.issueDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
            cbc(doc, root, "IssueTime", data.issueTime().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            cbc(doc, root, "InvoiceTypeCode", data.invoiceTypeCode());
            cbc(doc, root, "DocumentCurrencyCode", data.currency());
            cbc(doc, root, "LineCountNumeric", String.valueOf(data.lines().size()));

            root.appendChild(party(doc, "AccountingSupplierParty", data.seller()));
            root.appendChild(party(doc, "AccountingCustomerParty", data.buyer()));
            root.appendChild(taxTotal(doc, data.taxExclusiveTotal(), data.taxTotal()));
            root.appendChild(legalMonetaryTotal(doc, data));

            for (UblTrInvoiceData.Line l : data.lines()) {
                root.appendChild(invoiceLine(doc, data.currency(), l));
            }

            return serialize(doc);
        } catch (Exception e) {
            throw new IllegalStateException("UBL-TR XML üretilemedi: " + e.getMessage(), e);
        }
    }

    private static Element party(Document doc, String wrapperName, UblTrInvoiceData.Party p) {
        Element wrapper = doc.createElementNS(NS_CAC, "cac:" + wrapperName);
        Element party = doc.createElementNS(NS_CAC, "cac:Party");
        wrapper.appendChild(party);

        Element idWrap = doc.createElementNS(NS_CAC, "cac:PartyIdentification");
        Element id = doc.createElementNS(NS_CBC, "cbc:ID");
        id.setAttribute("schemeID", p.isIndividual() ? "TCKN" : "VKN");
        id.setTextContent(nz(p.identifier()));
        idWrap.appendChild(id);
        party.appendChild(idWrap);

        Element nameWrap = doc.createElementNS(NS_CAC, "cac:PartyName");
        cbc(doc, nameWrap, "Name", p.title());
        party.appendChild(nameWrap);

        Element addrWrap = doc.createElementNS(NS_CAC, "cac:PostalAddress");
        cbc(doc, addrWrap, "StreetName", nz(p.address()));
        cbc(doc, addrWrap, "CitySubdivisionName", nz(p.district()));
        cbc(doc, addrWrap, "CityName", nz(p.city()));
        cbc(doc, addrWrap, "PostalZone", nz(p.postcode()));
        Element country = doc.createElementNS(NS_CAC, "cac:Country");
        cbc(doc, country, "Name", "Türkiye");
        addrWrap.appendChild(country);
        party.appendChild(addrWrap);

        return wrapper;
    }

    private static Element taxTotal(Document doc, BigDecimal taxable, BigDecimal tax) {
        Element wrap = doc.createElementNS(NS_CAC, "cac:TaxTotal");
        cbc(doc, wrap, "TaxAmount", money(tax));
        Element sub = doc.createElementNS(NS_CAC, "cac:TaxSubtotal");
        cbc(doc, sub, "TaxableAmount", money(taxable));
        cbc(doc, sub, "TaxAmount", money(tax));
        Element cat = doc.createElementNS(NS_CAC, "cac:TaxCategory");
        Element scheme = doc.createElementNS(NS_CAC, "cac:TaxScheme");
        cbc(doc, scheme, "Name", "KDV");
        cbc(doc, scheme, "TaxTypeCode", "0015");
        cat.appendChild(scheme);
        sub.appendChild(cat);
        wrap.appendChild(sub);
        return wrap;
    }

    private static Element legalMonetaryTotal(Document doc, UblTrInvoiceData data) {
        Element wrap = doc.createElementNS(NS_CAC, "cac:LegalMonetaryTotal");
        cbc(doc, wrap, "LineExtensionAmount", money(data.lineExtensionTotal()));
        cbc(doc, wrap, "TaxExclusiveAmount", money(data.taxExclusiveTotal()));
        cbc(doc, wrap, "TaxInclusiveAmount", money(data.payableTotal()));
        cbc(doc, wrap, "AllowanceTotalAmount", money(data.allowanceTotal()));
        cbc(doc, wrap, "PayableAmount", money(data.payableTotal()));
        return wrap;
    }

    private static Element invoiceLine(Document doc, String currency, UblTrInvoiceData.Line l) {
        Element wrap = doc.createElementNS(NS_CAC, "cac:InvoiceLine");
        cbc(doc, wrap, "ID", String.valueOf(l.lineNo()));
        Element qty = doc.createElementNS(NS_CBC, "cbc:InvoicedQuantity");
        qty.setAttribute("unitCode", nzUnit(l.unitCode()));
        qty.setTextContent(qty(l.quantity()));
        wrap.appendChild(qty);
        cbc(doc, wrap, "LineExtensionAmount", money(l.lineNet()));

        Element lineTax = doc.createElementNS(NS_CAC, "cac:TaxTotal");
        cbc(doc, lineTax, "TaxAmount", money(l.lineVat()));
        Element lineTaxSub = doc.createElementNS(NS_CAC, "cac:TaxSubtotal");
        cbc(doc, lineTaxSub, "TaxableAmount", money(l.lineNet()));
        cbc(doc, lineTaxSub, "TaxAmount", money(l.lineVat()));
        cbc(doc, lineTaxSub, "Percent", money(l.vatRate()));
        Element lineCat = doc.createElementNS(NS_CAC, "cac:TaxCategory");
        Element lineScheme = doc.createElementNS(NS_CAC, "cac:TaxScheme");
        cbc(doc, lineScheme, "Name", "KDV");
        lineCat.appendChild(lineScheme);
        lineTaxSub.appendChild(lineCat);
        lineTax.appendChild(lineTaxSub);
        wrap.appendChild(lineTax);

        Element item = doc.createElementNS(NS_CAC, "cac:Item");
        cbc(doc, item, "Name", nz(l.description()));
        wrap.appendChild(item);

        Element price = doc.createElementNS(NS_CAC, "cac:Price");
        cbc(doc, price, "PriceAmount", money(l.unitPrice()));
        wrap.appendChild(price);

        return wrap;
    }

    private static void cbc(Document doc, Element parent, String name, String value) {
        Element el = doc.createElementNS(NS_CBC, "cbc:" + name);
        el.setTextContent(value == null ? "" : value);
        parent.appendChild(el);
    }

    private static String money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String qty(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(4, RoundingMode.HALF_UP).toPlainString();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String nzUnit(String s) {
        return (s == null || s.isBlank()) ? "C62" : s; // C62 = UN/ECE "adet"
    }

    private static String serialize(Document doc) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        Transformer t = tf.newTransformer();
        t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        t.setOutputProperty(OutputKeys.INDENT, "yes");
        t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        StringWriter sw = new StringWriter();
        t.transform(new DOMSource(doc), new StreamResult(sw));
        return sw.toString();
    }
}
