package com.beautysalonapp.settings.domain;

import com.beautysalonapp.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Şube bazlı anahtar-değer ayar (§9.1). Değer JSON veya düz metin olabilir;
 * yorumlama çağıran modülün sorumluluğundadır.
 */
@Entity
@Table(name = "setting", uniqueConstraints =
        @UniqueConstraint(name = "uq_setting_branch_key", columnNames = {"branch_id", "setting_key"}))
public class Setting extends BaseEntity {

    @Column(name = "setting_key", nullable = false, length = 120)
    private String key;

    @Column(name = "setting_value", columnDefinition = "text")
    private String value;

    @Column(name = "description", length = 300)
    private String description;

    /** true ise arayüzde maskeli gösterilir (ör. SMTP parolası). */
    @Column(name = "secret", nullable = false)
    private boolean secret = false;

    protected Setting() {
    }

    public Setting(String key, String value, String description, boolean secret) {
        this.key = key;
        this.value = value;
        this.description = description;
        this.secret = secret;
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isSecret() { return secret; }
    public void setSecret(boolean secret) { this.secret = secret; }
}
