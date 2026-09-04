package com.beautysalonapp.modules.invoice.application;

import com.beautysalonapp.audit.application.AuditService;
import com.beautysalonapp.core.error.BusinessRuleException;
import com.beautysalonapp.core.error.NotFoundException;
import com.beautysalonapp.core.sequence.SequenceService;
import com.beautysalonapp.modules.finance.application.ChequePort;
import com.beautysalonapp.modules.finance.application.FinancePort;
import com.beautysalonapp.modules.finance.application.PosSlipService;
import com.beautysalonapp.modules.finance.domain.ChequeType;
import com.beautysalonapp.modules.invoice.domain.Invoice;
import com.beautysalonapp.modules.invoice.domain.InvoiceCalculator;
import com.beautysalonapp.modules.invoice.domain.InvoiceCalculator.LineInput;
import com.beautysalonapp.modules.invoice.domain.InvoiceLine;
import com.beautysalonapp.modules.invoice.domain.InvoicePayment;
import com.beautysalonapp.modules.invoice.domain.InvoiceStatus;
import com.beautysalonapp.modules.invoice.domain.InvoiceType;
import com.beautysalonapp.modules.invoice.domain.PaymentMethod;
import com.beautysalonapp.modules.invoice.infrastructure.InvoiceLineRepository;
import com.beautysalonapp.modules.invoice.infrastructure.InvoicePaymentRepository;
import com.beautysalonapp.modules.invoice.infrastructure.InvoiceRepository;
import com.beautysalonapp.modules.loyalty.application.LoyaltyPort;
import com.beautysalonapp.modules.party.application.PartyDirectory;
import com.beautysalonapp.modules.party.application.PartyLedger;
import com.beautysalonapp.modules.party.domain.AccountKind;
import com.beautysalonapp.modules.stock.application.StockPort;
import com.beautysalonapp.settings.application.SettingService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
public class InvoiceService {

    private static final Long BRANCH = 1L;

    private final InvoiceRepository invoices;
    private final InvoiceLineRepository lineRepo;
    private final InvoicePaymentRepository paymentRepo;
    private final PartyDirectory partyDirectory;
    private final PartyLedger partyLedger;
    private final StockPort stock;
    private final FinancePort finance;
    private final ChequePort cheques;
    private final PosSlipService posSlips;
    private final LoyaltyPort loyalty;
    private final SequenceService sequences;
    private final SettingService settings;
    private final AuditService audit;

    public InvoiceService(InvoiceRepository invoices, InvoiceLineRepository lineRepo,
                          InvoicePaymentRepository paymentRepo, PartyDirectory partyDirectory,
                          PartyLedger partyLedger, StockPort stock, FinancePort finance,
                          ChequePort cheques, PosSlipService posSlips, LoyaltyPort loyalty,
                          SequenceService sequences, SettingService settings, AuditService audit) {
        this.invoices = invoices;
        this.lineRepo = lineRepo;
        this.paymentRepo = paymentRepo;
        this.partyDirectory = partyDirectory;
        this.partyLedger = partyLedger;
        this.stock = stock;
        this.finance = finance;
        this.cheques = cheques;
        this.posSlips = posSlips;
        this.loyalty = loyalty;
        this.sequences = sequences;
        this.settings = settings;
        this.audit = audit;
    }

    public record NewLine(Long itemId, boolean service, String description, BigDecimal quantity,
                          Long unitId, BigDecimal unitPrice, BigDecimal discountRate, BigDecimal vatRate) {}

    public record NewPayment(PaymentMethod method, BigDecimal amount, Long accountId,
                             String chequeNo, String chequeBank, LocalDate chequeDueDate,
                             Integer cardInstallments) {}

    public record CreateInvoiceCommand(
            InvoiceType type,
            LocalDate date,
            long partyId,
            Long warehouseId,
            String cashRegisterReceiptNo,
            String notes,
            List<NewLine> lines,
            List<NewPayment> payments) {
    }

