package com.beautysalonapp.modules.staff.infrastructure;

import com.beautysalonapp.modules.staff.domain.CommissionStatus;
import com.beautysalonapp.modules.staff.domain.StaffCommission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface StaffCommissionRepository extends JpaRepository<StaffCommission, Long> {

    boolean existsByStaffIdAndSourceTypeAndSourceRef(Long staffId, String sourceType, String sourceRef);

    List<StaffCommission> findAllByStaffIdAndPeriodYmOrderById(Long staffId, String periodYm);

    List<StaffCommission> findAllByStaffIdAndPeriodYmAndStatus(Long staffId, String periodYm, CommissionStatus status);

    @Query("""
            select c from StaffCommission c
            where c.status = com.beautysalonapp.modules.staff.domain.CommissionStatus.TAHAKKUK
              and c.accruedAt >= :fromTs and c.accruedAt < :toTs
            """)
    List<StaffCommission> accruedBetween(@Param("fromTs") java.time.Instant fromTs,
                                         @Param("toTs") java.time.Instant toTs);

    @Query("""
            select c.staffId as staffId, count(c) as cnt, sum(c.baseAmount) as base, sum(c.amount) as commission
            from StaffCommission c
            where c.accruedAt >= :fromTs and c.accruedAt < :toTs
              and c.status <> com.beautysalonapp.modules.staff.domain.CommissionStatus.IPTAL
            group by c.staffId
            """)
    List<PerfRow> performance(@Param("fromTs") java.time.Instant fromTs,
                              @Param("toTs") java.time.Instant toTs);

    interface PerfRow {
        Long getStaffId();
        long getCnt();
        java.math.BigDecimal getBase();
        java.math.BigDecimal getCommission();
    }
}
