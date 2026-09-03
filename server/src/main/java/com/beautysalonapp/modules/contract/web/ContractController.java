package com.beautysalonapp.modules.contract.web;

import com.beautysalonapp.modules.contract.application.ContractService;
import com.beautysalonapp.modules.contract.application.ContractService.CreateContractCommand;
import com.beautysalonapp.modules.contract.application.ContractService.NewLine;
import com.beautysalonapp.modules.contract.domain.ContractLine;
import com.beautysalonapp.modules.contract.domain.ContractStatus;
import com.beautysalonapp.modules.contract.domain.Installment;
import com.beautysalonapp.modules.contract.domain.InstallmentPeriod;
import com.beautysalonapp.modules.contract.domain.InstallmentStatus;
import com.beautysalonapp.modules.contract.domain.SalesContract;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/contracts")
@PreAuthorize("hasAuthority('CONTRACT_VIEW')")
public class ContractController {

    private final ContractService service;

    public ContractController(ContractService service) {
        this.service = service;
    }

    // --- DTO'lar ---
    public record LineDto(Long itemId, String description, @NotNull @Positive BigDecimal quantity,
                          Integer sessionCount, @NotNull @Positive BigDecimal unitPrice) {}

    public record CreateContractRequest(
            @NotNull Long partyId,
            LocalDate contractDate,
            @NotEmpty List<LineDto> lines,
            BigDecimal downPayment,
            @Positive int installmentCount,
            @NotNull LocalDate firstDueDate,
            InstallmentPeriod period,
            BigDecimal interestRate,
            Long downPaymentCashAccountId,
            String notes) {
    }

    public record PayRequest(Long cashAccountId, BigDecimal amount, Long cardId) {}

    public record CancelRequest(String reason) {}

    public record ContractView(long id, String docNo, long partyId, LocalDate contractDate,
                               BigDecimal totalAmount, BigDecimal downPayment, int installmentCount,
                               LocalDate firstDueDate, InstallmentPeriod period, ContractStatus status,
                               String currency) {
        static ContractView of(SalesContract c) {
            return new ContractView(c.getId(), c.getDocNo(), c.getPartyId(), c.getContractDate(),
                    c.getTotalAmount(), c.getDownPayment(), c.getInstallmentCount(), c.getFirstDueDate(),
                    c.getPeriod(), c.getStatus(), c.getCurrency());
        }
    }

    public record LineView(long id, Long itemId, String description, BigDecimal quantity,
                           Integer sessionCount, int sessionUsed, BigDecimal unitPrice, BigDecimal lineTotal) {
        static LineView of(ContractLine l) {
            return new LineView(l.getId(), l.getItemId(), l.getDescription(), l.getQuantity(),
                    l.getSessionCount(), l.getSessionUsed(), l.getUnitPrice(), l.getLineTotal());
        }
    }

    public record InstallmentView(long id, int seq, LocalDate dueDate, BigDecimal amount,
                                  BigDecimal paidAmount, InstallmentStatus status) {
        static InstallmentView of(Installment i) {
            return new InstallmentView(i.getId(), i.getSeq(), i.getDueDate(), i.getAmount(),
                    i.getPaidAmount(), i.effectiveStatus(LocalDate.now()));
        }
    }

    public record ContractDetail(ContractView contract, List<LineView> lines, List<InstallmentView> installments) {}

    // --- uçlar ---
    @GetMapping
    public Page<ContractView> list(@RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "25") int size) {
        return service.list(PageRequest.of(page, Math.min(size, 200))).map(ContractView::of);
    }

    @GetMapping("/{id}")
    public ContractDetail get(@PathVariable long id) {
        SalesContract c = service.get(id);
        return new ContractDetail(
                ContractView.of(c),
                service.lines(id).stream().map(LineView::of).toList(),
                service.installments(id).stream().map(InstallmentView::of).toList());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CONTRACT_ADD')")
    public ContractDetail create(@Valid @RequestBody CreateContractRequest r) {
        var cmd = new CreateContractCommand(
                r.partyId(), r.contractDate(),
                r.lines().stream().map(l -> new NewLine(l.itemId(), l.description(), l.quantity(),
                        l.sessionCount(), l.unitPrice())).toList(),
                r.downPayment(), r.installmentCount(), r.firstDueDate(), r.period(),
                r.interestRate(), r.downPaymentCashAccountId(), r.notes());
        SalesContract c = service.create(cmd);
        return get(c.getId());
    }

    @PostMapping("/installments/{installmentId}/pay")
    @PreAuthorize("hasAuthority('CONTRACT_EDIT')")
    public InstallmentView pay(@PathVariable long installmentId, @RequestBody PayRequest r) {
        return InstallmentView.of(service.payInstallment(installmentId, r.cashAccountId(), r.amount(), r.cardId()));
    }

    @PostMapping("/{id}/early-payoff")
    @PreAuthorize("hasAuthority('CONTRACT_EDIT')")
    public ContractDetail earlyPayoff(@PathVariable long id, @RequestBody(required = false) PayRequest r) {
        service.earlyPayoff(id, r == null ? null : r.cashAccountId());
        return get(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('CONTRACT_VOID')")
    public ContractDetail cancel(@PathVariable long id, @RequestBody CancelRequest r) {
        service.cancel(id, r.reason());
        return get(id);
    }

    @GetMapping("/schedule")
    @PreAuthorize("hasAuthority('CONTRACT_REPORT')")
    public List<InstallmentView> schedule(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate until) {
        return service.dueSchedule(until == null ? LocalDate.now().plusMonths(1) : until)
                .stream().map(InstallmentView::of).toList();
    }
}
