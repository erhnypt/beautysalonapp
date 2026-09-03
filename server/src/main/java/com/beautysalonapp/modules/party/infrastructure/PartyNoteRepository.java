package com.beautysalonapp.modules.party.infrastructure;

import com.beautysalonapp.modules.party.domain.PartyNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PartyNoteRepository extends JpaRepository<PartyNote, Long> {
    List<PartyNote> findAllByPartyIdAndDeletedFalseOrderByPinnedDescIdDesc(Long partyId);
}
