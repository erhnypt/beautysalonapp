package com.beautysalonapp.modules.party.domain;

import com.beautysalonapp.core.crypto.EncryptedStringConverter;
import com.beautysalonapp.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * Müşteri özel notu (§9.2). Alerji / cilt tipi gibi <b>özel nitelikli kişisel veri</b>
 * içerebileceğinden metin şifreli saklanır.
 */
@Entity
@Table(name = "party_note", indexes = @Index(name = "ix_party_note_party", columnList = "party_id"))
public class PartyNote extends BaseEntity {

    @Column(name = "party_id", nullable = false)
    private Long partyId;

    /** ALERJI | CILT_TIPI | SAGLIK | TERCIH | GENEL */
    @Column(name = "category", nullable = false, length = 20)
    private String category = "GENEL";

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "note_text", nullable = false, columnDefinition = "text")
    private String text;

    @Column(name = "pinned", nullable = false)
    private boolean pinned = false;

    protected PartyNote() {
    }

    public PartyNote(Long partyId, String category, String text) {
        this.partyId = partyId;
        this.category = category;
        this.text = text;
    }

    public Long getPartyId() { return partyId; }
    public void setPartyId(Long partyId) { this.partyId = partyId; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }
}
