package com.beautysalonapp.modules.notification.infrastructure;

import com.beautysalonapp.modules.notification.domain.NotificationQueueItem;
import com.beautysalonapp.modules.notification.domain.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface NotificationQueueRepository extends JpaRepository<NotificationQueueItem, Long> {

    boolean existsByDedupKey(String dedupKey);

    @Query("""
            select q from NotificationQueueItem q
            where q.status = com.beautysalonapp.modules.notification.domain.NotificationStatus.PENDING
              and (q.nextAttemptAt is null or q.nextAttemptAt <= :now)
            order by q.scheduledAt asc
            """)
    List<NotificationQueueItem> dueNow(@Param("now") Instant now, Pageable pageable);

    Page<NotificationQueueItem> findAllByStatusOrderByScheduledAtDesc(NotificationStatus status, Pageable pageable);

    Page<NotificationQueueItem> findAllByOrderByScheduledAtDesc(Pageable pageable);

    long countByStatus(NotificationStatus status);
}
