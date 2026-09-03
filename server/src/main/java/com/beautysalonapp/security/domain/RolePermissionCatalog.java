package com.beautysalonapp.security.domain;

import java.util.EnumSet;
import java.util.Set;

import static com.beautysalonapp.security.domain.Permission.*;

/**
 * Sistem rollerinin varsayılan yetki kümeleri. Bootstrap sırasında ve
 * "rolleri fabrika ayarına döndür" işleminde kullanılır. Yönetici bu kümeleri
 * daha sonra ekranlardan özelleştirebilir (ADMIN hariç).
 */
public final class RolePermissionCatalog {

    private RolePermissionCatalog() {
    }

    public static Set<Permission> defaultsFor(RoleName role) {
        return switch (role) {
            case ADMIN -> EnumSet.allOf(Permission.class);

            case MUDUR -> EnumSet.of(
                    SETTINGS_VIEW, SETTINGS_EDIT, USER_VIEW, AUDIT_VIEW, BACKUP_RUN, DATA_EXPORT,
                    STOCK_VIEW, STOCK_ADD, STOCK_EDIT, STOCK_REPORT,
                    STAFF_VIEW, STAFF_ADD, STAFF_EDIT, STAFF_REPORT,
                    PARTY_VIEW, PARTY_ADD, PARTY_EDIT, PARTY_REPORT,
                    FINANCE_VIEW, FINANCE_ADD, FINANCE_EDIT, FINANCE_VOID, FINANCE_REPORT,
                    INVOICE_VIEW, INVOICE_ADD, INVOICE_EDIT, INVOICE_VOID, INVOICE_REPORT,
                    APPOINTMENT_VIEW, APPOINTMENT_ADD, APPOINTMENT_EDIT, APPOINTMENT_CANCEL, APPOINTMENT_REPORT,
                    CONTRACT_VIEW, CONTRACT_ADD, CONTRACT_EDIT, CONTRACT_VOID, CONTRACT_REPORT,
                    LOYALTY_VIEW, LOYALTY_ADD, LOYALTY_EDIT, LOYALTY_REPORT,
                    NOTIFICATION_VIEW, NOTIFICATION_SEND, NOTIFICATION_TEMPLATE_EDIT,
                    REPORTING_VIEW);

            case KASIYER -> EnumSet.of(
                    DATA_EXPORT,
                    STOCK_VIEW,
                    PARTY_VIEW, PARTY_ADD, PARTY_EDIT,
                    FINANCE_VIEW, FINANCE_ADD,
                    INVOICE_VIEW, INVOICE_ADD,
                    APPOINTMENT_VIEW, APPOINTMENT_ADD, APPOINTMENT_EDIT, APPOINTMENT_CANCEL,
                    CONTRACT_VIEW, CONTRACT_ADD,
                    LOYALTY_VIEW, LOYALTY_ADD,
                    NOTIFICATION_VIEW);

            case PERSONEL -> EnumSet.of(
                    PARTY_VIEW,
                    APPOINTMENT_VIEW, APPOINTMENT_EDIT,
                    STOCK_VIEW,
                    LOYALTY_VIEW);

            case RAPOR_OKUYUCU -> EnumSet.of(
                    DATA_EXPORT, REPORTING_VIEW,
                    STOCK_REPORT, STAFF_REPORT, PARTY_REPORT, FINANCE_REPORT,
                    INVOICE_REPORT, APPOINTMENT_REPORT, CONTRACT_REPORT, LOYALTY_REPORT,
                    AUDIT_VIEW);
        };
    }
}
