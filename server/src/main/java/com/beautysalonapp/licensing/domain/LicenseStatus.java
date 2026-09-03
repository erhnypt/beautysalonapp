package com.beautysalonapp.licensing.domain;

/**
 * Kademeli kısıtlama merdiveni (§6.4). Sıra, kısıtlama şiddetini gösterir.
 */
public enum LicenseStatus {

    /** Lisans geçerli, tam işlevsellik. */
    ACTIVE(false),

    /** Bitişe ≤ 7 gün: sarı uyarı bandı, işlem serbest. */
    EXPIRING(false),

    /** notAfter geçti ama graceDays içinde: kırmızı bant + modal, işlem serbest. */
    GRACE(false),

    /** Grace bitti veya sunucu SUSPENDED: yeni kayıt YOK; görüntüleme, rapor, yedek, dışa aktarma VAR. */
    READ_ONLY(true),

    /** READ_ONLY'den readOnlyDays sonra veya REVOKED: yalnızca lisans ekranı + tam veri dışa aktarma. */
    LOCKED(true),

    /** Saat manipülasyonu / imza bozulması: READ_ONLY + zorunlu online doğrulama. */
    TAMPERED(true);

    private final boolean writesBlocked;

    LicenseStatus(boolean writesBlocked) {
        this.writesBlocked = writesBlocked;
    }

    /** Bu durumda veri değiştiren (POST/PUT/PATCH/DELETE) istekler engellenir mi? */
    public boolean isWritesBlocked() {
        return writesBlocked;
    }
}
