package com.beautysalonapp.modules.stock.infrastructure;

import com.beautysalonapp.modules.stock.domain.Unit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UnitRepository extends JpaRepository<Unit, Long> {
    Optional<Unit> findByBranchIdAndCode(Long branchId, String code);
    List<Unit> findAllByDeletedFalseOrderByCode();
}
