package com.beautysalonapp.license.domain;

public final class Enums {
    private Enums() {
    }

    public enum Plan { BASIC, PRO, ENTERPRISE }

    /** Abonelik durumu — lisans durumunu yönlendirir. */
    public enum SubscriptionStatus { PENDING_PAYMENT, ACTIVE, SUSPENDED, CANCELLED }

    /** Lisans durumu — heartbeat cevabında istemciye dönen. */
    public enum LicenseStatus { UNACTIVATED, ACTIVE, SUSPENDED, REVOKED }

    public enum TransferStatus { PENDING, APPROVED, REJECTED }
}
