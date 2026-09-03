package com.beautysalonapp.modules.contract.application;

import com.beautysalonapp.audit.application.AuditService;
import com.beautysalonapp.core.error.BusinessRuleException;
import com.beautysalonapp.core.error.NotFoundException;
import com.beautysalonapp.core.sequence.SequenceService;
import com.beautysalonapp.modules.contract.domain.ContractLine;
import com.beautysalonapp.modules.contract.domain.ContractStatus;
import com.beautysalonapp.modules.contract.domain.Installment;
import com.beautysalonapp.modules.contract.domain.InstallmentPeriod;
import com.beautysalonapp.modules.contract.domain.InstallmentPlan;
import com.beautysalonapp.modules.contract.domain.InstallmentStatus;
import com.beautysalonapp.modules.contract.domain.SalesContract;
import com.beautysalonapp.modules.contract.infrastructure.ContractLineRepository;
import com.beautysalonapp.modules.contract.infrastructure.InstallmentRepository;
import com.beautysalonapp.modules.contract.infrastructure.SalesContractRepository;
import com.beautysalonapp.modules.finance.application.FinancePort;
import com.beautysalonapp.modules.party.application.PartyDirectory;
import com.beautysalonapp.modules.party.application.PartyLedger;
import com.beautysalonapp.modules.party.domain.AccountKind;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class ContractService {

    private static final Long BRANCH = 1L;

    private final SalesContractRepository contracts;
    private final ContractLineRepository lines;
    private final InstallmentRepository installments;
    private final PartyDirectory partyDirectory;
    private final PartyLedger partyLedger;
    private final FinancePort finance;
    private final SequenceService sequences;
    private final AuditService audit;

    public ContractService(SalesContractRepository contracts, ContractLineRepository lines,
                           InstallmentRepository installments, PartyDirectory partyDirectory,
                           PartyLedger partyLedger, FinancePort finance,
                           SequenceService sequences, AuditService audit) {
        this.contracts = contracts;
        this.lines = lines;
        this.installments = installments;
        this.partyDirectory = partyDirectory;
        this.partyLedger = partyLedger;
        this.finance = finance;
        this.sequences = sequences;
        this.audit = audit;
    }

    public record NewLine(Long itemId, String description, BigDecimal quantity,
                          Integer sessionCount, BigDecimal unitPrice) {}

    public record CreateContractCommand(
            long partyId,
            LocalDate contractDate,
            List<NewLine> lines,
            BigDecimal downPayment,
            int installmentCount,
            LocalDate firstDueDate,
            InstallmentPeriod period,
            BigDecimal interestRate,
            Long downPaymentCashAccountId,
            String notes) {
    }

    public SalesContract create(CreateContractCommand cmd) {
        var partyRef = partyDirectory.require(cmd.partyId());
        long partyAccountId = partyLedger.resolveAccount(cmd.partyId(), AccountKind.NORMAL, "TRY");

        if (cmd.lines() == null || cmd.lines().isEmpty()) {
            throw new BusinessRuleException("no_lines", "Sözleşmede en az bir satır olmalı");
        }
        BigDecimal total = cmd.lines().stream()
                .map(l -> l.quantity().multiply(l.unitPrice()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal down = cmd.downPayment() == null ? BigDecimal.ZERO : cmd.downPayment();

        // Plan üretimi girdi doğrulamalarını da yapar (peşinat > toplam vb.)
        List<InstallmentPlan.PlannedInstallment> plan = InstallmentPlan.generate(
                total, down, cmd.installmentCount(), cmd.firstDueDate(),
                cmd.period() == null ? InstallmentPeriod.AYLIK : cmd.period());

        LocalDate contractDate = cmd.contractDate() == null ? LocalDate.now() : cmd.contractDate();
        String docNo = sequences.next(BRANCH, "CONTRACT", "SZ");

        SalesContract contract = new SalesContract(docNo, cmd.partyId(), partyAccountId, contractDate,
                total, down, cmd.installmentCount(), cmd.firstDueDate(),
                cmd.period() == null ? InstallmentPeriod.AYLIK : cmd.period());
        if (cmd.interestRate() != null) contract.setInterestRate(cmd.interestRate());
        contract.setNotes(cmd.notes());
        contracts.save(contract);

        for (NewLine l : cmd.lines()) {
            lines.save(new ContractLine(contract.getId(), l.itemId(), l.description(),
                    l.quantity(), l.sessionCount(), l.unitPrice()));
        }

        // Cari: sözleşme toplamı kadar müşteriye borç yaz
        partyLedger.post(PartyLedger.LedgerEntry.debit(partyAccountId, contractDate,
                "CONTRACT", docNo, "Sözleşme " + docNo + " — " + partyRef.title(), total, "TRY"));

        // Peşinat tahsilatı (kasaya + / cariye alacak) — FinancePort idempotent
        if (down.signum() > 0) {
            long cashAcc = cmd.downPaymentCashAccountId() != null
                    ? cmd.downPaymentCashAccountId() : finance.defaultCashAccountId();
            finance.collect(new FinancePort.CollectCommand(contractDate, cashAcc, partyAccountId, null,
                    down, "TRY", "Sözleşme peşinatı " + docNo, "CONTRACT", docNo, "down"));
        }

        for (InstallmentPlan.PlannedInstallment p : plan) {
            installments.save(new Installment(contract.getId(), p.seq(), p.dueDate(), p.amount()));
        }
        if (plan.isEmpty()) {
            contract.setStatus(ContractStatus.COMPLETED);
        }

        audit.record("CONTRACT_CREATE", "SalesContract", docNo,
                "Sözleşme: " + partyRef.title() + " toplam " + total + " peşinat " + down
                        + " / " + plan.size() + " taksit");
        return contract;
    }

    @Transactional(readOnly = true)
    public SalesContract get(long id) {
        return contracts.findById(id).orElseThrow(() -> new NotFoundException("Sözleşme", id));
    }

    @Transactional(readOnly = true)
    public List<ContractLine> lines(long contractId) {
        return lines.findAllByContractIdOrderById(contractId);
    }

    @Transactional(readOnly = true)
    public List<Installment> installments(long contractId) {
        return installments.findAllByContractIdOrderBySeq(contractId);
    }

    /** Bir taksit ödemesi. Fazla ödeme kalan tutara sabitlenir. */
    public Installment payInstallment(long installmentId, Long cashAccountId, BigDecimal amount, Long cardId) {
        Installment inst = installments.findById(installmentId)
                .orElseThrow(() -> new NotFoundException("Taksit", installmentId));
        if (inst.getStatus() == InstallmentStatus.ODENDI) {
            throw new BusinessRuleException("already_paid", "Bu taksit zaten ödenmiş");
        }
        if (inst.getStatus() == InstallmentStatus.IPTAL) {
            throw new BusinessRuleException("cancelled", "İptal edilmiş taksit ödenemez");
        }
        SalesContract contract = get(inst.getContractId());

        BigDecimal pay = amount == null ? inst.remaining() : amount.min(inst.remaining());
        if (pay.signum() <= 0) {
            throw new BusinessRuleException("bad_amount", "Ödeme tutarı pozitif olmalı");
        }

        long cashAcc = cashAccountId != null ? cashAccountId : finance.defaultCashAccountId();
        finance.collect(new FinancePort.CollectCommand(LocalDate.now(), cashAcc,
                contract.getPartyAccountId(), cardId, pay, contract.getCurrency(),
                "Taksit " + inst.getSeq() + "/" + contract.getInstallmentCount() + " — " + contract.getDocNo(),
                "INSTALLMENT", contract.getDocNo(), "i" + inst.getSeq() + "-" + System.currentTimeMillis()));

        inst.applyPayment(pay);
        installments.save(inst);

        // Tüm taksitler kapandıysa sözleşme tamamlandı
        boolean allDone = installments.findAllByContractIdOrderBySeq(contract.getId()).stream()
                .allMatch(x -> x.getStatus() == InstallmentStatus.ODENDI
                        || x.getStatus() == InstallmentStatus.IPTAL);
        if (allDone && contract.getStatus() == ContractStatus.ACTIVE) {
            contract.setStatus(ContractStatus.COMPLETED);
        }
        audit.record("INSTALLMENT_PAID", "Installment", installmentId,
                "Taksit ödemesi " + pay + " / sözleşme " + contract.getDocNo());
        return inst;
    }

    /** Sözleşme iptali: bekleyen taksitler IPTAL, kalan alacak cariden düşülür (§10.12). */
    public void cancel(long contractId, String reason) {
        SalesContract contract = get(contractId);
        if (contract.getStatus() == ContractStatus.CANCELLED) {
            throw new BusinessRuleException("already_cancelled", "Sözleşme zaten iptal edilmiş");
        }
        List<Installment> all = installments.findAllByContractIdOrderBySeq(contractId);
        BigDecimal unpaidScheduled = BigDecimal.ZERO;
        for (Installment i : all) {
            if (i.getStatus() != InstallmentStatus.ODENDI) {
                unpaidScheduled = unpaidScheduled.add(i.remaining());
                i.setStatus(InstallmentStatus.IPTAL);
                installments.save(i);
            }
        }
        if (unpaidScheduled.signum() > 0) {
            partyLedger.post(PartyLedger.LedgerEntry.credit(contract.getPartyAccountId(),
                    LocalDate.now(), "CONTRACT_CANCEL", contract.getDocNo(),
                    "Sözleşme iptali — kalan alacak düşüldü", unpaidScheduled, contract.getCurrency()));
        }
        contract.setStatus(ContractStatus.CANCELLED);
        contract.setCancelReason(reason);
        audit.record("CONTRACT_CANCEL", "SalesContract", contract.getDocNo(),
                "Sözleşme iptal edildi — kalan " + unpaidScheduled + " / gerekçe: " + reason);
    }

    /** Erken kapama: tüm bekleyen taksitlerin kalanını tek seferde tahsil eder. */
    public void earlyPayoff(long contractId, Long cashAccountId) {
        List<Installment> pending = new ArrayList<>();
        for (Installment i : installments.findAllByContractIdOrderBySeq(contractId)) {
            if (i.getStatus() == InstallmentStatus.BEKLIYOR || i.getStatus() == InstallmentStatus.GECIKMIS
                    || (i.getStatus() != InstallmentStatus.ODENDI && i.getStatus() != InstallmentStatus.IPTAL)) {
                pending.add(i);
            }
        }
        for (Installment i : pending) {
            payInstallment(i.getId(), cashAccountId, i.remaining(), null);
        }
        audit.record("CONTRACT_EARLY_PAYOFF", "SalesContract", String.valueOf(contractId),
                pending.size() + " taksit erken kapatıldı");
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<SalesContract> list(org.springframework.data.domain.Pageable p) {
        return contracts.findAllByDeletedFalseOrderByContractDateDescIdDesc(p);
    }

    /** Vade takvimi: verilen tarihe kadar açık taksitler. */
    @Transactional(readOnly = true)
    public List<Installment> dueSchedule(LocalDate until) {
        return installments.dueUpTo(until, List.of(InstallmentStatus.BEKLIYOR, InstallmentStatus.GECIKMIS));
    }
}
