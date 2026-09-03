package com.beautysalonapp.modules.invoice.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Fatura satır ve toplam hesabı (§10.5). Saf domain, %100 test.
 *
 * <pre>
 * net   = qty * unitPrice * (1 - discountRate/100)      (2 hane)
 * vat   = net * vatRate/100                              (2 hane)
 * total = net + vat
 * </pre>
 * Fatura toplamları satır bazında yuvarlanmış değerlerin toplamıdır (kuruş tutarlılığı).
 */
public final class InvoiceCalculator {

    private static final int SCALE = 2;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private InvoiceCalculator() {
    }

    public record LineInput(BigDecimal quantity, BigDecimal unitPrice,
                            BigDecimal discountRate, BigDecimal vatRate) {}

    public record LineResult(BigDecimal net, BigDecimal vat, BigDecimal total) {}

    public record Totals(BigDecimal subtotal, BigDecimal discountTotal,
                         BigDecimal vatTotal, BigDecimal grandTotal, List<LineResult> lines) {}

    public static LineResult line(LineInput in) {
        BigDecimal qty = req(in.quantity(), "quantity");
        BigDecimal price = req(in.unitPrice(), "unitPrice");
        BigDecimal discRate = in.discountRate() == null ? BigDecimal.ZERO : in.discountRate();
        BigDecimal vatRate = in.vatRate() == null ? BigDecimal.ZERO : in.vatRate();
        if (qty.signum() <= 0) {
            throw new IllegalArgumentException("Miktar pozitif olmalı");
        }
        if (discRate.signum() < 0 || discRate.compareTo(HUNDRED) > 0) {
            throw new IllegalArgumentException("İndirim oranı 0-100 arası olmalı");
        }

        BigDecimal gross = qty.multiply(price);
        BigDecimal discountFactor = BigDecimal.ONE.subtract(discRate.divide(HUNDRED, 6, RoundingMode.HALF_UP));
        BigDecimal net = gross.multiply(discountFactor).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal vat = net.multiply(vatRate).divide(HUNDRED, SCALE, RoundingMode.HALF_UP);
        BigDecimal total = net.add(vat);
        return new LineResult(net, vat, total);
    }

    /** Satır indirimi öncesi brüt (subtotal) ve indirim tutarı için ayrı hesap. */
    public static BigDecimal grossOf(LineInput in) {
        return req(in.quantity(), "quantity").multiply(req(in.unitPrice(), "unitPrice"))
                .setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static Totals totals(List<LineInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            throw new IllegalArgumentException("Fatura en az bir satır içermeli");
        }
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal discountTotal = BigDecimal.ZERO;
        BigDecimal vatTotal = BigDecimal.ZERO;
        BigDecimal grand = BigDecimal.ZERO;
        List<LineResult> results = new ArrayList<>(inputs.size());
        for (LineInput in : inputs) {
            BigDecimal gross = grossOf(in);
            LineResult r = line(in);
            subtotal = subtotal.add(gross);
            discountTotal = discountTotal.add(gross.subtract(r.net()));
            vatTotal = vatTotal.add(r.vat());
            grand = grand.add(r.total());
            results.add(r);
        }
        return new Totals(subtotal, discountTotal, vatTotal, grand, results);
    }

    private static BigDecimal req(BigDecimal v, String name) {
        if (v == null) {
            throw new IllegalArgumentException(name + " zorunlu");
        }
        return v;
    }
}
