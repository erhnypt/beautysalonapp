package com.beautysalonapp.modules.branch;

import com.beautysalonapp.core.context.BranchContextHolder;
import com.beautysalonapp.modules.branch.application.BranchService;
import com.beautysalonapp.modules.branch.domain.Branch;
import com.beautysalonapp.modules.finance.application.FinanceService;
import com.beautysalonapp.modules.finance.domain.FinAccount;
import com.beautysalonapp.modules.finance.infrastructure.FinAccountRepository;
import com.beautysalonapp.modules.stock.application.StockService;
import com.beautysalonapp.modules.stock.domain.Warehouse;
import com.beautysalonapp.modules.stock.infrastructure.WarehouseRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Faz 8 tam şube izolasyonu (ADR 0006): yeni bir şube açılınca kendi deposu/kasası
 * otomatik açılır ve {@link StockService}/{@link FinanceService}'in varsayılan kaynak
 * çözümü, aktif şube bağlamına göre doğru kaynağa gider — merkez şubede (1, bağlam yok)
 * davranış **hiç değişmez**.
 */
@SpringBootTest
class BranchIsolationTest {

    @Autowired
    private BranchService branches;
    @Autowired
    private StockService stock;
    @Autowired
    private FinanceService finance;
    @Autowired
    private WarehouseRepository warehouses;
    @Autowired
    private FinAccountRepository accounts;

    private String code() {
        return "S" + Long.toString(System.nanoTime(), 36).toUpperCase();
    }

    @AfterEach
    void clearContext() {
        // Bir testte hata olup context temizlenmese bile sonraki testleri etkilemesin.
        BranchContextHolder.clear();
    }

    @Test
    void yeni_sube_kendi_deposunu_ve_kasasini_otomatik_alir() {
        Branch b = branches.create(code(), "İzolasyon Şubesi", null, null, null);

        List<Warehouse> branchWarehouses = warehouses.findAllByBranchIdAndDeletedFalseOrderByCode(b.getId());
        assertThat(branchWarehouses).hasSize(1);
        assertThat(branchWarehouses.get(0).getBranchId()).isEqualTo(b.getId());

        var acc = accounts.findFirstByBranchIdAndKindAndActiveTrueOrderByIdAsc(
                b.getId(), com.beautysalonapp.modules.finance.domain.FinAccountKind.KASA);
        assertThat(acc).isPresent();
        assertThat(acc.get().getBranchId()).isEqualTo(b.getId());
    }

    @Test
    void merkez_sube_baglaminda_varsayilan_kaynak_cozumu_degismez() {
        // Bağlam yok (null) → eski global davranış: mevcut varsayılan depo/kasa.
        assertThat(BranchContextHolder.get()).isNull();
        long globalWarehouse = stock.defaultWarehouseId();
        long globalCash = finance.defaultCashAccountId();

        BranchContextHolder.set(1L); // açıkça merkez şube seçilse de aynı sonuç
        try {
            assertThat(stock.defaultWarehouseId()).isEqualTo(globalWarehouse);
            assertThat(finance.defaultCashAccountId()).isEqualTo(globalCash);
        } finally {
            BranchContextHolder.clear();
        }
    }

    @Test
    void yeni_sube_baglaminda_varsayilan_kaynak_cozumu_o_subeye_gider() {
        Branch b = branches.create(code(), "İzolasyon Şubesi 2", null, null, null);
        long globalWarehouse = stock.defaultWarehouseId();
        long globalCash = finance.defaultCashAccountId();

        BranchContextHolder.set(b.getId());
        try {
            long branchWarehouseId = stock.defaultWarehouseId();
            long branchCashId = finance.defaultCashAccountId();

            assertThat(branchWarehouseId).isNotEqualTo(globalWarehouse);
            assertThat(branchCashId).isNotEqualTo(globalCash);

            Warehouse w = warehouses.findById(branchWarehouseId).orElseThrow();
            FinAccount a = accounts.findById(branchCashId).orElseThrow();
            assertThat(w.getBranchId()).isEqualTo(b.getId());
            assertThat(a.getBranchId()).isEqualTo(b.getId());
        } finally {
            BranchContextHolder.clear();
        }
    }

    @Test
    void baglam_olmadan_olusturulan_kayit_varsayilan_sube_1de_kalir() {
        assertThat(BranchContextHolder.get()).isNull();
        Warehouse w = stock.createWarehouse("D-" + code(), "Test Depo", com.beautysalonapp.modules.stock.domain.WarehouseType.SHOWCASE, false);
        assertThat(w.getBranchId()).isEqualTo(1L);
    }
}
