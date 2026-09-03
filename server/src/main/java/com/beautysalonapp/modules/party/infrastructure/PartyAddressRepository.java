package com.beautysalonapp.modules.party.infrastructure;

import com.beautysalonapp.modules.party.domain.PartyAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PartyAddressRepository extends JpaRepository<PartyAddress, Long> {
    List<PartyAddress> findAllByPartyIdAndDeletedFalse(Long partyId);
}
