package com.beautysalonapp.modules.finance.infrastructure;

import com.beautysalonapp.modules.finance.domain.PosSlip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PosSlipRepository extends JpaRepository<PosSlip, Long> {
    List<PosSlip> findAllBySettledFalseOrderByValueDate();
    List<PosSlip> findAllByDeletedFalseOrderBySlipDateDesc();
}
