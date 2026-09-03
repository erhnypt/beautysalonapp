package com.beautysalonapp.modules.appointment.domain;

import com.beautysalonapp.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** Hizmet başına tüketilen stok (§9.7) → {@code GELDI}'de otomatik sarf. Miktar base unit. */
@Entity
@Table(name = "service_recipe", indexes = @Index(name = "ix_service_recipe_service", columnList = "service_id"))
public class ServiceRecipe extends BaseEntity {

    @Column(name = "service_id", nullable = false)
    private Long serviceId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    @Column(name = "quantity", precision = 19, scale = 6, nullable = false)
    private BigDecimal quantity;

    protected ServiceRecipe() {
    }

    public ServiceRecipe(Long serviceId, Long itemId, Long unitId, BigDecimal quantity) {
        this.serviceId = serviceId;
        this.itemId = itemId;
        this.unitId = unitId;
        this.quantity = quantity;
    }

    public Long getServiceId() { return serviceId; }
    public Long getItemId() { return itemId; }
    public Long getUnitId() { return unitId; }
    public BigDecimal getQuantity() { return quantity; }
}
