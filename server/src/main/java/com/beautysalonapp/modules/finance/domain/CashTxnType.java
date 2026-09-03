package com.beautysalonapp.modules.finance.domain;

/** Kasa hareket türü (§10.6). */
public enum CashTxnType {
    COLLECTION,   // tahsilat: kasaya +, cariye alacak
    PAYMENT,      // tediye:   kasadan -, cariye borç
    TRANSFER,     // virman:   account_id'den counter_account_id'ye
    FX_BUY,       // döviz alım
    FX_SELL       // döviz satım
}
