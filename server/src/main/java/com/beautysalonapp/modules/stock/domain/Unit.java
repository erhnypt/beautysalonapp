package com.beautysalonapp.modules.stock.domain;

import com.beautysalonapp.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Global birim tanımı (ADET, KOLI, ML, GR, SEANS…). */
@Entity
@Table(name = "unit", uniqueConstraints = @UniqueConstraint(name = "uq_unit_code", columnNames = {"branch_id", "code"}))
public class Unit extends BaseEntity {

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    protected Unit() {
    }

    public Unit(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
