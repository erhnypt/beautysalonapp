package com.beautysalonapp.modules.staff.domain;

import com.beautysalonapp.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

@Entity
@Table(name = "staff_class", uniqueConstraints =
        @UniqueConstraint(name = "uq_staff_class_name", columnNames = {"branch_id", "name"}))
public class StaffClass extends BaseEntity {

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "description", length = 200)
    private String description;

    @Column(name = "service_rate", precision = 7, scale = 4)
    private BigDecimal serviceRate;

    @Column(name = "product_rate", precision = 7, scale = 4)
    private BigDecimal productRate;

    protected StaffClass() {
    }

    public StaffClass(String name) {
        this.name = name;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getServiceRate() { return serviceRate; }
    public void setServiceRate(BigDecimal serviceRate) { this.serviceRate = serviceRate; }
    public BigDecimal getProductRate() { return productRate; }
    public void setProductRate(BigDecimal productRate) { this.productRate = productRate; }
}
