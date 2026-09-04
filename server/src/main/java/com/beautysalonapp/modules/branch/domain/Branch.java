package com.beautysalonapp.modules.branch.domain;

import com.beautysalonapp.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Şube kartı (§9.1, Faz 8 "merkezi işletme" şeması).
 *
 * <p><b>Kapsam notu:</b> Bu varlık şube <i>tanımlamayı</i> ve merkezi görünürlüğü sağlar.
 * Diğer modüllerin (cari, stok, fatura, randevu, sözleşme, sadakat…) yazma yollarını
 * kullanıcının seçtiği şubeye göre etiketlemesi ayrı, daha büyük bir iş — bkz.
 * {@code docs/adr/0006-merkezi-sube.md}. Bu fazda yalnızca Günlük Analiz/Raporlama
 * şube filtresini destekler.
 */
@Entity
@Table(name = "branch", uniqueConstraints = @UniqueConstraint(name = "uq_branch_code", columnNames = "code"))
public class Branch extends BaseEntity {

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "tax_id", length = 20)
    private String taxId;

    @Column(name = "address", length = 400)
    private String address;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "is_headquarters", nullable = false)
    private boolean headquarters = false;

    protected Branch() {
    }

    public Branch(String code, String title) {
        this.code = code;
        this.title = title;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getTaxId() { return taxId; }
    public void setTaxId(String taxId) { this.taxId = taxId; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public boolean isHeadquarters() { return headquarters; }
    public void setHeadquarters(boolean headquarters) { this.headquarters = headquarters; }
}
