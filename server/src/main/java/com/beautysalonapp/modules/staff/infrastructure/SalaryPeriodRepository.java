package com.beautysalonapp.modules.staff.infrastructure;

import com.beautysalonapp.modules.staff.domain.SalaryPeriod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SalaryPeriodRepository extends JpaRepository<SalaryPeriod, Long> {
    Optional<SalaryPeriod> findByStaffIdAndPeriodYm(Long staffId, String periodYm);
    List<SalaryPeriod> findAllByStaffIdOrderByPeriodYmDesc(Long staffId);
    boolean existsByStaffIdAndPeriodYmAndLockedTrue(Long staffId, String periodYm);
}
