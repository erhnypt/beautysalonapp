package com.beautysalonapp.modules.stock.infrastructure;

import com.beautysalonapp.modules.stock.domain.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {
    Optional<Warehouse> findByBranchIdAndCode(Long branchId, String code);
    List<Warehouse> findAllByDeletedFalseOrderByCode();
    Optional<Warehouse> findFirstByIsDefaultTrue();

    /** Faz 8 tam şube izolasyonu (ADR 0006): şubeye özel depo listesi/varsayılan çözümü için. */
    List<Warehouse> findAllByBranchIdAndDeletedFalseOrderByCode(Long branchId);
}
