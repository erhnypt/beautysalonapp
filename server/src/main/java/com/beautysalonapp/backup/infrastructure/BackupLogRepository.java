package com.beautysalonapp.backup.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BackupLogRepository extends JpaRepository<BackupLog, Long> {

    Optional<BackupLog> findFirstByKindAndStatusOrderByStartedAtDesc(String kind, String status);

    List<BackupLog> findTop50ByOrderByStartedAtDesc();
}
