package com.beautysalonapp.licensing.domain;

/**
 * Lisansla açılıp kapatılabilen işlevsel modüller (plan §1.2). Çekirdek (kullanıcı,
 * ayar, yedek, log, lisans) her zaman açıktır ve burada yer almaz.
 */
public enum ModuleCode {
    STOCK,
    STAFF,
    PARTY,
    FINANCE,
    INVOICE,
    APPOINTMENT,
    CONTRACT,
    LOYALTY,
    REPORTING,
    SMS,
    EMAIL,
    CENTRAL   // merkezi çok şubeli yönetim (v2)
}
