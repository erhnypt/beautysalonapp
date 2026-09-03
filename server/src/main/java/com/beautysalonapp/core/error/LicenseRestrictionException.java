package com.beautysalonapp.core.error;

/**
 * Lisans durumu (READ_ONLY / LOCKED / TAMPERED) nedeniyle yazma işlemi reddedildiğinde
 * fırlatılır. HTTP 423 (Locked) ile eşlenir.
 */
public class LicenseRestrictionException extends DomainException {

    public LicenseRestrictionException(String message) {
        super("license_restricted", message);
    }
}
