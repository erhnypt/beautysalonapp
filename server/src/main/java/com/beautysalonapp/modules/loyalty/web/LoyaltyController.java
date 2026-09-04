package com.beautysalonapp.modules.loyalty.web;

import com.beautysalonapp.modules.loyalty.application.LoyaltyService;
import com.beautysalonapp.modules.loyalty.domain.LoyaltyCard;
import com.beautysalonapp.modules.loyalty.domain.LoyaltyEnums.CardStatus;
import com.beautysalonapp.modules.loyalty.domain.LoyaltyProgram;
import com.beautysalonapp.modules.loyalty.domain.LoyaltyTransaction;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/loyalty")
@PreAuthorize("hasAuthority('LOYALTY_VIEW')")
public class LoyaltyController {

    private final LoyaltyService service;

    public LoyaltyController(LoyaltyService service) {
        this.service = service;
    }

    // --- program ---
    public record ProgramView(long id, String name, BigDecimal earnRate, BigDecimal pointToCurrency,
                              int expiryMonths, boolean active) {
        static ProgramView of(LoyaltyProgram p) {
            return new ProgramView(p.getId(), p.getName(), p.getEarnRate(), p.getPointToCurrency(),
                    p.getExpiryMonths(), p.isActive());
        }
    }

    public record UpsertProgramRequest(Long id, @NotBlank String name, BigDecimal earnRate,
                                       BigDecimal pointToCurrency, int expiryMonths, boolean active) {}

    @GetMapping("/programs")
    public List<ProgramView> programs() {
        return service.listPrograms().stream().map(ProgramView::of).toList();
    }

    @PostMapping("/programs")
    @PreAuthorize("hasAuthority('LOYALTY_EDIT')")
    public ProgramView upsertProgram(@Valid @RequestBody UpsertProgramRequest r) {
        return ProgramView.of(service.upsertProgram(r.id(), r.name(), r.earnRate(), r.pointToCurrency(),
                r.expiryMonths(), r.active()));
    }

    // --- kartlar ---
    public record CardView(long id, String cardNo, String magneticId, long partyId, CardStatus status,
                           int pointsBalance) {
        static CardView of(LoyaltyCard c) {
            return new CardView(c.getId(), c.getCardNo(), c.getMagneticId(), c.getPartyId(), c.getStatus(),
                    c.getPointsBalance());
        }
    }

    public record IssueCardRequest(@NotNull Long partyId, String cardNo, String magneticId) {}

    public record ReportLostRequest(String newCardNo) {}

    public record RedeemRequest(@NotNull Integer points, @NotBlank String sourceRef) {}

    public record AdjustRequest(@NotNull Integer points, @NotBlank String reason) {}

    public record TxnView(long id, String type, int points, BigDecimal spendAmount, BigDecimal currencyValue,
                          String sourceRef, Instant at, java.time.LocalDate expiresAt, boolean expired) {
        static TxnView of(LoyaltyTransaction t) {
            return new TxnView(t.getId(), t.getType().name(), t.getPoints(), t.getSpendAmount(),
                    t.getCurrencyValue(), t.getSourceRef(), t.getAt(), t.getExpiresAt(), t.isExpired());
        }
    }

    @GetMapping("/cards")
    public List<CardView> cards(@RequestParam(required = false) String q) {
        return service.listCards(q).stream().map(CardView::of).toList();
    }

    @PostMapping("/cards")
    @PreAuthorize("hasAuthority('LOYALTY_ADD')")
    public CardView issue(@Valid @RequestBody IssueCardRequest r) {
        return CardView.of(service.issueCard(r.partyId(), r.cardNo(), r.magneticId()));
    }

    @GetMapping("/resolve/{key}")
    public CardView resolve(@PathVariable String key) {
        return service.resolve(key)
                .map(CardView::of)
                .orElseThrow(() -> new com.beautysalonapp.core.error.NotFoundException("Kart bulunamadı: " + key));
    }

    @GetMapping("/cards/{id}/transactions")
    public List<TxnView> transactions(@PathVariable long id) {
        return service.transactions(id).stream().map(TxnView::of).toList();
    }

    @PostMapping("/cards/{id}/report-lost")
    @PreAuthorize("hasAuthority('LOYALTY_EDIT')")
    public CardView reportLost(@PathVariable long id, @RequestBody ReportLostRequest r) {
        return CardView.of(service.reportLost(id, r.newCardNo()));
    }

    @PostMapping("/cards/{id}/redeem")
    @PreAuthorize("hasAuthority('LOYALTY_EDIT')")
    public Map<String, Object> redeem(@PathVariable long id, @Valid @RequestBody RedeemRequest r) {
        var m = service.redeem(id, r.points(), r.sourceRef());
        return Map.of("points", r.points(), "valueTry", m.getAmount());
    }

    @PostMapping("/cards/{id}/adjust")
    @PreAuthorize("hasAuthority('LOYALTY_EDIT')")
    public TxnView adjust(@PathVariable long id, @Valid @RequestBody AdjustRequest r) {
        return TxnView.of(service.adjust(id, r.points(), r.reason()));
    }

    @GetMapping("/reports/liability")
    @PreAuthorize("hasAuthority('LOYALTY_REPORT')")
    public Map<String, Object> liability() {
        return service.liabilityReport();
    }
}
