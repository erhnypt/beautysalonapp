package com.beautysalonapp.modules.staff.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Prim tutarı hesabı — saf domain, test edilebilir. */
public final class CommissionCalc {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private CommissionCalc() {
    }

    public static BigDecimal amount(CommissionBasis basis, BigDecimal value, BigDecimal baseAmount) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException("Prim değeri negatif olamaz");
        }
        return switch (basis) {
            case AMOUNT -> value.setScale(2, RoundingMode.HALF_UP);
            case RATE -> {
                BigDecimal b = baseAmount == null ? BigDecimal.ZERO : baseAmount;
                yield b.multiply(value).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            }
        };
    }
}
