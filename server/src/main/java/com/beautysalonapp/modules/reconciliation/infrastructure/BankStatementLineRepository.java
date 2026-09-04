package com.beautysalonapp.modules.reconciliation.infrastructure;

import com.beautysalonapp.modules.reconciliation.domain.BankStatementLine;
import com.beautysalonapp.modules.reconciliation.domain.MatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface BankStatementLineRepository extends JpaRepository<BankStatementLine, Long> {

    List<BankStatementLine> findAllByStatementIdOrderByLineNo(Long statementId);

    int countByStatementIdAndMatchStatus(Long statementId, MatchStatus status);

    long countByStatementId(Long statementId);

    /** Herhangi bir ekstredeki eşleşmiş/oluşturulmuş satırların bağlı olduğu hareket id'leri
     * (aynı kasa hareketinin iki kez mutabık gösterilmesini önlemek için). */
    @Query("select l.matchedTxnId from BankStatementLine l where l.matchedTxnId is not null")
    List<Long> findAllMatchedTxnIds();
}
