package com.beautysalonapp.modules.stock.application;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Stok portu (CLAUDE.md #5). Fatura ve randevu modülleri stok giriş/çıkışını bununla yapar.
 */
public interface StockPort {

    BarcodeResolution resolveBarcode(String barcode);

    /** Çıkış (satış, randevu sarfı). Base miktar {@code enteredQty * factor} olarak hesaplanır. */
    void issue(StockCommand cmd);

    /** Giriş (alış). {@code unitCost} girilen birim başına maliyettir; base'e bölünür. */
    void receive(StockCommand cmd);

    BigDecimal onHandBase(long itemId, long warehouseId);

    record BarcodeResolution(long itemId, String itemName, long unitId, String unitCode, BigDecimal factor) {}

    record StockCommand(
            LocalDate date,
            long itemId,
            long warehouseId,
            long unitId,
            BigDecimal quantity,      // girilen birimde
            BigDecimal unitCost,      // girilen birim başına (receive için); issue'da yok sayılır
            String docType,
            String docRef,
            String lineKey,
            String note
    ) {}
}
