package com.beautysalonapp.modules.appointment.infrastructure;

import com.beautysalonapp.modules.appointment.domain.ServiceRecipe;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServiceRecipeRepository extends JpaRepository<ServiceRecipe, Long> {
    List<ServiceRecipe> findAllByServiceIdAndDeletedFalse(Long serviceId);
}
