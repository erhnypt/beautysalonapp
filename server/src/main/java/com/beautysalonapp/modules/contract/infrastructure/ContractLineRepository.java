package com.beautysalonapp.modules.contract.infrastructure;

import com.beautysalonapp.modules.contract.domain.ContractLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContractLineRepository extends JpaRepository<ContractLine, Long> {
    List<ContractLine> findAllByContractIdOrderById(Long contractId);
}
