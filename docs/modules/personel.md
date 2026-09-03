# Modül: Personel Hesapları + Prim (`modules.staff`)

Öncelik **P1** · Plan §10.2 · Veri modeli §9.4

## Amaç
Personel kartları, sınıf/kademe, prim kuralları ve **prim tahakkuk/ödeme** akışı, avanslar.
Personel bir `party` (PERSONEL) olduğundan cari hesabı vardır: prim tahakkuku alacak,
avans/ödeme borç yazar.

## Veri modeli
```
staff            party_id, title, hire_date, iban(enc), staff_class_id, active,
                 default_service_rate, default_product_rate
staff_class      name, description, service_rate, product_rate
commission_rule  scope = SERVICE | PRODUCT | REVENUE,
                 staff_id?, staff_class_id?,          ← özgüllük: staff > class > genel
                 basis = RATE | AMOUNT, value,
                 min_revenue (REVENUE eşiği), active
staff_commission staff_id, period_ym (YYYY-MM), source_type = APPOINTMENT | INVOICE,
                 source_ref, base_amount, rate, amount,
                 status = TAHAKKUK | ODENDI, accrued_at, paid_at
staff_advance    staff_id, adv_date, amount, account_id, closed_period
salary_period    staff_id, period_ym, gross, deductions, net, locked, paid_at
```

## İş kuralları
- **Kural çözümü:** `scope` için önce `staff_id` eşleşen aktif kural, yoksa `staff_class_id`,
  yoksa her ikisi de null olan genel kural. RATE → `base * value/100`, AMOUNT → sabit `value`.
- **Tahakkuk ≠ ödeme:** `accrue*` prim satırı (TAHAKKUK) üretir ve personel cariye **alacak** yazar.
  `payCommissions(period)` → dönemdeki TAHAKKUK satırları toplamını `FinancePort.pay` ile
  kasadan öder, personel cariye **borç** yazar, satırları ODENDI yapar.
- **Avans:** kasadan çıkış + personel cariye borç (`FinancePort.pay`).
- Kapanan `salary_period` (`locked`) için o döneme prim/avans girilemez.
- Tetikleyiciler (CommissionPort): randevu `GELDI` (hizmet primi), fatura SATIS/PERAKENDE
  (ürün/hizmet primi). Ciro eşiği primi periyodik iş olarak (v2).

## Port
```java
interface CommissionPort {
    void accrue(AccrueCommand c);   // scope, staffId, baseAmount, sourceType, sourceRef, periodYm
}
```

## Endpoint taslağı
```
GET/POST /api/v1/staff                       (personel kartı)
GET/POST /api/v1/staff/classes
GET/POST /api/v1/staff/commission-rules
GET      /api/v1/staff/{id}/commissions?period=
POST     /api/v1/staff/{id}/commissions/pay  ({period, cashAccountId})
POST     /api/v1/staff/{id}/advances         ({amount, accountId})
GET      /api/v1/staff/{id}/statement         (cari ekstre — party modülünden)
GET      /api/v1/staff/reports/performance?from=&to=
```
