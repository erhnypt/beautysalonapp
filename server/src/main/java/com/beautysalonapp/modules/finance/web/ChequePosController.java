package com.beautysalonapp.modules.finance.web;

import com.beautysalonapp.modules.finance.application.ChequePort;
import com.beautysalonapp.modules.finance.application.ChequeService;
import com.beautysalonapp.modules.finance.application.PosSlipService;
import com.beautysalonapp.modules.finance.domain.Cheque;
import com.beautysalonapp.modules.finance.domain.ChequeMovement;
import com.beautysalonapp.modules.finance.domain.ChequeStatus;
import com.beautysalonapp.modules.finance.domain.ChequeType;
import com.beautysalonapp.modules.finance.domain.PosSlip;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
@RequestMapping("/api/v1/finance")
@PreAuthorize("hasAuthority('FINANCE_VIEW')")
public class ChequePosController {

    private final ChequeService cheques;
    private final PosSlipService posSlips;

    public ChequePosController(ChequeService cheques, PosSlipService posSlips) {
        this.cheques = cheques;
        this.posSlips = posSlips;
    }

    // --- Çek ---
    public record ChequeView(long id, String chequeNo, ChequeType type, String bankName, LocalDate dueDate,
                             BigDecimal amount, String currency, Long partyAccountId, ChequeStatus status) {
        static ChequeView of(Cheque c) {
            return new ChequeView(c.getId(), c.getChequeNo(), c.getType(), c.getBankName(), c.getDueDate(),
                    c.getAmount(), c.getCurrency(), c.getPartyAccountId(), c.getStatus());
        }
    }

    public record RegisterChequeRequest(@NotNull String chequeNo, @NotNull ChequeType type, String bankName,
                                        String drawer, @NotNull LocalDate dueDate, @NotNull @Positive BigDecimal amount,
                                        Long partyAccountId) {}

    public record TransitionRequest(@NotNull ChequeStatus target, Long settleAccountId, String note) {}

    @GetMapping("/cheques")
    public List<ChequeView> chequeList() {
        return cheques.list().stream().map(ChequeView::of).toList();
    }

    @GetMapping("/cheques/{id}/movements")
    public List<ChequeMovement> chequeMovements(@PathVariable long id) {
        return cheques.history(id);
    }

    @GetMapping("/cheques/due")
    public List<ChequeView> chequeDue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate until) {
        return cheques.dueCalendar(until == null ? LocalDate.now().plusMonths(1) : until)
                .stream().map(ChequeView::of).toList();
    }

    @GetMapping("/cheques/risk/{partyAccountId}")
    public BigDecimal risk(@PathVariable long partyAccountId) {
        return cheques.customerRiskBalance(partyAccountId).getAmount();
    }

    @PostMapping("/cheques")
    @PreAuthorize("hasAuthority('FINANCE_ADD')")
    public long register(@Valid @RequestBody RegisterChequeRequest r) {
        return cheques.register(new ChequePort.RegisterChequeCommand(r.chequeNo(), r.type(), r.bankName(),
                r.drawer(), r.dueDate(), r.amount(), "TRY", r.partyAccountId(), null));
    }

    @PostMapping("/cheques/{id}/transition")
    @PreAuthorize("hasAuthority('FINANCE_EDIT')")
    public ChequeView transition(@PathVariable long id, @Valid @RequestBody TransitionRequest r) {
        return ChequeView.of(cheques.transition(id, r.target(), r.settleAccountId(), r.note()));
    }

    // --- POS ---
    public record PosSlipView(long id, Long posAccountId, LocalDate slipDate, BigDecimal amount,
                              int installmentCount, BigDecimal commissionAmount, BigDecimal netAmount,
                              LocalDate valueDate, boolean settled) {
        static PosSlipView of(PosSlip s) {
            return new PosSlipView(s.getId(), s.getPosAccountId(), s.getSlipDate(), s.getAmount(),
                    s.getInstallmentCount(), s.getCommissionAmount(), s.getNetAmount(), s.getValueDate(), s.isSettled());
        }
    }

    public record CreatePosSlipRequest(@NotNull Long posAccountId, LocalDate date, @NotNull @Positive BigDecimal amount,
                                       Integer installmentCount, BigDecimal commissionRate) {}

    public record SettleRequest(@NotNull Long bankAccountId, LocalDate valueDate) {}

    @GetMapping("/pos-slips")
    public List<PosSlipView> posSlips(@RequestParam(defaultValue = "false") boolean pendingOnly) {
        var list = pendingOnly ? posSlips.pending() : posSlips.all();
        return list.stream().map(PosSlipView::of).toList();
    }

    @PostMapping("/pos-slips")
    @PreAuthorize("hasAuthority('FINANCE_ADD')")
    public PosSlipView createSlip(@Valid @RequestBody CreatePosSlipRequest r) {
        return PosSlipView.of(posSlips.create(r.posAccountId(), r.date() == null ? LocalDate.now() : r.date(),
                r.amount(), r.installmentCount() == null ? 1 : r.installmentCount(), r.commissionRate(), null));
    }

    @PostMapping("/pos-slips/{id}/settle")
    @PreAuthorize("hasAuthority('FINANCE_EDIT')")
    public PosSlipView settle(@PathVariable long id, @Valid @RequestBody SettleRequest r) {
        return PosSlipView.of(posSlips.settle(id, r.bankAccountId(), r.valueDate()));
    }
}
