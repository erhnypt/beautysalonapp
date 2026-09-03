package com.beautysalonapp.modules.staff.web;

import com.beautysalonapp.modules.staff.application.StaffService;
import com.beautysalonapp.modules.staff.domain.CommissionBasis;
import com.beautysalonapp.modules.staff.domain.CommissionRule;
import com.beautysalonapp.modules.staff.domain.CommissionScope;
import com.beautysalonapp.modules.staff.domain.CommissionStatus;
import com.beautysalonapp.modules.staff.domain.Staff;
import com.beautysalonapp.modules.staff.domain.StaffAdvance;
import com.beautysalonapp.modules.staff.domain.StaffClass;
import com.beautysalonapp.modules.staff.domain.StaffCommission;
import com.beautysalonapp.modules.staff.infrastructure.StaffCommissionRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
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
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/staff")
@PreAuthorize("hasAuthority('STAFF_VIEW')")
public class StaffController {

    private final StaffService service;

    public StaffController(StaffService service) {
        this.service = service;
    }

    public record StaffView(long id, long partyId, String title, LocalDate hireDate, Long staffClassId,
                            BigDecimal defaultServiceRate, BigDecimal defaultProductRate, boolean active) {
        static StaffView of(Staff s) {
            return new StaffView(s.getId(), s.getPartyId(), s.getTitle(), s.getHireDate(), s.getStaffClassId(),
                    s.getDefaultServiceRate(), s.getDefaultProductRate(), s.isActive());
        }
    }

    public record CreateStaffRequest(Long partyId, String title, LocalDate hireDate, Long staffClassId,
                                     BigDecimal defaultServiceRate, BigDecimal defaultProductRate) {}

    public record ClassView(long id, String name, String description, BigDecimal serviceRate, BigDecimal productRate) {
        static ClassView of(StaffClass c) {
            return new ClassView(c.getId(), c.getName(), c.getDescription(), c.getServiceRate(), c.getProductRate());
        }
    }

    public record CreateClassRequest(String name, String description, BigDecimal serviceRate, BigDecimal productRate) {}

    public record RuleView(long id, CommissionScope scope, CommissionBasis basis, BigDecimal value,
                           Long staffId, Long staffClassId, BigDecimal minRevenue, boolean active) {
        static RuleView of(CommissionRule r) {
            return new RuleView(r.getId(), r.getScope(), r.getBasis(), r.getValue(), r.getStaffId(),
                    r.getStaffClassId(), r.getMinRevenue(), r.isActive());
        }
    }

    public record CreateRuleRequest(@NotNull CommissionScope scope, @NotNull CommissionBasis basis,
                                    @NotNull BigDecimal value, Long staffId, Long staffClassId, BigDecimal minRevenue) {}

    public record CommissionView(long id, String periodYm, String sourceType, String sourceRef,
                                 BigDecimal baseAmount, BigDecimal rate, BigDecimal amount, CommissionStatus status) {
        static CommissionView of(StaffCommission c) {
            return new CommissionView(c.getId(), c.getPeriodYm(), c.getSourceType(), c.getSourceRef(),
                    c.getBaseAmount(), c.getRate(), c.getAmount(), c.getStatus());
        }
    }

    public record AdvanceView(long id, LocalDate date, BigDecimal amount, Long accountId) {
        static AdvanceView of(StaffAdvance a) {
            return new AdvanceView(a.getId(), a.getDate(), a.getAmount(), a.getAccountId());
        }
    }

    public record PayCommissionsRequest(@NotNull String period, Long cashAccountId) {}

    public record AdvanceRequest(@NotNull @Positive BigDecimal amount, Long accountId) {}

    @GetMapping
    public List<StaffView> list() {
        return service.list().stream().map(StaffView::of).toList();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('STAFF_ADD')")
    public StaffView create(@Valid @RequestBody CreateStaffRequest r) {
        return StaffView.of(service.createStaff(r.partyId(), r.title(), r.hireDate(), r.staffClassId(),
                r.defaultServiceRate(), r.defaultProductRate()));
    }

    @GetMapping("/classes")
    public List<ClassView> classes() {
        return service.listClasses().stream().map(ClassView::of).toList();
    }

    @PostMapping("/classes")
    @PreAuthorize("hasAuthority('STAFF_EDIT')")
    public ClassView createClass(@Valid @RequestBody CreateClassRequest r) {
        return ClassView.of(service.createClass(r.name(), r.description(), r.serviceRate(), r.productRate()));
    }

    @GetMapping("/commission-rules")
    public List<RuleView> rules() {
        return service.listRules().stream().map(RuleView::of).toList();
    }

    @PostMapping("/commission-rules")
    @PreAuthorize("hasAuthority('STAFF_EDIT')")
    public RuleView createRule(@Valid @RequestBody CreateRuleRequest r) {
        return RuleView.of(service.createRule(r.scope(), r.basis(), r.value(), r.staffId(),
                r.staffClassId(), r.minRevenue()));
    }

    @GetMapping("/{id}/commissions")
    public List<CommissionView> commissions(@PathVariable long id,
                                            @RequestParam String period) {
        return service.commissions(id, period).stream().map(CommissionView::of).toList();
    }

    @PostMapping("/{id}/commissions/pay")
    @PreAuthorize("hasAuthority('STAFF_EDIT')")
    public BigDecimal payCommissions(@PathVariable long id, @Valid @RequestBody PayCommissionsRequest r) {
        return service.payCommissions(id, r.period(), r.cashAccountId());
    }

    @GetMapping("/{id}/advances")
    public List<AdvanceView> advances(@PathVariable long id) {
        return service.advances(id).stream().map(AdvanceView::of).toList();
    }

    @PostMapping("/{id}/advances")
    @PreAuthorize("hasAuthority('STAFF_EDIT')")
    public AdvanceView giveAdvance(@PathVariable long id, @Valid @RequestBody AdvanceRequest r) {
        return AdvanceView.of(service.giveAdvance(id, r.amount(), r.accountId()));
    }

    @GetMapping("/reports/performance")
    @PreAuthorize("hasAuthority('STAFF_REPORT')")
    public List<StaffCommissionRepository.PerfRow> performance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return service.performance(from, to);
    }
}
