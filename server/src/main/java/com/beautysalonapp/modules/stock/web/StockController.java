package com.beautysalonapp.modules.stock.web;

import com.beautysalonapp.modules.stock.application.StockPort;
import com.beautysalonapp.modules.stock.application.StockReportService;
import com.beautysalonapp.modules.stock.application.StockService;
import com.beautysalonapp.modules.stock.domain.Item;
import com.beautysalonapp.modules.stock.domain.ItemType;
import com.beautysalonapp.modules.stock.domain.ItemUnit;
import com.beautysalonapp.modules.stock.domain.MovementDirection;
import com.beautysalonapp.modules.stock.domain.Unit;
import com.beautysalonapp.modules.stock.infrastructure.UnitRepository;
import com.beautysalonapp.modules.stock.web.StockDtos.AddBarcodeRequest;
import com.beautysalonapp.modules.stock.web.StockDtos.AddItemUnitRequest;
import com.beautysalonapp.modules.stock.web.StockDtos.BarcodeResolutionView;
import com.beautysalonapp.modules.stock.web.StockDtos.BarcodeView;
import com.beautysalonapp.modules.stock.web.StockDtos.CategoryView;
import com.beautysalonapp.modules.stock.web.StockDtos.CreateCategoryRequest;
import com.beautysalonapp.modules.stock.web.StockDtos.CreateItemRequest;
import com.beautysalonapp.modules.stock.web.StockDtos.CreateUnitRequest;
import com.beautysalonapp.modules.stock.web.StockDtos.CreateWarehouseRequest;
import com.beautysalonapp.modules.stock.web.StockDtos.ItemDetail;
import com.beautysalonapp.modules.stock.web.StockDtos.ItemRow;
import com.beautysalonapp.modules.stock.web.StockDtos.ItemUnitView;
import com.beautysalonapp.modules.stock.web.StockDtos.LevelView;
import com.beautysalonapp.modules.stock.web.StockDtos.MovementRequest;
import com.beautysalonapp.modules.stock.web.StockDtos.TransferRequest;
import com.beautysalonapp.modules.stock.web.StockDtos.UnitView;
import com.beautysalonapp.modules.stock.web.StockDtos.UpdatePricesRequest;
import com.beautysalonapp.modules.stock.web.StockDtos.UpdateReorderLevelRequest;
import com.beautysalonapp.modules.stock.web.StockDtos.WarehouseView;
import com.beautysalonapp.security.application.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/stock")
@PreAuthorize("hasAuthority('STOCK_VIEW')")
public class StockController {

    private final StockService stock;
    private final StockPort stockPort;
    private final UnitRepository unitRepo;
    private final StockReportService reports;
    private final UserService userService;

    public StockController(StockService stock, StockPort stockPort, UnitRepository unitRepo,
                           StockReportService reports, UserService userService) {
        this.stock = stock;
        this.stockPort = stockPort;
        this.unitRepo = unitRepo;
        this.reports = reports;
        this.userService = userService;
    }

    // --- birim / depo ---
    @GetMapping("/units")
    public List<UnitView> units() {
        return stock.listUnits().stream().map(UnitView::of).toList();
    }

    @PostMapping("/units")
    @PreAuthorize("hasAuthority('STOCK_EDIT')")
    public UnitView createUnit(@Valid @RequestBody CreateUnitRequest r) {
        return UnitView.of(stock.createUnit(r.code(), r.name()));
    }

    @GetMapping("/warehouses")
    public List<WarehouseView> warehouses() {
        return stock.listWarehouses().stream().map(WarehouseView::of).toList();
    }

    @PostMapping("/warehouses")
    @PreAuthorize("hasAuthority('STOCK_EDIT')")
    public WarehouseView createWarehouse(@Valid @RequestBody CreateWarehouseRequest r) {
        return WarehouseView.of(stock.createWarehouse(r.code(), r.name(), r.type(), r.makeDefault()));
    }

    // --- kategori (ürün grupları) ---
    @GetMapping("/categories")
    public List<CategoryView> categories() {
        return stock.listCategories().stream().map(CategoryView::of).toList();
    }

