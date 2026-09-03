package com.beautysalonapp.modules.stock.domain;

import com.beautysalonapp.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "warehouse", uniqueConstraints =
        @UniqueConstraint(name = "uq_warehouse_code", columnNames = {"branch_id", "code"}))
public class Warehouse extends BaseEntity {

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "wh_type", nullable = false, length = 15)
    private WarehouseType type = WarehouseType.WAREHOUSE;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    protected Warehouse() {
    }

    public Warehouse(String code, String name, WarehouseType type) {
        this.code = code;
        this.name = name;
        this.type = type;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public WarehouseType getType() { return type; }
    public void setType(WarehouseType type) { this.type = type; }
    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean aDefault) { isDefault = aDefault; }
}
