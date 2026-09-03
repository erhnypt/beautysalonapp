package com.beautysalonapp.modules.party.infrastructure;

import com.beautysalonapp.modules.party.domain.AccountKind;
import com.beautysalonapp.modules.party.domain.PartyAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PartyAccountRepository extends JpaRepository<PartyAccount, Long> {

    List<PartyAccount> findAllByPartyIdAndDeletedFalse(Long partyId);

    Optional<PartyAccount> findByPartyIdAndKindAndCurrency(Long partyId, AccountKind kind, String currency);
}
