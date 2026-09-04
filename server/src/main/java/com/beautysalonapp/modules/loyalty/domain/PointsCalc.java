package com.beautysalonapp.modules.loyalty.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Puan hesabı (§10.11). Saf domain, %100 test. Puanlar tam sayıdır. */
public final class PointsCalc {

    private PointsCalc() {
    }

    /** Harcamadan kazanılan taban puan: floor(harcama * earnRate). */
    public static int earn(BigDecimal spendAmount, BigDecimal earnRate) {
        if (spendAmount == null || earnRate == null
                || spendAmount.signum() <= 0 || earnRate.signum() <= 0) {
            return 0;
        }
        return spendAmount.multiply(earnRate).setScale(0, RoundingMode.FLOOR).intValueExact();
    }

    /** Puanların TL karşılığı: puan * pointToCurrency (2 hane). */
    public static BigDecimal redemptionValue(int points, BigDecimal pointToCurrency) {
        if (points <= 0 || pointToCurrency == null || pointToCurrency.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(points).multiply(pointToCurrency).setScale(2, RoundingMode.HALF_UP);
    }

    /** Bir faturaya uygulanabilecek maksimum puan: min(bakiye, floor(tutar / pointToCurrency)). */
    public static int maxRedeemable(int balance, BigDecimal invoiceTotal, BigDecimal pointToCurrency) {
        if (balance <= 0 || invoiceTotal == null || invoiceTotal.signum() <= 0
                || pointToCurrency == null || pointToCurrency.signum() <= 0) {
            return 0;
        }
        int byTotal = invoiceTotal.divide(pointToCurrency, 0, RoundingMode.FLOOR).intValueExact();
        return Math.min(balance, byTotal);
    }
}
