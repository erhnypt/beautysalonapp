package com.beautysalonapp.modules.stock;

import com.beautysalonapp.modules.stock.domain.Unit;
import com.beautysalonapp.modules.stock.domain.Warehouse;
import com.beautysalonapp.modules.stock.domain.WarehouseType;
import com.beautysalonapp.modules.stock.infrastructure.UnitRepository;
import com.beautysalonapp.modules.stock.infrastructure.WarehouseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** İlk açılışta temel birimler ve varsayılan depo/vitrin. */
@Component
@Order(20)
public class StockDefaults implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StockDefaults.class);
    private static final Long BRANCH = 1L;

    private final UnitRepository units;
    private final WarehouseRepository warehouses;

    public StockDefaults(UnitRepository units, WarehouseRepository warehouses) {
        this.units = units;
        this.warehouses = warehouses;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        record UnitDef(String code, String name) {}
        List<UnitDef> defs = List.of(
                new UnitDef("ADET", "Adet"),
                new UnitDef("KOLI", "Koli"),
                new UnitDef("PAKET", "Paket"),
                new UnitDef("ML", "Mililitre"),
                new UnitDef("GR", "Gram"),
                new UnitDef("KG", "Kilogram"),
                new UnitDef("SEANS", "Seans"));
        for (UnitDef d : defs) {
            if (units.findByBranchIdAndCode(BRANCH, d.code()).isEmpty()) {
                units.save(new Unit(d.code(), d.name()));
                log.info("Birim eklendi: {}", d.code());
            }
        }

        if (warehouses.findByBranchIdAndCode(BRANCH, "DEPO").isEmpty()) {
            Warehouse w = new Warehouse("DEPO", "Ana Depo", WarehouseType.WAREHOUSE);
            w.setDefault(true);
            warehouses.save(w);
            log.info("Varsayılan depo oluşturuldu: DEPO");
        }
        if (warehouses.findByBranchIdAndCode(BRANCH, "VITRIN").isEmpty()) {
            warehouses.save(new Warehouse("VITRIN", "Vitrin", WarehouseType.SHOWCASE));
        }
        if (warehouses.findByBranchIdAndCode(BRANCH, "SARF").isEmpty()) {
            warehouses.save(new Warehouse("SARF", "Sarf (Hizmet Tüketimi)", WarehouseType.CONSUMPTION));
        }
    }
}
