package com.beautysalonapp.modules.branch.web;

import com.beautysalonapp.modules.branch.application.BranchService;
import com.beautysalonapp.modules.branch.domain.Branch;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Şube tanımlama (§9.1, Faz 8 "merkezi işletme" şeması). Kapsam: docs/adr/0006-merkezi-sube.md. */
@RestController
@RequestMapping("/api/v1/branches")
@PreAuthorize("hasAuthority('SETTINGS_VIEW')")
public class BranchController {

    private final BranchService service;

    public BranchController(BranchService service) {
        this.service = service;
    }

    public record BranchView(long id, String code, String title, String taxId, String address,
                             String phone, boolean headquarters) {
        static BranchView of(Branch b) {
            return new BranchView(b.getId(), b.getCode(), b.getTitle(), b.getTaxId(),
                    b.getAddress(), b.getPhone(), b.isHeadquarters());
        }
    }

    public record UpsertRequest(String code, @NotBlank String title, String taxId, String address, String phone) {
    }

    @GetMapping
    public List<BranchView> list() {
        return service.list().stream().map(BranchView::of).toList();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SETTINGS_EDIT')")
    public BranchView create(@Valid @RequestBody UpsertRequest r) {
        return BranchView.of(service.create(r.code(), r.title(), r.taxId(), r.address(), r.phone()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SETTINGS_EDIT')")
    public BranchView update(@PathVariable long id, @Valid @RequestBody UpsertRequest r) {
        return BranchView.of(service.update(id, r.title(), r.taxId(), r.address(), r.phone()));
    }

    @PostMapping("/{id}/headquarters")
    @PreAuthorize("hasAuthority('SETTINGS_EDIT')")
    public void setHeadquarters(@PathVariable long id) {
        service.setHeadquarters(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SETTINGS_EDIT')")
    public void delete(@PathVariable long id) {
        service.delete(id);
    }
}
