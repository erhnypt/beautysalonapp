package com.beautysalonapp.security.infrastructure;

import com.beautysalonapp.security.domain.Role;
import com.beautysalonapp.security.domain.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(RoleName name);

    boolean existsByName(RoleName name);
}
