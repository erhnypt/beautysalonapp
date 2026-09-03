package com.beautysalonapp.modules.stock.infrastructure;

import com.beautysalonapp.modules.stock.domain.StockLevel;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StockLevelRepository extends JpaRepository<StockLevel, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StockLevel s where s.itemId = :itemId and s.warehouseId = :warehouseId")
    Optional<StockLevel> lock(@Param("itemId") Long itemId, @Param("warehouseId") Long warehouseId);

    Optional<StockLevel> findByItemIdAndWarehouseId(Long itemId, Long warehouseId);

    List<StockLevel> findAllByItemId(Long itemId);

    List<StockLevel> findAllByWarehouseId(Long warehouseId);
}
