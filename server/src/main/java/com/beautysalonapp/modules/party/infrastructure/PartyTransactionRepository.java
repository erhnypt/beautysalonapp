package com.beautysalonapp.modules.party.infrastructure;

import com.beautysalonapp.modules.party.domain.PartyTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface PartyTransactionRepository extends JpaRepository<PartyTransaction, Long> {

    boolean existsByDocTypeAndDocRefAndLineKey(String docType, String docRef, String lineKey);

    List<PartyTransaction> findByDocTypeAndDocRef(String docType, String docRef);

    List<PartyTransaction> findByDocTypeAndDocRefAndReversesIdIsNull(String docType, String docRef);

    List<PartyTransaction> findByAccountIdOrderByDateAscIdAsc(Long accountId);

    @Query("""
            select p from PartyTransaction p
            where p.accountId = :accountId
              and (:from is null or p.date >= :from)
              and (:to is null or p.date <= :to)
            order by p.date asc, p.id asc
            """)
    List<PartyTransaction> statement(@Param("accountId") Long accountId,
                                     @Param("from") LocalDate from,
                                     @Param("to") LocalDate to);

    @Query("""
            select coalesce(sum(p.debit), 0) - coalesce(sum(p.credit), 0)
            from PartyTransaction p where p.accountId = :accountId
            """)
    BigDecimal balance(@Param("accountId") Long accountId);
}
