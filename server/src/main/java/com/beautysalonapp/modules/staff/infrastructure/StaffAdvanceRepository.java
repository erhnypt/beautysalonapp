package com.beautysalonapp.modules.staff.infrastructure;

import com.beautysalonapp.modules.staff.domain.StaffAdvance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface StaffAdvanceRepository extends JpaRepository<StaffAdvance, Long> {
    List<StaffAdvance> findAllByStaffIdOrderByDateDesc(Long staffId);
    List<StaffAdvance> findAllByStaffIdAndDateBetween(Long staffId, LocalDate from, LocalDate to);
}
