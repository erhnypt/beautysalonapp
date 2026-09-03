package com.beautysalonapp.modules.appointment.infrastructure;

import com.beautysalonapp.modules.appointment.domain.Appointment;
import com.beautysalonapp.modules.appointment.domain.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    @Query("""
            select a from Appointment a
            where a.startAt < :windowEnd and a.endAt > :windowStart
              and a.status <> com.beautysalonapp.modules.appointment.domain.AppointmentStatus.IPTAL
              and (:excludeId is null or a.id <> :excludeId)
              and ( a.staffPartyId = :staffId
                    or (:resourceId is not null and a.resourceId = :resourceId) )
            """)
    List<Appointment> findPotentialConflicts(@Param("windowStart") Instant windowStart,
                                             @Param("windowEnd") Instant windowEnd,
                                             @Param("staffId") Long staffId,
                                             @Param("resourceId") Long resourceId,
                                             @Param("excludeId") Long excludeId);

    @Query("""
            select a from Appointment a
            where a.startAt < :to and a.endAt > :from
              and (:staffId is null or a.staffPartyId = :staffId)
              and (:resourceId is null or a.resourceId = :resourceId)
            order by a.startAt
            """)
    List<Appointment> calendar(@Param("from") Instant from,
                               @Param("to") Instant to,
                               @Param("staffId") Long staffId,
                               @Param("resourceId") Long resourceId);

    List<Appointment> findAllByPartyIdOrderByStartAtDesc(Long partyId);

    @Query("""
            select a from Appointment a
            where a.startAt >= :from and a.startAt < :to
              and a.status = :status
            """)
    List<Appointment> byStatusInRange(@Param("from") Instant from, @Param("to") Instant to,
                                      @Param("status") AppointmentStatus status);

    long countByPartyIdAndNoShowTrue(Long partyId);
}
