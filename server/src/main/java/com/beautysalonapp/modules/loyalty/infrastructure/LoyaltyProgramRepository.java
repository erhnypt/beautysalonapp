package com.beautysalonapp.modules.loyalty.infrastructure;

import com.beautysalonapp.modules.loyalty.domain.LoyaltyProgram;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LoyaltyProgramRepository extends JpaRepository<LoyaltyProgram, Long> {
    Optional<LoyaltyProgram> findFirstByActiveTrueAndDeletedFalseOrderByIdAsc();
    List<LoyaltyProgram> findAllByDeletedFalseOrderByName();
}
