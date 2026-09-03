package com.beautysalonapp.modules.finance.application;

import com.beautysalonapp.core.domain.Money;
import com.beautysalonapp.modules.finance.domain.ChequeType;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Çek portu (CLAUDE.md #5). Fatura modülü çek tahsilat/tediyesini bununla portföye alır. */
public interface ChequePort {

    /**
     * Fatura ödemesinden çek kaydı. Müşteri çeki (satış) cari alacak, firma çeki (alış) cari borç yazar.
     * Nakit girişi çek tahsil edilene kadar oluşmaz.
     */
    long register(RegisterChequeCommand c);

    /** Müşteriden alınan, henüz tahsil/ciro edilmemiş çeklerin toplamı (§10.7 risk bakiyesi). */
    Money customerRiskBalance(long partyAccountId);

    record RegisterChequeCommand(
            String chequeNo,
            ChequeType type,
            String bankName,
            String drawer,
            LocalDate dueDate,
            BigDecimal amount,
            String currency,
            Long partyAccountId,
            String sourceDoc) {
    }
}
