package com.beautysalonapp.modules.party.domain;

/**
 * Cari hesap türü (§9.2, plan Madde 3).
 * {@link #RETAIL} hesaplar ayrı defterde tutulur; normal cari bakiye/raporlara karışmaz.
 */
public enum AccountKind {
    NORMAL,
    RETAIL
}
