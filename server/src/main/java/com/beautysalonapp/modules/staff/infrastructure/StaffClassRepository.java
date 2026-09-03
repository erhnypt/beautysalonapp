package com.beautysalonapp.modules.staff.infrastructure;

import com.beautysalonapp.modules.staff.domain.StaffClass;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StaffClassRepository extends JpaRepository<StaffClass, Long> {
    List<StaffClass> findAllByDeletedFalseOrderByName();
}
