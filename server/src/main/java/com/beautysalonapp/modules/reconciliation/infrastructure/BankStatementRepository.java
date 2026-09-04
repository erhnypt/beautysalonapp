package com.beautysalonapp.modules.reconciliation.infrastructure;

import com.beautysalonapp.modules.reconciliation.domain.BankStatement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BankStatementRepository extends JpaRepository<BankStatement, Long> {

    List<BankStatement> findAllByDeletedFalseOrderByImportedAtDesc();

    List<BankStatement> findAllByDeletedFalseAndFinAccountIdOrderByImportedAtDesc(Long finAccountId);
}
