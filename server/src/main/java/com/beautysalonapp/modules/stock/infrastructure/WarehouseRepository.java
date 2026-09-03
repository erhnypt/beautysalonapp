package com.beautysalonapp.modules.stock.infrastructure;

import com.beautysalonapp.modules.stock.domain.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {
    Optional<Warehouse> findByBranchIdAndCode(Long branchId, String code);
    List<Warehouse> findAllByDeletedFalseOrderByCode();
    Optional<Warehouse> findFirstByIsDefaultTrue();
}
