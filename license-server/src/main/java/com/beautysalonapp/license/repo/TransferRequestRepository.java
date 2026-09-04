package com.beautysalonapp.license.repo;

import com.beautysalonapp.license.domain.Enums.TransferStatus;
import com.beautysalonapp.license.domain.TransferRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface TransferRequestRepository extends JpaRepository<TransferRequest, Long> {
    List<TransferRequest> findAllByStatus(TransferStatus status);
    long countByLicenseIdAndAutoApprovedTrueAndRequestedAtAfter(String licenseId, Instant after);
    List<TransferRequest> findAllByLicenseIdOrderByRequestedAtDesc(String licenseId);
}
