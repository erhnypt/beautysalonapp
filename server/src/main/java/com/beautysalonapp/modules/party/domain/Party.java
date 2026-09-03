package com.beautysalonapp.modules.party.domain;

import com.beautysalonapp.core.crypto.EncryptedStringConverter;
import com.beautysalonapp.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Taraf ortak tabanı (§9.2): müşteri, satıcı, personel, perakende.
 * Hassas alanlar (tc_no, tax_id, phone, email) DB'de AES-256-GCM ile şifreli.
 */
@Entity
@Table(name = "party",
        uniqueConstraints = @UniqueConstraint(name = "uq_party_code", columnNames = {"branch_id", "code"}),
        indexes = {
                @Index(name = "ix_party_type", columnList = "party_type"),
                @Index(name = "ix_party_phone_bi", columnList = "phone_bi"),
                @Index(name = "ix_party_name", columnList = "title")
        })
public class Party extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "party_type", nullable = false, length = 20)
    private PartyType type;

    @Column(name = "code", nullable = false, length = 30)
    private String code;

    /** Görünen ünvan (kurumsal ad veya "Ad Soyad"). Arama ve listelerde kullanılır. */
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "tax_id", length = 200)
    private String taxId;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "tc_no", length = 200)
    private String tcNo;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "phone", length = 200)
    private String phone;

    /** Telefon için belirleyici arama anahtarı (blind index) — PII değil. */
    @Column(name = "phone_bi", length = 32)
    private String phoneBlindIndex;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "email", length = 300)
    private String email;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "wedding_anniversary")
    private LocalDate weddingAnniversary;

    @Column(name = "gender", length = 10)
    private String gender;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "sms_consent", nullable = false)
    private boolean smsConsent = false;

    @Column(name = "email_consent", nullable = false)
    private boolean emailConsent = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "iys_status", nullable = false, length = 15)
    private IysStatus iysStatus = IysStatus.BILINMIYOR;

    @Column(name = "consent_date")
    private Instant consentDate;

    @Column(name = "risk_limit", precision = 19, scale = 4)
    private BigDecimal riskLimit;

    @Column(name = "default_discount_rate", precision = 19, scale = 4, nullable = false)
    private BigDecimal defaultDiscountRate = BigDecimal.ZERO;

    @Column(name = "price_list_id")
    private Long priceListId;

    /** KVKK "unutulma hakkı" — kimlik alanları maskelendi mi? Mali hareketler korunur. */
    @Column(name = "anonymized", nullable = false)
    private boolean anonymized = false;

    protected Party() {
    }

    public Party(PartyType type, String code, String title) {
        this.type = type;
        this.code = code;
        this.title = title;
    }

    public boolean canReceiveCommercialMessage() {
        return !anonymized && iysStatus == IysStatus.IZINLI;
    }

    public PartyType getType() { return type; }
    public void setType(PartyType type) { this.type = type; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getTaxId() { return taxId; }
    public void setTaxId(String taxId) { this.taxId = taxId; }
    public String getTcNo() { return tcNo; }
    public void setTcNo(String tcNo) { this.tcNo = tcNo; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getPhoneBlindIndex() { return phoneBlindIndex; }
    public void setPhoneBlindIndex(String phoneBlindIndex) { this.phoneBlindIndex = phoneBlindIndex; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }
    public LocalDate getWeddingAnniversary() { return weddingAnniversary; }
    public void setWeddingAnniversary(LocalDate weddingAnniversary) { this.weddingAnniversary = weddingAnniversary; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public boolean isSmsConsent() { return smsConsent; }
    public void setSmsConsent(boolean smsConsent) { this.smsConsent = smsConsent; }
    public boolean isEmailConsent() { return emailConsent; }
    public void setEmailConsent(boolean emailConsent) { this.emailConsent = emailConsent; }
    public IysStatus getIysStatus() { return iysStatus; }
    public void setIysStatus(IysStatus iysStatus) { this.iysStatus = iysStatus; }
    public Instant getConsentDate() { return consentDate; }
    public void setConsentDate(Instant consentDate) { this.consentDate = consentDate; }
    public BigDecimal getRiskLimit() { return riskLimit; }
    public void setRiskLimit(BigDecimal riskLimit) { this.riskLimit = riskLimit; }
    public BigDecimal getDefaultDiscountRate() { return defaultDiscountRate; }
    public void setDefaultDiscountRate(BigDecimal r) { this.defaultDiscountRate = r; }
    public Long getPriceListId() { return priceListId; }
    public void setPriceListId(Long priceListId) { this.priceListId = priceListId; }
    public boolean isAnonymized() { return anonymized; }
    public void setAnonymized(boolean anonymized) { this.anonymized = anonymized; }
}
