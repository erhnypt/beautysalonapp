package com.beautysalonapp.modules.stock.application;

import com.beautysalonapp.audit.application.AuditService;
import com.beautysalonapp.core.error.BusinessRuleException;
import com.beautysalonapp.core.error.NotFoundException;
import com.beautysalonapp.core.sequence.SequenceService;
import com.beautysalonapp.modules.stock.domain.Item;
import com.beautysalonapp.modules.stock.domain.ItemBarcode;
import com.beautysalonapp.modules.stock.domain.ItemType;
import com.beautysalonapp.modules.stock.domain.ItemUnit;
import com.beautysalonapp.modules.stock.domain.MovementDirection;
import com.beautysalonapp.modules.stock.domain.StockLevel;
import com.beautysalonapp.modules.stock.domain.StockMovement;
import com.beautysalonapp.modules.stock.domain.Unit;
import com.beautysalonapp.modules.stock.domain.Warehouse;
import com.beautysalonapp.modules.stock.domain.WarehouseType;
import com.beautysalonapp.modules.stock.domain.WeightedAverageCost;
import com.beautysalonapp.modules.stock.infrastructure.ItemBarcodeRepository;
import com.beautysalonapp.modules.stock.infrastructure.ItemRepository;
import com.beautysalonapp.modules.stock.infrastructure.ItemUnitRepository;
import com.beautysalonapp.modules.stock.infrastructure.StockLevelRepository;
import com.beautysalonapp.modules.stock.infrastructure.StockMovementRepository;
import com.beautysalonapp.modules.stock.infrastructure.UnitRepository;
import com.beautysalonapp.modules.stock.infrastructure.WarehouseRepository;
import com.beautysalonapp.settings.application.SettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
public class StockService implements StockPort {

    private static final Logger log = LoggerFactory.getLogger(StockService.class);
    private static final Long BRANCH = 1L;
    private static final String NEG_STOCK_SETTING = "stock.negativeStock.mode"; // ALLOW | WARN | BLOCK

    private final ItemRepository items;
    private final ItemUnitRepository itemUnits;
    private final ItemBarcodeRepository barcodes;
    private final UnitRepository units;
    private final WarehouseRepository warehouses;
    private final StockMovementRepository movements;
    private final StockLevelRepository levels;
    private final SequenceService sequences;
    private final SettingService settings;
    private final AuditService audit;

    public StockService(ItemRepository items, ItemUnitRepository itemUnits, ItemBarcodeRepository barcodes,
                        UnitRepository units, WarehouseRepository warehouses, StockMovementRepository movements,
                        StockLevelRepository levels, SequenceService sequences, SettingService settings,
                        AuditService audit) {
        this.items = items;
        this.itemUnits = itemUnits;
        this.barcodes = barcodes;
        this.units = units;
        this.warehouses = warehouses;
        this.movements = movements;
        this.levels = levels;
        this.sequences = sequences;
        this.settings = settings;
        this.audit = audit;
    }

