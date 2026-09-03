package com.beautysalonapp.modules.appointment.infrastructure;

import com.beautysalonapp.modules.appointment.domain.Resource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResourceRepository extends JpaRepository<Resource, Long> {
    Optional<Resource> findByBranchIdAndCode(Long branchId, String code);
    List<Resource> findAllByDeletedFalseOrderByCode();
}
