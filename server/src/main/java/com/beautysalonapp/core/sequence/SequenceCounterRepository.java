package com.beautysalonapp.core.sequence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SequenceCounterRepository extends JpaRepository<SequenceCounter, Long> {

    /**
     * Aynı numaranın iki kez üretilmemesi için satır kilidiyle okur.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from SequenceCounter s
            where s.branchId = :branchId and s.type = :type and s.year = :year
            """)
    Optional<SequenceCounter> lockByKey(@Param("branchId") Long branchId,
                                        @Param("type") String type,
                                        @Param("year") int year);
}
