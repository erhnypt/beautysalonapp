package com.beautysalonapp.modules.finance.web;

import com.beautysalonapp.modules.finance.application.FinanceService;
import com.beautysalonapp.modules.finance.domain.CardDirection;
import com.beautysalonapp.modules.finance.domain.CashCount;
import com.beautysalonapp.modules.finance.domain.CashTransaction;
import com.beautysalonapp.modules.finance.domain.CashTxnType;
import com.beautysalonapp.modules.finance.domain.FinAccount;
import com.beautysalonapp.modules.finance.domain.FinAccountKind;
import com.beautysalonapp.modules.finance.domain.IncomeExpenseCard;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
public class FinanceController {

    private final FinanceService finance;

    public FinanceController(FinanceService finance) {
        this.finance = finance;
    }

    // --- hesap planı ---
    public record AccountView(long id, String code, String name, FinAccountKind kind, String currency,
                              BigDecimal openingBalance, BigDecimal balance, boolean isDefault, BigDecimal avgRate) {}

    public record CreateAccountRequest(@NotBlank String code, @NotBlank String name,
                                       @NotNull FinAccountKind kind, String currency,
                                       BigDecimal openingBalance, boolean makeDefault) {}

    @GetMapping("/accounts")
    public List<AccountView> accounts() {
        return finance.listAccounts().stream().map(this::toView).toList();
    }

    @PostMapping("/accounts")
    @PreAuthorize("hasAuthority('FINANCE_EDIT')")
    public AccountView createAccount(@Valid @RequestBody CreateAccountRequest r) {
        return toView(finance.createAccount(r.code(), r.name(), r.kind(), r.currency(),
                r.openingBalance(), r.makeDefault()));
    }

    @GetMapping("/accounts/{id}/balance")
    public AccountView balance(@PathVariable long id) {
        return toView(finance.getAccount(id));
    }

    // --- gelir/gider kartları ---
    public record CardView(long id, Long parentId, String code, String name, CardDirection direction,
                           boolean serviceCard, boolean postable, BigDecimal budget) {
        static CardView of(IncomeExpenseCard c) {
            return new CardView(c.getId(), c.getParentId(), c.getCode(), c.getName(), c.getDirection(),
                    c.isServiceCard(), c.isPostable(), c.getBudgetAmount());
        }
    }

    public record CreateCardRequest(Long parentId, @NotBlank String code, @NotBlank String name,
                                    @NotNull CardDirection direction, boolean serviceCard) {}

    public record UpdateBudgetRequest(BigDecimal budgetAmount) {}

    @GetMapping("/cards")
    public List<CardView> cards() {
        return finance.listCards().stream().map(CardView::of).toList();
    }

    @PostMapping("/cards")
    @PreAuthorize("hasAuthority('FINANCE_EDIT')")
    public CardView createCard(@Valid @RequestBody CreateCardRequest r) {
        return CardView.of(finance.createCard(r.parentId(), r.code(), r.name(), r.direction(), r.serviceCard()));
    }

    @PostMapping("/cards/{id}/budget")
    @PreAuthorize("hasAuthority('FINANCE_EDIT')")
    public CardView updateBudget(@PathVariable long id, @RequestBody UpdateBudgetRequest r) {
        return CardView.of(finance.updateCardBudget(id, r.budgetAmount()));
    }

    // --- hareketler ---
    public record TxnView(long id, CashTxnType type, LocalDate date, long accountId, Long counterAccountId,
                          Long partyAccountId, Long cardId, BigDecimal amount, String currency,
                          String description, String docNo, boolean voided) {
        static TxnView of(CashTransaction t) {
            return new TxnView(t.getId(), t.getType(), t.getDate(), t.getAccountId(), t.getCounterAccountId(),
                    t.getPartyAccountId(), t.getIncomeExpenseCardId(), t.getAmount(), t.getCurrency(),
                    t.getDescription(), t.getDocNo(), t.isVoided());
        }
    }

    public record CollectRequest(LocalDate date, @NotNull Long accountId, Long partyAccountId, Long cardId,
                                 @NotNull @Positive BigDecimal amount, String description) {}

    public record TransferRequest(LocalDate date, @NotNull Long accountId, @NotNull Long counterAccountId,
                                  @NotNull @Positive BigDecimal amount, String description) {}