    public Invoice create(CreateInvoiceCommand cmd) {
        partyDirectory.require(cmd.partyId());
        AccountKind accKind = cmd.type() == InvoiceType.PERAKENDE ? AccountKind.RETAIL : AccountKind.NORMAL;
        long partyAccountId = partyLedger.resolveAccount(cmd.partyId(), accKind, "TRY");

        if (cmd.lines() == null || cmd.lines().isEmpty()) {
            throw new BusinessRuleException("no_lines", "Fatura en az bir satır içermeli");
        }
        if (cmd.type() == InvoiceType.PERAKENDE
                && settings.getBoolean("invoice.retail.requireReceiptNo", false)
                && (cmd.cashRegisterReceiptNo() == null || cmd.cashRegisterReceiptNo().isBlank())) {
            throw new BusinessRuleException("receipt_no_required", "Perakende faturada yazarkasa fiş no zorunlu");
        }

        List<LineInput> calcInputs = cmd.lines().stream()
                .map(l -> new LineInput(l.quantity(), l.unitPrice(), l.discountRate(), l.vatRate()))
                .toList();
        InvoiceCalculator.Totals totals = InvoiceCalculator.totals(calcInputs);

        LocalDate date = cmd.date() == null ? LocalDate.now() : cmd.date();
        String docNo = sequences.next(BRANCH, "INVOICE_" + cmd.type().name(), seriesPrefix(cmd.type()));
        Long warehouseId = cmd.warehouseId() != null ? cmd.warehouseId() : stock.defaultWarehouseId();

        Invoice inv = new Invoice(cmd.type(), docNo, date, cmd.partyId(), partyAccountId, warehouseId, "TRY");
        inv.setCashRegisterReceiptNo(cmd.cashRegisterReceiptNo());
        inv.setNotes(cmd.notes());
        inv.setSubtotal(totals.subtotal());
        inv.setDiscountTotal(totals.discountTotal());
        inv.setVatTotal(totals.vatTotal());
        inv.setGrandTotal(totals.grandTotal());
        invoices.save(inv);

        // Satırlar + stok hareketleri
        for (int i = 0; i < cmd.lines().size(); i++) {
            NewLine l = cmd.lines().get(i);
            InvoiceCalculator.LineResult r = totals.lines().get(i);
            boolean isService = l.service() || l.itemId() == null;
            lineRepo.save(new InvoiceLine(inv.getId(), i + 1, l.itemId(), isService, l.description(),
                    l.quantity(), l.unitId(), l.unitPrice(),
                    l.discountRate() == null ? BigDecimal.ZERO : l.discountRate(),
                    l.vatRate() == null ? BigDecimal.ZERO : l.vatRate(),
                    r.net(), r.vat(), r.total()));

            if (!isService && l.itemId() != null) {
                long unitId = l.unitId() != null ? l.unitId() : stock.baseUnitId(l.itemId());
                var sc = new StockPort.StockCommand(date, l.itemId(), warehouseId, unitId,
                        l.quantity(),
                        r.net().divide(l.quantity(), 4, java.math.RoundingMode.HALF_UP), // birim maliyet (net)
                        "INVOICE", docNo, "L" + (i + 1), cmd.type() + " faturası");
                if (cmd.type().stockDirection() == com.beautysalonapp.modules.stock.domain.MovementDirection.IN) {
                    stock.receive(sc);
                } else {
                    stock.issue(sc);
                }
            }
        }

        // Cari: fatura tutarı kadar borç/alacak
        if (cmd.type().isPartyDebit()) {
            partyLedger.post(PartyLedger.LedgerEntry.debit(partyAccountId, date, "INVOICE", docNo,
                    cmd.type() + " faturası " + docNo, totals.grandTotal(), "TRY"));
        } else {
            partyLedger.post(PartyLedger.LedgerEntry.credit(partyAccountId, date, "INVOICE", docNo,
                    cmd.type() + " faturası " + docNo, totals.grandTotal(), "TRY"));
        }

        // Ödeme dağılımı
        applyPayments(inv, cmd.payments(), partyAccountId, date, docNo);

        // Sadakat puan kazanımı (satış tarafı, kart varsa; yoksa no-op)
        if (cmd.type() == InvoiceType.SATIS || cmd.type() == InvoiceType.PERAKENDE) {
            int earned = loyalty.accrueFromSale(cmd.partyId(), totals.grandTotal(), docNo);
            if (earned > 0) {
                audit.record("LOYALTY_EARN", "Invoice", docNo, earned + " sadakat puanı kazandırıldı");
            }
        }

        audit.record("INVOICE_CREATE", "Invoice", docNo,
                cmd.type() + " " + docNo + " toplam " + totals.grandTotal());
        return inv;
    }

