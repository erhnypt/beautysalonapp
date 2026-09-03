package com.beautysalonapp.modules.party.application;

import com.beautysalonapp.modules.party.domain.PartyType;

import java.util.Optional;

/**
 * Diğer modüllerin taraf kaydına erişimi için port (CLAUDE.md #5).
 * Modüller {@code domain}/{@code infrastructure} sınıflarına değil bu arayüze bağlanır.
 */
public interface PartyDirectory {

    PartyRef require(long partyId);

    Optional<PartyRef> findByCode(String code);

    /** Verilen türde yeni bir taraf oluşturur ve NORMAL/TRY hesabını açar (ör. personel kaydı). */
    PartyRef createBasic(PartyType type, String title);

    record PartyRef(long id, String code, PartyType type, String title, boolean anonymized) {}
}
