package com.beautysalonapp.core.error;

public class NotFoundException extends DomainException {

    public NotFoundException(String entity, Object id) {
        super("not_found", entity + " bulunamadı: " + id);
    }

    public NotFoundException(String message) {
        super("not_found", message);
    }
}