    @PostMapping("/categories")
    @PreAuthorize("hasAuthority('STOCK_EDIT')")
    public CategoryView createCategory(@Valid @RequestBody CreateCategoryRequest r) {
        return CategoryView.of(stock.createCategory(r.parentId(), r.name()));
    }

    // --- ürün kartı ---
    @GetMapping("/items")
    public Page<ItemRow> items(@RequestParam(required = false) ItemType type,
                               @RequestParam(required = false) Long categoryId,
                               @RequestParam(required = false) String q,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "25") int size) {
        return stock.searchItems(type, categoryId, q, PageRequest.of(page, Math.min(size, 200)))
                .map(i -> StockDtos.toRow(i, totalOnHand(i.getId()), baseUnitOf(i.getId())));
    }

    @GetMapping("/items/{id}")
    public ItemDetail item(@PathVariable long id) {
        Item i = stock.getItem(id);
        Map<Long, String> unitCodes = unitCodeMap();
        List<ItemUnit> unitEntities = stock.unitsOf(id);
        List<ItemUnitView> us = unitEntities.stream()
                .map(iu -> ItemUnitView.of(iu, unitCodes.getOrDefault(iu.getUnitId(), "?"))).toList();
        List<BarcodeView> bs = stock.barcodesOf(id).stream().map(BarcodeView::of).toList();
        List<LevelView> ls = stock.levelsOf(id).stream().map(LevelView::of).toList();
        ItemUnit base = unitEntities.stream().filter(ItemUnit::isBase).findFirst().orElse(null);
        return new ItemDetail(StockDtos.toRow(i, totalOnHand(id), base), us, bs, ls);
    }

    @PostMapping("/items")
    @PreAuthorize("hasAuthority('STOCK_ADD')")
    public ItemDetail createItem(@Valid @RequestBody CreateItemRequest r) {
        Item i = stock.createItem(r.code(), r.name(), r.type(), r.baseUnitCode(), r.vatRate(),
                r.categoryId(), r.brand(), r.salePrice(), r.purchasePrice());
        return item(i.getId());
    }

    @PostMapping("/items/{id}/units")
    @PreAuthorize("hasAuthority('STOCK_EDIT')")
    public ItemDetail addUnit(@PathVariable long id, @Valid @RequestBody AddItemUnitRequest r) {
        stock.addUnit(id, r.unitCode(), r.factor(), r.salePrice(), r.purchasePrice());
        return item(id);
    }

    @PostMapping("/items/{id}/prices")
    @PreAuthorize("hasAuthority('STOCK_EDIT')")
    public ItemDetail updatePrices(@PathVariable long id, @RequestBody UpdatePricesRequest r) {
        stock.updateBasePrices(id, r.salePrice(), r.purchasePrice());
        return item(id);
    }

    @PostMapping("/items/{id}/barcodes")
    @PreAuthorize("hasAuthority('STOCK_EDIT')")
    public ItemDetail addBarcode(@PathVariable long id, @Valid @RequestBody AddBarcodeRequest r) {
        stock.addBarcode(id, r.barcode(), r.unitCode(), r.primary());
        return item(id);
    }

    @PostMapping("/items/{id}/reorder-level")
    @PreAuthorize("hasAuthority('STOCK_EDIT')")
    public ItemDetail updateReorderLevel(@PathVariable long id, @RequestBody UpdateReorderLevelRequest r) {
        stock.updateReorderLevel(id, r.reorderLevel());
        return item(id);
    }

    // --- barkod çözümleme ---
    @GetMapping("/barcode/{barcode}")
    public BarcodeResolutionView resolve(@PathVariable String barcode) {
        var r = stockPort.resolveBarcode(barcode);
        return new BarcodeResolutionView(r.itemId(), r.itemName(), r.unitId(), r.unitCode(), r.factor());
    }

    // --- hareketler ---
    @PostMapping("/movements")
    @PreAuthorize("hasAuthority('STOCK_ADD')")
    public void movements(@Valid @RequestBody MovementRequest r) {
        String ref = "MV-" + System.currentTimeMillis();
        int idx = 0;
        for (var line : r.lines()) {
            stock.record(r.date(), line.itemId(), r.warehouseId(),
                    r.direction() == MovementDirection.IN ? MovementDirection.IN : MovementDirection.OUT,
                    line.unitId(), line.quantity(), line.unitCost(),
                    "ADJUSTMENT", ref, "L" + (idx++), line.note());
        }
    }

    @PostMapping("/transfers")
    @PreAuthorize("hasAuthority('STOCK_ADD')")
    public void transfer(@Valid @RequestBody TransferRequest r) {
        stock.transfer(r.date(), r.itemId(), r.fromWarehouseId(), r.toWarehouseId(),
                r.unitId(), r.quantity(), r.note());
    }

    // --- raporlar (plan §10.1) ---

    @GetMapping("/reports/stock-status")
    @PreAuthorize("hasAuthority('STOCK_REPORT')")
    public List<StockReportService.StockStatusRow> stockStatus(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long branchId, Authentication auth) {
        return reports.stockStatus(warehouseId, userService.resolveRequestedBranch(auth.getName(), branchId));
    }

    @GetMapping("/reports/movements")
    @PreAuthorize("hasAuthority('STOCK_REPORT')")
    public List<StockReportService.MovementRow> movementReport(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) Long itemId,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long branchId, Authentication auth) {
        return reports.movements(from, to, itemId, warehouseId,
                userService.resolveRequestedBranch(auth.getName(), branchId));
    }

    @GetMapping("/reports/critical-stock")
    @PreAuthorize("hasAuthority('STOCK_REPORT')")
    public List<StockReportService.CriticalStockRow> criticalStock(
            @RequestParam(required = false) Long branchId, Authentication auth) {
        return reports.criticalStock(userService.resolveRequestedBranch(auth.getName(), branchId));
    }

    @GetMapping("/reports/turnover")
    @PreAuthorize("hasAuthority('STOCK_REPORT')")
    public List<StockReportService.TurnoverRow> turnover(
            @RequestParam LocalDate from, @RequestParam LocalDate to,
            @RequestParam(required = false) Long branchId, Authentication auth) {
        return reports.turnover(from, to, userService.resolveRequestedBranch(auth.getName(), branchId));
    }

    @GetMapping("/reports/dead-stock")
    @PreAuthorize("hasAuthority('STOCK_REPORT')")
    public List<StockReportService.DeadStockRow> deadStock(
            @RequestParam(defaultValue = "90") int idleDays,
            @RequestParam(required = false) Long branchId, Authentication auth) {
        return reports.deadStock(idleDays, userService.resolveRequestedBranch(auth.getName(), branchId));
    }

    @GetMapping("/reports/inventory-value")
    @PreAuthorize("hasAuthority('STOCK_REPORT')")
    public List<StockReportService.InventoryValueRow> inventoryValue(
            @RequestParam(required = false) Long branchId, Authentication auth) {
        return reports.inventoryValue(userService.resolveRequestedBranch(auth.getName(), branchId));
    }

    @GetMapping("/reports/warehouse-comparison")
    @PreAuthorize("hasAuthority('STOCK_REPORT')")
    public List<StockReportService.WarehouseComparisonRow> warehouseComparison(
            @RequestParam(required = false) Long branchId, Authentication auth) {
        return reports.warehouseComparison(userService.resolveRequestedBranch(auth.getName(), branchId));
    }

    private BigDecimal totalOnHand(long itemId) {
        return stock.levelsOf(itemId).stream()
                .map(l -> l.getQtyBase())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private ItemUnit baseUnitOf(long itemId) {
        return stock.unitsOf(itemId).stream().filter(ItemUnit::isBase).findFirst().orElse(null);
    }

    private Map<Long, String> unitCodeMap() {
        return unitRepo.findAll().stream().collect(Collectors.toMap(Unit::getId, Unit::getCode));
    }
}
