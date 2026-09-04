package com.beautysalonapp.modules.branch.infrastructure;

import com.beautysalonapp.modules.branch.domain.Branch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BranchRepository extends JpaRepository<Branch, Long> {

    List<Branch> findAllByDeletedFalseOrderByCodeAsc();

    Optional<Branch> findByCodeIgnoreCase(String code);

    long countByDeletedFalse();
}
