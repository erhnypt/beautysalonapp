package com.beautysalonapp.license.repo;

import com.beautysalonapp.license.domain.License;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LicenseRepository extends JpaRepository<License, Long> {
    Optional<License> findByLicenseId(String licenseId);
    Optional<License> findByActivationKey(String activationKey);
    List<License> findAllByCustomerId(Long customerId);
    List<License> findAllBySubscriptionId(Long subscriptionId);
    boolean existsByLicenseId(String licenseId);
}
