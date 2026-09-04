package com.beautysalonapp.modules.invoice.application;

import com.beautysalonapp.audit.application.AuditService;
import com.beautysalonapp.core.error.BusinessRuleException;
import com.beautysalonapp.core.error.NotFoundException;
import com.beautysalonapp.modules.branch.application.BranchService;
import com.beautysalonapp.modules.branch.domain.Branch;
import com.beautysalonapp.modules.invoice.domain.Invoice;
import com.beautysalonapp.modules.invoice.domain.InvoiceLine;
import com.beautysalonapp.modules.invoice.domain.InvoiceStatus;
import com.beautysalonapp.modules.invoice.domain.InvoiceType;
import com.beautysalonapp.modules.invoice.domain.UblTrInvoiceBuilder;
import com.beautysalonapp.modules.invoice.domain.UblTrInvoiceData;
import com.beautysalonapp.modules.invoice.infrastructure.InvoiceLineRepository;
import com.beautysalonapp.modules.invoice.infrastructure.InvoiceRepository;
import com.beautysalonapp.modules.party.application.PartyDirectory;
import com.beautysalonapp.modules.party.application.PartyDirectory.EInvoiceParty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * e-Fatura HAZIRLIĞI (Faz 8, docs/modules/e-fatura.md). GİB'e veya bir özel entegratöre
 * **hiçbir şey göndermez** (CLAUDE.md #1) — yalnızca UBL-TR 1.2 uyumlu XML üretir; işletme
 * bunu indirip kendi seçtiği entegratöre elle/API ile yükler. Gerçek imzalama (mali mühür),
 * gönderim ve durum takibi kapsam dışıdır.
 */
@Service
@Transactional
public class EInvoiceService {

    /** e-Fatura yalnızca satış yönlü faturalar için üretilir (alış faturaları karşı tarafın belgesidir). */
    private static final Set<InvoiceType> ELIGIBLE = Set.of(InvoiceType.SATIS, InvoiceType.PERAKENDE, InvoiceType.IADE_SATIS);

    private final InvoiceRepository invoices;
    private final InvoiceLineRepository lineRepo;
    private final PartyDirectory partyDirectory;
    private final BranchService branches;
    private final AuditService audit;

    public EInvoiceService(InvoiceRepository invoices, InvoiceLineRepository lineRepo,
                           PartyDirectory partyDirectory, BranchService branches, AuditService audit) {
        this.invoices = invoices;
        this.lineRepo = lineRepo;
        this.partyDirectory = partyDirectory;
        this.branches = branches;
        this.audit = audit;
    }

    public record EInvoiceResult(String uuid, String status, String filename, String xml) {}

    /**
     * XML'i üretir. İlk çağrıda faturaya kalıcı bir UUID atar ve durumunu {@code HAZIR} yapar
     * (idempotent — sonraki çağrılar aynı UUID'yi kullanır, aynı XML'i yeniden üretir).
     */
    public EInvoiceResult generate(long invoiceId) {
        Invoice inv = invoices.findById(invoiceId)
                .orElseThrow(() -> new NotFoundException("Fatura", invoiceId));
        if (inv.getStatus() == InvoiceStatus.VOIDED) {
            throw new BusinessRuleException("invoice_voided", "İptal edilmiş fatura için e-Fatura üretilemez");
        }
        if (!ELIGIBLE.contains(inv.getType())) {
            throw new BusinessRuleException("not_eligible",
                    "e-Fatura yalnızca satış yönlü faturalar için üretilir: " + inv.getType());
        }
        List<InvoiceLine> lines = lineRepo.findAllByInvoiceIdOrderByLineNo(invoiceId);
        if (lines.isEmpty()) {
            throw new BusinessRuleException("no_lines", "Faturada satır yok");
        }

        EInvoiceParty buyer = partyDirectory.eInvoiceInfo(inv.getPartyId())
                .orElseThrow(() -> new NotFoundException("Müşteri", inv.getPartyId()));
        if ((buyer.taxId() == null || buyer.taxId().isBlank()) && (buyer.tcNo() == null || buyer.tcNo().isBlank())) {
            throw new BusinessRuleException("buyer_tax_id_missing",
                    "Müşteri kartında vergi no veya TC kimlik no yok — e-Fatura için zorunlu");
        }
        Branch seller = resolveSellerBranch(inv.getBranchId());

        if (inv.getEinvoiceUuid() == null || inv.getEinvoiceUuid().isBlank()) {
            inv.setEinvoiceUuid(UUID.randomUUID().toString());
            inv.setEinvoiceStatus("HAZIR");
            audit.record("EINVOICE_PREPARE", "Invoice", inv.getDocNo(), "e-Fatura XML'i ilk kez hazırlandı");
        }

        // taxExclusiveTotal = subtotal - discountTotal (satır bazlı net, KDV hariç)
        var taxExclusive = inv.getSubtotal().subtract(inv.getDiscountTotal());

        UblTrInvoiceData data = new UblTrInvoiceData(
                inv.getEinvoiceUuid(),
                inv.getDocNo(),
                inv.getDate(),
                LocalTime.now().withNano(0),
                mapTypeCode(inv.getType()),
                inv.getCurrency(),
                new UblTrInvoiceData.Party(seller.getTaxId(), null, seller.getTitle(),
                        seller.getAddress(), null, null, null),
                new UblTrInvoiceData.Party(buyer.taxId(), buyer.tcNo(), buyer.title(),
                        buyer.address(), buyer.city(), buyer.district(), buyer.postcode()),
                lines.stream().map(this::toLine).toList(),
                taxExclusive,
                inv.getDiscountTotal(),
                taxExclusive,
                inv.getVatTotal(),
                inv.getGrandTotal());

        String xml = UblTrInvoiceBuilder.build(data);
        String filename = "efatura-" + inv.getDocNo().replaceAll("[^A-Za-z0-9-]", "_") + ".xml";
        return new EInvoiceResult(inv.getEinvoiceUuid(), inv.getEinvoiceStatus(), filename, xml);
    }

    private UblTrInvoiceData.Line toLine(InvoiceLine l) {
        return new UblTrInvoiceData.Line(l.getLineNo(), l.getDescription(), l.getQuantity(),
                null, l.getUnitPrice(), l.getVatRate(), l.getLineNet(), l.getLineVat(), l.getLineTotal());
    }

    private Branch resolveSellerBranch(Long branchId) {
        return branches.list().stream()
                .filter(b -> b.getId().equals(branchId))
                .findFirst()
                .or(() -> branches.list().stream().filter(Branch::isHeadquarters).findFirst())
                .orElseThrow(() -> new BusinessRuleException("no_branch",
                        "Tanımlı şube yok — Ayarlar → Şubeler'den işletme bilgilerini girin"));
    }

    private static String mapTypeCode(InvoiceType type) {
        return type == InvoiceType.IADE_SATIS ? "IADE" : "SATIS";
    }
}
