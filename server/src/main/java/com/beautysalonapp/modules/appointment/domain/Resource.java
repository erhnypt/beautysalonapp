package com.beautysalonapp.modules.appointment.domain;

import com.beautysalonapp.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Oda / koltuk / cihaz (§9.7). */
@Entity
@Table(name = "resource", uniqueConstraints =
        @UniqueConstraint(name = "uq_resource_code", columnNames = {"branch_id", "code"}))
public class Resource extends BaseEntity {

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** ODA | KOLTUK | CIHAZ */
    @Column(name = "res_type", nullable = false, length = 12)
    private String type = "ODA";

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected Resource() {
    }

    public Resource(String code, String name, String type) {
        this.code = code;
        this.name = name;
        this.type = type;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
