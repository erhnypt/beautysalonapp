# Modül: Frondex Randevu Takip Sistemi (`modules.appointment`)

Öncelik **P0** · Plan §10.10 · Veri modeli §9.7

## Kapsam (bu faz)
Hizmet tanımları + sarf reçetesi, kaynaklar (oda/koltuk/cihaz), personel vardiyası,
randevu takvimi (gün/hafta), **çakışma kontrolü**, durum akışı ve
**`GELDI` zinciri**: stok sarfı + hizmet satışının cariye işlenmesi + seans paketi düşümü.
Prim tahakkuku ve sadakat puanı hook'ları Faz 4/6'da bağlanacak (şimdilik olay noktası bırakıldı).

Ertelenen: `appointment_series` (paket randevu serisi — seans takibi contract_line ile),
`waitlist` (bekleme listesi), online kaynak.

## Veri modeli
```
service_definition   name, duration_min, buffer_before_min, buffer_after_min,
                     price, vat_rate, resource_required, active, stock_item_id?
service_recipe       service_id, item_id, quantity NUMERIC(19,6), unit_id     → otomatik sarf
resource             code, name, res_type = ODA | KOLTUK | CIHAZ, active
staff_shift          staff_party_id, shift_date, start_time, end_time, shift_type = WORK | LEAVE
appointment          party_id, staff_party_id, resource_id?, service_id,
                     start_at, end_at, status, source, notes,
                     price_snapshot, reminder_sent_at, contract_line_id?,
                     no_show(boolean), arrived_at
```

## İş kuralları
- **Çakışma:** aynı `staff_party_id` VEYA aynı `resource_id` için zaman aralıkları
  (buffer dahil) kesişemez. `TimeSlot.overlaps` saf domain, test kapsamında.
- `end_at` = `start_at + duration_min` (buffer çakışma penceresine dahil, randevu süresine değil).
- Durum akışı: `PLANLANDI → ONAYLANDI → GELDI | GELMEDI`, her durumdan `IPTAL`.
- **`GELDI` işaretleme:**
  1. `service_recipe` varsa SARF deposundan otomatik stok çıkışı (`StockPort.issue`).
  2. `contract_line_id` doluysa `session_used++` (paketten seans düş).
  3. Aksi halde hizmet bedeli cariye borç + (ayar: peşin ise) `FinancePort.collect`.
  4. `arrived_at` damgalanır.
- `GELMEDI` → müşteri no-show sayacı (rapor); randevu no_show=true.
- Randevu geçmişi müşteri kartında görünür (party_id ile sorgu).

## Endpoint taslağı
```
GET  /api/v1/appointments/services      POST ...            (hizmet tanımı + reçete)
GET  /api/v1/appointments/resources     POST ...
GET  /api/v1/appointments/shifts?staffId=&from=&to=   POST ...
GET  /api/v1/appointments?from=&to=&staffId=&resourceId=   (takvim)
POST /api/v1/appointments                (çakışma kontrolü ile)
PUT  /api/v1/appointments/{id}/move      (sürükle-bırak: yeni start/staff/resource)
POST /api/v1/appointments/{id}/status    ({status, collectCash?, cashAccountId?})
GET  /api/v1/appointments/party/{partyId}/history
GET  /api/v1/appointments/reports/occupancy?from=&to=
```

## Raporlar (v1)
Doluluk oranı (personel/kaynak), no-show oranı, hizmet dağılımı, en yoğun saatler.
