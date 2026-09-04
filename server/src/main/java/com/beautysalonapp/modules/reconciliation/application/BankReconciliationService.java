package com.beautysalonapp.modules.reconciliation.application;

import com.beautysalonapp.audit.application.AuditService;
import com.beautysalonapp.core.error.BusinessRuleException;
import com.beautysalonapp.core.error.NotFoundException;
import com.beautysalonapp.modules.finance.application.FinancePort;
import com.beautysalonapp.modules.finance.application.FinancePort.BankAccountView;
import com.beautysalonapp.modules.finance.application.FinancePort.BankTxnView;
import com.beautysalonapp.modules.finance.application.FinancePort.CollectCommand;
import com.beautysalonapp.modules.finance.application.FinancePort.PayCommand;
import com.beautysalonapp.modules.reconciliation.domain.BankStatement;
import com.beautysalonapp.modules.reconciliation.domain.BankStatementLine;
import com.beautysalonapp.modules.reconciliation.domain.CsvLayout;
import com.beautysalonapp.modules.reconciliation.domain.CsvStatementParser;
import com.beautysalonapp.modules.reconciliation.domain.MatchCandidate;
import com.beautysalonapp.modules.reconciliation.domain.MatchStatus;
import com.beautysalonapp.modules.reconciliation.domain.Mt940Parser;
import com.beautysalonapp.modules.reconciliation.domain.ParsedLine;
import com.beautysalonapp.modules.reconciliation.domain.ParsedStatement;
import com.beautysalonapp.modules.reconciliation.domain.StatementFormat;
import com.beautysalonapp.modules.reconciliation.domain.StatementMatcher;
import com.beautysalonapp.modules.reconciliation.infrastructure.BankStatementLineRepository;
import com.beautysalonapp.modules.reconciliation.infrastructure.BankStatementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Banka ekstresi içe aktarma + mutabakat (Faz 8, docs/modules/banka-mutabakat.md).
 * Cross-module erişim yalnızca {@link FinancePort} üzerindendir (CLAUDE.md #5).
 */
@Service
@Transactional
public class BankReconciliationService {

    /** Aday hareket ararken ekstre dönemi etrafında bakılan pencere. */
    private static final int CANDIDATE_WINDOW_DAYS = 45;

    private final BankStatementRepository statements;
    private final BankStatementLineRepository lines;
    private final FinancePort finance;
    private final com.beautysalonapp.settings.application.SettingService settings;
    private final AuditService audit;

    public BankReconciliationService(BankStatementRepository statements, BankStatementLineRepository lines,
                                     FinancePort finance,
                                     com.beautysalonapp.settings.application.SettingService settings,
                                     AuditService audit) {
        this.statements = statements;
        this.lines = lines;
        this.finance = finance;
        this.settings = settings;
        this.audit = audit;
    }

    // --- sorgular --------------------------------------------------------

    @Transactional(readOnly = true)
    public List<BankAccountView> accounts() {
        return finance.bankAccounts();
    }

    @Transactional(readOnly = true)
    public List<BankStatement> list() {
        return statements.findAllByDeletedFalseOrderByImportedAtDesc();
    }

    @Transactional(readOnly = true)
    public BankStatement get(long id) {
        return statements.findById(id).filter(s -> !s.isDeleted())
                .orElseThrow(() -> new NotFoundException("Ekstre", id));
    }

    @Transactional(readOnly = true)
    public List<BankStatementLine> linesOf(long statementId) {
        return lines.findAllByStatementIdOrderByLineNo(statementId);
    }

    /** UNMATCHED bir satır için güncel aday listesini (skorla) hesaplar. */
    @Transactional(readOnly = true)
    public List<MatchCandidate> suggestionsFor(long lineId) {
        BankStatementLine line = requireLine(lineId);
        BankStatement stmt = get(line.getStatementId());
        List<BankTxnView> candidates = candidateTxns(stmt);
        Set<Long> excluded = new HashSet<>(lines.findAllMatchedTxnIds());
        return StatementMatcher.suggest(toParsedLine(line), toTxnViews(candidates), excluded);
    }

    // --- içe aktarma -------------------------------------------------------

    public BankStatement importStatement(long finAccountId, StatementFormat format, String originalFilename,
                                         byte[] content) {
        BankAccountView account = requireAccount(finAccountId);
        String text = new String(content, StandardCharsets.UTF_8);
        ParsedStatement parsed = switch (format) {
            case MT940 -> Mt940Parser.parse(text);
            case CSV -> {
                String header = text.lines().findFirst().orElse("");
                char delim = header.contains(";") ? ';' : (header.contains("\t") ? '\t' : ',');
                CsvLayout layout = CsvLayout.detect(header, delim);
                yield CsvStatementParser.parse(text, layout, account.currency());
            }
        };
        if (parsed.lines().isEmpty()) {
            throw new BusinessRuleException("empty_statement", "Ekstrede işlenecek satır bulunamadı");
        }

        BankStatement stmt = new BankStatement(finAccountId, format, originalFilename);
        stmt.applyParsed(parsed);
        statements.save(stmt);

        List<BankStatementLine> entities = new ArrayList<>();
        int n = 1;
        for (ParsedLine p : parsed.lines()) {
            entities.add(new BankStatementLine(stmt.getId(), n++, p));
        }
        lines.saveAll(entities);
        recomputeStatus(stmt.getId());

        int autoMatched = autoMatch(stmt.getId());
        audit.record("BANK_STMT_IMPORT", "BankStatement", stmt.getId(),
                "Ekstre içe aktarıldı: " + originalFilename + " (" + parsed.lineCount()
                        + " satır, " + autoMatched + " otomatik eşleşti)");
        return get(stmt.getId());
    }

    // --- eşleştirme aksiyonları ---------------------------------------------

    public void confirmMatch(long lineId, long txnId) {
        BankStatementLine line = requireLine(lineId);
        requireUnresolvedOrMatched(line);
        line.markMatched(txnId, null);
        recomputeStatus(line.getStatementId());
        audit.record("BANK_LINE_MATCH", "BankStatementLine", lineId, "Elle eşleştirildi: hareket #" + txnId);
    }

    public void unmatch(long lineId) {
        BankStatementLine line = requireLine(lineId);
        if (line.getMatchStatus() == MatchStatus.CREATED && line.getMatchedTxnId() != null) {
            finance.voidByDoc("BANK_STMT_LINE", String.valueOf(lineId), "Banka mutabakatı geri alındı");
        }
        line.clearMatch();
        recomputeStatus(line.getStatementId());
        audit.record("BANK_LINE_UNMATCH", "BankStatementLine", lineId, "Eşleşme geri alındı");
    }

    public void ignore(long lineId, String note) {
        if (note == null || note.isBlank()) {
            throw new BusinessRuleException("note_required", "Yok sayma gerekçesi zorunlu");
        }
        BankStatementLine line = requireLine(lineId);
        line.markIgnored(note.trim());
        recomputeStatus(line.getStatementId());
        audit.record("BANK_LINE_IGNORE", "BankStatementLine", lineId, "Yok sayıldı: " + note);
    }

    /** Satırdan yeni bir kasa hareketi üretir (banka masrafı, gelen havale vb.). */
    public long createTransaction(long lineId, Long incomeExpenseCardId, String description, Long partyAccountId) {
        BankStatementLine line = requireLine(lineId);
        if (line.getMatchStatus().isResolved()) {
            throw new BusinessRuleException("already_resolved", "Bu satır zaten çözülmüş");
        }
        BankStatement stmt = get(line.getStatementId());
        String desc = (description != null && !description.isBlank()) ? description.trim()
                : (line.getDescription() != null ? line.getDescription() : "Banka ekstresi hareketi");
        BigDecimal amount = line.getAmount().abs();
        long txnId;
        if (line.getAmount().signum() > 0) {
            txnId = finance.collect(new CollectCommand(line.getValueDate(), stmt.getFinAccountId(),
                    partyAccountId, incomeExpenseCardId, amount, line.getCurrency(), desc,
                    "BANK_STMT_LINE", String.valueOf(lineId), "1"));
        } else {
            txnId = finance.pay(new PayCommand(line.getValueDate(), stmt.getFinAccountId(),
                    partyAccountId, incomeExpenseCardId, amount, line.getCurrency(), desc,
                    "BANK_STMT_LINE", String.valueOf(lineId), "1"));
        }
        line.markCreated(txnId);
        recomputeStatus(stmt.getId());
        audit.record("BANK_LINE_CREATE_TXN", "BankStatementLine", lineId,
                "Satırdan hareket üretildi: #" + txnId + " (" + amount + " " + line.getCurrency() + ")");
        return txnId;
    }

    /** Skoru eşiğin üzerindeki tüm önerileri uygular; kaç satırın eşleştiğini döner. */
    public int autoMatch(long statementId) {
        BankStatement stmt = get(statementId);
        List<BankStatementLine> unmatched = lines.findAllByStatementIdOrderByLineNo(statementId).stream()
                .filter(l -> l.getMatchStatus() == MatchStatus.UNMATCHED)
                .toList();
        if (unmatched.isEmpty()) {
            return 0;
        }
        List<BankTxnView> candidates = candidateTxns(stmt);
        Set<Long> excluded = new HashSet<>(lines.findAllMatchedTxnIds());
        List<BankTxnView> available = candidates.stream()
                .filter(t -> !excluded.contains(t.id()))
                .toList();

        int threshold = parseThreshold();
        List<ParsedLine> asParsed = unmatched.stream().map(BankReconciliationService::toParsedLine).toList();
        var result = StatementMatcher.autoReconcile(asParsed, toTxnViews(available), threshold);

        int applied = 0;
        for (var e : result.entrySet()) {
            BankStatementLine line = unmatched.get(e.getKey());
            int score = StatementMatcher.suggest(toParsedLine(line), toTxnViews(available), Set.of()).stream()
                    .filter(c -> c.txnId() == e.getValue())
                    .findFirst().map(MatchCandidate::score).orElse(threshold);
            line.markMatched(e.getValue(), score);
            applied++;
        }
        recomputeStatus(statementId);
        if (applied > 0) {
            audit.record("BANK_STMT_AUTO_MATCH", "BankStatement", statementId, applied + " satır otomatik eşleşti");
        }
        return applied;
    }

    public void delete(long statementId) {
        BankStatement stmt = get(statementId);
        // Oluşturulmuş hareketler varsa önce onları geri al (soft delete mali kaydı silmez).
        for (BankStatementLine l : lines.findAllByStatementIdOrderByLineNo(statementId)) {
            if (l.getMatchStatus() == MatchStatus.CREATED) {
                finance.voidByDoc("BANK_STMT_LINE", String.valueOf(l.getId()), "Ekstre içe aktarımı geri alındı");
            }
        }
        stmt.setDeleted(true);
        audit.record("BANK_STMT_DELETE", "BankStatement", statementId, "Ekstre silindi (yanlış içe aktarım)");
    }

    // --- iç yardımcılar ------------------------------------------------

    private List<BankTxnView> candidateTxns(BankStatement stmt) {
        LocalDate from = (stmt.getPeriodStart() != null ? stmt.getPeriodStart() : LocalDate.now())
                .minusDays(CANDIDATE_WINDOW_DAYS);
        LocalDate to = (stmt.getPeriodEnd() != null ? stmt.getPeriodEnd() : LocalDate.now())
                .plusDays(CANDIDATE_WINDOW_DAYS);
        return finance.bankLedger(stmt.getFinAccountId(), from, to);
    }

    private void recomputeStatus(long statementId) {
        long total = lines.countByStatementId(statementId);
        int unmatched = lines.countByStatementIdAndMatchStatus(statementId, MatchStatus.UNMATCHED);
        BankStatement stmt = get(statementId);
        stmt.recomputeStatus((int) total - unmatched, unmatched);
    }

    private BankStatementLine requireLine(long id) {
        return lines.findById(id).orElseThrow(() -> new NotFoundException("Ekstre satırı", id));
    }

    private void requireUnresolvedOrMatched(BankStatementLine line) {
        if (line.getMatchStatus() == MatchStatus.CREATED) {
            throw new BusinessRuleException("already_created",
                    "Bu satırdan zaten bir hareket üretildi; önce geri alın");
        }
    }

    private int parseThreshold() {
        try {
            return Integer.parseInt(settings.getOrDefault("reconciliation.autoMatchThreshold", "80").trim());
        } catch (NumberFormatException e) {
            return StatementMatcher.AUTO_THRESHOLD;
        }
    }

    private BankAccountView requireAccount(long finAccountId) {
        return finance.bankAccounts().stream().filter(a -> a.id() == finAccountId).findFirst()
                .orElseThrow(() -> new NotFoundException("Banka hesabı", finAccountId));
    }

    private static ParsedLine toParsedLine(BankStatementLine e) {
        return new ParsedLine(e.getValueDate(), e.getBookingDate(), e.getAmount(), e.getCurrency(),
                e.getDescription(), e.getCounterparty(), e.getBankRef(), e.getRawLine());
    }

    private static List<StatementMatcher.TxnView> toTxnViews(List<BankTxnView> src) {
        return src.stream()
                .map(t -> new StatementMatcher.TxnView(t.id(), t.date(), t.signedAmount(), t.description(), t.docNo()))
                .toList();
    }
}
