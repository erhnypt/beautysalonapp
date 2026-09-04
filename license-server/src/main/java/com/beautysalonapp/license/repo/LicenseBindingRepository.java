package com.beautysalonapp.license.repo;

import com.beautysalonapp.license.domain.LicenseBinding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LicenseBindingRepository extends JpaRepository<LicenseBinding, Long> {
    List<LicenseBinding> findAllByLicenseId(String licenseId);
    List<LicenseBinding> findAllByLicenseIdAndActiveTrue(String licenseId);
    Optional<LicenseBinding> findByLicenseIdAndFingerprint(String licenseId, String fingerprint);
    long countByLicenseIdAndActiveTrue(String licenseId);
}
