package com.beautysalonapp.modules.finance.infrastructure;

import com.beautysalonapp.modules.finance.domain.CashCount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CashCountRepository extends JpaRepository<CashCount, Long> {
    List<CashCount> findAllByAccountIdOrderByDateDescIdDesc(Long accountId);
}
