package com.beautysalonapp.modules.finance.domain;

import java.util.Set;

/** Çek portföy durumu geçişleri (§10.7). */
public enum ChequeStatus {
    PORTFOYDE,
    BANKAYA_TAHSILE,
    CIROLANDI,
    TAHSIL_EDILDI,
    KARSILIKSIZ,
    IADE;

    private static final java.util.Map<ChequeStatus, Set<ChequeStatus>> ALLOWED = java.util.Map.of(
            PORTFOYDE, Set.of(BANKAYA_TAHSILE, CIROLANDI, TAHSIL_EDILDI, KARSILIKSIZ, IADE),
            BANKAYA_TAHSILE, Set.of(TAHSIL_EDILDI, KARSILIKSIZ, PORTFOYDE),
            CIROLANDI, Set.of(KARSILIKSIZ, IADE),
            TAHSIL_EDILDI, Set.of(),
            KARSILIKSIZ, Set.of(IADE, PORTFOYDE),
            IADE, Set.of()
    );

    public boolean canTransitionTo(ChequeStatus next) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(next);
    }

    /** Bu durumdaki çek müşteri risk bakiyesine dahil mi? */
    public boolean countsInRisk() {
        return this == PORTFOYDE || this == BANKAYA_TAHSILE;
    }
}
