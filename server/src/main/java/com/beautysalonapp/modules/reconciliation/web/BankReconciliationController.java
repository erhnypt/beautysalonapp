package com.beautysalonapp.modules.reconciliation.web;

import com.beautysalonapp.core.error.BusinessRuleException;
import com.beautysalonapp.modules.finance.application.FinancePort.BankAccountView;
import com.beautysalonapp.modules.reconciliation.application.BankReconciliationService;
import com.beautysalonapp.modules.reconciliation.domain.BankStatement;
import com.beautysalonapp.modules.reconciliation.domain.BankStatementLine;
import com.beautysalonapp.modules.reconciliation.domain.MatchCandidate;
import com.beautysalonapp.modules.reconciliation.domain.MatchStatus;
import com.beautysalonapp.modules.reconciliation.domain.StatementFormat;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Banka ekstresi içe aktarma & mutabakat REST API (Faz 8, docs/modules/banka-mutabakat.md).
 * Dışarıya HTTP çağrısı yapmaz (CLAUDE.md #1) — ekstre kullanıcı tarafından yüklenir.
 */
@RestController
@RequestMapping("/api/v1/bank-reconciliation")
@PreAuthorize("hasAuthority('FINANCE_VIEW')")
public class BankReconciliationController {

    private final BankReconciliationService service;

    public BankReconciliationController(BankReconciliationService service) {
        this.service = service;
    }

    // --- DTO'lar ------------------------------------------------------------

    public record StatementView(long id, long finAccountId, StatementFormat sourceFormat, String statementRef,
                                LocalDate periodStart, LocalDate periodEnd, BigDecimal openingBalance,
                                BigDecimal closingBalance, int lineCount, int matchedCount,
                                String originalFilename, Instant importedAt, String status) {
        static StatementView of(BankStatement s) {
            return new StatementView(s.getId(), s.getFinAccountId(), s.getSourceFormat(), s.getStatementRef(),
                    s.getPeriodStart(), s.getPeriodEnd(), s.getOpeningBalance(), s.getClosingBalance(),
                    s.getLineCount(), s.getMatchedCount(), s.getOriginalFilename(), s.getImportedAt(),
                    s.getStatus().name());
        }
    }

    public record LineView(long id, int lineNo, LocalDate valueDate, BigDecimal amount, String currency,
                           String description, String counterparty, String bankRef, String matchStatus,
                           Long matchedTxnId, Integer matchScore, String note,
                           List<MatchCandidate> suggestions) {
    }

    public record MatchRequest(@NotNull Long txnId) {}

    public record IgnoreRequest(@NotNull String note) {}

    public record CreateTxnRequest(Long incomeExpenseCardId, String description, Long partyAccountId) {}

    // --- uçlar ---------------------------------------------------------

    @GetMapping("/accounts")
    public List<BankAccountView> accounts() {
        return service.accounts();
    }

    @GetMapping
    public List<StatementView> list() {
        return service.list().stream().map(StatementView::of).toList();
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable long id) {
        BankStatement stmt = service.get(id);
        List<LineView> lineViews = service.linesOf(id).stream()
                .map(l -> new LineView(l.getId(), l.getLineNo(), l.getValueDate(), l.getAmount(), l.getCurrency(),
                        l.getDescription(), l.getCounterparty(), l.getBankRef(), l.getMatchStatus().name(),
                        l.getMatchedTxnId(), l.getMatchScore(), l.getNote(),
                        l.getMatchStatus() == MatchStatus.UNMATCHED ? service.suggestionsFor(l.getId()) : List.of()))
                .toList();
        return Map.of("statement", StatementView.of(stmt), "lines", lineViews);
    }

    @PostMapping(value = "/import")
    @PreAuthorize("hasAuthority('FINANCE_EDIT')")
    public StatementView importStatement(@RequestParam("finAccountId") long finAccountId,
                                         @RequestParam("format") StatementFormat format,
                                         @RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new BusinessRuleException("empty_file", "Dosya boş");
        }
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "ekstre";
        return StatementView.of(service.importStatement(finAccountId, format, filename, file.getBytes()));
    }

    @PostMapping("/lines/{lineId}/match")
    @PreAuthorize("hasAuthority('FINANCE_EDIT')")
    public void match(@PathVariable long lineId, @RequestBody MatchRequest r) {
        service.confirmMatch(lineId, r.txnId());
    }

    @PostMapping("/lines/{lineId}/unmatch")
    @PreAuthorize("hasAuthority('FINANCE_EDIT')")
    public void unmatch(@PathVariable long lineId) {
        service.unmatch(lineId);
    }

    @PostMapping("/lines/{lineId}/ignore")
    @PreAuthorize("hasAuthority('FINANCE_EDIT')")
    public void ignore(@PathVariable long lineId, @RequestBody IgnoreRequest r) {
        service.ignore(lineId, r.note());
    }

    @PostMapping("/lines/{lineId}/create-transaction")
    @PreAuthorize("hasAuthority('FINANCE_EDIT')")
    public Map<String, Long> createTransaction(@PathVariable long lineId, @RequestBody CreateTxnRequest r) {
        long txnId = service.createTransaction(lineId, r.incomeExpenseCardId(), r.description(), r.partyAccountId());
        return Map.of("txnId", txnId);
    }

    @PostMapping("/{id}/auto-match")
    @PreAuthorize("hasAuthority('FINANCE_EDIT')")
    public Map<String, Integer> autoMatch(@PathVariable long id) {
        return Map.of("matched", service.autoMatch(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('FINANCE_EDIT')")
    public void delete(@PathVariable long id) {
        service.delete(id);
    }
}
