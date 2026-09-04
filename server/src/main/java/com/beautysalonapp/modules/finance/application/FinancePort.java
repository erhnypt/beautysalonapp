package com.beautysalonapp.modules.finance.application;

import com.beautysalonapp.core.domain.Money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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

    /** Aktif BANKA türü hesaplar (banka ekstresi mutabakatı için, Faz 8). */
    List<BankAccountView> bankAccounts();

    /** Bir banka hesabının verilen aralıktaki iptal edilmemiş hareketleri, hesaba göre işaretli tutarla. */
    List<BankTxnView> bankLedger(long accountId, LocalDate from, LocalDate to);

    record BankAccountView(long id, String code, String name, String currency) {
    }

    record BankTxnView(long id, LocalDate date, BigDecimal signedAmount, String description, String docNo) {
    }

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
