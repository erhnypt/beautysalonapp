package com.beautysalonapp.modules.party.application;

import com.beautysalonapp.core.domain.Money;
import com.beautysalonapp.core.error.NotFoundException;
import com.beautysalonapp.modules.party.domain.AccountKind;
import com.beautysalonapp.modules.party.domain.PartyAccount;
import com.beautysalonapp.modules.party.domain.PartyTransaction;
import com.beautysalonapp.modules.party.infrastructure.PartyAccountRepository;
import com.beautysalonapp.modules.party.infrastructure.PartyTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
public class PartyLedgerService implements PartyLedger {

    private static final Logger log = LoggerFactory.getLogger(PartyLedgerService.class);

    private final PartyTransactionRepository txns;
    private final PartyAccountRepository accounts;

    public PartyLedgerService(PartyTransactionRepository txns, PartyAccountRepository accounts) {
        this.txns = txns;
        this.accounts = accounts;
    }

    @Override
    public void post(LedgerEntry e) {
        String lineKey = e.lineKey() == null ? "-" : e.lineKey();
        if (e.docRef() != null && txns.existsByDocTypeAndDocRefAndLineKey(e.docType(), e.docRef(), lineKey)) {
            log.debug("Cari hareket zaten mevcut, atlanıyor: {} {} {}", e.docType(), e.docRef(), lineKey);
            return;
        }
        BigDecimal debit = e.debit() == null ? BigDecimal.ZERO : e.debit();
        BigDecimal credit = e.credit() == null ? BigDecimal.ZERO : e.credit();
        if (debit.signum() < 0 || credit.signum() < 0) {
            throw new IllegalArgumentException("Cari harekette borç/alacak negatif olamaz; ters kayıt kullanın");
        }
        txns.save(new PartyTransaction(e.accountId(), e.date(), e.docType(), e.docRef(), lineKey,
                e.description(), debit, credit, e.currency()));
    }

    @Override
    public void reverse(String docType, String docRef, String reason) {
        List<PartyTransaction> original = txns.findByDocTypeAndDocRefAndReversesIdIsNull(docType, docRef);
        if (original.isEmpty()) {
            throw new NotFoundException("Ters kaydedilecek cari hareket bulunamadı: " + docType + "/" + docRef);
        }
        for (PartyTransaction t : original) {
            PartyTransaction rev = new PartyTransaction(
                    t.getAccountId(), LocalDate.now(), "REVERSAL", docRef,
                    "rev-" + t.getId(),
                    "İptal: " + (reason == null ? "" : reason) + " (kaynak #" + t.getId() + ")",
                    t.getCredit(), t.getDebit(), t.getCurrency());
            rev.setReversesId(t.getId());
            txns.save(rev);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Money balance(long accountId) {
        PartyAccount acc = accounts.findById(accountId)
                .orElseThrow(() -> new NotFoundException("PartyAccount", accountId));
        BigDecimal bal = txns.balance(accountId);
        BigDecimal total = acc.getOpeningBalance().add(bal == null ? BigDecimal.ZERO : bal);
        return Money.of(total, acc.getCurrency());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionView> statement(long accountId, LocalDate from, LocalDate to) {
        PartyAccount acc = accounts.findById(accountId)
                .orElseThrow(() -> new NotFoundException("PartyAccount", accountId));
        List<PartyTransaction> rows = txns.statement(accountId, from, to);
        // Yürüyen bakiye: açılış + aralık öncesi devir
        BigDecimal running = acc.getOpeningBalance().add(openingBefore(accountId, from));
        List<TransactionView> out = new java.util.ArrayList<>(rows.size());
        for (PartyTransaction t : rows) {
            running = running.add(t.getDebit()).subtract(t.getCredit());
            out.add(new TransactionView(t.getId(), t.getDate(), t.getDocType(), t.getDocRef(),
                    t.getDescription(), t.getDebit(), t.getCredit(), running, t.getCurrency()));
        }
        return out;
    }

    @Override
    public long resolveAccount(long partyId, AccountKind kind, String currency) {
        return accounts.findByPartyIdAndKindAndCurrency(partyId, kind, currency)
                .orElseGet(() -> accounts.save(new PartyAccount(partyId, kind, currency)))
                .getId();
    }

    private BigDecimal openingBefore(long accountId, LocalDate from) {
        if (from == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (PartyTransaction t : txns.findByAccountIdOrderByDateAscIdAsc(accountId)) {
            if (t.getDate().isBefore(from)) {
                sum = sum.add(t.getDebit()).subtract(t.getCredit());
            }
        }
        return sum;
    }
}
