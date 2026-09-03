package com.beautysalonapp.audit.infrastructure;

import com.beautysalonapp.audit.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("""
            select a from AuditLog a
            where (:actor is null or a.actor = :actor)
              and (:entityType is null or a.entityType = :entityType)
              and (:from is null or a.at >= :from)
              and (:to is null or a.at <= :to)
            order by a.at desc
            """)
    Page<AuditLog> search(@Param("actor") String actor,
                          @Param("entityType") String entityType,
                          @Param("from") Instant from,
                          @Param("to") Instant to,
                          Pageable pageable);
}
