package com.beautysalonapp.license.repo;

import com.beautysalonapp.license.domain.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    List<Subscription> findAllByCustomerId(Long customerId);
}
