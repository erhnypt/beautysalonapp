package com.beautysalonapp.modules.party.web;

import com.beautysalonapp.modules.party.application.PartyLedger;
import com.beautysalonapp.modules.party.application.PartyService;
import com.beautysalonapp.modules.party.domain.PartyAddress;
import com.beautysalonapp.modules.party.domain.PartyType;
import com.beautysalonapp.modules.party.web.PartyDtos.AccountView;
import com.beautysalonapp.modules.party.web.PartyDtos.AnonymizeRequest;
import com.beautysalonapp.modules.party.web.PartyDtos.CreatePartyRequest;
import com.beautysalonapp.modules.party.web.PartyDtos.ManualTxnRequest;
import com.beautysalonapp.modules.party.web.PartyDtos.NoteRequest;
import com.beautysalonapp.modules.party.web.PartyDtos.NoteView;
import com.beautysalonapp.modules.party.web.PartyDtos.PartyDetail;
import com.beautysalonapp.modules.party.web.PartyDtos.PartyRow;
import com.beautysalonapp.modules.party.web.PartyDtos.StatementLine;
import com.beautysalonapp.modules.party.web.PartyDtos.UpdatePartyRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/parties")
@PreAuthorize("hasAuthority('PARTY_VIEW')")
public class PartyController {

    private final PartyService partyService;
    private final PartyLedger ledger;

    public PartyController(PartyService partyService, PartyLedger ledger) {
        this.partyService = partyService;
        this.ledger = ledger;
    }

    @GetMapping
    public Page<PartyRow> list(@RequestParam(required = false) PartyType type,
                               @RequestParam(required = false) String q,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "25") int size) {
        return partyService.search(type, q, PageRequest.of(page, Math.min(size, 200))).map(PartyRow::of);
    }

    @GetMapping("/{id}")
    public PartyDetail get(@PathVariable long id) {
        return PartyDetail.of(partyService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PARTY_ADD')")
    public PartyDetail create(@Valid @RequestBody CreatePartyRequest r) {
        return PartyDetail.of(partyService.create(r.type(), r.code(), r.title(), r.firstName(), r.lastName(),
                r.phone(), r.email(), r.taxId(), r.tcNo()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PARTY_EDIT')")
    public PartyDetail update(@PathVariable long id, @Valid @RequestBody UpdatePartyRequest r) {
        return PartyDetail.of(partyService.update(id, r.title(), r.firstName(), r.lastName(),
                r.phone(), r.email(), r.taxId(), r.tcNo(),
                r.smsConsent(), r.emailConsent(), r.iysStatus()));
    }

    @PostMapping("/{id}/anonymize")
    @PreAuthorize("hasAuthority('PARTY_DELETE')")
    public void anonymize(@PathVariable long id, @Valid @RequestBody AnonymizeRequest r) {
        partyService.anonymize(id, r.reason());
    }

    @GetMapping("/{id}/accounts")
    public List<AccountView> accounts(@PathVariable long id) {
        return partyService.accounts(id).stream().map(AccountView::of).toList();
    }

    @GetMapping("/{id}/notes")
    public List<NoteView> notes(@PathVariable long id) {
        return partyService.notes(id).stream().map(NoteView::of).toList();
    }

    @PostMapping("/{id}/notes")
    @PreAuthorize("hasAuthority('PARTY_EDIT')")
    public NoteView addNote(@PathVariable long id, @Valid @RequestBody NoteRequest r) {
        return NoteView.of(partyService.addNote(id, r.category(), r.text(), r.pinned()));
    }

    @PostMapping("/{id}/addresses")
    @PreAuthorize("hasAuthority('PARTY_EDIT')")
    public PartyAddress addAddress(@PathVariable long id, @RequestBody PartyAddress address) {
        return partyService.addAddress(id, address);
    }

    @GetMapping("/accounts/{accountId}/statement")
    public List<StatementLine> statement(
            @PathVariable long accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ledger.statement(accountId, from, to).stream().map(StatementLine::of).toList();
    }

    @GetMapping("/accounts/{accountId}/balance")
    public BalanceView balance(@PathVariable long accountId) {
        var m = ledger.balance(accountId);
        return new BalanceView(accountId, m.getAmount(), m.getCurrency());
    }

    public record BalanceView(long accountId, java.math.BigDecimal balance, String currency) {}

    @PostMapping("/transactions")
    @PreAuthorize("hasAuthority('FINANCE_ADD')")
    public void manualTransaction(@Valid @RequestBody ManualTxnRequest r) {
        ledger.post(new PartyLedger.LedgerEntry(
                r.accountId(), r.date(), "ADJUSTMENT",
                "MAN-" + System.currentTimeMillis(), null, r.description(),
                r.debit(), r.credit(), r.currency() == null ? "TRY" : r.currency()));
    }
}
