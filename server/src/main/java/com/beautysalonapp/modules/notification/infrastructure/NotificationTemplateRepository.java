package com.beautysalonapp.modules.notification.infrastructure;

import com.beautysalonapp.modules.notification.domain.NotificationChannel;
import com.beautysalonapp.modules.notification.domain.NotificationTemplate;
import com.beautysalonapp.modules.notification.domain.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Long> {
    Optional<NotificationTemplate> findByTypeAndChannelAndActiveTrueAndDeletedFalse(
            NotificationType type, NotificationChannel channel);
    Optional<NotificationTemplate> findByBranchIdAndTypeAndChannel(
            Long branchId, NotificationType type, NotificationChannel channel);
    List<NotificationTemplate> findAllByDeletedFalseOrderByTypeAscChannelAsc();
}
