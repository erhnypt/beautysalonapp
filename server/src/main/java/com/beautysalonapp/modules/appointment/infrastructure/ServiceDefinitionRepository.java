package com.beautysalonapp.modules.appointment.infrastructure;

import com.beautysalonapp.modules.appointment.domain.ServiceDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceDefinitionRepository extends JpaRepository<ServiceDefinition, Long> {
    Optional<ServiceDefinition> findByBranchIdAndCode(Long branchId, String code);
    List<ServiceDefinition> findAllByDeletedFalseOrderByName();
}
