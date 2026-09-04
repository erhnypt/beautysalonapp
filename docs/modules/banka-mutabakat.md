# Modül: Banka Ekstresi İçe Aktarma & Mutabakat (`modules.reconciliation`)

Öncelik **P2 (Faz 8)** · Plan §8+, §10.4 ("Banka ekstresi içe aktarma → sonraki faz")
· Veri modeli: `bank_statement`, `bank_statement_line` (V12)

Bir **BANKA** hesabının banka ekstresini (MT940 veya CSV) içe aktarır, her ekstre
satırını mevcut **kasa/banka hareketleriyle** (`cash_transaction`) eşleştirir; eşleşmeyen
satırlar için ya var olan bir hareketle elle bağ kurulur, ya yeni bir hareket **oluşturulur**
(banka masrafı, faiz, gelen havale gibi işletmenin kaydetmediği hareketler), ya da satır
**yok sayılır** (açılış kaydı, bilinen çift kayıt).

> CLAUDE.md #1: Bu modül **dışarıya HTTP çağrısı yapmaz**. Ekstre dosyası kullanıcı tarafından
> yüklenir; hiçbir iş verisi internete gönderilmez.

## Veri modeli (V12)

```
bank_statement
  fin_account_id      BANKA türü fin_account (zorunlu)
  source_format       MT940 | CSV
  statement_ref       dosyadaki ekstre no (MT940 :28C:)
  period_start/end    DATE
  opening_balance     NUMERIC(19,4)   -- dosyadan (:60F:)
  closing_balance     NUMERIC(19,4)   -- dosyadan (:62F:)
  line_count          INTEGER
  matched_count       INTEGER         -- MATCHED + CREATED + IGNORED
  original_filename   VARCHAR(255)
  imported_at         TIMESTAMP
  status              IMPORTED | RECONCILED   -- tüm satırlar çözülünce RECONCILED

bank_statement_line
  statement_id        FK
  line_no             INTEGER
  value_date          DATE (valör)
  booking_date        DATE (işlem)
  amount              NUMERIC(19,4)   -- İŞARETLİ: + para girişi, − para çıkışı (banka bakışı)
  currency            VARCHAR(3)
  description         VARCHAR(500)
  counterparty        VARCHAR(200)
  bank_ref            VARCHAR(80)     -- MT940 :61: referans / CSV referans sütunu
  raw_line            VARCHAR(1000)   -- denetim / hata ayıklama
  match_status        UNMATCHED | MATCHED | IGNORED | CREATED
  matched_txn_id      cash_transaction.id  (MATCHED/CREATED iken)
  match_score         0..100 (otomatik öneri güveni; elle işlemde null)
  note               VARCHAR(300)
  UNIQUE (statement_id, line_no)
```

`cash_transaction` **değiştirilmez**. Bir hareketin "mutabık" olup olmadığı, herhangi bir
`bank_statement_line.matched_txn_id` onu gösteriyor mu diye bakılarak bulunur.

## Domain (framework'süz, %100 birim testli)

| Sınıf | İş |
|---|---|
| `Mt940Parser` | SWIFT MT940 metnini `ParsedStatement`'a çevirir (`:20: :25: :28C: :60F: :61: :86: :62F:`). Virgüllü ondalık, `YYMMDD` tarih, `C/D/RC/RD` işaret. |
| `CsvStatementParser` | Esnek CSV. `CsvLayout` (sütun eşlemesi, ondalık ayıracı, tarih biçimi) verilir; `CsvLayout.detect(header)` yaygın Türk banka başlıklarını tanır. Tek `amount` sütunu **veya** ayrı `debit`/`credit` sütunları. |
| `StatementMatcher` | `suggest(line, adaylar)` → 0–100 skorla sıralı `MatchCandidate` listesi. Skor: tutar (işaret dahil) tam +50 · tarih tam +30 / ±1g +20 / ±3g +10 · referans/`docNo` içerir +20 · açıklama ortak kelime +≤10. `reconcile(satırlar, hareketler)` → açgözlü 1:1 atama (her hareket bir kez). Eşik ≥ `AUTO_THRESHOLD` (80) = otomatik önerilebilir. |

Değer nesneleri: `ParsedStatement`, `ParsedLine` (işaretli `BigDecimal`), `MatchCandidate`,
`StatementFormat`, `MatchStatus`, `CsvLayout`.

## İş kuralları

