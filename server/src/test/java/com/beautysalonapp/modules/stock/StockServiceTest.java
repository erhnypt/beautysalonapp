package com.beautysalonapp.modules.stock;

import com.beautysalonapp.core.error.BusinessRuleException;
import com.beautysalonapp.modules.stock.application.StockPort;
import com.beautysalonapp.modules.stock.application.StockService;
import com.beautysalonapp.modules.stock.domain.Item;
import com.beautysalonapp.modules.stock.domain.ItemType;
import com.beautysalonapp.modules.stock.domain.MovementDirection;
import com.beautysalonapp.modules.stock.domain.Warehouse;
import com.beautysalonapp.settings.application.SettingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class StockServiceTest {

    @Autowired StockService stock;
    @Autowired StockPort stockPort;
    @Autowired SettingService settings;

    private Item newItem() {
        return stock.createItem(null, "Şampuan " + System.nanoTime(), ItemType.EMTIA, "ADET",
                new BigDecimal("20"), null, "MarkaX", new BigDecimal("120"));
    }

    private long depo() {
        return stock.defaultWarehouse().getId();
    }

    @Test
    void koli_birimi_capraz_donusum_1_koli_12_adet() {
        Item it = newItem();
        stock.addUnit(it.getId(), "KOLI", new BigDecimal("12"), new BigDecimal("1300"));
        stock.addBarcode(it.getId(), "869KOLI001", "KOLI", true);
        stock.addBarcode(it.getId(), "869ADET001", "ADET", false);

        var res = stockPort.resolveBarcode("869KOLI001");
        assertThat(res.unitCode()).isEqualTo("KOLI");
        assertThat(res.factor()).isEqualByComparingTo("12");

        // 2 KOLI giriş => base 24 ADET
        stock.record(LocalDate.now(), it.getId(), depo(), MovementDirection.IN,
                res.unitId(), new BigDecimal("2"), new BigDecimal("1200"), "TEST", "T-1", "1", null);
        assertThat(stockPort.onHandBase(it.getId(), depo())).isEqualByComparingTo("24");
    }

    @Test
    void agirlikli_ortalama_iki_alistan_sonra() {
        Item it = newItem();
        long adet = stock.unitsOf(it.getId()).get(0).getUnitId();
        stock.record(LocalDate.now(), it.getId(), depo(), MovementDirection.IN, adet,
                new BigDecimal("10"), new BigDecimal("25"), "TEST", "A-1", "1", null);
        stock.record(LocalDate.now(), it.getId(), depo(), MovementDirection.IN, adet,
                new BigDecimal("30"), new BigDecimal("30"), "TEST", "A-2", "1", null);

        var level = stock.levelsOf(it.getId()).stream().filter(l -> l.getWarehouseId() == depo()).findFirst().orElseThrow();
        assertThat(level.getQtyBase()).isEqualByComparingTo("40");
        assertThat(level.getAvgCost()).isEqualByComparingTo("28.7500");
    }

    @Test
    void cikis_stok_dusurur_ortalama_sabit() {
        Item it = newItem();
        long adet = stock.unitsOf(it.getId()).get(0).getUnitId();
        stock.record(LocalDate.now(), it.getId(), depo(), MovementDirection.IN, adet,
                new BigDecimal("40"), new BigDecimal("28.75"), "TEST", "B-1", "1", null);
        stock.record(LocalDate.now(), it.getId(), depo(), MovementDirection.OUT, adet,
                new BigDecimal("15"), null, "TEST", "B-2", "1", null);

        var level = stock.levelsOf(it.getId()).stream().filter(l -> l.getWarehouseId() == depo()).findFirst().orElseThrow();
        assertThat(level.getQtyBase()).isEqualByComparingTo("25");
        assertThat(level.getAvgCost()).isEqualByComparingTo("28.7500");
    }

    @Test
    void negatif_stok_BLOCK_modunda_reddedilir() {
        settings.put("stock.negativeStock.mode", "BLOCK");
        try {
            Item it = newItem();
            long adet = stock.unitsOf(it.getId()).get(0).getUnitId();
            assertThatThrownBy(() -> stock.record(LocalDate.now(), it.getId(), depo(), MovementDirection.OUT,
                    adet, new BigDecimal("5"), null, "TEST", "C-1", "1", null))
                    .isInstanceOf(BusinessRuleException.class);
        } finally {
            settings.put("stock.negativeStock.mode", "WARN");
        }
    }

    @Test
    void hizmet_karti_stok_tutmaz() {
        Item svc = stock.createItem(null, "Cilt Bakımı", ItemType.HIZMET, "SEANS",
                new BigDecimal("20"), null, null, new BigDecimal("500"));
        long seans = stock.unitsOf(svc.getId()).get(0).getUnitId();
        assertThatThrownBy(() -> stock.record(LocalDate.now(), svc.getId(), depo(), MovementDirection.IN,
                seans, BigDecimal.ONE, BigDecimal.TEN, "TEST", "D-1", "1", null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void depolar_arasi_transfer() {
        Item it = newItem();
        long adet = stock.unitsOf(it.getId()).get(0).getUnitId();
        Warehouse vitrin = stock.listWarehouses().stream()
                .filter(w -> w.getCode().equals("VITRIN")).findFirst().orElseThrow();
        stock.record(LocalDate.now(), it.getId(), depo(), MovementDirection.IN, adet,
                new BigDecimal("20"), new BigDecimal("50"), "TEST", "E-1", "1", null);

        stock.transfer(LocalDate.now(), it.getId(), depo(), vitrin.getId(), adet, new BigDecimal("8"), "vitrine");

        assertThat(stockPort.onHandBase(it.getId(), depo())).isEqualByComparingTo("12");
        assertThat(stockPort.onHandBase(it.getId(), vitrin.getId())).isEqualByComparingTo("8");
        var vitrinLevel = stock.levelsOf(it.getId()).stream()
                .filter(l -> l.getWarehouseId() == vitrin.getId()).findFirst().orElseThrow();
        assertThat(vitrinLevel.getAvgCost()).isEqualByComparingTo("50.0000");
    }

    @Test
    void ayni_belge_line_key_idempotent() {
        Item it = newItem();
        long adet = stock.unitsOf(it.getId()).get(0).getUnitId();
        stock.record(LocalDate.now(), it.getId(), depo(), MovementDirection.IN, adet,
                new BigDecimal("5"), new BigDecimal("10"), "INVOICE", "F-1", "L1", null);
        stock.record(LocalDate.now(), it.getId(), depo(), MovementDirection.IN, adet,
                new BigDecimal("5"), new BigDecimal("10"), "INVOICE", "F-1", "L1", null);
        assertThat(stockPort.onHandBase(it.getId(), depo())).isEqualByComparingTo("5");
    }
}
