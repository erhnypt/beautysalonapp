package com.beautysalonapp.modules.finance.infrastructure;

import com.beautysalonapp.modules.finance.domain.IncomeExpenseCard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IncomeExpenseCardRepository extends JpaRepository<IncomeExpenseCard, Long> {
    Optional<IncomeExpenseCard> findByBranchIdAndCode(Long branchId, String code);
    List<IncomeExpenseCard> findAllByDeletedFalseOrderByCode();
}
