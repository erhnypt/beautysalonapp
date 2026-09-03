package com.beautysalonapp.modules.party.application;

import com.beautysalonapp.audit.application.AuditService;
import com.beautysalonapp.core.crypto.FieldCrypto;
import com.beautysalonapp.core.error.BusinessRuleException;
import com.beautysalonapp.core.error.NotFoundException;
import com.beautysalonapp.core.sequence.SequenceService;
import com.beautysalonapp.modules.party.domain.AccountKind;
import com.beautysalonapp.modules.party.domain.Party;
import com.beautysalonapp.modules.party.domain.PartyAccount;
import com.beautysalonapp.modules.party.domain.PartyAddress;
import com.beautysalonapp.modules.party.domain.PartyNote;
import com.beautysalonapp.modules.party.domain.PartyType;
import com.beautysalonapp.modules.party.infrastructure.PartyAccountRepository;
import com.beautysalonapp.modules.party.infrastructure.PartyAddressRepository;
import com.beautysalonapp.modules.party.infrastructure.PartyNoteRepository;
import com.beautysalonapp.modules.party.infrastructure.PartyRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Transactional
public class PartyService implements PartyDirectory {

    private static final Long BRANCH = 1L;

    private final PartyRepository parties;
    private final PartyAccountRepository accounts;
    private final PartyAddressRepository addresses;
    private final PartyNoteRepository notes;
    private final SequenceService sequences;
    private final FieldCrypto crypto;
    private final AuditService audit;

