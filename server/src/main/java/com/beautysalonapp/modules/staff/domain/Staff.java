package com.beautysalonapp.modules.staff.domain;

import com.beautysalonapp.core.crypto.EncryptedStringConverter;
import com.beautysalonapp.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Personel kartı (§9.4). {@code party_id} bir PERSONEL taraf kaydına bağlıdır. */
@Entity
@Table(name = "staff", uniqueConstraints = @UniqueConstraint(name = "uq_staff_party", columnNames = "party_id"))
public class Staff extends BaseEntity {

    @Column(name = "party_id", nullable = false)
    private Long partyId;

    @Column(name = "title", length = 100)
    private String title;

    @Column(name = "hire_date")
    private LocalDate hireDate;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "iban", length = 200)
    private String iban;

    @Column(name = "staff_class_id")
    private Long staffClassId;

    @Column(name = "default_service_rate", precision = 7, scale = 4)
    private BigDecimal defaultServiceRate;

    @Column(name = "default_product_rate", precision = 7, scale = 4)
    private BigDecimal defaultProductRate;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected Staff() {
    }

    public Staff(Long partyId, String title) {
        this.partyId = partyId;
        this.title = title;
    }

    public Long getPartyId() { return partyId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public LocalDate getHireDate() { return hireDate; }
    public void setHireDate(LocalDate hireDate) { this.hireDate = hireDate; }
    public String getIban() { return iban; }
    public void setIban(String iban) { this.iban = iban; }
    public Long getStaffClassId() { return staffClassId; }
    public void setStaffClassId(Long staffClassId) { this.staffClassId = staffClassId; }
    public BigDecimal getDefaultServiceRate() { return defaultServiceRate; }
    public void setDefaultServiceRate(BigDecimal v) { this.defaultServiceRate = v; }
    public BigDecimal getDefaultProductRate() { return defaultProductRate; }
    public void setDefaultProductRate(BigDecimal v) { this.defaultProductRate = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
