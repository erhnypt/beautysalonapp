package com.beautysalonapp.modules.contract.infrastructure;

import com.beautysalonapp.modules.contract.domain.SalesContract;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SalesContractRepository extends JpaRepository<SalesContract, Long> {
    Optional<SalesContract> findByBranchIdAndDocNo(Long branchId, String docNo);
    Page<SalesContract> findAllByDeletedFalseOrderByContractDateDescIdDesc(Pageable pageable);
    List<SalesContract> findAllByPartyIdAndDeletedFalseOrderByContractDateDesc(Long partyId);
}
