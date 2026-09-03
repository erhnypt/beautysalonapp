package com.beautysalonapp.modules.finance.application;

import com.beautysalonapp.audit.application.AuditService;
import com.beautysalonapp.core.domain.Money;
import com.beautysalonapp.core.error.BusinessRuleException;
import com.beautysalonapp.core.error.NotFoundException;
import com.beautysalonapp.core.sequence.SequenceService;
import com.beautysalonapp.modules.finance.domain.CashTransaction;
import com.beautysalonapp.modules.finance.domain.CashTxnType;
import com.beautysalonapp.modules.finance.domain.FinAccount;
import com.beautysalonapp.modules.finance.domain.FinAccountKind;
import com.beautysalonapp.modules.finance.domain.IncomeExpenseCard;
import com.beautysalonapp.modules.finance.infrastructure.CashTransactionRepository;
import com.beautysalonapp.modules.finance.infrastructure.FinAccountRepository;
import com.beautysalonapp.modules.finance.infrastructure.IncomeExpenseCardRepository;
import com.beautysalonapp.modules.party.application.PartyLedger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
public class FinanceService implements FinancePort {

    private static final Long BRANCH = 1L;

    private final FinAccountRepository accounts;
    private final IncomeExpenseCardRepository cards;
    private final CashTransactionRepository txns;
    private final PartyLedger partyLedger;
    private final SequenceService sequences;
    private final AuditService audit;

    public FinanceService(FinAccountRepository accounts, IncomeExpenseCardRepository cards,
                          CashTransactionRepository txns, PartyLedger partyLedger,
                          SequenceService sequences, AuditService audit) {
        this.accounts = accounts;
        this.cards = cards;
        this.txns = txns;
        this.partyLedger = partyLedger;
        this.sequences = sequences;
        this.audit = audit;
    }

    // --- hesap planı ----------------------------------------------------

    @Transactional(readOnly = true)
    public List<FinAccount> listAccounts() {
        return accounts.findAllByDeletedFalseOrderByKindAscCodeAsc();
    }

    @Transactional(readOnly = true)
    public FinAccount getAccount(long id) {
        return accounts.findById(id).orElseThrow(() -> new NotFoundException("Hesap", id));
    }

    public FinAccount createAccount(String code, String name, FinAccountKind kind, String currency,
                                    BigDecimal openingBalance, boolean makeDefault) {
        if (accounts.findByBranchIdAndCode(BRANCH, code).isPresent()) {
            throw new BusinessRuleException("account_exists", "Bu hesap kodu zaten var: " + code);
        }
        FinAccount a = new FinAccount(code.trim().toUpperCase(), name.trim(), kind,
                currency == null ? "TRY" : currency);
        if (openingBalance != null) a.setOpeningBalance(openingBalance);
        if (makeDefault) {
            accounts.findFirstByKindAndIsDefaultTrue(kind).ifPresent(x -> x.setDefault(false));
            a.setDefault(true);
        }
        audit.record("FIN_ACCOUNT_CREATE", "FinAccount", a.getCode(), "Hesap açıldı: " + a.getName());
        return accounts.save(a);
    }

    @Transactional(readOnly = true)
    public List<IncomeExpenseCard> listCards() {
        return cards.findAllByDeletedFalseOrderByCode();
    }

    public IncomeExpenseCard createCard(Long parentId, String code, String name,
                                        com.beautysalonapp.modules.finance.domain.CardDirection direction,
                                        boolean serviceCard) {
        if (cards.findByBranchIdAndCode(BRANCH, code).isPresent()) {
            throw new BusinessRuleException("card_exists", "Bu kart kodu zaten var: " + code);
        }
        if (parentId != null) {
            IncomeExpenseCard parent = cards.findById(parentId)
                    .orElseThrow(() -> new NotFoundException("Üst kart", parentId));
            parent.setPostable(false); // üst karta hareket yazılamaz
        }
        IncomeExpenseCard c = new IncomeExpenseCard(parentId, code.trim(), name.trim(), direction);
        c.setServiceCard(serviceCard);
        return cards.save(c);
    }

    // --- hareketler ---------------------------------------------------

