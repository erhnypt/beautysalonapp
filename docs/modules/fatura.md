# Modül: Fatura İşlemleri (`modules.invoice`)

Öncelik **P1** · Plan §10.5 · Veri modeli §9.6

## Amaç
Alış / Satış / Perakende / İade faturaları. Bir fatura **tek transaction** içinde
cari hareket + stok hareketi + (tahsilat/tediye varsa) kasa hareketi üretir.

## Veri modeli
```
invoice        type = ALIS | SATIS | PERAKENDE | IADE_ALIS | IADE_SATIS
               series, number (SequenceService), doc_no, date, party_id, party_account_id,
               warehouse_id, currency, fx_rate,
               cash_register_receipt_no,           ← YAZARKASA/ÖKC FİŞ NO
               subtotal, discount_total, vat_total, grand_total, status,
               einvoice_uuid, einvoice_status      ← v2 için şimdiden bırakıldı
invoice_line   item_id, description, quantity, unit_id, unit_price,
               discount_rate, vat_rate, line_net, line_vat, line_total
invoice_payment method = CASH | CARD | CHEQUE | CREDIT,
               amount, account_id? (CASH→kasa, CARD→POS), cheque_id?
```

## Yön kuralı
| Tür | Stok | Cari |
|---|---|---|
| SATIS, PERAKENDE, IADE_ALIS | ÇIKIŞ | borç (debit) |
| ALIS, IADE_SATIS | GİRİŞ (maliyetli) | alacak (credit) |

## İş kuralları
- Satır toplamı: `net = qty*price*(1-discount)`, `vat = net*vatRate/100`, `total = net+vat`.
  Fatura toplamı satırların toplamı (kuruş yuvarlama satır bazında).
- Yalnızca **EMTIA** satırları stok hareketi üretir; HIZMET satırları üretmez.
- ALIS'ta satır `unit_price` maliyet olarak stok girişine yazılır (ağırlıklı ortalama).
- Onaylanan fatura **silinmez**; `void` → tüm hareketler ters kaydedilir (CLAUDE.md #3).
- Perakende faturada `cash_register_receipt_no` alanı ayara göre zorunlu:
  `invoice.retail.requireReceiptNo = true|false`.
- `invoice_payment` dağılımı:
  - CASH → `FinancePort.collect` (satış) / `pay` (alış)
  - CARD → POS hesabına `collect`/`pay` (komisyon Faz 4 POS modülünde mahsuplaşır)
  - CHEQUE → `ChequePort.registerFromInvoice` (portföye çek)
  - CREDIT → cari üzerinde kalır (ek işlem yok)
- e-Arşiv/e-Fatura: v1 kapsam dışı; alanlar rezerve.

## Endpoint taslağı
```
GET  /api/v1/invoices?type=&partyId=&from=&to=
POST /api/v1/invoices                 (satırlar + ödemeler, tek transaction)
GET  /api/v1/invoices/{id}
POST /api/v1/invoices/{id}/void
GET  /api/v1/invoices/reports/retail-reconciliation?date=   (fişli satış ↔ stok çıkışı)
```
