package com.beautysalonapp.core.error;

/** Genel iş kuralı ihlali (ör. risk limiti aşımı, kapalı dönem). */
public class BusinessRuleException extends DomainException {

    public BusinessRuleException(String code, String message) {
        super(code, message);
    }

    public BusinessRuleException(String message) {
        super("business_rule", message);
    }
}
