# Modül: Satış Sözleşmesi ve Otomatik Taksitlendirme (`modules.contract`)

Öncelik **P0** · Plan §10.12 · Veri modeli §9.8

## Amaç

Bir sözleşme (paket hizmet/ürün satışı) → peşinat + taksit planı üretir, taksit
tahsilatlarını takip eder, erken kapama ve iptali destekler. Taksit üretim algoritması
**saf domain sınıfında** ve tamamen birim testlidir (CLAUDE.md "mali hesaplama testsiz
merge edilmez").

## Veri modeli

```
sales_contract   party_id, party_account_id, contract_date, total_amount, down_payment,
                 installment_count, first_due_date, period (AYLIK|HAFTALIK),
                 interest_rate (saklanır, v1'de tutara uygulanmaz — bkz. not),
                 status (AKTIF|IPTAL|KAPANDI), cancel_reason, currency, doc_no
contract_line    item_id?, description, quantity, unit_price, session_count?
                 (paket satırındaki seans adedi — bkz. modules.appointment SessionConsumptionPort)
installment      contract_id, seq, due_date, amount, paid_amount, paid_at,
                 status (BEKLIYOR|GECIKMIS|ODENDI|IPTAL)
```

## Otomatik taksitlendirme algoritması (`InstallmentPlan`, saf domain)

```
kalan        = toplam - peşinat
taksitTutarı = ROUND(kalan / adet, 2)            ← kuruş
son taksit   = kalan - taksitTutarı * (adet-1)   ← kuruş farkı son taksite gider
vadeler      = ilkVade, ilkVade + 1 dönem, …      ← LocalDate.plusMonths ay sonunu
                                                     otomatik kırpar (31 Ocak → 28/29 Şubat)
```

- `AYLIK` ve `HAFTALIK` periyot destekleniyor (`InstallmentPeriod.advance`).
- Kalan tutar sıfırsa (tamamı peşin) **boş taksit planı** üretilir — 0 TL'lik taksit satırı
  oluşturulmaz.
- `interestRate` alanı v1'de yalnızca **saklanır**; taksit tutarına vade farkı olarak
  uygulanmaz (plan bunu ayrı bir faz olarak bırakıyor — bu davranışı değiştirirseniz
  `InstallmentPlan`'ı ve testlerini güncelleyin, hesaplamayı kontrolcü/servis katmanına
  değil domain sınıfına ekleyin).
- Test kapsamı: `InstallmentPlanTest` — kuruş farkı, ay sonu düzeltmesi (31 Ocak→Şubat),
  tamamı peşin, tek taksit, haftalık periyot senaryoları.

## İş kuralları

- **Taksit ödeme** (`ContractService.payInstallment`): tahsilat kasa/banka/POS/çek
  üzerinden `FinancePort`/`ChequePort` ile işlenir, taksit `ODENDI` olur, cariye alacak
  yazılır (borç azalır).
- **Erken kapama** (`earlyPayoff`): `BEKLIYOR`/`GECIKMIS` durumundaki tüm taksitlerin
  kalan tutarı tek seferde tahsil edilir; her taksit kendi `payInstallment` akışından geçer
  (idempotent, ayrı ayrı işlenir — kısmi hata durumunda hangi taksitlerin kapandığı nettir).
- **İptal** (`cancel`): ödenmemiş taksitlerin toplam kalanı cariye **alacak** (credit)
  olarak yazılır — yani müşterinin sözleşmeden doğan borcu düşer (kalan seans/hizmet
  mahsuplaşması budur). Taksitler `IPTAL` olur. Sözleşme **silinmez**; `status=CANCELLED`
  + `cancelReason` (CLAUDE.md #3 — hard delete yasak, iptal = ters kayıt).
  - Zaten iptal edilmiş bir sözleşme tekrar iptal edilemez (`already_cancelled`).
- **Yeniden yapılandırma (restructure):** v1'de **yok** — yalnızca erken kapama ve iptal
  var. Vade/taksit sayısını değiştirmeden yeniden planlama ihtiyacı doğarsa: mevcut
  sözleşmeyi iptal edip yeni bir sözleşme açmak geçici çözümdür; gerçek bir "restructure"
  akışı (mevcut `installment` kayıtlarını koruyarak yeniden plan üretme) plan §10.12'de
  istenen ama henüz uygulanmamış bir özelliktir.
- Sözleşme satırındaki `session_count` (paket seans adedi), randevu modülünün
  `SessionConsumptionPort`'u üzerinden "10 seans aldı, 4 kullandı, 6 kaldı" takibine
  bağlanır — bu modül sadece paketin **kaynağıdır**, tüketimi `modules.appointment` yönetir.

## Endpoint taslağı

```
GET  /api/v1/contracts?...                          (CONTRACT_VIEW)
GET  /api/v1/contracts/{id}                          (CONTRACT_VIEW)
POST /api/v1/contracts                               (CONTRACT_ADD) — satırlar + taksit planı önizlemesiz, doğrudan üretim
POST /api/v1/contracts/installments/{id}/pay         (CONTRACT_EDIT)
POST /api/v1/contracts/{id}/early-payoff             (CONTRACT_EDIT)
POST /api/v1/contracts/{id}/cancel                   (CONTRACT_VOID)
GET  /api/v1/contracts/schedule?until=               (CONTRACT_REPORT) — vade takvimi
```

> Not: plan §10.12 bir "sözleşme oluşturma sihirbazı ... vade planı önizleme → onay" akışı
> tarif ediyor. Backend `POST /contracts` doğrudan planı üretip kaydeder; **önizleme adımı
> arayüz (frontend) tarafında** `InstallmentPlan.generate` mantığının bir kopyasıyla ya da
> "dry-run" bir servis çağrısıyla sağlanmalı — bugün ayrı bir "önizle" uç noktası yok.

## KVKK değerlendirmesi (CLAUDE.md #10)

Bu modülde yeni bir PII alanı yok — `sales_contract`/`installment`/`contract_line`
yalnızca tutar, tarih ve durum bilgisi taşır; kimlik/iletişim bilgisi zaten `party`
tablosunda şifreli tutuluyor ve buradan yalnızca `party_id` ile referans veriliyor.
Taranmış imzalı sözleşme nüshası (plan §10.12) `attachment` tablosuna referansla
saklanmalı — dosyanın kendisi DB'de değil, `attachments/` dizininde durur (bkz.
`docs/04-guvenlik-kvkk.md` §1 madde 6).

## Test

- `InstallmentPlanTest` — algoritmanın tam kapsamı (bkz. yukarı).
- `ContractServiceTest` — oluşturma, ödeme, erken kapama, iptal senaryoları
  (`@SpringBootTest`, gerçek `PartyLedger`/`FinancePort` ile uçtan uca).