    public PartyService(PartyRepository parties, PartyAccountRepository accounts,
                        PartyAddressRepository addresses, PartyNoteRepository notes,
                        SequenceService sequences, FieldCrypto crypto, AuditService audit) {
        this.parties = parties;
        this.accounts = accounts;
        this.addresses = addresses;
        this.notes = notes;
        this.sequences = sequences;
        this.crypto = crypto;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<Party> search(PartyType type, String q, Pageable pageable) {
        String phoneBi = crypto.blindIndex(q);
        String needle = (q == null || q.isBlank()) ? null : q.trim();
        return parties.search(type, needle, phoneBi, pageable);
    }

    @Transactional(readOnly = true)
    public Party get(long id) {
        return parties.findById(id).orElseThrow(() -> new NotFoundException("Cari", id));
    }

    public Party create(PartyType type, String code, String title, String firstName, String lastName,
                        String phone, String email, String taxId, String tcNo) {
        String finalCode = (code == null || code.isBlank())
                ? sequences.next(BRANCH, "PARTY_" + type.name(), prefixFor(type))
                : code.trim();
        if (parties.existsByBranchIdAndCode(BRANCH, finalCode)) {
            throw new BusinessRuleException("code_taken", "Bu cari kodu zaten kullanılıyor: " + finalCode);
        }
        Party p = new Party(type, finalCode, title.trim());
        p.setFirstName(firstName);
        p.setLastName(lastName);
        applyContact(p, phone, email, taxId, tcNo);
        parties.save(p);

        // Varsayılan NORMAL/TRY hesabı; perakende taraf ise ek olarak RETAIL hesabı
        accounts.save(new PartyAccount(p.getId(), AccountKind.NORMAL, "TRY"));
        if (type == PartyType.PERAKENDE) {
            accounts.save(new PartyAccount(p.getId(), AccountKind.RETAIL, "TRY"));
        }
        audit.record("PARTY_CREATE", "Party", p.getId(), "Cari oluşturuldu: " + p.getCode() + " " + p.getTitle());
        return p;
    }

    public Party update(long id, String title, String firstName, String lastName,
                        String phone, String email, String taxId, String tcNo,
                        Boolean smsConsent, Boolean emailConsent,
                        com.beautysalonapp.modules.party.domain.IysStatus iysStatus) {
        Party p = get(id);
        if (p.isAnonymized()) {
            throw new BusinessRuleException("anonymized", "Anonimleştirilmiş cari düzenlenemez");
        }
        if (title != null) p.setTitle(title.trim());
        p.setFirstName(firstName);
        p.setLastName(lastName);
        applyContact(p, phone, email, taxId, tcNo);
        if (smsConsent != null) p.setSmsConsent(smsConsent);
        if (emailConsent != null) p.setEmailConsent(emailConsent);
        if (iysStatus != null) {
            p.setIysStatus(iysStatus);
            p.setConsentDate(Instant.now());
        }
        audit.record("PARTY_UPDATE", "Party", id, "Cari güncellendi: " + p.getCode());
        return p;
    }

    /** KVKK unutulma hakkı (§8.3): kimlik alanları maskelenir, mali hareketler korunur. */
    public void anonymize(long id, String reason) {
        Party p = get(id);
        p.setTitle("ANONİM #" + p.getId());
        p.setFirstName(null);
        p.setLastName(null);
        p.setPhone(null);
        p.setPhoneBlindIndex(null);
        p.setEmail(null);
        p.setTcNo(null);
        p.setTaxId(null);
        p.setBirthDate(null);
        p.setWeddingAnniversary(null);
        p.setNotes(null);
        p.setSmsConsent(false);
        p.setEmailConsent(false);
        p.setIysStatus(com.beautysalonapp.modules.party.domain.IysStatus.IZINSIZ);
        p.setAnonymized(true);
        notes.findAllByPartyIdAndDeletedFalseOrderByPinnedDescIdDesc(id)
                .forEach(n -> n.setDeleted(true));
        audit.record("PARTY_ANONYMIZE", "Party", id,
                "Cari anonimleştirildi (KVKK)", "gerekçe=" + reason);
    }

    @Transactional(readOnly = true)
    public List<PartyAccount> accounts(long partyId) {
        return accounts.findAllByPartyIdAndDeletedFalse(partyId);
    }

    @Transactional(readOnly = true)
    public List<PartyAddress> addresses(long partyId) {
        return addresses.findAllByPartyIdAndDeletedFalse(partyId);
    }

    public PartyAddress addAddress(long partyId, PartyAddress addr) {
        get(partyId);
        addr.setPartyId(partyId);
        return addresses.save(addr);
    }

    @Transactional(readOnly = true)
    public List<PartyNote> notes(long partyId) {
        return notes.findAllByPartyIdAndDeletedFalseOrderByPinnedDescIdDesc(partyId);
    }

    public PartyNote addNote(long partyId, String category, String text, boolean pinned) {
        get(partyId);
        PartyNote n = new PartyNote(partyId, category, text);
        n.setPinned(pinned);
        audit.record("PARTY_NOTE_ADD", "Party", partyId, "Cari notu eklendi (" + category + ")");
        return notes.save(n);
    }

    // --- PartyDirectory portu ------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public PartyRef require(long partyId) {
        return toRef(get(partyId));
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<PartyRef> findByCode(String code) {
        return parties.findByBranchIdAndCode(BRANCH, code).map(this::toRef);
    }

    @Override
    public PartyRef createBasic(PartyType type, String title) {
        return toRef(create(type, null, title, null, null, null, null, null, null));
    }

    private PartyRef toRef(Party p) {
        return new PartyRef(p.getId(), p.getCode(), p.getType(), p.getTitle(), p.isAnonymized());
    }

    private void applyContact(Party p, String phone, String email, String taxId, String tcNo) {
        p.setPhone(emptyToNull(phone));
        p.setPhoneBlindIndex(crypto.blindIndex(emptyToNull(phone)));
        p.setEmail(emptyToNull(email));
        p.setTaxId(emptyToNull(taxId));
        p.setTcNo(emptyToNull(tcNo));
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static String prefixFor(PartyType type) {
        return switch (type) {
            case MUSTERI -> "MUS";
            case SATICI -> "SAT";
            case PERSONEL -> "PER";
            case PERAKENDE -> "PRK";
        };
    }
}
