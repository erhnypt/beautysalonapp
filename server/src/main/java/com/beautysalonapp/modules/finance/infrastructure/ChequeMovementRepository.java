package com.beautysalonapp.modules.finance.infrastructure;

import com.beautysalonapp.modules.finance.domain.ChequeMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChequeMovementRepository extends JpaRepository<ChequeMovement, Long> {
    List<ChequeMovement> findAllByChequeIdOrderByAt(Long chequeId);
}