- Ekstre satırının işareti bankanın bakışıdır: **+ = hesaba para girişi**. Eşleşecek kasa
  hareketi: banka hesabına göre `signedEffectOnAccount(bankAccountId)` aynı işaretli olmalı
  (girişte `COLLECTION`/gelen virman, çıkışta `PAYMENT`/giden virman).
- Bir `cash_transaction` yalnızca **bir** ekstre satırıyla eşleşebilir; matcher zaten
  eşleşmiş hareket id'lerini aday kümesinden çıkarır.
- **Yeni hareket oluşturma** (`createTransaction`): satırdan `FinancePort.collect`/`pay` ile
  banka hesabına hareket üretir (gelir/gider kartı seçilir, açıklama satırdan gelir),
  sonra satırı `CREATED` + `matched_txn_id` yapar. Ters yön: hareket iptal edilirse
  (`FinancePort.voidByDoc`) satır tekrar `UNMATCHED` olur.
- **Yok sayma** (`ignore`): satır `IGNORED` + zorunlu `note`.
- İçe aktarma **idempotent değildir**; aynı dosyayı iki kez yüklemek iki ekstre üretir.
  Kullanıcı yanlış yüklemeyi siler (`deleted=true`, mali kayıt olmadığı için soft-delete serbest).
- Ekstre `RECONCILED` = hiç `UNMATCHED` satır kalmadı. Dashboard'a "mutabık olmayan N satır"
  uyarısı eklenebilir (sonraki iterasyon).
- Kur: ekstre satırı `currency` hesabın para birimiyle aynı olmalı; değilse satır
  `UNMATCHED` kalır ve uyarı verilir (çoklu döviz mutabakatı v2).

## Cross-module (CLAUDE.md #5)

`FinancePort`'a eklenen salt-okunur + yazma uçları:
```java
List<BankAccountView> bankAccounts();                 // kind = BANKA
List<BankTxnView> bankLedger(long accountId, LocalDate from, LocalDate to);
// collect(...) / pay(...) / voidByDoc(...) zaten var
record BankAccountView(long id, String code, String name, String currency, BigDecimal balance) {}
record BankTxnView(long id, LocalDate date, BigDecimal signedAmount, String description, String docNo) {}
```

## REST API (`/api/v1/bank-reconciliation`)

| Metot | Uç | Açıklama |
|---|---|---|
| `GET`  | `/accounts` | Mutabakat yapılabilir BANKA hesapları |
| `POST` | `/import` | `multipart: finAccountId, format, file` → ekstre + satırlar + otomatik öneriler |
| `GET`  | `/` | Ekstre listesi (özet) |
| `GET`  | `/{id}` | Ekstre + satırlar + her `UNMATCHED` satır için öneri adayları |
| `POST` | `/lines/{lineId}/match` | `{ txnId }` — var olan hareketle bağ kur |
| `POST` | `/lines/{lineId}/unmatch` | Bağı çöz |
| `POST` | `/lines/{lineId}/ignore` | `{ note }` — yok say |
| `POST` | `/lines/{lineId}/create-transaction` | `{ incomeExpenseCardId, description?, partyAccountId? }` — satırdan hareket üret |
| `POST` | `/{id}/auto-match` | Skoru eşiğin üzerindeki tüm önerileri uygula |
| `DELETE` | `/{id}` | Yanlış içe aktarmayı sil (soft) |

Yetki: `FINANCE_VIEW` (okuma), `FINANCE_EDIT` (eşleştirme/oluşturma/silme).

## Frontend

`web/src/pages/BankReconciliationPage.tsx` — hesap seç → dosya yükle → satır tablosu:
her satır için durum rozeti, tutar (renkli işaret), en iyi öneri + "Eşleştir" / "Yeni hareket" /
"Yok say" aksiyonları. Üstte "otomatik eşleştir" ve özet (N/M mutabık, açılış/kapanış bakiye farkı).

## Test

- `Mt940ParserTest` — gerçek örnek MT940 blokları, çok satırlı `:86:`, `C/D/RC/RD`, açılış/kapanış.
- `CsvStatementParserTest` — tek-tutar ve debit/credit düzenleri, TR/EN ondalık, başlık tespiti.
- `StatementMatcherTest` — tam eşleşme, tarih toleransı, referans, çoklu aday sıralama,
  1:1 atama, zaten eşleşmişi eleme.
- `BankReconciliationServiceTest` — import → auto-match → manuel match → create-transaction →
  void yansıması → RECONCILED geçişi.
- `BankReconciliationApiTest` — MockMvc slice, yetki senaryoları, multipart import.
