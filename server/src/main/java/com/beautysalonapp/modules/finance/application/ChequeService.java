package com.beautysalonapp.modules.finance.application;

import com.beautysalonapp.audit.application.AuditService;
import com.beautysalonapp.core.domain.Money;
import com.beautysalonapp.core.error.BusinessRuleException;
import com.beautysalonapp.core.error.NotFoundException;
import com.beautysalonapp.modules.finance.domain.Cheque;
import com.beautysalonapp.modules.finance.domain.ChequeMovement;
import com.beautysalonapp.modules.finance.domain.ChequeStatus;
import com.beautysalonapp.modules.finance.domain.ChequeType;
import com.beautysalonapp.modules.finance.domain.CashTxnType;
import com.beautysalonapp.modules.finance.infrastructure.ChequeMovementRepository;
import com.beautysalonapp.modules.finance.infrastructure.ChequeRepository;
import com.beautysalonapp.modules.party.application.PartyLedger;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
public class ChequeService implements ChequePort {

    private final ChequeRepository cheques;
    private final ChequeMovementRepository movements;
    private final PartyLedger partyLedger;
    private final FinanceService finance;
    private final AuditService audit;

    public ChequeService(ChequeRepository cheques, ChequeMovementRepository movements,
                         PartyLedger partyLedger, FinanceService finance, AuditService audit) {
        this.cheques = cheques;
        this.movements = movements;
        this.partyLedger = partyLedger;
        this.finance = finance;
        this.audit = audit;
    }

    @Override
    public long register(RegisterChequeCommand c) {
        Cheque ch = new Cheque(c.chequeNo(), c.type(), c.dueDate(), c.amount(),
                c.currency() == null ? "TRY" : c.currency(), c.partyAccountId());
        ch.setBankName(c.bankName());
        ch.setDrawer(c.drawer());
        ch.setSourceDoc(c.sourceDoc());
        cheques.save(ch);
        movements.save(new ChequeMovement(ch.getId(), null, ChequeStatus.PORTFOYDE.name(), actor(),
                "Çek portföye alındı"));

        // Cari etkisi: müşteri çeki alacak (borç azalır), firma çeki borç
        if (c.partyAccountId() != null) {
            String ref = c.sourceDoc() != null ? c.sourceDoc() : "CHK-" + ch.getId();
            if (c.type() == ChequeType.MUSTERI_CEKI) {
                partyLedger.post(PartyLedger.LedgerEntry.credit(c.partyAccountId(), LocalDate.now(),
                        "CHEQUE", ref, "Müşteri çeki: " + c.chequeNo(), c.amount(), ch.getCurrency()));
            } else {
                partyLedger.post(PartyLedger.LedgerEntry.debit(c.partyAccountId(), LocalDate.now(),
                        "CHEQUE", ref, "Firma çeki: " + c.chequeNo(), c.amount(), ch.getCurrency()));
            }
        }
        audit.record("CHEQUE_REGISTER", "Cheque", ch.getId(),
                c.type() + " " + c.chequeNo() + " " + c.amount() + " " + ch.getCurrency());
        return ch.getId();
    }

    public Cheque transition(long chequeId, ChequeStatus target, Long settleAccountId, String note) {
        Cheque ch = cheques.findById(chequeId).orElseThrow(() -> new NotFoundException("Çek", chequeId));
        ChequeStatus from = ch.getStatus();
        if (from == target) {
            return ch;
        }
        if (!from.canTransitionTo(target)) {
            throw new BusinessRuleException("bad_cheque_transition",
                    "Geçersiz çek durum geçişi: " + from + " → " + target);
        }

        switch (target) {
            case TAHSIL_EDILDI -> {
                if (settleAccountId == null) {
                    throw new BusinessRuleException("no_account", "Tahsil için banka/kasa hesabı seçin");
                }
                ch.setSettledAccountId(settleAccountId);
                CashTxnType t = ch.getType() == ChequeType.MUSTERI_CEKI
                        ? CashTxnType.COLLECTION : CashTxnType.PAYMENT;
                finance.createManual(t, LocalDate.now(), settleAccountId, null, null, null,
                        ch.getAmount(), "Çek tahsilatı: " + ch.getChequeNo());
            }
            case KARSILIKSIZ -> {
                // Kayıt anındaki cari etkisini geri al
                if (ch.getPartyAccountId() != null) {
                    String ref = ch.getSourceDoc() != null ? ch.getSourceDoc() : "CHK-" + ch.getId();
                    if (ch.getType() == ChequeType.MUSTERI_CEKI) {
                        partyLedger.post(PartyLedger.LedgerEntry.debit(ch.getPartyAccountId(), LocalDate.now(),
                                "CHEQUE_BOUNCE", ref, "Karşılıksız çek: " + ch.getChequeNo(),
                                ch.getAmount(), ch.getCurrency()));
                    } else {
                        partyLedger.post(PartyLedger.LedgerEntry.credit(ch.getPartyAccountId(), LocalDate.now(),
                                "CHEQUE_BOUNCE", ref, "Karşılıksız firma çeki: " + ch.getChequeNo(),
                                ch.getAmount(), ch.getCurrency()));
                    }
                }
            }
            default -> { /* BANKAYA_TAHSILE, CIROLANDI, IADE, PORTFOYDE: yalnızca durum + hareket */ }
        }

        ch.setStatus(target);
        movements.save(new ChequeMovement(chequeId, from.name(), target.name(), actor(), note));
        audit.record("CHEQUE_TRANSITION", "Cheque", chequeId, from + " → " + target
                + (note == null ? "" : " (" + note + ")"));
        return ch;
    }

    @Override
    @Transactional(readOnly = true)
    public Money customerRiskBalance(long partyAccountId) {
        BigDecimal sum = cheques.findAllByPartyAccountIdAndStatusIn(partyAccountId,
                        List.of(ChequeStatus.PORTFOYDE, ChequeStatus.BANKAYA_TAHSILE)).stream()
                .filter(c -> c.getType() == ChequeType.MUSTERI_CEKI)
                .map(Cheque::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return Money.of(sum, "TRY");
    }

    @Transactional(readOnly = true)
    public List<Cheque> list() {
        return cheques.findAllByDeletedFalseOrderByDueDate();
    }

    @Transactional(readOnly = true)
    public List<ChequeMovement> history(long chequeId) {
        return movements.findAllByChequeIdOrderByAt(chequeId);
    }

    @Transactional(readOnly = true)
    public List<Cheque> dueCalendar(LocalDate until) {
        return cheques.findAllByStatusInAndDueDateLessThanEqualOrderByDueDate(
                List.of(ChequeStatus.PORTFOYDE, ChequeStatus.BANKAYA_TAHSILE), until);
    }

    private static String actor() {
        var a = SecurityContextHolder.getContext().getAuthentication();
        return a != null ? a.getName() : "system";
    }
}
