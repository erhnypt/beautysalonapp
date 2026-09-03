package com.beautysalonapp.modules.contract.domain;

import java.time.LocalDate;

public enum InstallmentPeriod {
    AYLIK {
        @Override
        public LocalDate advance(LocalDate first, int steps) {
            // LocalDate.plusMonths ay sonunu kırpar: 31 Ocak + 1 ay = 28/29 Şubat
            return first.plusMonths(steps);
        }
    },
    HAFTALIK {
        @Override
        public LocalDate advance(LocalDate first, int steps) {
            return first.plusWeeks(steps);
        }
    };

    /** İlk vadeden {@code steps} dönem sonrasının tarihi ({@code steps = 0} → ilk vade). */
    public abstract LocalDate advance(LocalDate first, int steps);
}
