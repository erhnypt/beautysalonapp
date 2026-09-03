package com.beautysalonapp.modules.invoice.web;

import com.beautysalonapp.modules.invoice.application.InvoiceService;
import com.beautysalonapp.modules.invoice.application.InvoiceService.CreateInvoiceCommand;
import com.beautysalonapp.modules.invoice.application.InvoiceService.NewLine;
import com.beautysalonapp.modules.invoice.application.InvoiceService.NewPayment;
import com.beautysalonapp.modules.invoice.domain.Invoice;
import com.beautysalonapp.modules.invoice.domain.InvoiceLine;
import com.beautysalonapp.modules.invoice.domain.InvoicePayment;
import com.beautysalonapp.modules.invoice.domain.InvoiceStatus;
import com.beautysalonapp.modules.invoice.domain.InvoiceType;
import com.beautysalonapp.modules.invoice.domain.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
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

@RestController
@RequestMapping("/api/v1/invoices")
@PreAuthorize("hasAuthority('INVOICE_VIEW')")
public class InvoiceController {

    private final InvoiceService service;

    public InvoiceController(InvoiceService service) {
        this.service = service;
    }

    public record LineDto(Long itemId, boolean service, String description, @NotNull BigDecimal quantity,
                          Long unitId, @NotNull BigDecimal unitPrice, BigDecimal discountRate, BigDecimal vatRate) {}

    public record PaymentDto(@NotNull PaymentMethod method, @NotNull BigDecimal amount, Long accountId,
                             String chequeNo, String chequeBank, LocalDate chequeDueDate, Integer cardInstallments) {}

    public record CreateInvoiceRequest(
            @NotNull InvoiceType type,
            LocalDate date,
            @NotNull Long partyId,
            Long warehouseId,
            String cashRegisterReceiptNo,
            String notes,
            @NotEmpty List<LineDto> lines,
            List<PaymentDto> payments) {
    }

    public record VoidRequest(String reason) {}

    public record InvoiceView(long id, InvoiceType type, String docNo, LocalDate date, long partyId,
                              Long warehouseId, BigDecimal subtotal, BigDecimal discountTotal,
                              BigDecimal vatTotal, BigDecimal grandTotal, InvoiceStatus status,
                              String cashRegisterReceiptNo) {
        static InvoiceView of(Invoice i) {
            return new InvoiceView(i.getId(), i.getType(), i.getDocNo(), i.getDate(), i.getPartyId(),
                    i.getWarehouseId(), i.getSubtotal(), i.getDiscountTotal(), i.getVatTotal(),
                    i.getGrandTotal(), i.getStatus(), i.getCashRegisterReceiptNo());
        }
    }

    public record LineView(int lineNo, Long itemId, boolean service, String description, BigDecimal quantity,
                           BigDecimal unitPrice, BigDecimal discountRate, BigDecimal vatRate,
                           BigDecimal lineNet, BigDecimal lineVat, BigDecimal lineTotal) {
        static LineView of(InvoiceLine l) {
            return new LineView(l.getLineNo(), l.getItemId(), l.isService(), l.getDescription(),
                    l.getQuantity(), l.getUnitPrice(), l.getDiscountRate(), l.getVatRate(),
                    l.getLineNet(), l.getLineVat(), l.getLineTotal());
        }
    }

    public record PaymentView(PaymentMethod method, BigDecimal amount, Long accountId, Long chequeId, Long posSlipId) {
        static PaymentView of(InvoicePayment p) {
            return new PaymentView(p.getMethod(), p.getAmount(), p.getAccountId(), p.getChequeId(), p.getPosSlipId());
        }
    }

    public record InvoiceDetail(InvoiceView invoice, List<LineView> lines, List<PaymentView> payments) {}

    @GetMapping
    public Page<InvoiceView> list(@RequestParam(required = false) InvoiceType type,
                                  @RequestParam(required = false) Long partyId,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "25") int size) {
        return service.search(type, partyId, from, to, PageRequest.of(page, Math.min(size, 200)))
                .map(InvoiceView::of);
    }

    @GetMapping("/{id}")
    public InvoiceDetail get(@PathVariable long id) {
        Invoice inv = service.get(id);
        return new InvoiceDetail(InvoiceView.of(inv),
                service.lines(id).stream().map(LineView::of).toList(),
                service.payments(id).stream().map(PaymentView::of).toList());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('INVOICE_ADD')")
    public InvoiceDetail create(@Valid @RequestBody CreateInvoiceRequest r) {
        var cmd = new CreateInvoiceCommand(r.type(), r.date(), r.partyId(), r.warehouseId(),
                r.cashRegisterReceiptNo(), r.notes(),
                r.lines().stream().map(l -> new NewLine(l.itemId(), l.service(), l.description(),
                        l.quantity(), l.unitId(), l.unitPrice(), l.discountRate(), l.vatRate())).toList(),
                r.payments() == null ? List.of() : r.payments().stream().map(p -> new NewPayment(p.method(),
                        p.amount(), p.accountId(), p.chequeNo(), p.chequeBank(), p.chequeDueDate(),
                        p.cardInstallments())).toList());
        return get(service.create(cmd).getId());
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("hasAuthority('INVOICE_VOID')")
    public InvoiceDetail voidInvoice(@PathVariable long id, @RequestBody VoidRequest r) {
        service.voidInvoice(id, r.reason());
        return get(id);
    }
}
