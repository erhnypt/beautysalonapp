package com.beautysalonapp.modules.finance.infrastructure;

import com.beautysalonapp.modules.finance.domain.Cheque;
import com.beautysalonapp.modules.finance.domain.ChequeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ChequeRepository extends JpaRepository<Cheque, Long> {
    List<Cheque> findAllByPartyAccountIdAndStatusIn(Long partyAccountId, List<ChequeStatus> statuses);
    List<Cheque> findAllByStatusInAndDueDateLessThanEqualOrderByDueDate(List<ChequeStatus> statuses, LocalDate until);
    List<Cheque> findAllByDeletedFalseOrderByDueDate();
}
