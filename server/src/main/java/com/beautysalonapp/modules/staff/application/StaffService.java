package com.beautysalonapp.modules.staff.application;

import com.beautysalonapp.audit.application.AuditService;
import com.beautysalonapp.core.error.BusinessRuleException;
import com.beautysalonapp.core.error.NotFoundException;
import com.beautysalonapp.modules.finance.application.FinancePort;
import com.beautysalonapp.modules.party.application.PartyDirectory;
import com.beautysalonapp.modules.party.application.PartyLedger;
import com.beautysalonapp.modules.party.domain.AccountKind;
import com.beautysalonapp.modules.party.domain.PartyType;
import com.beautysalonapp.modules.staff.domain.CommissionBasis;
import com.beautysalonapp.modules.staff.domain.CommissionCalc;
import com.beautysalonapp.modules.staff.domain.CommissionRule;
import com.beautysalonapp.modules.staff.domain.CommissionScope;
import com.beautysalonapp.modules.staff.domain.CommissionStatus;
import com.beautysalonapp.modules.staff.domain.Staff;
import com.beautysalonapp.modules.staff.domain.StaffAdvance;
import com.beautysalonapp.modules.staff.domain.StaffClass;
import com.beautysalonapp.modules.staff.domain.StaffCommission;
import com.beautysalonapp.modules.staff.infrastructure.CommissionRuleRepository;
import com.beautysalonapp.modules.staff.infrastructure.StaffAdvanceRepository;
import com.beautysalonapp.modules.staff.infrastructure.StaffClassRepository;
import com.beautysalonapp.modules.staff.infrastructure.StaffCommissionRepository;
import com.beautysalonapp.modules.staff.infrastructure.StaffRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class StaffService implements CommissionPort {

    private static final Logger log = LoggerFactory.getLogger(StaffService.class);
    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyy-MM");

    private final StaffRepository staff;
    private final StaffClassRepository classes;
    private final CommissionRuleRepository rules;
    private final StaffCommissionRepository commissions;
    private final StaffAdvanceRepository advances;
    private final PartyDirectory partyDirectory;
    private final PartyLedger partyLedger;
    private final FinancePort finance;
    private final AuditService audit;

    public StaffService(StaffRepository staff, StaffClassRepository classes, CommissionRuleRepository rules,
                        StaffCommissionRepository commissions, StaffAdvanceRepository advances,
                        PartyDirectory partyDirectory, PartyLedger partyLedger, FinancePort finance,
                        AuditService audit) {
        this.staff = staff;
        this.classes = classes;
        this.rules = rules;
        this.commissions = commissions;
        this.advances = advances;
        this.partyDirectory = partyDirectory;
        this.partyLedger = partyLedger;
        this.finance = finance;
        this.audit = audit;
    }

    // --- kartlar / sınıflar / kurallar ------------------------------

    @Transactional(readOnly = true)
    public List<Staff> list() {
        return staff.findAllByDeletedFalseOrderByTitle();
    }

    @Transactional(readOnly = true)
    public Staff get(long id) {
        return staff.findById(id).orElseThrow(() -> new NotFoundException("Personel", id));
    }

    /** Var olan PERSONEL tarafına personel kartı bağlar (yoksa taraf da oluşturur). */
    public Staff createStaff(Long partyId, String title, LocalDate hireDate, Long staffClassId,
                             BigDecimal serviceRate, BigDecimal productRate) {
        long pid;
        if (partyId != null) {
            var ref = partyDirectory.require(partyId);
            if (ref.type() != PartyType.PERSONEL) {
                throw new BusinessRuleException("not_staff_party", "Taraf PERSONEL değil: " + ref.title());
            }
            pid = partyId;
        } else {
            pid = partyDirectory.createBasic(PartyType.PERSONEL, title).id();
        }
        if (staff.findByPartyId(pid).isPresent()) {
            throw new BusinessRuleException("staff_exists", "Bu kişi için personel kartı zaten var");
        }
        Staff s = new Staff(pid, title);
        s.setHireDate(hireDate);
        s.setStaffClassId(staffClassId);
        s.setDefaultServiceRate(serviceRate);
        s.setDefaultProductRate(productRate);
        staff.save(s);
        audit.record("STAFF_CREATE", "Staff", s.getId(), "Personel kartı: " + title);
        return s;
    }

    @Transactional(readOnly = true)
    public List<StaffClass> listClasses() {
        return classes.findAllByDeletedFalseOrderByName();
    }

    public StaffClass createClass(String name, String description, BigDecimal serviceRate, BigDecimal productRate) {
        StaffClass c = new StaffClass(name);
        c.setDescription(description);
        c.setServiceRate(serviceRate);
        c.setProductRate(productRate);
        return classes.save(c);
    }

    @Transactional(readOnly = true)
    public List<CommissionRule> listRules() {
        return rules.findAllByDeletedFalseOrderByScope();
    }

    public CommissionRule createRule(CommissionScope scope, CommissionBasis basis, BigDecimal value,
                                     Long staffId, Long staffClassId, BigDecimal minRevenue) {
        CommissionRule r = new CommissionRule(scope, basis, value);
        r.setStaffId(staffId);
        r.setStaffClassId(staffClassId);
        r.setMinRevenue(minRevenue);
        return rules.save(r);
    }

    // --- prim tahakkuk (CommissionPort) ----------------------------

    @Override
    public void accrue(AccrueCommand c) {
        Optional<Staff> maybe = staff.findByPartyId(c.staffPartyId());
        if (maybe.isEmpty()) {
            log.debug("Prim atlandı: {} için personel kartı yok", c.staffPartyId());
            return;
        }
        Staff s = maybe.get();
        if (commissions.existsByStaffIdAndSourceTypeAndSourceRef(s.getId(), c.sourceType(), c.sourceRef())) {
            return; // idempotent
        }

        var resolved = resolveRule(s, c.scope());
        if (resolved.isEmpty()) {
            log.debug("Prim kuralı yok: staff={} scope={}", s.getId(), c.scope());
            return;
        }
        var rr = resolved.get();
        BigDecimal base = c.baseAmount() == null ? BigDecimal.ZERO : c.baseAmount();
        if (rr.getScope() == CommissionScope.REVENUE && rr.getMinRevenue() != null) {
            base = base.subtract(rr.getMinRevenue()).max(BigDecimal.ZERO);
        }
        BigDecimal amount = CommissionCalc.amount(rr.getBasis(), rr.getValue(), base);
        if (amount.signum() <= 0) {
            return;
        }

        String periodYm = (c.onDate() == null ? LocalDate.now() : c.onDate()).format(YM);
        BigDecimal rateForRecord = rr.getBasis() == CommissionBasis.RATE ? rr.getValue() : null;
        commissions.save(new StaffCommission(s.getId(), periodYm, c.sourceType(), c.sourceRef(),
                base, rateForRecord, amount));

        long acc = partyLedger.resolveAccount(s.getPartyId(), AccountKind.NORMAL, "TRY");
        partyLedger.post(PartyLedger.LedgerEntry.credit(acc, c.onDate() == null ? LocalDate.now() : c.onDate(),
                "COMMISSION", c.sourceType() + ":" + c.sourceRef(),
                "Prim tahakkuku (" + c.scope() + ")", amount, "TRY"));
        audit.record("COMMISSION_ACCRUE", "StaffCommission", s.getId(),
                c.scope() + " primi " + amount + " (kaynak " + c.sourceType() + ":" + c.sourceRef() + ")");
    }

    /** Özgüllük: personel özel > sınıf > genel. */
    private Optional<CommissionRule> resolveRule(Staff s, CommissionScope scope) {
        List<CommissionRule> all = rules.findAllByScopeAndActiveTrueAndDeletedFalse(scope);
        Optional<CommissionRule> best = all.stream()
                .filter(r -> matches(r, s))
                .max(Comparator.comparingInt(CommissionRule::specificity));
        if (best.isPresent()) {
            return best;
        }
        // Kural yoksa personel varsayılan oranından sentetik kural
        BigDecimal fallbackRate = switch (scope) {
            case SERVICE -> firstNonNull(s.getDefaultServiceRate(), classRate(s, true));
            case PRODUCT -> firstNonNull(s.getDefaultProductRate(), classRate(s, false));
            case REVENUE -> null;
        };
        if (fallbackRate != null && fallbackRate.signum() > 0) {
            CommissionRule synthetic = new CommissionRule(scope, CommissionBasis.RATE, fallbackRate);
            return Optional.of(synthetic);
        }
        return Optional.empty();
    }

    private boolean matches(CommissionRule r, Staff s) {
        if (r.getStaffId() != null) {
            return r.getStaffId().equals(s.getId());
        }
        if (r.getStaffClassId() != null) {
            return r.getStaffClassId().equals(s.getStaffClassId());
        }
        return true; // genel
    }

    private BigDecimal classRate(Staff s, boolean service) {
        if (s.getStaffClassId() == null) {
            return null;
        }
        return classes.findById(s.getStaffClassId())
                .map(c -> service ? c.getServiceRate() : c.getProductRate())
                .orElse(null);
    }

    private static BigDecimal firstNonNull(BigDecimal a, BigDecimal b) {
        return a != null ? a : b;
    }

    // --- prim ödeme / avans ---------------------------------------

    public BigDecimal payCommissions(long staffId, String periodYm, Long cashAccountId) {
        Staff s = get(staffId);
        List<StaffCommission> pending = commissions.findAllByStaffIdAndPeriodYmAndStatus(
                staffId, periodYm, CommissionStatus.TAHAKKUK);
        BigDecimal total = pending.stream().map(StaffCommission::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        long acc = partyLedger.resolveAccount(s.getPartyId(), AccountKind.NORMAL, "TRY");
        long cashAcc = cashAccountId != null ? cashAccountId : finance.defaultCashAccountId();
        finance.pay(new FinancePort.PayCommand(LocalDate.now(), cashAcc, acc, null, total, "TRY",
                "Prim ödemesi " + periodYm, "COMMISSION_PAY", "COMPAY-" + staffId + "-" + periodYm, "sum"));
        pending.forEach(pc -> { pc.markPaid(); commissions.save(pc); });
        audit.record("COMMISSION_PAY", "Staff", staffId, periodYm + " primi ödendi: " + total);
        return total;
    }

    public StaffAdvance giveAdvance(long staffId, BigDecimal amount, Long accountId) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessRuleException("bad_amount", "Avans tutarı pozitif olmalı");
        }
        Staff s = get(staffId);
        long acc = partyLedger.resolveAccount(s.getPartyId(), AccountKind.NORMAL, "TRY");
        long cashAcc = accountId != null ? accountId : finance.defaultCashAccountId();
        String docNo = "ADV-" + staffId + "-" + System.currentTimeMillis();
        finance.pay(new FinancePort.PayCommand(LocalDate.now(), cashAcc, acc, null, amount, "TRY",
                "Personel avansı", "ADVANCE", docNo, "adv"));
        StaffAdvance adv = new StaffAdvance(staffId, LocalDate.now(), amount, cashAcc);
        adv.setDocNo(docNo);
        advances.save(adv);
        audit.record("STAFF_ADVANCE", "Staff", staffId, "Avans verildi: " + amount);
        return adv;
    }

    @Transactional(readOnly = true)
    public List<StaffCommission> commissions(long staffId, String periodYm) {
        return commissions.findAllByStaffIdAndPeriodYmOrderById(staffId, periodYm);
    }

    @Transactional(readOnly = true)
    public List<StaffAdvance> advances(long staffId) {
        return advances.findAllByStaffIdOrderByDateDesc(staffId);
    }

    @Transactional(readOnly = true)
    public List<StaffCommissionRepository.PerfRow> performance(java.time.Instant from, java.time.Instant to) {
        return commissions.performance(from, to);
    }
}