    // --- birim / depo -------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Unit> listUnits() {
        return units.findAllByDeletedFalseOrderByCode();
    }

    public Unit createUnit(String code, String name) {
        if (units.findByBranchIdAndCode(BRANCH, code).isPresent()) {
            throw new BusinessRuleException("unit_exists", "Bu birim kodu zaten var: " + code);
        }
        return units.save(new Unit(code.trim().toUpperCase(), name.trim()));
    }

    @Transactional(readOnly = true)
    public List<Warehouse> listWarehouses() {
        return warehouses.findAllByDeletedFalseOrderByCode();
    }

    public Warehouse createWarehouse(String code, String name, WarehouseType type, boolean makeDefault) {
        if (warehouses.findByBranchIdAndCode(BRANCH, code).isPresent()) {
            throw new BusinessRuleException("warehouse_exists", "Bu depo kodu zaten var: " + code);
        }
        Warehouse w = new Warehouse(code.trim().toUpperCase(), name.trim(), type);
        if (makeDefault || warehouses.findFirstByIsDefaultTrue().isEmpty()) {
            warehouses.findFirstByIsDefaultTrue().ifPresent(existing -> existing.setDefault(false));
            w.setDefault(true);
        }
        return warehouses.save(w);
    }

    @Transactional(readOnly = true)
    public Warehouse defaultWarehouse() {
        return warehouses.findFirstByIsDefaultTrue()
                .orElseThrow(() -> new BusinessRuleException("no_warehouse", "Tanımlı varsayılan depo yok"));
    }

    // --- ürün kartı -------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<Item> searchItems(ItemType type, Long categoryId, String q, Pageable pageable) {
        return items.search(type, categoryId, (q == null || q.isBlank()) ? null : q.trim(), pageable);
    }

    @Transactional(readOnly = true)
    public Item getItem(long id) {
        return items.findById(id).orElseThrow(() -> new NotFoundException("Ürün", id));
    }

    public Item createItem(String code, String name, ItemType type, String baseUnitCode,
                           BigDecimal vatRate, Long categoryId, String brand, BigDecimal salePrice) {
        Unit baseUnit = units.findByBranchIdAndCode(BRANCH, baseUnitCode.toUpperCase())
                .orElseThrow(() -> new NotFoundException("Birim: " + baseUnitCode));
        String finalCode = (code == null || code.isBlank())
                ? sequences.next(BRANCH, "ITEM", type == ItemType.HIZMET ? "H" : "S")
                : code.trim();
        if (items.existsByBranchIdAndCode(BRANCH, finalCode)) {
            throw new BusinessRuleException("code_taken", "Bu ürün kodu zaten var: " + finalCode);
        }
        Item item = new Item(finalCode, name.trim(), type, baseUnit.getId());
        if (vatRate != null) item.setVatRate(vatRate);
        item.setCategoryId(categoryId);
        item.setBrand(brand);
        items.save(item);

        // Base birim satırı (factor 1)
        ItemUnit iu = new ItemUnit(item.getId(), baseUnit.getId(), BigDecimal.ONE, true);
        iu.setSalePrice(salePrice);
        itemUnits.save(iu);

        audit.record("ITEM_CREATE", "Item", item.getId(), "Ürün/hizmet kartı: " + item.getCode() + " " + item.getName());
        return item;
    }

    public ItemUnit addUnit(long itemId, String unitCode, BigDecimal factor, BigDecimal salePrice) {
        Item item = getItem(itemId);
        Unit unit = units.findByBranchIdAndCode(BRANCH, unitCode.toUpperCase())
                .orElseThrow(() -> new NotFoundException("Birim: " + unitCode));
        if (factor == null || factor.signum() <= 0) {
            throw new BusinessRuleException("bad_factor", "Birim çarpanı pozitif olmalı");
        }
        if (itemUnits.findByItemIdAndUnitId(itemId, unit.getId()).isPresent()) {
            throw new BusinessRuleException("unit_exists", "Bu ürün için bu birim zaten tanımlı");
        }
        ItemUnit iu = new ItemUnit(item.getId(), unit.getId(), factor, false);
        iu.setSalePrice(salePrice);
        return itemUnits.save(iu);
    }

    public ItemBarcode addBarcode(long itemId, String barcode, String unitCode, boolean primary) {
        getItem(itemId);
        Unit unit = units.findByBranchIdAndCode(BRANCH, unitCode.toUpperCase())
                .orElseThrow(() -> new NotFoundException("Birim: " + unitCode));
        if (barcodes.existsByBranchIdAndBarcode(BRANCH, barcode.trim())) {
            throw new BusinessRuleException("barcode_taken", "Bu barkod başka bir kayıtta kullanılıyor");
        }
        // Barkodun birimi ürünün birim listesinde olmalı
        itemUnits.findByItemIdAndUnitId(itemId, unit.getId())
                .orElseThrow(() -> new BusinessRuleException("unit_not_on_item",
                        "Önce bu birimi ürüne ekleyin: " + unitCode));
        return barcodes.save(new ItemBarcode(itemId, barcode.trim(), unit.getId(), primary));
    }

    @Transactional(readOnly = true)
    public List<ItemUnit> unitsOf(long itemId) {
        return itemUnits.findAllByItemIdAndDeletedFalse(itemId);
    }

    @Transactional(readOnly = true)
    public List<ItemBarcode> barcodesOf(long itemId) {
        return barcodes.findAllByItemIdAndDeletedFalse(itemId);
    }

    @Transactional(readOnly = true)
    public List<StockLevel> levelsOf(long itemId) {
        return levels.findAllByItemId(itemId);
    }

    // --- hareket kaydı (çekirdek) ----------------------------------------

    /** Tek satır giriş/çıkış. Miktar girilen birimde; base'e çevrilir. */
    public StockMovement record(LocalDate date, long itemId, long warehouseId, MovementDirection direction,
                                long enteredUnitId, BigDecimal enteredQty, BigDecimal enteredUnitCost,
                                String docType, String docRef, String lineKey, String note) {
        Item item = getItem(itemId);
        if (!item.tracksStock()) {
            throw new BusinessRuleException("service_no_stock", "Hizmet kartı stok tutmaz: " + item.getCode());
        }
        if (enteredQty == null || enteredQty.signum() <= 0) {
            throw new BusinessRuleException("bad_qty", "Miktar pozitif olmalı");
        }
        String key = lineKey == null ? "-" : lineKey;
        if (docRef != null && movements.existsByDocTypeAndDocRefAndLineKey(docType, docRef, key)) {
            log.debug("Stok hareketi zaten var, atlanıyor: {} {} {}", docType, docRef, key);
            return null;
        }

        BigDecimal factor = factorFor(itemId, enteredUnitId);
        BigDecimal baseQty = enteredQty.multiply(factor).setScale(6, RoundingMode.HALF_UP);
        BigDecimal baseUnitCost = direction == MovementDirection.IN && enteredUnitCost != null
                ? enteredUnitCost.divide(factor, 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        StockLevel level = levels.lock(itemId, warehouseId)
                .orElseGet(() -> levels.save(new StockLevel(itemId, warehouseId)));
        WeightedAverageCost wac = new WeightedAverageCost(level.getQtyBase(), level.getAvgCost());

        if (direction == MovementDirection.IN) {
            wac = wac.receive(baseQty, baseUnitCost);
        } else {
            BigDecimal remaining = wac.quantityBase().subtract(baseQty);
            if (remaining.signum() < 0) {
                enforceNegativePolicy(item, warehouseId, remaining);
            }
            baseUnitCost = wac.avgUnitCost(); // çıkışta o anki ortalama
            wac = wac.issue(baseQty);
        }

        level.setQtyBase(wac.quantityBase());
        level.setAvgCost(wac.avgUnitCost());

        return movements.save(new StockMovement(
                date == null ? LocalDate.now() : date, itemId, warehouseId, direction,
                baseQty, enteredUnitId, enteredQty, baseUnitCost, docType, docRef, key, note));
    }

    /** Depolar arası transfer: kaynak OUT + hedef IN (aynı base_qty, aynı maliyet). */
    public void transfer(LocalDate date, long itemId, long fromWarehouseId, long toWarehouseId,
                         long enteredUnitId, BigDecimal enteredQty, String note) {
        if (fromWarehouseId == toWarehouseId) {
            throw new BusinessRuleException("same_warehouse", "Kaynak ve hedef depo aynı olamaz");
        }
        String ref = sequences.next(BRANCH, "STOCK_TRANSFER", "TR");
        record(date, itemId, fromWarehouseId, MovementDirection.OUT, enteredUnitId, enteredQty, null,
                "TRANSFER", ref, "out", note);
        // Hedef girişte kaynak deponun o anki ortalama maliyetini taşı
        StockLevel src = levels.findByItemIdAndWarehouseId(itemId, fromWarehouseId).orElseThrow();
        BigDecimal factor = factorFor(itemId, enteredUnitId);
        BigDecimal enteredUnitCost = src.getAvgCost().multiply(factor);
        record(date, itemId, toWarehouseId, MovementDirection.IN, enteredUnitId, enteredQty, enteredUnitCost,
                "TRANSFER", ref, "in", note);
        audit.record("STOCK_TRANSFER", "StockMovement", ref,
                "Transfer: ürün#" + itemId + " " + fromWarehouseId + "→" + toWarehouseId);
    }

    // --- StockPort -------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public BarcodeResolution resolveBarcode(String barcode) {
        ItemBarcode bc = barcodes.findByBranchIdAndBarcode(BRANCH, barcode.trim())
                .orElseThrow(() -> new NotFoundException("Barkod bulunamadı: " + barcode));
        Item item = getItem(bc.getItemId());
        Unit unit = units.findById(bc.getUnitId()).orElseThrow();
        BigDecimal factor = factorFor(bc.getItemId(), bc.getUnitId());
        return new BarcodeResolution(item.getId(), item.getName(), unit.getId(), unit.getCode(), factor);
    }

    @Override
    public void issue(StockCommand c) {
        record(c.date(), c.itemId(), c.warehouseId(), MovementDirection.OUT, c.unitId(), c.quantity(),
                null, c.docType(), c.docRef(), c.lineKey(), c.note());
    }

    @Override
    public void receive(StockCommand c) {
        record(c.date(), c.itemId(), c.warehouseId(), MovementDirection.IN, c.unitId(), c.quantity(),
                c.unitCost(), c.docType(), c.docRef(), c.lineKey(), c.note());
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal onHandBase(long itemId, long warehouseId) {
        return levels.findByItemIdAndWarehouseId(itemId, warehouseId)
                .map(StockLevel::getQtyBase)
                .orElse(BigDecimal.ZERO);
    }

    // --- yardımcılar ---------------------------------------------------

    private BigDecimal factorFor(long itemId, long unitId) {
        return itemUnits.findByItemIdAndUnitId(itemId, unitId)
                .map(ItemUnit::getFactor)
                .orElseThrow(() -> new BusinessRuleException("unit_not_on_item",
                        "Bu birim ürün için tanımlı değil (item=" + itemId + ", unit=" + unitId + ")"));
    }

    private void enforceNegativePolicy(Item item, long warehouseId, BigDecimal remaining) {
        String mode = settings.getOrDefault(NEG_STOCK_SETTING, "WARN").toUpperCase();
        String msg = "Stok yetersiz: " + item.getCode() + " (depo " + warehouseId
                + "), çıkış sonrası bakiye " + remaining.stripTrailingZeros().toPlainString();
        switch (mode) {
            case "BLOCK" -> throw new BusinessRuleException("negative_stock", msg);
            case "ALLOW" -> { /* sessiz */ }
            default -> log.warn(msg);
        }
    }
}
