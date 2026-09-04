package com.beautysalonapp.modules.branch.application;

import com.beautysalonapp.audit.application.AuditService;
import com.beautysalonapp.core.context.BranchContextHolder;
import com.beautysalonapp.core.error.BusinessRuleException;
import com.beautysalonapp.core.error.NotFoundException;
import com.beautysalonapp.modules.branch.domain.Branch;
import com.beautysalonapp.modules.branch.infrastructure.BranchRepository;
import com.beautysalonapp.modules.finance.application.FinanceService;
import com.beautysalonapp.modules.finance.domain.FinAccountKind;
import com.beautysalonapp.modules.stock.application.StockService;
import com.beautysalonapp.modules.stock.domain.WarehouseType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Şube tanımlama ve merkezi görünürlük (Faz 8 "merkezi işletme" şeması, tam yazma izolasyonu
 * için bkz. ADR 0006 "sonraki adım").
 */
@Service
@Transactional
public class BranchService {

    private final BranchRepository branches;
    private final AuditService audit;
    private final StockService stock;
    private final FinanceService finance;

    public BranchService(BranchRepository branches, AuditService audit, StockService stock, FinanceService finance) {
        this.branches = branches;
        this.audit = audit;
        this.stock = stock;
        this.finance = finance;
    }

    @Transactional(readOnly = true)
    public List<Branch> list() {
        return branches.findAllByDeletedFalseOrderByCodeAsc();
    }

    @Transactional(readOnly = true)
    public Branch get(long id) {
        return branches.findById(id).filter(b -> !b.isDeleted())
                .orElseThrow(() -> new NotFoundException("Şube", id));
    }

    public Branch create(String code, String title, String taxId, String address, String phone) {
        String normalizedCode = code == null ? "" : code.trim().toUpperCase();
        if (normalizedCode.isBlank()) {
            throw new BusinessRuleException("code_required", "Şube kodu zorunlu");
        }
        if (branches.findByCodeIgnoreCase(normalizedCode).filter(b -> !b.isDeleted()).isPresent()) {
            throw new BusinessRuleException("branch_exists", "Bu şube kodu zaten var: " + normalizedCode);
        }
        Branch b = new Branch(normalizedCode, requireTitle(title));
        b.setTaxId(taxId);
        b.setAddress(address);
        b.setPhone(phone);
        b.setHeadquarters(branches.countByDeletedFalse() == 0); // ilk şube otomatik merkez
        branches.save(b);
        audit.record("BRANCH_CREATE", "Branch", b.getCode(), "Şube tanımlandı: " + b.getTitle());
        provisionDefaults(b);
        return b;
    }

    /**
     * Faz 8 tam şube izolasyonu (ADR 0006): yeni şube için kendi varsayılan deposunu ve kasa
     * hesabını açar. {@code makeDefault=false} ile çağrılır ki merkez şubenin (id=1) global
     * varsayılan bayrağı bozulmasın — yeni şubenin kaynakları {@code branch_id} eşleşmesiyle
     * bulunur ({@code isDefault} bayrağıyla değil, bkz. {@code StockService.branchWarehouse}).
     * Kod çakışmasını önlemek için şube koduna göre türetilir (şube kodları benzersizdir).
     */
    private void provisionDefaults(Branch b) {
        BranchContextHolder.set(b.getId());
        try {
            // warehouse.code / fin_account.code VARCHAR(20); "D-"/"K-" + şube kodu (max 20)
            // sığması için kısaltılır — şube kodu tek başına benzersiz olduğundan yine benzersizdir.
            String suffix = b.getCode().length() > 18 ? b.getCode().substring(0, 18) : b.getCode();
            stock.createWarehouse("D-" + suffix, "Depo (" + b.getTitle() + ")",
                    WarehouseType.WAREHOUSE, false);
            finance.createAccount("K-" + suffix, "Kasa (" + b.getTitle() + ")",
                    FinAccountKind.KASA, "TRY", null, false);
        } finally {
            BranchContextHolder.clear();
        }
    }

    public Branch update(long id, String title, String taxId, String address, String phone) {
        Branch b = get(id);
        b.setTitle(requireTitle(title));
        b.setTaxId(taxId);
        b.setAddress(address);
        b.setPhone(phone);
        return b;
    }

    /** Merkez şubeyi değiştirir; her zaman tam olarak bir merkez şube olur. */
    public void setHeadquarters(long id) {
        Branch target = get(id);
        for (Branch b : branches.findAllByDeletedFalseOrderByCodeAsc()) {
            if (b.isHeadquarters() && !b.getId().equals(id)) {
                b.setHeadquarters(false);
            }
        }
        target.setHeadquarters(true);
        audit.record("BRANCH_SET_HQ", "Branch", target.getCode(), "Merkez şube değişti: " + target.getTitle());
    }

    /** Soft delete: son kalan şube veya merkez şube (önce merkezi başka şubeye taşıyın) silinemez. */
    public void delete(long id) {
        Branch b = get(id);
        if (branches.countByDeletedFalse() <= 1) {
            throw new BusinessRuleException("last_branch", "Son kalan şube silinemez");
        }
        if (b.isHeadquarters()) {
            throw new BusinessRuleException("hq_delete",
                    "Merkez şube silinemez; önce başka bir şubeyi merkez yapın");
        }
        b.setDeleted(true);
        audit.record("BRANCH_DELETE", "Branch", b.getCode(), "Şube silindi: " + b.getTitle());
    }

    private static String requireTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new BusinessRuleException("title_required", "Şube ünvanı zorunlu");
        }
        return title.trim();
    }
}