    public record VoidRequest(@NotBlank String reason) {}

    public record FxRequest(LocalDate date, @NotNull Long fxAccountId, @NotNull Long tryAccountId,
                            @NotNull Boolean buy, @NotNull @Positive BigDecimal fxAmount,
                            @NotNull @Positive BigDecimal rate, String description) {}

    public record CashCountRequest(LocalDate date, @NotNull BigDecimal countedAmount, String reason) {}

    public record CashCountView(long id, LocalDate date, BigDecimal expectedAmount, BigDecimal countedAmount,
                                BigDecimal difference, String reason, String adjustmentDocNo) {
        static CashCountView of(CashCount c) {
            return new CashCountView(c.getId(), c.getDate(), c.getExpectedAmount(), c.getCountedAmount(),
                    c.getDifference(), c.getReason(), c.getAdjustmentDocNo());
        }
    }

    @GetMapping("/transactions")
    public List<TxnView> transactions(@RequestParam long accountId,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return finance.ledger(accountId, from, to).stream().map(TxnView::of).toList();
    }

    @PostMapping("/collect")
    @PreAuthorize("hasAuthority('FINANCE_ADD')")
    public TxnView collect(@Valid @RequestBody CollectRequest r) {
        return TxnView.of(finance.createManual(CashTxnType.COLLECTION, r.date(), r.accountId(),
                null, r.partyAccountId(), r.cardId(), r.amount(), r.description()));
    }

    @PostMapping("/pay")
    @PreAuthorize("hasAuthority('FINANCE_ADD')")
    public TxnView pay(@Valid @RequestBody CollectRequest r) {
        return TxnView.of(finance.createManual(CashTxnType.PAYMENT, r.date(), r.accountId(),
                null, r.partyAccountId(), r.cardId(), r.amount(), r.description()));
    }

    @PostMapping("/transfer")
    @PreAuthorize("hasAuthority('FINANCE_ADD')")
    public TxnView transfer(@Valid @RequestBody TransferRequest r) {
        return TxnView.of(finance.createManual(CashTxnType.TRANSFER, r.date(), r.accountId(),
                r.counterAccountId(), null, null, r.amount(), r.description()));
    }

    @PostMapping("/fx")
    @PreAuthorize("hasAuthority('FINANCE_ADD')")
    public TxnView fx(@Valid @RequestBody FxRequest r) {
        return TxnView.of(finance.fxExchange(new FinanceService.FxCommand(r.date(), r.fxAccountId(),
                r.tryAccountId(), r.buy(), r.fxAmount(), r.rate(), r.description())));
    }

    @GetMapping("/accounts/{id}/cash-counts")
    public List<CashCountView> cashCounts(@PathVariable long id) {
        return finance.cashCounts(id).stream().map(CashCountView::of).toList();
    }

    @PostMapping("/accounts/{id}/cash-counts")
    @PreAuthorize("hasAuthority('FINANCE_ADD')")
    public CashCountView countCash(@PathVariable long id, @Valid @RequestBody CashCountRequest r) {
        return CashCountView.of(finance.countCash(id, r.date(), r.countedAmount(), r.reason()));
    }

    @PostMapping("/transactions/{id}/void")
    @PreAuthorize("hasAuthority('FINANCE_VOID')")
    public void voidTxn(@PathVariable long id, @Valid @RequestBody VoidRequest r) {
        finance.voidTransaction(id, r.reason());
    }

    @GetMapping("/reports/income-expense")
    @PreAuthorize("hasAuthority('FINANCE_REPORT')")
    public List<FinanceService.IncomeExpenseRow> incomeExpense(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return finance.incomeExpenseReport(from, to);
    }

    @GetMapping("/reports/budget")
    @PreAuthorize("hasAuthority('FINANCE_REPORT')")
    public List<FinanceService.BudgetRow> budget(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return finance.budgetVsActual(from, to);
    }

    private AccountView toView(FinAccount a) {
        var bal = finance.accountBalance(a.getId());
        return new AccountView(a.getId(), a.getCode(), a.getName(), a.getKind(), a.getCurrency(),
                a.getOpeningBalance(), bal.getAmount(), a.isDefault(), a.getAvgRate());
    }
}
