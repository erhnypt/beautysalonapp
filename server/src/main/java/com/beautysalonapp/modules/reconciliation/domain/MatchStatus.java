package com.beautysalonapp.modules.reconciliation.domain;

/** Bir ekstre satırının mutabakat durumu. */
public enum MatchStatus {
    /** Henüz bir kasa hareketine bağlanmadı. */
    UNMATCHED,
    /** Var olan bir {@code cash_transaction} ile eşleşti. */
    MATCHED,
    /** Kullanıcı satırı bilinçli olarak yok saydı (açılış kaydı, çift kayıt vb.). */
    IGNORED,
    /** Satırdan yeni bir {@code cash_transaction} üretildi ve ona bağlandı. */
    CREATED;

    public boolean isResolved() {
        return this != UNMATCHED;
    }
}
