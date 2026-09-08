package com.beautysalonapp.modules.stock;

import com.beautysalonapp.modules.stock.application.StockReportService;
import com.beautysalonapp.modules.stock.application.StockService;
import com.beautysalonapp.modules.stock.domain.Item;
import com.beautysalonapp.modules.stock.domain.ItemType;
import com.beautysalonapp.modules.stock.domain.MovementDirection;
import com.beautysalonapp.modules.stock.domain.WarehouseType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class StockReportServiceTest {

    @Autowired StockService stock;
    @Autowired StockReportService reports;

    private Item newItem() {
        return stock.createItem(null, "Rapor Ürünü " + System.nanoTime(), ItemType.EMTIA, "ADET",
                new BigDecimal("20"), null, "MarkaR", new BigDecimal("150"), null);
    }

    private long depo() {
        return stock.defaultWarehouse().getId();
    }

    @Test
    void stok_durumu_giristen_sonra_dogru_miktar_ve_degeri_gosterir() {
        Item it = newItem();
        long unit = stock.baseUnitId(it.getId());
        stock.record(LocalDate.now(), it.getId(), depo(), MovementDirection.IN,
                unit, new BigDecimal("10"), new BigDecimal("50"), "TEST", "SR-" + it.getId(), "1", null);

        var rows = reports.stockStatus(depo(), null);
        var row = rows.stream().filter(r -> r.itemId() == it.getId()).findFirst().orElseThrow();
        assertThat(row.qtyBase()).isEqualByComparingTo("10");
        assertThat(row.avgCost()).isEqualByComparingTo("50.0000");
        assertThat(row.value()).isEqualByComparingTo(new BigDecimal("500.000000"));
    }

    @Test
    void kritik_stok_esik_altina_dusen_urunu_bulur() {
        Item it = newItem();
        long unit = stock.baseUnitId(it.getId());
        stock.record(LocalDate.now(), it.getId(), depo(), MovementDirection.IN,
                unit, new BigDecimal("5"), new BigDecimal("10"), "TEST", "CR-" + it.getId(), "1", null);
        stock.updateReorderLevel(it.getId(), new BigDecimal("10")); // 5 <= 10 → kritik

        var rows = reports.criticalStock(null);
        assertThat(rows).anyMatch(r -> r.itemId() == it.getId());

        stock.updateReorderLevel(it.getId(), new BigDecimal("2")); // 5 > 2 → artık kritik değil
        assertThat(reports.criticalStock(null)).noneMatch(r -> r.itemId() == it.getId());
    }

    @Test
    void hareket_dokumu_tarih_araligina_gore_filtrelenir() {
        Item it = newItem();
        long unit = stock.baseUnitId(it.getId());
        LocalDate today = LocalDate.now();
        stock.record(today, it.getId(), depo(), MovementDirection.IN,
                unit, new BigDecimal("5"), new BigDecimal("40"), "TEST", "MV-" + it.getId(), "1", "not");

        var rows = reports.movements(today, today, it.getId(), null, null);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).direction()).isEqualTo("IN");
        assertThat(rows.get(0).baseQty()).isEqualByComparingTo("5");

        var outsideRange = reports.movements(today.minusDays(10), today.minusDays(5), it.getId(), null, null);
        assertThat(outsideRange).isEmpty();
    }

    @Test
    void devir_hizi_cikis_miktarina_gore_hesaplanir() {
        Item it = newItem();
        long unit = stock.baseUnitId(it.getId());
        LocalDate today = LocalDate.now();
        stock.record(today, it.getId(), depo(), MovementDirection.IN,
                unit, new BigDecimal("20"), new BigDecimal("10"), "TEST", "TO-IN-" + it.getId(), "1", null);
        stock.record(today, it.getId(), depo(), MovementDirection.OUT,
                unit, new BigDecimal("8"), null, "TEST", "TO-OUT-" + it.getId(), "1", null);

        var rows = reports.turnover(today, today, null);
        var row = rows.stream().filter(r -> r.itemId() == it.getId()).findFirst().orElseThrow();
        assertThat(row.issuedQty()).isEqualByComparingTo("8");
        assertThat(row.currentOnHand()).isEqualByComparingTo("12"); // 20-8
        assertThat(row.turnoverRatio()).isEqualByComparingTo(new BigDecimal("0.6667"));
    }

    @Test
    void olu_stok_uzun_suredir_hareketsiz_urunu_bulur() {
        Item it = newItem();
        long unit = stock.baseUnitId(it.getId());
        // Hareket "bugün" olduğundan idleDays=0 ile bile "eşiğin altında/eşit" sayılmalı.
        stock.record(LocalDate.now(), it.getId(), depo(), MovementDirection.IN,
                unit, new BigDecimal("3"), new BigDecimal("10"), "TEST", "DEAD-" + it.getId(), "1", null);

        var rows = reports.deadStock(0, null);
        assertThat(rows).anyMatch(r -> r.itemId() == it.getId());
    }

    @Test
    void envanter_degeri_depo_bazinda_toplanir() {
        Item it = newItem();
        long unit = stock.baseUnitId(it.getId());
        stock.record(LocalDate.now(), it.getId(), depo(), MovementDirection.IN,
                unit, new BigDecimal("4"), new BigDecimal("25"), "TEST", "INV-" + it.getId(), "1", null);

        var rows = reports.inventoryValue(null);
        var row = rows.stream().filter(r -> r.warehouseId() == depo()).findFirst().orElseThrow();
        assertThat(row.totalQty()).isGreaterThanOrEqualTo(new BigDecimal("4"));
        assertThat(row.totalValue()).isGreaterThanOrEqualTo(new BigDecimal("100"));
    }

    @Test
    void depo_karsilastirma_turlere_gore_gruplanir() {
        var rows = reports.warehouseComparison(null);
        assertThat(rows).extracting(StockReportService.WarehouseComparisonRow::warehouseType)
                .contains(WarehouseType.WAREHOUSE.name());
    }
}
