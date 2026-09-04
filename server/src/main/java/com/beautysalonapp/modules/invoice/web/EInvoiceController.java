package com.beautysalonapp.modules.invoice.web;

import com.beautysalonapp.modules.invoice.application.EInvoiceService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * e-Fatura HAZIRLIĞI (Faz 8, docs/modules/e-fatura.md). Yalnızca UBL-TR XML üretip indirtir;
 * dışarıya hiçbir çağrı yapmaz (CLAUDE.md #1).
 */
@RestController
@RequestMapping("/api/v1/invoices/{id}/e-fatura")
@PreAuthorize("hasAuthority('INVOICE_EDIT')")
public class EInvoiceController {

    private final EInvoiceService service;

    public EInvoiceController(EInvoiceService service) {
        this.service = service;
    }

    /**
     * XML'i üretir (ilk çağrıda faturaya kalıcı UUID atar, sonraki çağrılar aynı UUID'yle
     * idempotent olarak yeniden üretir) ve dosya olarak indirtir. Bu bir mutasyon içerdiğinden
     * (UUID ataması) INVOICE_EDIT gerektirir — yalnızca görüntüleme yetkisiyle tetiklenemez.
     */
    @GetMapping(produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<byte[]> download(@PathVariable long id) {
        EInvoiceService.EInvoiceResult r = service.generate(id);
        byte[] bytes = r.xml().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .header("Content-Disposition", ContentDisposition.attachment().filename(r.filename()).build().toString())
                .header("X-Einvoice-Uuid", r.uuid())
                .body(bytes);
    }
}
