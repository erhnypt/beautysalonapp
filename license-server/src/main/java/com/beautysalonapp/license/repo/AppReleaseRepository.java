package com.beautysalonapp.license.repo;

import com.beautysalonapp.license.domain.AppRelease;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppReleaseRepository extends JpaRepository<AppRelease, Long> {
    Optional<AppRelease> findFirstByChannelOrderByReleasedAtDesc(String channel);
}
