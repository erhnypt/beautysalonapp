package com.beautysalonapp.modules.loyalty.infrastructure;

import com.beautysalonapp.modules.loyalty.domain.LoyaltyEnums.LoyaltyTxnType;
import com.beautysalonapp.modules.loyalty.domain.LoyaltyTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface LoyaltyTransactionRepository extends JpaRepository<LoyaltyTransaction, Long> {

    List<LoyaltyTransaction> findAllByCardIdOrderByAtDesc(Long cardId);

    boolean existsByCardIdAndTypeAndSourceRef(Long cardId, LoyaltyTxnType type, String sourceRef);

    @Query("""
            select t from LoyaltyTransaction t
            where t.type = :earnType
              and t.expired = false and t.expiresAt is not null and t.expiresAt < :today
            """)
    List<LoyaltyTransaction> expiredEarnings(@Param("earnType") LoyaltyTxnType earnType,
                                            @Param("today") LocalDate today);
}
