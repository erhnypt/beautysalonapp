package com.beautysalonapp.attachment.domain;

/**
 * Ek dosyanın bağlı olduğu varlık türü (plan §9.1). Sabit bir allowlist —
 * {@code storage_path} üretiminde kullanıcı girdisinin dizin yoluna karışmasını
 * önler (bkz. {@code AttachmentService}, güvenlik gözden geçirme C5).
 */
public enum AttachmentOwnerType {
    PARTY,
    CONTRACT
}
