package com.beautysalonapp.modules.party.domain;

import com.beautysalonapp.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "party_address", indexes = @Index(name = "ix_party_address_party", columnList = "party_id"))
public class PartyAddress extends BaseEntity {

    @Column(name = "party_id", nullable = false)
    private Long partyId;

    @Column(name = "label", length = 60)
    private String label;

    @Column(name = "address", length = 400, nullable = false)
    private String address;

    @Column(name = "city", length = 60)
    private String city;

    @Column(name = "district", length = 60)
    private String district;

    @Column(name = "postcode", length = 12)
    private String postcode;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    protected PartyAddress() {
    }

    public PartyAddress(Long partyId, String address) {
        this.partyId = partyId;
        this.address = address;
    }

    public Long getPartyId() { return partyId; }
    public void setPartyId(Long partyId) { this.partyId = partyId; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public String getPostcode() { return postcode; }
    public void setPostcode(String postcode) { this.postcode = postcode; }
    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean aDefault) { isDefault = aDefault; }
}
