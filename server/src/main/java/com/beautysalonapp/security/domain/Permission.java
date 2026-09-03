package com.beautysalonapp.security.domain;

/**
 * Yetki = modül × işlem. Spring Security authority stringi olarak {@code name()} kullanılır
 * (ör. {@code STOCK_EDIT}). Rol → yetki eşlemesi {@code role_permission} tablosundadır.
 */
public enum Permission {

    // Çekirdek yönetim
    SETTINGS_VIEW, SETTINGS_EDIT,
    USER_VIEW, USER_EDIT,
    AUDIT_VIEW,
    LICENSE_MANAGE,
    BACKUP_RUN, BACKUP_RESTORE,
    DATA_EXPORT,          // LOCKED durumda dahi çalışır

    // Stok
    STOCK_VIEW, STOCK_ADD, STOCK_EDIT, STOCK_DELETE, STOCK_REPORT,

    // Personel
    STAFF_VIEW, STAFF_ADD, STAFF_EDIT, STAFF_DELETE, STAFF_REPORT,

    // Cari
    PARTY_VIEW, PARTY_ADD, PARTY_EDIT, PARTY_DELETE, PARTY_REPORT,

    // Finans (kasa, banka, pos, çek, gelir-gider)
    FINANCE_VIEW, FINANCE_ADD, FINANCE_EDIT, FINANCE_VOID, FINANCE_REPORT,

    // Fatura
    INVOICE_VIEW, INVOICE_ADD, INVOICE_EDIT, INVOICE_VOID, INVOICE_REPORT,

    // Randevu
    APPOINTMENT_VIEW, APPOINTMENT_ADD, APPOINTMENT_EDIT, APPOINTMENT_CANCEL, APPOINTMENT_REPORT,

    // Sözleşme / taksit
    CONTRACT_VIEW, CONTRACT_ADD, CONTRACT_EDIT, CONTRACT_VOID, CONTRACT_REPORT,

    // Sadakat
    LOYALTY_VIEW, LOYALTY_ADD, LOYALTY_EDIT, LOYALTY_REPORT,

    // Bildirim
    NOTIFICATION_VIEW, NOTIFICATION_SEND, NOTIFICATION_TEMPLATE_EDIT,

    // Raporlama merkezi
    REPORTING_VIEW
}
