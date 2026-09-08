-- Stok ürün kartına alış fiyatı (satış fiyatının yanına) — kar marjı gösterimi için.
ALTER TABLE item_unit ADD COLUMN purchase_price NUMERIC(19,4);
