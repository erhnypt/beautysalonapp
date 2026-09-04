package com.beautysalonapp.modules.loyalty.infrastructure;

import com.beautysalonapp.modules.loyalty.domain.LoyaltyCard;
import com.beautysalonapp.modules.loyalty.domain.LoyaltyEnums.CardStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LoyaltyCardRepository extends JpaRepository<LoyaltyCard, Long> {

    Optional<LoyaltyCard> findByPartyIdAndStatus(Long partyId, CardStatus status);

    Optional<LoyaltyCard> findByBranchIdAndCardNo(Long branchId, String cardNo);

    Optional<LoyaltyCard> findByMagneticId(String magneticId);

    List<LoyaltyCard> findAllByStatus(CardStatus status);

    @Query("select coalesce(sum(c.pointsBalance), 0) from LoyaltyCard c where c.status = 'ACTIVE'")
    long totalOutstandingPoints();

    @Query("""
            select c from LoyaltyCard c
            where c.deleted = false
              and (:q is null or lower(c.cardNo) like lower(concat('%', :q, '%')))
            order by c.id desc
            """)
    List<LoyaltyCard> search(@Param("q") String q);
}
