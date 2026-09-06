package com.beautysalonapp.modules.appointment.infrastructure;

import com.beautysalonapp.modules.appointment.domain.Waitlist;
import com.beautysalonapp.modules.appointment.domain.WaitlistStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WaitlistRepository extends JpaRepository<Waitlist, Long> {
    List<Waitlist> findAllByStatusOrderByCreatedAtAsc(WaitlistStatus status);
    List<Waitlist> findAllByPartyIdOrderByCreatedAtDesc(Long partyId);
}
