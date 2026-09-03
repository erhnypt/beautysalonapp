package com.beautysalonapp.modules.appointment.application;

import com.beautysalonapp.audit.application.AuditService;
import com.beautysalonapp.core.error.BusinessRuleException;
import com.beautysalonapp.core.error.NotFoundException;
import com.beautysalonapp.modules.appointment.domain.Appointment;
import com.beautysalonapp.modules.appointment.domain.AppointmentStatus;
import com.beautysalonapp.modules.appointment.domain.Resource;
import com.beautysalonapp.modules.appointment.domain.ServiceDefinition;
import com.beautysalonapp.modules.appointment.domain.ServiceRecipe;
import com.beautysalonapp.modules.appointment.domain.StaffShift;
import com.beautysalonapp.modules.appointment.domain.TimeSlot;
import com.beautysalonapp.modules.appointment.infrastructure.AppointmentRepository;
import com.beautysalonapp.modules.appointment.infrastructure.ResourceRepository;
import com.beautysalonapp.modules.appointment.infrastructure.ServiceDefinitionRepository;
import com.beautysalonapp.modules.appointment.infrastructure.ServiceRecipeRepository;
import com.beautysalonapp.modules.appointment.infrastructure.StaffShiftRepository;
import com.beautysalonapp.modules.contract.application.SessionConsumptionPort;
import com.beautysalonapp.modules.finance.application.FinancePort;
import com.beautysalonapp.modules.party.application.PartyDirectory;
import com.beautysalonapp.modules.party.application.PartyLedger;
import com.beautysalonapp.modules.party.domain.AccountKind;
import com.beautysalonapp.modules.party.domain.PartyType;
import com.beautysalonapp.modules.staff.application.CommissionPort;
import com.beautysalonapp.modules.staff.domain.CommissionScope;
import com.beautysalonapp.modules.stock.application.StockPort;
import com.beautysalonapp.settings.application.SettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
@Transactional
public class AppointmentService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentService.class);
    private static final Long BRANCH = 1L;

    private final ServiceDefinitionRepository services;
    private final ServiceRecipeRepository recipes;
    private final ResourceRepository resources;
    private final StaffShiftRepository shifts;
    private final AppointmentRepository appointments;
    private final PartyDirectory partyDirectory;
    private final PartyLedger partyLedger;
    private final StockPort stock;
    private final FinancePort finance;
    private final SessionConsumptionPort sessions;
    private final CommissionPort commissions;
    private final SettingService settings;
    private final AuditService audit;

    public AppointmentService(ServiceDefinitionRepository services, ServiceRecipeRepository recipes,
                              ResourceRepository resources, StaffShiftRepository shifts,
                              AppointmentRepository appointments, PartyDirectory partyDirectory,
                              PartyLedger partyLedger, StockPort stock, FinancePort finance,
                              SessionConsumptionPort sessions, CommissionPort commissions,
                              SettingService settings, AuditService audit) {
        this.services = services;
        this.recipes = recipes;
        this.resources = resources;
        this.shifts = shifts;
        this.appointments = appointments;
        this.partyDirectory = partyDirectory;
        this.partyLedger = partyLedger;
        this.stock = stock;
        this.finance = finance;
        this.sessions = sessions;
        this.commissions = commissions;
        this.settings = settings;
        this.audit = audit;
    }

    // --- hizmet tanımları / reçete ------------------------------------

    @Transactional(readOnly = true)
    public List<ServiceDefinition> listServices() {
        return services.findAllByDeletedFalseOrderByName();
    }

    public ServiceDefinition createService(String code, String name, int durationMin, BigDecimal price,
                                           int bufferBefore, int bufferAfter, boolean resourceRequired) {
        if (services.findByBranchIdAndCode(BRANCH, code).isPresent()) {
            throw new BusinessRuleException("service_exists", "Bu hizmet kodu zaten var: " + code);
        }
        ServiceDefinition s = new ServiceDefinition(code.trim(), name.trim(), durationMin,
                price == null ? BigDecimal.ZERO : price);
        s.setBufferBeforeMin(bufferBefore);
        s.setBufferAfterMin(bufferAfter);
        s.setResourceRequired(resourceRequired);
        return services.save(s);
    }

    public ServiceRecipe addRecipe(long serviceId, long itemId, long unitId, BigDecimal quantity) {
        getService(serviceId);
        if (quantity == null || quantity.signum() <= 0) {
            throw new BusinessRuleException("bad_qty", "Reçete miktarı pozitif olmalı");
        }
        return recipes.save(new ServiceRecipe(serviceId, itemId, unitId, quantity));
    }

    @Transactional(readOnly = true)
    public ServiceDefinition getService(long id) {
        return services.findById(id).orElseThrow(() -> new NotFoundException("Hizmet", id));
    }

    // --- kaynaklar / vardiya ----------------------------------------

    @Transactional(readOnly = true)
    public List<Resource> listResources() {
        return resources.findAllByDeletedFalseOrderByCode();
    }

    public Resource createResource(String code, String name, String type) {
        if (resources.findByBranchIdAndCode(BRANCH, code).isPresent()) {
            throw new BusinessRuleException("resource_exists", "Bu kaynak kodu zaten var: " + code);
        }
        return resources.save(new Resource(code.trim().toUpperCase(), name.trim(),
                type == null ? "ODA" : type.toUpperCase()));
    }

    public StaffShift addShift(long staffPartyId, LocalDate date, LocalTime start, LocalTime end, String type) {
        requireStaff(staffPartyId);
        return shifts.save(new StaffShift(staffPartyId, date, start, end, type == null ? "WORK" : type.toUpperCase()));
    }

    @Transactional(readOnly = true)
    public List<StaffShift> shifts(LocalDate from, LocalDate to) {
        return shifts.findAllByDateBetween(from, to);
    }

    // --- randevu ---------------------------------------------------

    public record BookCommand(long partyId, long staffPartyId, Long resourceId, long serviceId,
                              Instant startAt, String source, String notes, Long contractLineId) {}

    public Appointment book(BookCommand c) {
        partyDirectory.require(c.partyId());
        requireStaff(c.staffPartyId());
        ServiceDefinition svc = getService(c.serviceId());

        if (svc.isResourceRequired() && c.resourceId() == null) {
            throw new BusinessRuleException("resource_required", "Bu hizmet için bir kaynak seçilmelidir");
        }
        Instant end = c.startAt().plusSeconds(svc.getDurationMin() * 60L);
        checkConflicts(new TimeSlot(c.startAt(), end), svc, c.staffPartyId(), c.resourceId(), null);

        Appointment appt = new Appointment(c.partyId(), c.staffPartyId(), c.resourceId(), c.serviceId(),
                c.startAt(), end, svc.getPrice());
        appt.setSource(c.source() == null ? "YERINDE" : c.source().toUpperCase());
        appt.setNotes(c.notes());
        appt.setContractLineId(c.contractLineId());
        appointments.save(appt);
        audit.record("APPT_BOOK", "Appointment", appt.getId(),
                "Randevu: hizmet " + svc.getName() + " @ " + c.startAt());
        return appt;
    }

    /** Sürükle-bırak taşıma: yeni başlangıç / personel / kaynak. */
    public Appointment move(long id, Instant newStart, Long newStaffId, Long newResourceId) {
        Appointment appt = get(id);
        if (appt.getStatus().isTerminal()) {
            throw new BusinessRuleException("terminal", "Tamamlanmış randevu taşınamaz");
        }
        ServiceDefinition svc = getService(appt.getServiceId());
        Instant start = newStart != null ? newStart : appt.getStartAt();
        Long staffId = newStaffId != null ? newStaffId : appt.getStaffPartyId();
        Long resourceId = newResourceId != null ? newResourceId : appt.getResourceId();
        if (newStaffId != null) requireStaff(newStaffId);

        Instant end = start.plusSeconds(svc.getDurationMin() * 60L);
        checkConflicts(new TimeSlot(start, end), svc, staffId, resourceId, id);

        appt.setStartAt(start);
        appt.setEndAt(end);
        appt.setStaffPartyId(staffId);
        appt.setResourceId(resourceId);
        audit.record("APPT_MOVE", "Appointment", id, "Randevu taşındı → " + start);
        return appt;
    }

    public record StatusChange(AppointmentStatus status, boolean collectCash, Long cashAccountId) {}

    public Appointment changeStatus(long id, StatusChange sc) {
        Appointment appt = get(id);
        if (appt.getStatus() == sc.status()) {
            return appt;
        }
        if (!appt.getStatus().canTransitionTo(sc.status())) {
            throw new BusinessRuleException("bad_transition",
                    "Geçersiz durum geçişi: " + appt.getStatus() + " → " + sc.status());
        }
        appt.setStatus(sc.status());
        switch (sc.status()) {
            case GELDI -> runArrivalChain(appt, sc.collectCash(), sc.cashAccountId());
            case GELMEDI -> {
                appt.setNoShow(true);
                audit.record("APPT_NO_SHOW", "Appointment", id, "Müşteri gelmedi");
            }
            case IPTAL -> audit.record("APPT_CANCEL", "Appointment", id, "Randevu iptal edildi");
            default -> { /* PLANLANDI/ONAYLANDI: bilgi amaçlı */ }
        }
        return appt;
    }

    /**
     * {@code GELDI} zinciri (§10.10): stok sarfı + seans düşümü / hizmet bedeli cariye + tahsilat.
     * Idempotent: {@code chainDone}.
     */
    private void runArrivalChain(Appointment appt, boolean collectCash, Long cashAccountId) {
        if (appt.isChainDone()) {
            return;
        }
        ServiceDefinition svc = getService(appt.getServiceId());

        // 1) Sarf reçetesi (ayar ile kapatılabilir)
        boolean autoConsume = settings.getBoolean("appointment.autoConsumeRecipe", true);
        if (autoConsume) {
            long sarfWh = stock.consumptionWarehouseId();
            for (ServiceRecipe r : recipes.findAllByServiceIdAndDeletedFalse(svc.getId())) {
                stock.issue(new StockPort.StockCommand(LocalDate.now(), r.getItemId(), sarfWh,
                        r.getUnitId(), r.getQuantity(), null, "APPOINTMENT",
                        "APPT-" + appt.getId(), "r" + r.getId(), "Randevu sarfı: " + svc.getName()));
            }
        }

        // 2) Paketten geliyorsa seans düş; değilse hizmet bedelini cariye işle
        if (appt.getContractLineId() != null) {
            sessions.consumeSession(appt.getContractLineId());
        } else if (appt.getPriceSnapshot().signum() > 0) {
            long partyAccountId = partyLedger.resolveAccount(appt.getPartyId(), AccountKind.NORMAL, "TRY");
            partyLedger.post(PartyLedger.LedgerEntry.debit(partyAccountId, LocalDate.now(),
                    "APPOINTMENT", "APPT-" + appt.getId(), "Hizmet: " + svc.getName(),
                    appt.getPriceSnapshot(), "TRY"));
            if (collectCash) {
                long cashAcc = cashAccountId != null ? cashAccountId : finance.defaultCashAccountId();
                finance.collect(new FinancePort.CollectCommand(LocalDate.now(), cashAcc, partyAccountId,
                        svc.getIncomeCardId(), appt.getPriceSnapshot(), "TRY",
                        "Randevu tahsilatı: " + svc.getName(), "APPOINTMENT", "APPT-" + appt.getId(), "pay"));
            }
        }

        // 3) Prim tahakkuku (hizmet primi) — sadakat puanı Faz 6'da bağlanacak
        BigDecimal commissionBase = svc.getPrice() != null && svc.getPrice().signum() > 0
                ? svc.getPrice() : appt.getPriceSnapshot();
        commissions.accrue(new CommissionPort.AccrueCommand(appt.getStaffPartyId(),
                CommissionScope.SERVICE, commissionBase, "APPOINTMENT", "APPT-" + appt.getId(),
                LocalDate.now()));

        appt.setArrivedAt(Instant.now());
        appt.setChainDone(true);
        audit.record("APPT_ARRIVED", "Appointment", appt.getId(),
                "GELDI zinciri tamamlandı: " + svc.getName());
    }

    private void checkConflicts(TimeSlot slot, ServiceDefinition svc, Long staffId, Long resourceId, Long excludeId) {
        TimeSlot buffered = slot.withBuffers(svc.getBufferBeforeMin(), svc.getBufferAfterMin());
        List<Appointment> candidates = appointments.findPotentialConflicts(
                buffered.start(), buffered.end(), staffId, resourceId, excludeId);
        for (Appointment other : candidates) {
            ServiceDefinition otherSvc = services.findById(other.getServiceId()).orElse(null);
            TimeSlot otherBuffered = otherSvc == null ? other.slot()
                    : other.slot().withBuffers(otherSvc.getBufferBeforeMin(), otherSvc.getBufferAfterMin());
            if (buffered.overlaps(otherBuffered)) {
                boolean sameStaff = other.getStaffPartyId().equals(staffId);
                boolean sameResource = resourceId != null && resourceId.equals(other.getResourceId());
                throw new BusinessRuleException("appt_conflict",
                        (sameStaff ? "Personel" : "Kaynak") + " bu saatte dolu (çakışan randevu #" + other.getId() + ")");
            }
        }
    }

    @Transactional(readOnly = true)
    public Appointment get(long id) {
        return appointments.findById(id).orElseThrow(() -> new NotFoundException("Randevu", id));
    }

    @Transactional(readOnly = true)
    public List<Appointment> calendar(Instant from, Instant to, Long staffId, Long resourceId) {
        return appointments.calendar(from, to, staffId, resourceId);
    }

    @Transactional(readOnly = true)
    public List<Appointment> partyHistory(long partyId) {
        return appointments.findAllByPartyIdOrderByStartAtDesc(partyId);
    }

    @Transactional(readOnly = true)
    public long noShowCount(long partyId) {
        return appointments.countByPartyIdAndNoShowTrue(partyId);
    }

    public record OccupancyRow(long staffPartyId, long total, long arrived, long noShow, long cancelled) {}

    @Transactional(readOnly = true)
    public List<OccupancyRow> occupancy(Instant from, Instant to) {
        var all = appointments.calendar(from, to, null, null);
        java.util.Map<Long, long[]> agg = new java.util.HashMap<>();
        for (Appointment a : all) {
            long[] row = agg.computeIfAbsent(a.getStaffPartyId(), k -> new long[4]);
            row[0]++;
            switch (a.getStatus()) {
                case GELDI -> row[1]++;
                case GELMEDI -> row[2]++;
                case IPTAL -> row[3]++;
                default -> { }
            }
        }
        return agg.entrySet().stream()
                .map(e -> new OccupancyRow(e.getKey(), e.getValue()[0], e.getValue()[1],
                        e.getValue()[2], e.getValue()[3]))
                .toList();
    }

    private void requireStaff(long staffPartyId) {
        var ref = partyDirectory.require(staffPartyId);
        if (ref.type() != PartyType.PERSONEL) {
            throw new BusinessRuleException("not_staff", "Seçilen kişi personel değil: " + ref.title());
        }
    }
}
