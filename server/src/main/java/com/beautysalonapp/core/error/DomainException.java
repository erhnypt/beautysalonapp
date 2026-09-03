package com.beautysalonapp.core.error;

/**
 * İş kuralı ihlallerinin ortak tabanı. {@code code} istemcinin i18n anahtarı olarak
 * kullanabileceği makine-okur bir kimliktir; {@code getMessage()} geliştirici içindir.
 */
public abstract class DomainException extends RuntimeException {

    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
