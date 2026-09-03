package com.beautysalonapp.modules.stock.infrastructure;

import com.beautysalonapp.modules.stock.domain.Item;
import com.beautysalonapp.modules.stock.domain.ItemType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, Long> {

    Optional<Item> findByBranchIdAndCode(Long branchId, String code);

    boolean existsByBranchIdAndCode(Long branchId, String code);

    long countByDeletedFalse();

    @Query("""
            select i from Item i
            where i.deleted = false
              and (:type is null or i.type = :type)
              and (:categoryId is null or i.categoryId = :categoryId)
              and (:q is null
                   or lower(i.name) like lower(concat('%', :q, '%'))
                   or lower(i.code) like lower(concat('%', :q, '%'))
                   or lower(coalesce(i.brand,'')) like lower(concat('%', :q, '%')))
            order by i.name
            """)
    Page<Item> search(@Param("type") ItemType type,
                      @Param("categoryId") Long categoryId,
                      @Param("q") String q,
                      Pageable pageable);
}
