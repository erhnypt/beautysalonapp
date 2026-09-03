package com.beautysalonapp.modules.finance.infrastructure;

import com.beautysalonapp.modules.finance.domain.FinAccount;
import com.beautysalonapp.modules.finance.domain.FinAccountKind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FinAccountRepository extends JpaRepository<FinAccount, Long> {
    Optional<FinAccount> findByBranchIdAndCode(Long branchId, String code);
    List<FinAccount> findAllByDeletedFalseOrderByKindAscCodeAsc();
    Optional<FinAccount> findFirstByKindAndIsDefaultTrue(FinAccountKind kind);
    Optional<FinAccount> findFirstByKindAndActiveTrueOrderByIdAsc(FinAccountKind kind);
}
