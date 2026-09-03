package com.beautysalonapp.modules.party.domain;

/** Taraf türü (§9.2). Bir kişi aynı anda birden çok rol taşıyabilir (ör. hem müşteri hem satıcı) — o durumda ayrı party kayıtları veya çoklu hesap kullanılır. */
public enum PartyType {
    MUSTERI,
    SATICI,
    PERSONEL,
    PERAKENDE
}
