package com.beautysalonapp.modules.appointment.web;

import com.beautysalonapp.modules.appointment.application.AppointmentService;
import com.beautysalonapp.modules.appointment.application.AppointmentService.BookCommand;
import com.beautysalonapp.modules.appointment.application.AppointmentService.OccupancyRow;
import com.beautysalonapp.modules.appointment.application.AppointmentService.StatusChange;
import com.beautysalonapp.modules.appointment.domain.Appointment;
import com.beautysalonapp.modules.appointment.domain.AppointmentStatus;
import com.beautysalonapp.modules.appointment.domain.Resource;
import com.beautysalonapp.modules.appointment.domain.ServiceDefinition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/appointments")
@PreAuthorize("hasAuthority('APPOINTMENT_VIEW')")
public class AppointmentController {

    private final AppointmentService service;

    public AppointmentController(AppointmentService service) {
        this.service = service;
    }

    // --- hizmet tanımları ---
    public record ServiceView(long id, String code, String name, int durationMin, int bufferBeforeMin,
                              int bufferAfterMin, BigDecimal price, boolean resourceRequired, boolean active) {
        static ServiceView of(ServiceDefinition s) {
            return new ServiceView(s.getId(), s.getCode(), s.getName(), s.getDurationMin(),
                    s.getBufferBeforeMin(), s.getBufferAfterMin(), s.getPrice(), s.isResourceRequired(), s.isActive());
        }
    }

    public record CreateServiceRequest(String code, String name, @Positive int durationMin, BigDecimal price,
                                       int bufferBeforeMin, int bufferAfterMin, boolean resourceRequired) {}

    public record AddRecipeRequest(@NotNull Long itemId, @NotNull Long unitId, @NotNull @Positive BigDecimal quantity) {}

    @GetMapping("/services")
    public List<ServiceView> services() {
        return service.listServices().stream().map(ServiceView::of).toList();
    }

    @PostMapping("/services")
    @PreAuthorize("hasAuthority('APPOINTMENT_EDIT')")
    public ServiceView createService(@Valid @RequestBody CreateServiceRequest r) {
        return ServiceView.of(service.createService(r.code(), r.name(), r.durationMin(), r.price(),
                r.bufferBeforeMin(), r.bufferAfterMin(), r.resourceRequired()));
    }

    @PostMapping("/services/{id}/recipe")
    @PreAuthorize("hasAuthority('APPOINTMENT_EDIT')")
    public void addRecipe(@PathVariable long id, @Valid @RequestBody AddRecipeRequest r) {
        service.addRecipe(id, r.itemId(), r.unitId(), r.quantity());
    }

    // --- kaynaklar ---
    public record ResourceView(long id, String code, String name, String type, boolean active) {
        static ResourceView of(Resource r) {
            return new ResourceView(r.getId(), r.getCode(), r.getName(), r.getType(), r.isActive());
        }
    }

    public record CreateResourceRequest(String code, String name, String type) {}

    @GetMapping("/resources")
    public List<ResourceView> resources() {
        return service.listResources().stream().map(ResourceView::of).toList();
    }

    @PostMapping("/resources")
    @PreAuthorize("hasAuthority('APPOINTMENT_EDIT')")
    public ResourceView createResource(@Valid @RequestBody CreateResourceRequest r) {
        return ResourceView.of(service.createResource(r.code(), r.name(), r.type()));
    }

    // --- randevular ---
    public record ApptView(long id, long partyId, long staffPartyId, Long resourceId, long serviceId,
                           Instant startAt, Instant endAt, AppointmentStatus status, String source,
                           String notes, BigDecimal priceSnapshot, boolean noShow, Long contractLineId) {
        static ApptView of(Appointment a) {
            return new ApptView(a.getId(), a.getPartyId(), a.getStaffPartyId(), a.getResourceId(),
                    a.getServiceId(), a.getStartAt(), a.getEndAt(), a.getStatus(), a.getSource(),
                    a.getNotes(), a.getPriceSnapshot(), a.isNoShow(), a.getContractLineId());
        }
    }

    public record BookRequest(@NotNull Long partyId, @NotNull Long staffPartyId, Long resourceId,
                              @NotNull Long serviceId, @NotNull Instant startAt, String source,
                              String notes, Long contractLineId) {}

    public record MoveRequest(Instant startAt, Long staffPartyId, Long resourceId) {}

    public record StatusRequest(@NotNull AppointmentStatus status, boolean collectCash, Long cashAccountId) {}

    @GetMapping
    public List<ApptView> calendar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Long staffId,
            @RequestParam(required = false) Long resourceId) {
        return service.calendar(from, to, staffId, resourceId).stream().map(ApptView::of).toList();
    }

    @GetMapping("/{id}")
    public ApptView get(@PathVariable long id) {
        return ApptView.of(service.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('APPOINTMENT_ADD')")
    public ApptView book(@Valid @RequestBody BookRequest r) {
        return ApptView.of(service.book(new BookCommand(r.partyId(), r.staffPartyId(), r.resourceId(),
                r.serviceId(), r.startAt(), r.source(), r.notes(), r.contractLineId())));
    }

    @PutMapping("/{id}/move")
    @PreAuthorize("hasAuthority('APPOINTMENT_EDIT')")
    public ApptView move(@PathVariable long id, @RequestBody MoveRequest r) {
        return ApptView.of(service.move(id, r.startAt(), r.staffPartyId(), r.resourceId()));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('APPOINTMENT_EDIT')")
    public ApptView status(@PathVariable long id, @Valid @RequestBody StatusRequest r) {
        return ApptView.of(service.changeStatus(id, new StatusChange(r.status(), r.collectCash(), r.cashAccountId())));
    }

    @GetMapping("/party/{partyId}/history")
    public List<ApptView> history(@PathVariable long partyId) {
        return service.partyHistory(partyId).stream().map(ApptView::of).toList();
    }

    @GetMapping("/reports/occupancy")
    @PreAuthorize("hasAuthority('APPOINTMENT_REPORT')")
    public List<OccupancyRow> occupancy(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return service.occupancy(from, to);
    }
}
