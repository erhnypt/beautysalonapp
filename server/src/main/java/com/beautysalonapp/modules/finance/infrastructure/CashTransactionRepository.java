package com.beautysalonapp.modules.finance.infrastructure;

import com.beautysalonapp.modules.finance.domain.CashTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface CashTransactionRepository extends JpaRepository<CashTransaction, Long> {

    boolean existsByDocTypeAndDocRefAndLineKey(String docType, String docRef, String lineKey);

    List<CashTransaction> findByDocTypeAndDocRefAndReversesIdIsNullAndVoidedFalse(String docType, String docRef);

    @Query("""
            select c from CashTransaction c
            where (c.accountId = :accountId or c.counterAccountId = :accountId)
              and (:from is null or c.date >= :from)
              and (:to is null or c.date <= :to)
            order by c.date desc, c.id desc
            """)
    List<CashTransaction> ledger(@Param("accountId") Long accountId,
                                 @Param("from") LocalDate from,
                                 @Param("to") LocalDate to);

    @Query("""
            select c from CashTransaction c
            where c.voided = false
              and c.incomeExpenseCardId is not null
              and (:from is null or c.date >= :from)
              and (:to is null or c.date <= :to)
            """)
    List<CashTransaction> withCardBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    List<CashTransaction> findByAccountIdOrCounterAccountId(Long a, Long b);
}
