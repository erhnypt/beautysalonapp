package com.beautysalonapp.modules.invoice.infrastructure;

import com.beautysalonapp.modules.invoice.domain.InvoiceLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InvoiceLineRepository extends JpaRepository<InvoiceLine, Long> {
    List<InvoiceLine> findAllByInvoiceIdOrderByLineNo(Long invoiceId);
}
