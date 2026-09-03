package com.beautysalonapp.modules.notification.domain;

/** Bildirim türü (§9.10). {@code commercial()} true ise İYS izni zorunludur. */
public enum NotificationType {
    DOGUM_GUNU(true),
    YILDONUMU(true),
    RANDEVU_HATIRLATMA(false),
    BORC(false),
    TAKSIT(false),
    KAMPANYA(true),
    GUNLUK_RAPOR(false);

    private final boolean commercial;

    NotificationType(boolean commercial) {
        this.commercial = commercial;
    }

    /** Ticari ileti mi? (İYS + kanal onayı gerekir) */
    public boolean commercial() {
        return commercial;
    }

    /** Yalnızca iç kullanıcıya mı gider? (müşteri onayı aranmaz) */
    public boolean internalOnly() {
        return this == GUNLUK_RAPOR;
    }
}
