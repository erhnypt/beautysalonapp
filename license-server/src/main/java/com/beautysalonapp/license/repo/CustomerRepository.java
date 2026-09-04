package com.beautysalonapp.license.repo;

import com.beautysalonapp.license.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
}