    private void applyPayments(Invoice inv, List<NewPayment> payments, long partyAccountId,
                               LocalDate date, String docNo) {
        if (payments == null || payments.isEmpty()) {
            return;
        }
        BigDecimal paidSum = payments.stream()
                .filter(p -> p.method() != PaymentMethod.CREDIT)
                .map(NewPayment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (paidSum.compareTo(inv.getGrandTotal()) > 0) {
            throw new BusinessRuleException("overpaid", "Ödeme toplamı fatura tutarını aşamaz");
        }
        boolean salesSide = !inv.getType().isPurchaseSide();
        int idx = 0;
        for (NewPayment p : payments) {
            idx++;
            if (p.method() == PaymentMethod.CREDIT || p.amount() == null || p.amount().signum() <= 0) {
                continue;
            }
            InvoicePayment ip = new InvoicePayment(inv.getId(), p.method(), p.amount(), p.accountId());
            switch (p.method()) {
                case CASH -> {
                    long acc = p.accountId() != null ? p.accountId() : finance.defaultCashAccountId();
                    if (salesSide) {
                        finance.collect(new FinancePort.CollectCommand(date, acc, partyAccountId, null,
                                p.amount(), "TRY", "Fatura tahsilatı " + docNo, "INVOICE", docNo, "cash" + idx));
                    } else {
                        finance.pay(new FinancePort.PayCommand(date, acc, partyAccountId, null,
                                p.amount(), "TRY", "Fatura ödemesi " + docNo, "INVOICE", docNo, "cash" + idx));
                    }
                }
                case CARD -> {
                    if (p.accountId() == null) {
                        throw new BusinessRuleException("no_pos", "Kart ödemesi için POS hesabı seçin");
                    }
                    if (salesSide) {
                        finance.collect(new FinancePort.CollectCommand(date, p.accountId(), partyAccountId, null,
                                p.amount(), "TRY", "Kart tahsilatı " + docNo, "INVOICE", docNo, "card" + idx));
                    } else {
                        finance.pay(new FinancePort.PayCommand(date, p.accountId(), partyAccountId, null,
                                p.amount(), "TRY", "Kart ödemesi " + docNo, "INVOICE", docNo, "card" + idx));
                    }
                    var slip = posSlips.create(p.accountId(), date, p.amount(),
                            p.cardInstallments() == null ? 1 : p.cardInstallments(), null, docNo);
                    ip.setPosSlipId(slip.getId());
                }
                case CHEQUE -> {
                    ChequeType ct = salesSide ? ChequeType.MUSTERI_CEKI : ChequeType.FIRMA_CEKI;
                    long chequeId = cheques.register(new ChequePort.RegisterChequeCommand(
                            p.chequeNo() != null ? p.chequeNo() : docNo + "-C" + idx, ct,
                            p.chequeBank(), null,
                            p.chequeDueDate() != null ? p.chequeDueDate() : date.plusDays(30),
                            p.amount(), "TRY", partyAccountId, docNo));
                    ip.setChequeId(chequeId);
                }
                case CREDIT -> { /* atlandı */ }
            }
            paymentRepo.save(ip);
        }
    }

    public void voidInvoice(long id, String reason) {
        Invoice inv = invoices.findById(id).orElseThrow(() -> new NotFoundException("Fatura", id));
        if (inv.getStatus() == InvoiceStatus.VOIDED) {
            throw new BusinessRuleException("already_voided", "Fatura zaten iptal edilmiş");
        }
        String docNo = inv.getDocNo();

        // Ödemeler
        finance.voidByDoc("INVOICE", docNo, reason);
        for (InvoicePayment p : paymentRepo.findAllByInvoiceId(id)) {
            if (p.getChequeId() != null) {
                // Çekin cari etkisini geri al (kayıt docType 'CHEQUE', docRef = fatura no)
                partyLedger.reverse("CHEQUE", docNo, reason);
            }
        }
        // Stok
        stock.reverseByDoc("INVOICE", docNo, reason);
        // Cari (fatura ana kaydı)
        partyLedger.reverse("INVOICE", docNo, reason);

        inv.setStatus(InvoiceStatus.VOIDED);
        inv.setVoidReason(reason);
        audit.record("INVOICE_VOID", "Invoice", docNo, "Fatura iptal edildi: " + reason);
    }

    @Transactional(readOnly = true)
    public Invoice get(long id) {
        return invoices.findById(id).orElseThrow(() -> new NotFoundException("Fatura", id));
    }

    @Transactional(readOnly = true)
    public List<InvoiceLine> lines(long invoiceId) {
        return lineRepo.findAllByInvoiceIdOrderByLineNo(invoiceId);
    }

    @Transactional(readOnly = true)
    public List<InvoicePayment> payments(long invoiceId) {
        return paymentRepo.findAllByInvoiceId(invoiceId);
    }

    @Transactional(readOnly = true)
    public Page<Invoice> search(InvoiceType type, Long partyId, LocalDate from, LocalDate to, Pageable p) {
        return invoices.search(type, partyId, from, to, p);
    }

    private static String seriesPrefix(InvoiceType t) {
        return switch (t) {
            case ALIS -> "A";
            case SATIS -> "S";
            case PERAKENDE -> "P";
            case IADE_ALIS -> "IA";
            case IADE_SATIS -> "IS";
        };
    }
}
