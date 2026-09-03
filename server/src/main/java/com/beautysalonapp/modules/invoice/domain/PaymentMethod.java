package com.beautysalonapp.modules.invoice.domain;

/** Fatura ödeme dağılımı yöntemi (§9.6). */
public enum PaymentMethod {
    CASH,     // kasa
    CARD,     // POS
    CHEQUE,   // çek portföyü
    CREDIT    // vadeli — cari üzerinde kalır
}
