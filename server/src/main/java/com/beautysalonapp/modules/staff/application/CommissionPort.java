package com.beautysalonapp.modules.staff.application;

import com.beautysalonapp.modules.staff.domain.CommissionScope;

import java.math.BigDecimal;

/**
 * Prim tahakkuk portu (CLAUDE.md #5). Randevu ({@code GELDI}) ve fatura (satış) modülleri
 * bunu çağırarak ilgili personele prim tahakkuk ettirir. İdempotent:
 * aynı {@code (staffId, sourceType, sourceRef)} ikinci kez işlenmez.
 */
public interface CommissionPort {

    void accrue(AccrueCommand c);

    record AccrueCommand(
            long staffPartyId,
            CommissionScope scope,
            BigDecimal baseAmount,
            String sourceType,   // APPOINTMENT | INVOICE
            String sourceRef,
            java.time.LocalDate onDate) {
    }
}
