package com.beautysalonapp.modules.finance.application;

import com.beautysalonapp.audit.application.AuditService;
import com.beautysalonapp.core.error.BusinessRuleException;
import com.beautysalonapp.core.error.NotFoundException;
import com.beautysalonapp.modules.finance.domain.CashTxnType;
import com.beautysalonapp.modules.finance.domain.FinAccount;
import com.beautysalonapp.modules.finance.domain.FinAccountKind;
import com.beautysalonapp.modules.finance.domain.PosSlip;
import com.beautysalonapp.modules.finance.infrastructure.FinAccountRepository;
import com.beautysalonapp.modules.finance.infrastructure.PosSlipRepository;
import com.beautysalonapp.settings.application.SettingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
public class PosSlipService {

    private final PosSlipRepository slips;
    private final FinAccountRepository accounts;
    private final FinanceService finance;
    private final SettingService settings;
    private final AuditService audit;

    public PosSlipService(PosSlipRepository slips, FinAccountRepository accounts,
                          FinanceService finance, SettingService settings, AuditService audit) {
        this.slips = slips;
        this.accounts = accounts;
        this.finance = finance;
        this.settings = settings;
        this.audit = audit;
    }

    /** Kart ödemesinden POS slibi. Komisyon oranı hesabınkinden alınır (yoksa 0). */
    public PosSlip create(long posAccountId, LocalDate date, BigDecimal amount, int installmentCount,
                          BigDecimal commissionRateOverride, String sourceDoc) {
        FinAccount pos = accounts.findById(posAccountId)
                .orElseThrow(() -> new NotFoundException("POS hesabı", posAccountId));
        if (pos.getKind() != FinAccountKind.POS) {
            throw new BusinessRuleException("not_pos", "Seçilen hesap POS değil: " + pos.getCode());
        }
        BigDecimal rate = commissionRateOverride != null ? commissionRateOverride
                : (pos.getCommissionRate() != null ? pos.getCommissionRate() : BigDecimal.ZERO);
        PosSlip slip = new PosSlip(posAccountId, date, amount, installmentCount, rate);
        slip.setSourceDoc(sourceDoc);
        slips.save(slip);
        audit.record("POS_SLIP_CREATE", "PosSlip", slip.getId(),
                "POS slip " + amount + " komisyon " + slip.getCommissionAmount() + " net " + slip.getNetAmount());
        return slip;
    }

    /** Mahsuplaşma: net tutar bankaya girer, komisyon gider kartına yazılır. */
    public PosSlip settle(long slipId, long bankAccountId, LocalDate valueDate) {
        PosSlip slip = slips.findById(slipId).orElseThrow(() -> new NotFoundException("POS slip", slipId));
        if (slip.isSettled()) {
            throw new BusinessRuleException("already_settled", "Bu slip zaten mahsuplaştı");
        }
        slip.setBankAccountId(bankAccountId);
        slip.setValueDate(valueDate == null ? LocalDate.now() : valueDate);
        slip.setSettled(true);

        finance.createManual(CashTxnType.COLLECTION, slip.getValueDate(), bankAccountId, null, null, null,
                slip.getNetAmount(), "POS mahsup net: slip #" + slip.getId());

        if (slip.getCommissionAmount().signum() > 0) {
            Long commissionCardId = settings.get("finance.posCommissionCardId")
                    .map(Long::valueOf).orElse(null);
            // Komisyon banka hesabından çıkış (gider) — kart bilgisi ayardan
            finance.createManual(CashTxnType.PAYMENT, slip.getValueDate(), bankAccountId, null, null,
                    commissionCardId, slip.getCommissionAmount(), "POS komisyonu: slip #" + slip.getId());
        }
        audit.record("POS_SLIP_SETTLE", "PosSlip", slipId,
                "Mahsup: net " + slip.getNetAmount() + " → banka " + bankAccountId);
        return slip;
    }

    @Transactional(readOnly = true)
    public List<PosSlip> pending() {
        return slips.findAllBySettledFalseOrderByValueDate();
    }

    @Transactional(readOnly = true)
    public List<PosSlip> all() {
        return slips.findAllByDeletedFalseOrderBySlipDateDesc();
    }
}
