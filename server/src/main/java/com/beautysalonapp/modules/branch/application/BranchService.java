package com.beautysalonapp.modules.branch.application;

import com.beautysalonapp.audit.application.AuditService;
import com.beautysalonapp.core.error.BusinessRuleException;
import com.beautysalonapp.core.error.NotFoundException;
import com.beautysalonapp.modules.branch.domain.Branch;
import com.beautysalonapp.modules.branch.infrastructure.BranchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Şube tanımlama ve merkezi görünürlük (Faz 8 "merkezi işletme" şeması).
 * Kapsam: docs/adr/0006-merkezi-sube.md.
 */
@Service
@Transactional
public class BranchService {

    private final BranchRepository branches;
    private final AuditService audit;

    public BranchService(BranchRepository branches, AuditService audit) {
        this.branches = branches;
        this.audit = audit;
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
        return b;
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
