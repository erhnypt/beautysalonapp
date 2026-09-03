package com.beautysalonapp.modules.finance.application;

import com.beautysalonapp.core.domain.Money;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Finans portu (CLAUDE.md #5). Fatura, sözleşme, randevu modülleri tahsilat/tediyeyi bununla yapar.
 */
public interface FinancePort {

    long defaultCashAccountId();

    /** Müşteriden tahsilat: kasaya +, müşteri cari hesabına alacak. */
    long collect(CollectCommand c);

    /** Satıcıya / gidere tediye: kasadan -, cari hesaba borç (partyAccountId varsa). */
    long pay(PayCommand c);

    /** Belgeye bağlı tüm kasa hareketlerini iptal eder (ters kayıt). */
    void voidByDoc(String docType, String docRef, String reason);

    Money accountBalance(long accountId);

    record CollectCommand(
            LocalDate date,
            long accountId,
            Long partyAccountId,
            Long incomeExpenseCardId,
            BigDecimal amount,
            String currency,
            String description,
            String docType,
            String docRef,
            String lineKey) {
    }

    record PayCommand(
            LocalDate date,
            long accountId,
            Long partyAccountId,
            Long incomeExpenseCardId,
            BigDecimal amount,
            String currency,
            String description,
            String docType,
            String docRef,
            String lineKey) {
    }
}
