package com.beautysalonapp.modules.invoice.domain;

import com.beautysalonapp.modules.stock.domain.MovementDirection;

/** Fatura türü (§9.6). */
public enum InvoiceType {
    ALIS(MovementDirection.IN, false),
    SATIS(MovementDirection.OUT, true),
    PERAKENDE(MovementDirection.OUT, true),
    IADE_ALIS(MovementDirection.OUT, true),
    IADE_SATIS(MovementDirection.IN, false);

    private final MovementDirection stockDirection;
    /** Cari borç mu (true) yoksa alacak mı (false)? */
    private final boolean partyDebit;

    InvoiceType(MovementDirection stockDirection, boolean partyDebit) {
        this.stockDirection = stockDirection;
        this.partyDebit = partyDebit;
    }

    public MovementDirection stockDirection() {
        return stockDirection;
    }

    public boolean isPartyDebit() {
        return partyDebit;
    }

    public boolean isPurchaseSide() {
        return this == ALIS || this == IADE_SATIS;
    }
}
