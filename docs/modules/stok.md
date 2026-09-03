# Modül: Stok Takip (`modules.stock`)

Öncelik **P0** · Plan §10.1 · Veri modeli §9.3

## Amaç
Ürün/hizmet kartları, **çoklu barkod**, **çapraz birim formülasyonu**, depo/vitrin,
stok hareketleri ve seviyeleri. Maliyet yöntemi: **ağırlıklı ortalama** (v1'de FIFO yok).

## Çekirdek kural — çapraz birim
Tüm stok hareketleri **base unit** cinsinden saklanır. Giriş/çıkış ekranında seçilen
birim `item_unit.factor` ile çarpılarak base unit'e çevrilir. "1 KOLİ = 12 ADET" tek
yerde (`item_unit`) tanımlanır → tüm raporlar tutarlı.

## Veri modeli (bu faz)
```
unit              global birim: code (ADET, KOLI, ML, GR, SEANS), name
item_category     ağaç: parent_id, name
item              code, name, item_type = EMTIA | HIZMET, vat_rate,
                  base_unit_id, category_id, brand, active
item_unit         item_id, unit_id, factor NUMERIC(19,6) (base'e oran), sale_price, is_base
item_barcode      item_id, barcode (benzersiz), unit_id, is_primary   ← ÇOKLU BARKOD
warehouse         code, name, wh_type = SHOWCASE | WAREHOUSE | CONSUMPTION
stock_movement    append-only: mv_date, item_id, warehouse_id, direction = IN | OUT,
                  base_qty NUMERIC(19,6), entered_unit_id, entered_qty,
                  unit_cost NUMERIC(19,4) (base birim başına), doc_type, doc_ref, line_key
stock_level       item_id + warehouse_id (benzersiz): qty_base, avg_cost  (materialized)
```

## İş kuralları
- Bir ürünün N barkodu; her barkod bir birime bağlı (koli barkodu ≠ adet barkodu).
- Barkod okut → `(item, unit, factor)` çözülür → miktar o birimde girilir → base'e çevrilir.
- Negatif stok: ayar `stock.negativeStock.mode = ALLOW | WARN | BLOCK` (varsayılan WARN).
- Maliyet: **ağırlıklı ortalama** — `WeightedAverageCost` saf domain sınıfı, %100 test.
  Giriş: `yeniOrt = (mevcutMiktar*mevcutOrt + girenMiktar*girenMaliyet) / (mevcutMiktar+girenMiktar)`.
  Çıkış: ortalama sabit kalır, miktar düşer.
- Depolar arası transfer = kaynak depoda OUT + hedef depoda IN (aynı base_qty, aynı avg_cost).
- Hizmet (`HIZMET`) kartları stok tutmaz ama `item` olarak vardır (fiyat/KDV/reçete için).
- Hareketler append-only; düzeltme ters yön hareketle (CLAUDE.md #3).

## Port (diğer modüller için)
```java
interface StockPort {
    BarcodeResolution resolveBarcode(String barcode);      // (itemId, unitId, factor, itemName)
    void issue(StockIssueCommand cmd);   // randevu sarfı / satış çıkışı  (OUT)
    void receive(StockReceiveCommand cmd); // alış girişi (IN, maliyetli)
    BigDecimal onHandBase(long itemId, long warehouseId);
}
```

## Endpoint taslağı
```
GET  /api/v1/stock/items?q=&categoryId=&type=
POST /api/v1/stock/items
GET  /api/v1/stock/items/{id}                (barkodlar + birimler + seviyeler dahil)
PUT  /api/v1/stock/items/{id}
POST /api/v1/stock/items/{id}/barcodes
POST /api/v1/stock/items/{id}/units
GET  /api/v1/stock/units          POST /api/v1/stock/units
GET  /api/v1/stock/warehouses     POST /api/v1/stock/warehouses
POST /api/v1/stock/movements                 (giriş/çıkış fişi, çok satır)
POST /api/v1/stock/transfers                  (vitrin↔depo)
GET  /api/v1/stock/levels?warehouseId=&critical=true
GET  /api/v1/stock/barcode/{barcode}          (hızlı çözümleme)
```

## Raporlar
Stok durum, hareket dökümü, kritik stok, devir hızı, ölü stok, envanter değeri,
depo/vitrin karşılaştırma. (v1'de: stok durum + hareket dökümü + kritik stok.)
