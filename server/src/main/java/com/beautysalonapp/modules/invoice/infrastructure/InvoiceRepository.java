package com.beautysalonapp.modules.invoice.infrastructure;

import com.beautysalonapp.modules.invoice.domain.Invoice;
import com.beautysalonapp.modules.invoice.domain.InvoiceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    @Query("""
            select i from Invoice i
            where i.deleted = false
              and (:type is null or i.type = :type)
              and (:partyId is null or i.partyId = :partyId)
              and (:from is null or i.date >= :from)
              and (:to is null or i.date <= :to)
            order by i.date desc, i.id desc
            """)
    Page<Invoice> search(@Param("type") InvoiceType type,
                         @Param("partyId") Long partyId,
                         @Param("from") LocalDate from,
                         @Param("to") LocalDate to,
                         Pageable pageable);
}
