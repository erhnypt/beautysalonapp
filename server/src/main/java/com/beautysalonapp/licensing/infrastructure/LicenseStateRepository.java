package com.beautysalonapp.licensing.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LicenseStateRepository extends JpaRepository<LicenseState, Long> {

    default LicenseState singleton() {
        return findById(1L).orElseGet(() -> save(new LicenseState()));
    }
}
