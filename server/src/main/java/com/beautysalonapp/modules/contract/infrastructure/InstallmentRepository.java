package com.beautysalonapp.modules.contract.infrastructure;

import com.beautysalonapp.modules.contract.domain.Installment;
import com.beautysalonapp.modules.contract.domain.InstallmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface InstallmentRepository extends JpaRepository<Installment, Long> {

    List<Installment> findAllByContractIdOrderBySeq(Long contractId);

    @Query("""
            select i from Installment i
            where i.status in :statuses
              and i.dueDate <= :until
            order by i.dueDate
            """)
    List<Installment> dueUpTo(@Param("until") LocalDate until,
                              @Param("statuses") List<InstallmentStatus> statuses);

    long countByContractIdAndStatusNot(Long contractId, InstallmentStatus status);
}
