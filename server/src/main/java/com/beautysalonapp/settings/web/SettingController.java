package com.beautysalonapp.settings.web;

import com.beautysalonapp.settings.application.SettingService;
import com.beautysalonapp.settings.domain.Setting;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/settings")
@PreAuthorize("hasAuthority('SETTINGS_VIEW')")
public class SettingController {

    private final SettingService settingService;

    public SettingController(SettingService settingService) {
        this.settingService = settingService;
    }

    public record SettingView(String key, String value, String description, boolean secret) {
        static SettingView of(Setting s) {
            return new SettingView(s.getKey(), s.isSecret() ? null : s.getValue(),
                    s.getDescription(), s.isSecret());
        }
    }

    public record UpsertRequest(@NotBlank String value, String description, boolean secret) {
    }

    @GetMapping
    public List<SettingView> all() {
        return settingService.all().stream().map(SettingView::of).toList();
    }

    @PutMapping("/{key}")
    @PreAuthorize("hasAuthority('SETTINGS_EDIT')")
    public SettingView upsert(@PathVariable String key, @Valid @RequestBody UpsertRequest req) {
        return SettingView.of(settingService.put(key, req.value(), req.description(), req.secret()));
    }
}
