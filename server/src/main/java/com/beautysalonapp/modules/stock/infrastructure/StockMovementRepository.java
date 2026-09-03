package com.beautysalonapp.modules.stock.infrastructure;

import com.beautysalonapp.modules.stock.domain.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    boolean existsByDocTypeAndDocRefAndLineKey(String docType, String docRef, String lineKey);

    List<StockMovement> findByDocTypeAndDocRef(String docType, String docRef);

    @Query("""
            select m from StockMovement m
            where m.itemId = :itemId
              and (:warehouseId is null or m.warehouseId = :warehouseId)
            order by m.date desc, m.id desc
            """)
    Page<StockMovement> history(@Param("itemId") Long itemId,
                                @Param("warehouseId") Long warehouseId,
                                Pageable pageable);
}
