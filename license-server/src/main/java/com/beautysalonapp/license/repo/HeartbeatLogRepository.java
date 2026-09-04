package com.beautysalonapp.license.repo;

import com.beautysalonapp.license.domain.HeartbeatLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HeartbeatLogRepository extends JpaRepository<HeartbeatLog, Long> {
    List<HeartbeatLog> findTop20ByLicenseIdOrderByReceivedAtDesc(String licenseId);
    HeartbeatLog findFirstByLicenseIdOrderByReceivedAtDesc(String licenseId);
}
