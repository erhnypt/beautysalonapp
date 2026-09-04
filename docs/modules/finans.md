# Modül: Kasa & Gelir-Gider (`modules.finance`)

Öncelik **P0** · Plan §10.4, §10.6 · Veri modeli §9.5

Bu faz kapsamı: **hesap planı (KASA/BANKA/POS/CEK)**, **gelir-gider kart ağacı**,
**kasa hareketleri** (tahsilat, tediye, virman, döviz al-sat) ve cari defterle entegrasyon.
POS mahsuplaşma, çek portföyü → Faz 4. Banka ekstresi içe aktarma & mutabakat → Faz 8,
bkz. [`banka-mutabakat.md`](banka-mutabakat.md) (`FinancePort.bankAccounts/bankLedger`
bu modülden sağlanır).

## Veri modeli (bu faz)
```
fin_account          kind = KASA | BANKA | POS | CEK
                     code, name, currency, opening_balance,
                     is_commission_bearing, commission_rate, bank_info
income_expense_card  ağaç: parent_id, code, name,
                     direction = INCOME | EXPENSE, budget_amount, is_service_card
cash_transaction     txn_type = COLLECTION | PAYMENT | TRANSFER | FX_BUY | FX_SELL,
                     txn_date, account_id, counter_account_id (virman),
                     party_account_id (tahsilat/tediye), income_expense_card_id,
                     amount NUMERIC(19,4), currency, fx_rate,
                     description, doc_no, voided, void_reason, reverses_id
```

## İş kuralları
- **Silme yok, iptal var** (CLAUDE.md #3): `void` → ters `cash_transaction` (`reverses_id`)
  + cari tarafında `PartyLedger.reverse`.
- Kasa bakiyesi = `opening_balance + Σ(giren) - Σ(çıkan)` (iptal edilmişler hariç).
  Performans için ileride özet tablo; v1'de anlık toplam.
- **Tahsilat (COLLECTION):** kasaya +; müşteri cari hesabına **alacak** (credit) yazılır.
- **Tediye (PAYMENT):** kasadan −; cari hesaba **borç** (debit) yazılır.
- **Virman (TRANSFER):** `account_id`'den çıkar, `counter_account_id`'ye girer (cari yok).
- **Döviz al-sat:** iki kasa arası; kur farkı otomatik gelir/gider kartına (v1'de kartı kullanıcı seçer).
- Her tahsilat/tediye bir **gelir/gider kartına** bağlanabilir (zorunlu değil; ayar ile zorunlu yapılabilir).
- Belge no: `SequenceService` (`RECEIPT` = dekont no).

## Port
```java
interface FinancePort {
    long defaultCashAccount();
    CashTxnRef collect(CollectCommand c);   // fatura/sözleşme/randevu tahsilatı
    CashTxnRef pay(PayCommand c);
    void voidByDoc(String docType, String docRef, String reason);
    Money accountBalance(long accountId);
}
```

## Endpoint taslağı
```
GET  /api/v1/finance/accounts            POST /api/v1/finance/accounts
GET  /api/v1/finance/cards               POST /api/v1/finance/cards      (gelir/gider ağacı)
GET  /api/v1/finance/accounts/{id}/balance
GET  /api/v1/finance/transactions?accountId=&from=&to=
POST /api/v1/finance/collect             (tahsilat)
POST /api/v1/finance/pay                 (tediye)
POST /api/v1/finance/transfer            (virman)
POST /api/v1/finance/transactions/{id}/void
GET  /api/v1/finance/reports/income-expense?from=&to=   (kart bazlı özet)
```

## Raporlar (v1)
Kasa hareket dökümü, kasa bakiyeleri, gelir-gider tablosu (kart bazlı), kâr-zarar özeti.
