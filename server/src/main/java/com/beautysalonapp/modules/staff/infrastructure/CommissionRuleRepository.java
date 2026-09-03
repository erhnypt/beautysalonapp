package com.beautysalonapp.modules.staff.infrastructure;

import com.beautysalonapp.modules.staff.domain.CommissionRule;
import com.beautysalonapp.modules.staff.domain.CommissionScope;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommissionRuleRepository extends JpaRepository<CommissionRule, Long> {
    List<CommissionRule> findAllByScopeAndActiveTrueAndDeletedFalse(CommissionScope scope);
    List<CommissionRule> findAllByDeletedFalseOrderByScope();
}
