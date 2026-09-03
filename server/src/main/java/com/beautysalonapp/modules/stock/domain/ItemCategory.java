package com.beautysalonapp.modules.stock.domain;

import com.beautysalonapp.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Ürün/hizmet kategorisi (ağaç: {@code parentId}). */
@Entity
@Table(name = "item_category")
public class ItemCategory extends BaseEntity {

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    protected ItemCategory() {
    }

    public ItemCategory(Long parentId, String name) {
        this.parentId = parentId;
        this.name = name;
    }

    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
