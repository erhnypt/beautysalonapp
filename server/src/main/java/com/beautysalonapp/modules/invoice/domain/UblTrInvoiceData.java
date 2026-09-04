package com.beautysalonapp.modules.invoice.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * {@link UblTrInvoiceBuilder} için girdi modeli — GİB e-Fatura/e-Arşiv temel alanlarını
 * (UBL-TR 1.2 profili) taşır. Bu, gerçek bir "özel entegratör" gönderimi DEĞİLDİR;
 * yalnızca standarda uygun XML **üretir** (CLAUDE.md #1 — dışarıya hiçbir çağrı yapılmaz).
 * bkz. docs/modules/e-fatura.md.
 */
public record UblTrInvoiceData(
        String uuid,
        String docNo,
        LocalDate issueDate,
        LocalTime issueTime,
        String invoiceTypeCode,
        String currency,
        Party seller,
        Party buyer,
        List<Line> lines,
        BigDecimal lineExtensionTotal,
        BigDecimal allowanceTotal,
        BigDecimal taxExclusiveTotal,
        BigDecimal taxTotal,
        BigDecimal payableTotal) {

    public UblTrInvoiceData {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }

    /** Vergi kimliği (VKN, 10 hane) veya TC kimlik no (11 hane) — biri dolu olmalı. */
    public record Party(
            String taxId,
            String tcNo,
            String title,
            String address,
            String city,
            String district,
            String postcode) {

        public boolean isIndividual() {
            return (taxId == null || taxId.isBlank()) && tcNo != null && !tcNo.isBlank();
        }

        public String identifier() {
            return isIndividual() ? tcNo : taxId;
        }
    }

    public record Line(
            int lineNo,
            String description,
            BigDecimal quantity,
            String unitCode,
            BigDecimal unitPrice,
            BigDecimal vatRate,
            BigDecimal lineNet,
            BigDecimal lineVat,
            BigDecimal lineTotal) {
    }
}
