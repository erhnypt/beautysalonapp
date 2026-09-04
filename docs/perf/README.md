# Performans Testi — 10 Yıllık Sentetik Veri

Teknik plan §17 (Faz 7) ve §18: **liste ekranları < 300 ms**, sentetik veri ~500k stok hareketi.

## Bileşenler

| Dosya | Rol |
|---|---|
| `server/.../perf/PerfDataGenerator.java` | `beautysalonapp.perf.seed=true` iken çalışan `ApplicationRunner`. Toplu JDBC `batchUpdate` ile 10 yıllık veri basar. Üretim derlemesinde **asla** etkin değildir. |
| `server/src/test/.../perf/PerformanceBudgetTest.java` | `@Tag("perf")` — normal `mvn test`'ten dışlanır. İzole bellek-içi H2'ye indirgenmiş ölçek (~120k hareket) tohumlar, en ağır rapor/liste sorgularını ölçer, bütçeyle karşılaştırır. |
| `pom.xml` → `surefire.excludedGroups=perf` + `-Pperf` profili | Etiket dışlama/dahil etme. |

## 1. Otomatik bütçe testi (CI'da manuel tetik)

```bash
cd server
./mvnw -Pperf test -Dtest=PerformanceBudgetTest
```

- İzole `jdbc:h2:mem:perfbudget` kullanır; dosya veritabanına dokunmaz.
- Eşik gevşektir (2000 ms) çünkü CI donanımı değişkendir; **as l değerlendirme loglanan sürelerdir**
  (`=== Faz 7 performans bütçesi ===` bloğu).
- Referans ölçüm (Apple M-serisi, 2026): dashboard ~40 ms, saf liste sorguları < 1 ms.

## 2. Gerçek 500k manuel doğrulama

Ayrı bir veri diziniyle çalıştır (mevcut kurulumu kirletme):

```bash
cd server
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./mvnw spring-boot:run \
  -Dspring-boot.run.arguments="\
--beautysalonapp.data-dir=./perf-data \
--beautysalonapp.perf.seed=true \
--beautysalonapp.perf.movements=500000 \
--beautysalonapp.perf.appointments=150000 \
--beautysalonapp.perf.invoices=60000 \
--beautysalonapp.perf.cashTxns=150000 \
--beautysalonapp.perf.partyTxns=250000 \
--beautysalonapp.perf.customers=8000 \
--beautysalonapp.perf.contracts=6000"
```

Tohumlama loglarına `PerfDataGenerator BİTTİ — N sn` yazılır. Ardından:

- Tarayıcıda `http://localhost:8734` → Cari listesi, Stok hareket listesi, Fatura listesi,
  Günlük Analiz dashboard'u açılış sürelerini ölç (tarayıcı DevTools → Network → XHR).
- Veya doğrudan API: `curl -w '%{time_total}\n' -o /dev/null -s -b cookies http://localhost:8734/api/v1/reports/daily`
- H2 dosya boyutu `perf-data/data/beautysalonapp.mv.db` ~ birkaç yüz MB olmalı.

### Ölçek anahtarları (`--beautysalonapp.perf.*`)

| Anahtar | Varsayılan | Not |
|---|---|---|
| `years` | 10 | Veri zaman aralığı |
| `customers` | 5000 | `party` (MUSTERI) + `party_account` |
| `items` | 250 | `item` + `item_unit` + `stock_level` |
| `staff` | 15 | `party` (PERSONEL) + `staff` |
| `services` | 30 | `service_definition` |
| `movements` | 500000 | **başlık metriği** — `stock_movement` |
| `appointments` | 120000 | `appointment` |
| `invoices` | 40000 | `invoice` (+ 2× `invoice_line`) |
| `cashTxns` | 100000 | `cash_transaction` |
| `partyTxns` | 150000 | `party_transaction` |
| `contracts` | 4000 | `sales_contract` (+ 12× `installment`) |
| `cheques` | 2000 | `cheque` |

Yeniden çalıştırmada `stock_movement` doluysa tohumlama atlanır (log uyarısı). Baştan başlamak
için `perf-data/` dizinini silin.

## 3. İndeks notları

Migration'larda liste/rapor yollarını kapsayan indeksler tanımlı:

- `party` → `ix_party_type`, `ix_party_name (title)`, `ix_party_phone_bi`
- `stock_movement` → `ix_stock_mv_item_wh (item_id, warehouse_id, mv_date)`, `ix_stock_mv_doc`
- `appointment` → `ix_appt_staff_time`, `ix_appt_time (start_at)`, `ix_appt_party`
- `invoice` → `ix_invoice_party`, `ix_invoice_type_date (invoice_type, invoice_date)`
- `cash_transaction` → `ix_cash_txn_account (account_id, txn_date)`, `ix_cash_txn_card`
- `installment` → `ix_installment_due (due_date, status)`
- `party_transaction` → `ix_party_txn_account (account_id, txn_date)`

Bütçe aşımı görülürse: (1) ilgili sorgunun `EXPLAIN ANALYZE` çıktısına bak, (2) eksik
indeksi **yeni bir Flyway migration** ile ekle (var olan migration düzenlenmez — CLAUDE.md #4),
(3) rapor sorgusunu okuma modeli / materyalize görünüm ile sadeleştir.

## 4. Bilinen sınırlar

- `PerfDataGenerator` referans bütünlüğünü basit tutar (rastgele ilişkiler); mali tutarlar
  gerçekçi değildir — yalnızca **hacim** ve **sorgu planı** testi içindir, mutabakat testi değil.
- PostgreSQL profili (`-Dspring.profiles.active=postgres`) ile de çalışır; büyük hacimde
  asıl hedef üretim veritabanı PostgreSQL'dir (plan Risk #7).
