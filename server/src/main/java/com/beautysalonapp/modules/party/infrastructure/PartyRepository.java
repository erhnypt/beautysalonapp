package com.beautysalonapp.modules.party.infrastructure;

import com.beautysalonapp.modules.party.domain.Party;
import com.beautysalonapp.modules.party.domain.PartyType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PartyRepository extends JpaRepository<Party, Long> {

    Optional<Party> findByBranchIdAndCode(Long branchId, String code);

    boolean existsByBranchIdAndCode(Long branchId, String code);

    @Query("""
            select p from Party p
            where p.deleted = false
              and (:type is null or p.type = :type)
              and (
                :q is null
                or lower(p.title) like lower(concat('%', :q, '%'))
                or lower(p.code)  like lower(concat('%', :q, '%'))
                or p.phoneBlindIndex = :phoneBi
              )
            order by p.title
            """)
    Page<Party> search(@Param("type") PartyType type,
                       @Param("q") String q,
                       @Param("phoneBi") String phoneBlindIndex,
                       Pageable pageable);

    long countByTypeAndDeletedFalse(PartyType type);
}
