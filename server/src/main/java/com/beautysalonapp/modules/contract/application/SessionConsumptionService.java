package com.beautysalonapp.modules.contract.application;

import com.beautysalonapp.core.error.BusinessRuleException;
import com.beautysalonapp.core.error.NotFoundException;
import com.beautysalonapp.modules.contract.domain.ContractLine;
import com.beautysalonapp.modules.contract.infrastructure.ContractLineRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SessionConsumptionService implements SessionConsumptionPort {

    private final ContractLineRepository lines;

    public SessionConsumptionService(ContractLineRepository lines) {
        this.lines = lines;
    }

    @Override
    public void consumeSession(long contractLineId) {
        ContractLine line = line(contractLineId);
        Integer cap = line.getSessionCount();
        if (cap != null && line.getSessionUsed() >= cap) {
            throw new BusinessRuleException("no_session_left",
                    "Pakette kalan seans yok (" + cap + "/" + cap + ")");
        }
        line.setSessionUsed(line.getSessionUsed() + 1);
    }

    @Override
    public void restoreSession(long contractLineId) {
        ContractLine line = line(contractLineId);
        if (line.getSessionUsed() > 0) {
            line.setSessionUsed(line.getSessionUsed() - 1);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public int remainingSessions(long contractLineId) {
        ContractLine line = line(contractLineId);
        Integer cap = line.getSessionCount();
        return cap == null ? Integer.MAX_VALUE : Math.max(0, cap - line.getSessionUsed());
    }

    private ContractLine line(long id) {
        return lines.findById(id).orElseThrow(() -> new NotFoundException("Sözleşme satırı", id));
    }
}
