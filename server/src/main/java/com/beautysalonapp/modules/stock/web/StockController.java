package com.beautysalonapp.modules.stock.web;

import com.beautysalonapp.modules.stock.application.StockPort;
import com.beautysalonapp.modules.stock.application.StockService;
import com.beautysalonapp.modules.stock.domain.Item;
import com.beautysalonapp.modules.stock.domain.ItemType;
import com.beautysalonapp.modules.stock.domain.MovementDirection;
import com.beautysalonapp.modules.stock.domain.Unit;
import com.beautysalonapp.modules.stock.infrastructure.UnitRepository;
import com.beautysalonapp.modules.stock.web.StockDtos.AddBarcodeRequest;
import com.beautysalonapp.modules.stock.web.StockDtos.AddItemUnitRequest;
import com.beautysalonapp.modules.stock.web.StockDtos.BarcodeResolutionView;
import com.beautysalonapp.modules.stock.web.StockDtos.BarcodeView;
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
import com.beautysalonapp.modules.stock.web.StockDtos.WarehouseView;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
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

    public StockController(StockService stock, StockPort stockPort, UnitRepository unitRepo) {
        this.stock = stock;
        this.stockPort = stockPort;
        this.unitRepo = unitRepo;
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

    // --- ürün kartı ---
    @GetMapping("/items")
    public Page<ItemRow> items(@RequestParam(required = false) ItemType type,
                               @RequestParam(required = false) Long categoryId,
                               @RequestParam(required = false) String q,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "25") int size) {
        return stock.searchItems(type, categoryId, q, PageRequest.of(page, Math.min(size, 200)))
                .map(i -> StockDtos.toRow(i, totalOnHand(i.getId())));
    }

    @GetMapping("/items/{id}")
    public ItemDetail item(@PathVariable long id) {
        Item i = stock.getItem(id);
        Map<Long, String> unitCodes = unitCodeMap();
        List<ItemUnitView> us = stock.unitsOf(id).stream()
                .map(iu -> ItemUnitView.of(iu, unitCodes.getOrDefault(iu.getUnitId(), "?"))).toList();
        List<BarcodeView> bs = stock.barcodesOf(id).stream().map(BarcodeView::of).toList();
        List<LevelView> ls = stock.levelsOf(id).stream().map(LevelView::of).toList();
        return new ItemDetail(StockDtos.toRow(i, totalOnHand(id)), us, bs, ls);
    }

    @PostMapping("/items")
    @PreAuthorize("hasAuthority('STOCK_ADD')")
    public ItemDetail createItem(@Valid @RequestBody CreateItemRequest r) {
        Item i = stock.createItem(r.code(), r.name(), r.type(), r.baseUnitCode(), r.vatRate(),
                r.categoryId(), r.brand(), r.salePrice());
        return item(i.getId());
    }

    @PostMapping("/items/{id}/units")
    @PreAuthorize("hasAuthority('STOCK_EDIT')")
    public ItemDetail addUnit(@PathVariable long id, @Valid @RequestBody AddItemUnitRequest r) {
        stock.addUnit(id, r.unitCode(), r.factor(), r.salePrice());
        return item(id);
    }

    @PostMapping("/items/{id}/barcodes")
    @PreAuthorize("hasAuthority('STOCK_EDIT')")
    public ItemDetail addBarcode(@PathVariable long id, @Valid @RequestBody AddBarcodeRequest r) {
        stock.addBarcode(id, r.barcode(), r.unitCode(), r.primary());
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

    private BigDecimal totalOnHand(long itemId) {
        return stock.levelsOf(itemId).stream()
                .map(l -> l.getQtyBase())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<Long, String> unitCodeMap() {
        return unitRepo.findAll().stream().collect(Collectors.toMap(Unit::getId, Unit::getCode));
    }
}
