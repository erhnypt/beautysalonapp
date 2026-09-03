# Modül: Müşteri / Satıcı Cari Takip (`modules.party`)

Öncelik **P0** · Plan §10.3 · Veri modeli §9.2

## Amaç
Tüm taraf kayıtlarının (müşteri, satıcı, personel, perakende) ortak tabanı ve cari
hesap hareketleri. Diğer modüller (fatura, kasa, sözleşme, randevu, personel) taraf
ve cari hareket için bu modülün **application** arayüzlerini kullanır.

## Ekranlar
- Cari liste: **Müşteri / Satıcı / Perakende** sekmeleri, arama (kod, ünvan, ad, telefon)
- Cari kart: Genel · Adresler · Notlar (özel nitelikli, şifreli) · Hesap · Ekstre
- Cari ekstre (tarih aralığı, bakiye yürüyen)
- Borç/alacak listesi, yaşlandırma (0-30-60-90+)
- Toplu SMS/e-posta için seçim (İYS izni olmayanlar hariç — bildirim modülünde kontrol)

## Veri modeli (bu fazda)
```
party            ortak taban: type = MUSTERI | SATICI | PERSONEL | PERAKENDE
  code (benzersiz), title, first_name, last_name,
  tax_id (enc), tc_no (enc), phone (enc), email (enc),
  birth_date, wedding_anniversary, gender, notes,
  sms_consent, email_consent, iys_status, consent_date,
  risk_limit (NUMERIC 19,4), default_discount_rate (NUMERIC 19,4), price_list_id
party_address    party_id, label, address, city, district, postcode, is_default
party_note       party_id, category (ALERJI|CILT_TIPI|GENEL...), text (enc), pinned
party_account    party_id, account_kind = NORMAL | RETAIL, currency, opening_balance
party_transaction account_id, txn_date, doc_type, doc_ref, description,
                  debit (NUMERIC 19,4), credit (NUMERIC 19,4), currency
party_balance_mv  account_id, debit_sum, credit_sum, balance (özet; gece + tetikleyici güncel)
```

### Perakende cari ayrımı (plan Madde 3)
`party_account.account_kind = RETAIL` hesaplar **ayrı defterde** tutulur; normal cari
bakiyelerine ve raporlarına karışmaz. Perakende faturaları ve promosyon işlemleri bu
hesapla ilişkilendirilir.

## İş kuralları
- `code` boşsa otomatik üretilir (`SequenceService`, tip öneki: MUS/SAT/PER/PRK).
- Şifreli alanlar: `tc_no`, `tax_id`, `phone`, `email`, `party_note.text`
  → `FieldCrypto` (AES-256-GCM, anahtar OS keystore/`app.crypto.key`).
- Cari bakiye = `SUM(debit) - SUM(credit)` (borç bakiye pozitif).
  Performans: `party_balance_mv` özet + `POST /recalculate`.
- Risk limiti: satış tarafında `FINANCE`/`INVOICE` modülleri kontrol eder
  (ayar: `party.riskLimit.mode = WARN | BLOCK`).
- Çek bakiyesi cariden ayrı **"Risk Bakiyesi"** (çek modülü ekler; burada alan mevcut).
- Silme yok: `deleted` + KVKK "anonimleştir" (kimlik alanları maskele, hareketler kalır).
- `party_transaction` **append-only**: düzeltme ters kayıt ile.

## Uygulama arayüzü (diğer modüller için)
```java
interface PartyDirectory {
    PartyRef require(long partyId);
    Optional<PartyRef> findByCode(String code);
}
interface PartyLedger {
    void post(PartyLedgerEntry entry);       // tek hareket (doc_type, doc_ref, debit/credit)
    void reverse(String docType, String docRef, String reason);
    Money balance(long accountId);
    List<PartyTransactionView> statement(long accountId, LocalDate from, LocalDate to);
}
```

## Raporlar
Ekstre, borç/alacak listesi, yaşlandırma, en çok harcayan müşteriler, kayıp müşteri
(X gündür gelmeyen — randevu modülü ile), doğum günü listesi.

## Endpoint taslağı
```
GET    /api/v1/parties?type=MUSTERI&q=...
POST   /api/v1/parties
GET    /api/v1/parties/{id}
PUT    /api/v1/parties/{id}
POST   /api/v1/parties/{id}/anonymize
GET    /api/v1/parties/{id}/accounts
GET    /api/v1/parties/{id}/statement?from=&to=
POST   /api/v1/parties/{id}/transactions        (manuel açılış/düzeltme; FINANCE_ADD)
POST   /api/v1/parties/accounts/{accountId}/recalculate
```