    /** Elle tahsilat/tediye/virman. */
    public CashTransaction createManual(CashTxnType type, LocalDate date, long accountId,
                                        Long counterAccountId, Long partyAccountId, Long cardId,
                                        BigDecimal amount, String description) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessRuleException("bad_amount", "Tutar pozitif olmalı");
        }
        FinAccount account = getAccount(accountId);
        CashTransaction t = new CashTransaction(type, date == null ? LocalDate.now() : date,
                accountId, amount, account.getCurrency());
        t.setDescription(description);
        t.setDocNo(sequences.next(BRANCH, "RECEIPT", "DK"));
        t.setIncomeExpenseCardId(validateCard(cardId));

        switch (type) {
            case COLLECTION -> {
                t.setPartyAccountId(partyAccountId);
                txns.save(t);
                if (partyAccountId != null) {
                    partyLedger.post(PartyLedger.LedgerEntry.credit(partyAccountId, t.getDate(),
                            "PAYMENT", t.getDocNo(), "Tahsilat: " + nz(description), amount, t.getCurrency()));
                }
            }
            case PAYMENT -> {
                t.setPartyAccountId(partyAccountId);
                txns.save(t);
                if (partyAccountId != null) {
                    partyLedger.post(PartyLedger.LedgerEntry.debit(partyAccountId, t.getDate(),
                            "PAYMENT", t.getDocNo(), "Tediye: " + nz(description), amount, t.getCurrency()));
                }
            }
            case TRANSFER -> {
                if (counterAccountId == null || counterAccountId.equals(accountId)) {
                    throw new BusinessRuleException("bad_transfer", "Geçerli bir hedef hesap seçin");
                }
                getAccount(counterAccountId);
                t.setCounterAccountId(counterAccountId);
                txns.save(t);
            }
            default -> throw new BusinessRuleException("unsupported", "Bu hareket türü v1'de desteklenmiyor: " + type);
        }
        audit.record("CASH_TXN", "CashTransaction", t.getDocNo(),
                type + " " + amount.toPlainString() + " " + t.getCurrency() + " / hesap " + account.getCode());
        return t;
    }

    public void voidTransaction(long id, String reason) {
        CashTransaction t = txns.findById(id).orElseThrow(() -> new NotFoundException("Hareket", id));
        if (t.isVoided()) {
            throw new BusinessRuleException("already_voided", "Bu hareket zaten iptal edilmiş");
        }
        t.setVoided(true);
        t.setVoidReason(reason);
        // Bakiye etkisi: voided hareket signedEffectOnAccount()'ta 0 döner — ayrı ters kayıt gereksiz.
        // Cari tarafında ise gerçek bir ters kayıt yazılır (append-only defter).

        if (t.getPartyAccountId() != null && (t.getType() == CashTxnType.COLLECTION || t.getType() == CashTxnType.PAYMENT)) {
            String ledgerRef = t.getDocRef() != null ? t.getDocRef() : t.getDocNo();
            partyLedger.reverse("PAYMENT", ledgerRef, reason);
        }
        audit.record("CASH_TXN_VOID", "CashTransaction", t.getDocNo(), "Kasa hareketi iptal: " + nz(reason));
    }

    // --- FinancePort ------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public long defaultCashAccountId() {
        return accounts.findFirstByKindAndIsDefaultTrue(FinAccountKind.KASA)
                .or(() -> accounts.findFirstByKindAndActiveTrueOrderByIdAsc(FinAccountKind.KASA))
                .orElseThrow(() -> new BusinessRuleException("no_cash_account", "Tanımlı kasa hesabı yok"))
                .getId();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public long collect(CollectCommand c) {
        CashTransaction t = postDocLinked(CashTxnType.COLLECTION, c.date(), c.accountId(),
                c.partyAccountId(), c.incomeExpenseCardId(), c.amount(), c.currency(), c.description(),
                c.docType(), c.docRef(), c.lineKey());
        if (c.partyAccountId() != null) {
            partyLedger.post(new PartyLedger.LedgerEntry(c.partyAccountId(), t.getDate(),
                    "PAYMENT", c.docRef(), c.lineKey(), "Tahsilat: " + nz(c.description()),
                    BigDecimal.ZERO, c.amount(), t.getCurrency()));
        }
        return t.getId();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public long pay(PayCommand c) {
        CashTransaction t = postDocLinked(CashTxnType.PAYMENT, c.date(), c.accountId(),
                c.partyAccountId(), c.incomeExpenseCardId(), c.amount(), c.currency(), c.description(),
                c.docType(), c.docRef(), c.lineKey());
        if (c.partyAccountId() != null) {
            partyLedger.post(new PartyLedger.LedgerEntry(c.partyAccountId(), t.getDate(),
                    "PAYMENT", c.docRef(), c.lineKey(), "Tediye: " + nz(c.description()),
                    c.amount(), BigDecimal.ZERO, t.getCurrency()));
        }
        return t.getId();
    }

    @Override
    public void voidByDoc(String docType, String docRef, String reason) {
        List<CashTransaction> list = txns.findByDocTypeAndDocRefAndReversesIdIsNullAndVoidedFalse(docType, docRef);
        for (CashTransaction t : list) {
            voidTransaction(t.getId(), reason);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Money accountBalance(long accountId) {
        FinAccount a = getAccount(accountId);
        BigDecimal bal = a.getOpeningBalance();
        for (CashTransaction t : txns.findByAccountIdOrCounterAccountId(accountId, accountId)) {
            bal = bal.add(t.signedEffectOnAccount(accountId));
        }
        return Money.of(bal, a.getCurrency());
    }

    @Transactional(readOnly = true)
    public List<CashTransaction> ledger(long accountId, LocalDate from, LocalDate to) {
        return txns.ledger(accountId, from, to);
    }

    /** Gelir-gider tablosu: kart bazlı toplamlar. */
    @Transactional(readOnly = true)
    public List<IncomeExpenseRow> incomeExpenseReport(LocalDate from, LocalDate to) {
        var cardById = cards.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(IncomeExpenseCard::getId, x -> x));
        var totals = new java.util.HashMap<Long, BigDecimal>();
        for (CashTransaction t : txns.withCardBetween(from, to)) {
            totals.merge(t.getIncomeExpenseCardId(), signForReport(t), BigDecimal::add);
        }
        return totals.entrySet().stream()
                .map(e -> {
                    IncomeExpenseCard c = cardById.get(e.getKey());
                    return new IncomeExpenseRow(e.getKey(),
                            c == null ? "?" : c.getCode(),
                            c == null ? "?" : c.getName(),
                            c == null ? null : c.getDirection().name(),
                            e.getValue(),
                            c == null ? null : c.getBudgetAmount());
                })
                .sorted(java.util.Comparator.comparing(IncomeExpenseRow::code))
                .toList();
    }

    public record IncomeExpenseRow(long cardId, String code, String name, String direction,
                                   BigDecimal amount, BigDecimal budget) {}

    // --- iç yardımcılar ------------------------------------------

    private CashTransaction postDocLinked(CashTxnType type, LocalDate date, long accountId,
                                          Long partyAccountId, Long cardId, BigDecimal amount, String currency,
                                          String description, String docType, String docRef, String lineKey) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessRuleException("bad_amount", "Tutar pozitif olmalı");
        }
        FinAccount account = getAccount(accountId);
        String key = lineKey == null ? "-" : lineKey;
        List<CashTransaction> existing = txns.findByDocTypeAndDocRefAndReversesIdIsNullAndVoidedFalse(docType, docRef);
        for (CashTransaction e : existing) {
            if (key.equals(e.getLineKey() == null ? "-" : e.getLineKey())) {
                return e; // idempotent
            }
        }
        CashTransaction t = new CashTransaction(type, date == null ? LocalDate.now() : date,
                accountId, amount, currency == null ? account.getCurrency() : currency);
        t.setPartyAccountId(partyAccountId);
        t.setIncomeExpenseCardId(validateCard(cardId));
        t.setDescription(description);
        t.setDocNo(sequences.next(BRANCH, "RECEIPT", "DK"));
        t.setDocType(docType);
        t.setDocRef(docRef);
        t.setLineKey(key);
        return txns.save(t);
    }

    private Long validateCard(Long cardId) {
        if (cardId == null) {
            return null;
        }
        IncomeExpenseCard c = cards.findById(cardId)
                .orElseThrow(() -> new NotFoundException("Gelir/gider kartı", cardId));
        if (!c.isPostable()) {
            throw new BusinessRuleException("card_not_postable", "Üst karta hareket yazılamaz: " + c.getCode());
        }
        return cardId;
    }

    private static BigDecimal signForReport(CashTransaction t) {
        return switch (t.getType()) {
            case COLLECTION, FX_BUY -> t.getAmount();
            case PAYMENT, FX_SELL -> t.getAmount().negate();
            case TRANSFER -> BigDecimal.ZERO;
        };
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
