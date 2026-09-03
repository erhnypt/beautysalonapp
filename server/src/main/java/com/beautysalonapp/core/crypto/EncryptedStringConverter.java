package com.beautysalonapp.core.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/**
 * JPA alan dönüştürücü: DB'ye şifreli yazar, okurken çözer.
 * Kullanım: {@code @Convert(converter = EncryptedStringConverter.class)}.
 *
 * <p>Spring-yönetimli bean olduğu için {@link FieldCrypto} enjekte edilebilir
 * (Hibernate 6 + Spring Boot 3 managed converter desteği).
 */
@Converter
@Component
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final FieldCrypto crypto;

    public EncryptedStringConverter(FieldCrypto crypto) {
        this.crypto = crypto;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return crypto.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return crypto.decrypt(dbData);
    }
}
