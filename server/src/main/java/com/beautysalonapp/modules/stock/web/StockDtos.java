package com.beautysalonapp.modules.stock.web;

import com.beautysalonapp.modules.stock.domain.Item;
import com.beautysalonapp.modules.stock.domain.ItemBarcode;
import com.beautysalonapp.modules.stock.domain.ItemType;
import com.beautysalonapp.modules.stock.domain.ItemUnit;
import com.beautysalonapp.modules.stock.domain.MovementDirection;
import com.beautysalonapp.modules.stock.domain.StockLevel;
import com.beautysalonapp.modules.stock.domain.Unit;
import com.beautysalonapp.modules.stock.domain.Warehouse;
import com.beautysalonapp.modules.stock.domain.WarehouseType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class StockDtos {

    private StockDtos() {
    }

    public record UnitView(long id, String code, String name) {
        public static UnitView of(Unit u) { return new UnitView(u.getId(), u.getCode(), u.getName()); }
    }

    public record CreateUnitRequest(@NotBlank String code, @NotBlank String name) {}

    public record WarehouseView(long id, String code, String name, WarehouseType type, boolean isDefault) {
        public static WarehouseView of(Warehouse w) {
            return new WarehouseView(w.getId(), w.getCode(), w.getName(), w.getType(), w.isDefault());
        }
    }

    public record CreateWarehouseRequest(@NotBlank String code, @NotBlank String name,
                                         @NotNull WarehouseType type, boolean makeDefault) {}

    public record ItemRow(long id, String code, String name, ItemType type, String brand,
                          BigDecimal vatRate, boolean active, BigDecimal totalOnHand) {}

    public record ItemUnitView(long id, long unitId, String unitCode, BigDecimal factor,
                               BigDecimal salePrice, boolean isBase) {
        public static ItemUnitView of(ItemUnit iu, String unitCode) {
            return new ItemUnitView(iu.getId(), iu.getUnitId(), unitCode, iu.getFactor(), iu.getSalePrice(), iu.isBase());
        }
    }

    public record BarcodeView(long id, String barcode, long unitId, boolean primary) {
        public static BarcodeView of(ItemBarcode b) {
            return new BarcodeView(b.getId(), b.getBarcode(), b.getUnitId(), b.isPrimary());
        }
    }

    public record LevelView(long warehouseId, BigDecimal qtyBase, BigDecimal avgCost) {
        public static LevelView of(StockLevel s) {
            return new LevelView(s.getWarehouseId(), s.getQtyBase(), s.getAvgCost());
        }
    }

    public record ItemDetail(ItemRow item, List<ItemUnitView> units, List<BarcodeView> barcodes,
                             List<LevelView> levels) {}

    public record CreateItemRequest(
            String code,
            @NotBlank String name,
            @NotNull ItemType type,
            @NotBlank String baseUnitCode,
            BigDecimal vatRate,
            Long categoryId,
            String brand,
            BigDecimal salePrice) {
    }

    public record AddItemUnitRequest(@NotBlank String unitCode, @NotNull @Positive BigDecimal factor,
                                     BigDecimal salePrice) {}

    public record AddBarcodeRequest(@NotBlank String barcode, @NotBlank String unitCode, boolean primary) {}

    public record MovementLine(@NotNull Long itemId, @NotNull Long unitId,
                               @NotNull @Positive BigDecimal quantity, BigDecimal unitCost, String note) {}

    public record MovementRequest(
            LocalDate date,
            @NotNull Long warehouseId,
            @NotNull MovementDirection direction,
            @NotNull List<MovementLine> lines) {
    }

    public record TransferRequest(
            LocalDate date,
            @NotNull Long itemId,
            @NotNull Long fromWarehouseId,
            @NotNull Long toWarehouseId,
            @NotNull Long unitId,
            @NotNull @Positive BigDecimal quantity,
            String note) {
    }

    public record BarcodeResolutionView(long itemId, String itemName, long unitId, String unitCode,
                                        BigDecimal factor) {}

    public static ItemRow toRow(Item i, BigDecimal totalOnHand) {
        return new ItemRow(i.getId(), i.getCode(), i.getName(), i.getType(), i.getBrand(),
                i.getVatRate(), i.isActive(), totalOnHand);
    }
}
