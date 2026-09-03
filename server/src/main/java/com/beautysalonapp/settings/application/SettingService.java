package com.beautysalonapp.settings.application;

import com.beautysalonapp.audit.application.AuditService;
import com.beautysalonapp.settings.domain.Setting;
import com.beautysalonapp.settings.infrastructure.SettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class SettingService {

    private static final Long DEFAULT_BRANCH = 1L;

    private final SettingRepository repository;
    private final AuditService auditService;

    public SettingService(SettingRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<Setting> all() {
        return repository.findAllByBranchIdOrderByKey(DEFAULT_BRANCH);
    }

    @Transactional(readOnly = true)
    public Optional<String> get(String key) {
        return repository.findByBranchIdAndKey(DEFAULT_BRANCH, key).map(Setting::getValue);
    }

    @Transactional(readOnly = true)
    public String getOrDefault(String key, String fallback) {
        return get(key).orElse(fallback);
    }

    @Transactional(readOnly = true)
    public boolean getBoolean(String key, boolean fallback) {
        return get(key).map(Boolean::parseBoolean).orElse(fallback);
    }

    public Setting put(String key, String value, String description, boolean secret) {
        Setting s = repository.findByBranchIdAndKey(DEFAULT_BRANCH, key).orElse(null);
        String oldValue = s == null ? null : s.getValue();
        if (s == null) {
            s = new Setting(key, value, description, secret);
            repository.save(s);
        } else {
            s.setValue(value);
            if (description != null) s.setDescription(description);
        }
        auditService.record("SETTING_CHANGE", "Setting", key,
                "Ayar güncellendi: " + key,
                secret ? "***" : ("eski=" + oldValue + " yeni=" + value));
        return s;
    }

    public Setting put(String key, String value) {
        return put(key, value, null, false);
    }
}
