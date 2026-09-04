# Modül: Kartlı Promosyon / Sadakat — PPOS (`modules.loyalty`)

Öncelik **P2** · Plan §10.11 · Veri modeli §9.9

## Kapsam (v1)
Sadakat programı, kart yönetimi (çıkar / kayıp bildir → bakiye devri / blokla),
harcamadan **otomatik puan kazanımı**, puanla ödeme (bağımsız `redeem` işlemi),
puan zaman aşımı + uyarı, puan yükümlülüğü raporu.

Kart okuyucu: **HID klavye emülatörü** cihazlar sürücüsüz çalışır (kart no / manyetik id
alanına yazar). PC/SC çip okuyucu için ayrı yerel köprü servisi gerekir (kapsam dışı).

## Veri modeli
```
loyalty_program      name, earn_rate (1 TL harcama başına puan), point_to_currency (puan→TL),
                     expiry_months, active
loyalty_card         card_no (uq), magnetic_id, party_id, program_id, status,
                     points_balance, issued_at
loyalty_transaction  card_id, txn_type = EARN | REDEEM | EXPIRE | TRANSFER_IN | TRANSFER_OUT | ADJUST,
                     points, spend_amount (EARN), currency_value (REDEEM), source_ref,
                     at, expires_at (EARN satırının vadesi)
promotion            code, name, start_date, end_date, min_spend,
                     reward_type = POINT_BONUS | DISCOUNT_RATE | GIFT, reward_value, active
```

## İş kuralları (`PointsCalc` — saf domain)
- Kazanım: `puan = floor(harcama * earn_rate)`. Kampanya `POINT_BONUS` varsa bonus eklenir.
- Puan değeri: `puan * point_to_currency` (TL).
- Kullanılabilir maksimum puan: `min(bakiye, floor(faturaTutarı / point_to_currency))` (kısmi ödeme).
- Zaman aşımı: EARN satırının `expires_at = issued + expiry_months`; süresi geçen puanlar
  günlük iş ile EXPIRE edilir + müşteriye uyarı bildirimi (NotificationService).
- Kart kaybı: eski kart `MERGED`/`BLOCKED`, yeni kart oluşturulur, bakiye `TRANSFER` ile taşınır.

## Port (diğer modüller için)
```java
interface LoyaltyPort {
    int accrueFromSale(long partyId, BigDecimal spendAmount, String sourceRef); // kart yoksa 0
    Optional<CardInfo> cardForParty(long partyId);
    Money redeem(long cardId, int points, String sourceRef);
}
```
Fatura SATIS/PERAKENDE ve randevu `GELDI` → `accrueFromSale` (yumuşak: kart yoksa no-op).
Faturaya entegre puanla ödeme (LOYALTY payment method) → v2.

## Endpoint taslağı
```
GET/POST /api/v1/loyalty/programs
GET/POST /api/v1/loyalty/cards            POST /api/v1/loyalty/cards/{id}/report-lost
GET      /api/v1/loyalty/resolve/{key}    (kart no veya manyetik id → bakiye + puan)
GET      /api/v1/loyalty/cards/{id}/transactions
POST     /api/v1/loyalty/cards/{id}/redeem   ({points, sourceRef})
POST     /api/v1/loyalty/cards/{id}/adjust   ({points, reason})
GET      /api/v1/loyalty/reports/liability
GET/POST /api/v1/loyalty/promotions
```
