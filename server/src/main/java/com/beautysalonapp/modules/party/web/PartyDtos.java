package com.beautysalonapp.modules.party.web;

import com.beautysalonapp.modules.party.application.PartyLedger;
import com.beautysalonapp.modules.party.domain.AccountKind;
import com.beautysalonapp.modules.party.domain.IysStatus;
import com.beautysalonapp.modules.party.domain.Party;
import com.beautysalonapp.modules.party.domain.PartyAccount;
import com.beautysalonapp.modules.party.domain.PartyNote;
import com.beautysalonapp.modules.party.domain.PartyType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class PartyDtos {

    private PartyDtos() {
    }

    /** Liste satırı — PII maskeli (telefon son 4 hane). */
    public record PartyRow(long id, PartyType type, String code, String title,
                           String phoneMasked, boolean anonymized) {
        public static PartyRow of(Party p) {
            return new PartyRow(p.getId(), p.getType(), p.getCode(), p.getTitle(),
                    mask(p.getPhone()), p.isAnonymized());
        }
    }

    /** Detay — çözülmüş iletişim bilgileri (yalnızca PARTY_VIEW yetkisiyle döner). */
    public record PartyDetail(long id, PartyType type, String code, String title,
                              String firstName, String lastName,
                              String phone, String email, String taxId, String tcNo,
                              LocalDate birthDate, LocalDate weddingAnniversary, String gender,
                              String notes, boolean smsConsent, boolean emailConsent,
                              IysStatus iysStatus, BigDecimal riskLimit, BigDecimal defaultDiscountRate,
                              boolean anonymized) {
        public static PartyDetail of(Party p) {
            return new PartyDetail(p.getId(), p.getType(), p.getCode(), p.getTitle(),
                    p.getFirstName(), p.getLastName(), p.getPhone(), p.getEmail(), p.getTaxId(), p.getTcNo(),
                    p.getBirthDate(), p.getWeddingAnniversary(), p.getGender(), p.getNotes(),
                    p.isSmsConsent(), p.isEmailConsent(), p.getIysStatus(),
                    p.getRiskLimit(), p.getDefaultDiscountRate(), p.isAnonymized());
        }
    }

    public record CreatePartyRequest(
            @NotNull PartyType type,
            String code,
            @NotBlank String title,
            String firstName, String lastName,
            String phone, String email, String taxId, String tcNo) {
    }

    public record UpdatePartyRequest(
            String title, String firstName, String lastName,
            String phone, String email, String taxId, String tcNo,
            Boolean smsConsent, Boolean emailConsent, IysStatus iysStatus) {
    }

    public record AnonymizeRequest(@NotBlank String reason) {
    }

    public record AccountView(long id, AccountKind kind, String currency, BigDecimal openingBalance) {
        public static AccountView of(PartyAccount a) {
            return new AccountView(a.getId(), a.getKind(), a.getCurrency(), a.getOpeningBalance());
        }
    }

    public record NoteRequest(@NotBlank String category, @NotBlank String text, boolean pinned) {
    }

    public record NoteView(long id, String category, String text, boolean pinned) {
        public static NoteView of(PartyNote n) {
            return new NoteView(n.getId(), n.getCategory(), n.getText(), n.isPinned());
        }
    }

    public record ManualTxnRequest(
            @NotNull Long accountId,
            @NotNull LocalDate date,
            @NotBlank String description,
            BigDecimal debit,
            BigDecimal credit,
            String currency) {
    }

    public record StatementLine(long id, LocalDate date, String docType, String docRef,
                                String description, BigDecimal debit, BigDecimal credit,
                                BigDecimal runningBalance, String currency) {
        public static StatementLine of(PartyLedger.TransactionView v) {
            return new StatementLine(v.id(), v.date(), v.docType(), v.docRef(), v.description(),
                    v.debit(), v.credit(), v.runningBalance(), v.currency());
        }
    }

    private static String mask(String phone) {
        if (phone == null || phone.length() < 4) {
            return null;
        }
        return "••• ••• " + phone.substring(phone.length() - 4);
    }
}
