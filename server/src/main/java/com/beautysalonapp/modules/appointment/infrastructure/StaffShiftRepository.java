package com.beautysalonapp.modules.appointment.infrastructure;

import com.beautysalonapp.modules.appointment.domain.StaffShift;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface StaffShiftRepository extends JpaRepository<StaffShift, Long> {
    List<StaffShift> findAllByStaffPartyIdAndDateBetweenOrderByDate(Long staffPartyId, LocalDate from, LocalDate to);
    List<StaffShift> findAllByDateBetween(LocalDate from, LocalDate to);
}
