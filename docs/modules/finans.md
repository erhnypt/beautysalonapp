# Modül: Kasa & Gelir-Gider (`modules.finance`)

Öncelik **P0** · Plan §10.4, §10.6 · Veri modeli §9.5

Bu faz kapsamı: **hesap planı (KASA/BANKA/POS/CEK)**, **gelir-gider kart ağacı**,
**kasa hareketleri** (tahsilat, tediye, virman, döviz al-sat), **kasa sayımı/kasa farkı**,
**bütçe/gerçekleşen**, **dekont PDF çıktısı** ve cari defterle entegrasyon.
POS mahsuplaşma, çek portföyü → Faz 4. Banka ekstresi içe aktarma & mutabakat → Faz 8,
bkz. [`banka-mutabakat.md`](banka-mutabakat.md) (`FinancePort.bankAccounts/bankLedger`
bu modülden sağlanır).

## Veri modeli
```
fin_account          kind = KASA | BANKA | POS | CEK
                     code, name, currency, opening_balance,
                     is_commission_bearing, commission_rate, bank_info,
                     avg_rate                         ← V13: döviz hesabı ağırlıklı ort. kur
income_expense_card  ağaç: parent_id, code, name,
                     direction = INCOME | EXPENSE, budget_amount, is_service_card
cash_transaction     txn_type = COLLECTION | PAYMENT | TRANSFER | FX_BUY | FX_SELL,
                     txn_date, account_id, counter_account_id (virman),
                     party_account_id (tahsilat/tediye), income_expense_card_id,
                     amount NUMERIC(19,4), currency, fx_rate,
                     description, doc_no, voided, void_reason, reverses_id
cash_count           account_id, count_date, expected_amount, counted_amount,     ← V14
                     difference, reason, adjustment_doc_no
```

## İş kuralları
- **Silme yok, iptal var** (CLAUDE.md #3): `void` → ters `cash_transaction` (`reverses_id`)
  + cari tarafında `PartyLedger.reverse`.
- Kasa bakiyesi = `opening_balance + Σ(giren) - Σ(çıkan)` (iptal edilmişler hariç).
  Performans için ileride özet tablo; v1'de anlık toplam.
- **Tahsilat (COLLECTION):** kasaya +; müşteri cari hesabına **alacak** (credit) yazılır.
- **Tediye (PAYMENT):** kasadan −; cari hesaba **borç** (debit) yazılır.
- **Virman (TRANSFER):** `account_id`'den çıkar, `counter_account_id`'ye girer (cari yok).
- **Döviz al-sat** (`FinanceService.fxExchange`): alımda döviz hesabının ağırlıklı ortalama
  kuru güncellenir (`fin_account.avg_rate`, `WeightedAverageCost` ile aynı yaklaşım);
  satımda gerçekleşen kur farkı (satış kuru − ortalama maliyet) ayrı bir kayıtla
  `finance.fxGainCardId`/`finance.fxLossCardId` ayarındaki karta işlenir — kart tanımlı
  değilse fark yine de kasaya işlenir, sadece raporda kartsız görünür.
- **Kasa sayımı** (`FinanceService.countCash`): sayılan tutar sistem bakiyesinden
  farklıysa gerekçe zorunludur; fark `finance.cashSurplusCardId`/`cashShortageCardId`
  ayarındaki karta "kasa farkı" olarak işlenir ve hesap bakiyesi fiziksel sayıma eşitlenir.
- **Bütçe/gerçekleşen** (`FinanceService.budgetVsActual`): bütçesi tanımlı **her** kart
  listelenir — dönemde hiç hareketi olmasa bile (`actual=0`), aksi halde tam
  harcanmamış bütçeler raporda hiç görünmez.
- Her tahsilat/tediye bir **gelir/gider kartına** bağlanabilir (zorunlu değil; ayar ile zorunlu yapılabilir).
- Belge no: `SequenceService` (`RECEIPT` = dekont no).
- **Dekont PDF** (`VoucherService`, Faz 8): JasperReports ile üretilir. Şablon
  `<dataDir>/config/reports/dekont.jrxml` altında saklanır — ilk kullanımda JAR içindeki
  varsayılan oraya kopyalanır ve **bir daha üzerine yazılmaz**, kullanıcı orada özelleştirebilir
  (logo eklemek isterse `<image>` elemanı ekler). `finance.voucherCompanyName` /
  `finance.voucherFootnote` ayarları başlık ve dipnot metnini besler.

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
POST /api/v1/finance/cards/{id}/budget
GET  /api/v1/finance/accounts/{id}/balance
GET  /api/v1/finance/transactions?accountId=&from=&to=
POST /api/v1/finance/collect             (tahsilat)
POST /api/v1/finance/pay                 (tediye)
POST /api/v1/finance/transfer            (virman)
POST /api/v1/finance/fx                  (döviz alım/satım)
GET  /api/v1/finance/accounts/{id}/cash-counts    POST .../cash-counts   (kasa sayımı)
POST /api/v1/finance/transactions/{id}/void
GET  /api/v1/finance/transactions/{id}/voucher    (dekont PDF)
GET  /api/v1/finance/reports/income-expense?from=&to=   (kart bazlı özet)
GET  /api/v1/finance/reports/budget?from=&to=           (bütçe/gerçekleşen)
```

## Raporlar
Kasa hareket dökümü, kasa bakiyeleri, gelir-gider tablosu (kart bazlı), bütçe/gerçekleşen.
