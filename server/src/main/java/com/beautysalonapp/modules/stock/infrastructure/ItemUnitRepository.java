package com.beautysalonapp.modules.stock.infrastructure;

import com.beautysalonapp.modules.stock.domain.ItemUnit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ItemUnitRepository extends JpaRepository<ItemUnit, Long> {
    List<ItemUnit> findAllByItemIdAndDeletedFalse(Long itemId);
    Optional<ItemUnit> findByItemIdAndUnitId(Long itemId, Long unitId);
    Optional<ItemUnit> findByItemIdAndIsBaseTrue(Long itemId);
}
