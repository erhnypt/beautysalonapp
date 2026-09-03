package com.beautysalonapp.settings.infrastructure;

import com.beautysalonapp.settings.domain.Setting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SettingRepository extends JpaRepository<Setting, Long> {

    Optional<Setting> findByBranchIdAndKey(Long branchId, String key);

    List<Setting> findAllByBranchIdOrderByKey(Long branchId);
}
