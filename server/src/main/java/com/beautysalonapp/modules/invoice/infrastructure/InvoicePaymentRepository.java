package com.beautysalonapp.modules.invoice.infrastructure;

import com.beautysalonapp.modules.invoice.domain.InvoicePayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InvoicePaymentRepository extends JpaRepository<InvoicePayment, Long> {
    List<InvoicePayment> findAllByInvoiceId(Long invoiceId);
}
