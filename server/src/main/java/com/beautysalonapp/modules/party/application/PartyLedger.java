package com.beautysalonapp.modules.party.application;

import com.beautysalonapp.core.domain.Money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Cari defter portu (CLAUDE.md #5). Fatura, kasa, sözleşme modülleri cari hareketi
 * bu arayüzle yazar; append-only ve idempotent'tir.
 */
public interface PartyLedger {

    /**
     * Tek bir cari hareket yazar. Aynı {@code (docType, docRef, lineKey)} üçlüsüyle
     * ikinci çağrı yok sayılır (idempotent).
     */
    void post(LedgerEntry entry);

    /** Belgeye ait tüm hareketleri ters kayıtla iptal eder. */
    void reverse(String docType, String docRef, String reason);

    /** Hesabın güncel bakiyesi (borç pozitif). */
    Money balance(long accountId);

    List<TransactionView> statement(long accountId, LocalDate from, LocalDate to);

    /** Taraf için (kind, currency) hesabını bulur, yoksa açar. */
    long resolveAccount(long partyId, com.beautysalonapp.modules.party.domain.AccountKind kind, String currency);

    record LedgerEntry(
            long accountId,
            LocalDate date,
            String docType,
            String docRef,
            String lineKey,
            String description,
            BigDecimal debit,
            BigDecimal credit,
            String currency
    ) {
        public static LedgerEntry debit(long accountId, LocalDate date, String docType, String docRef,
                                        String description, BigDecimal amount, String currency) {
            return new LedgerEntry(accountId, date, docType, docRef, null, description, amount, BigDecimal.ZERO, currency);
        }

        public static LedgerEntry credit(long accountId, LocalDate date, String docType, String docRef,
                                         String description, BigDecimal amount, String currency) {
            return new LedgerEntry(accountId, date, docType, docRef, null, description, BigDecimal.ZERO, amount, currency);
        }
    }

    record TransactionView(
            long id,
            LocalDate date,
            String docType,
            String docRef,
            String description,
            BigDecimal debit,
            BigDecimal credit,
            BigDecimal runningBalance,
            String currency
    ) {}
}
