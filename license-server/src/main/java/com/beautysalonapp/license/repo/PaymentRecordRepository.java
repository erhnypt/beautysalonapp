package com.beautysalonapp.license.repo;

import com.beautysalonapp.license.domain.PaymentRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, Long> {
    List<PaymentRecord> findAllBySubscriptionIdOrderByPaidAtDesc(Long subscriptionId);
}
