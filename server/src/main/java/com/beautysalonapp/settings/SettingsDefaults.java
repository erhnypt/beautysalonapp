package com.beautysalonapp.settings;

import com.beautysalonapp.config.AppProperties;
import com.beautysalonapp.settings.application.SettingService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * İlk açılışta, salon sahibinin Ayarlar ekranından düzenleyebileceği işletim
 * ayarlarını tohumlar (yalnızca yoksa). {@code application.yml}'deki değer
 * ilk değer olarak kullanılır; bundan sonra kaynak-doğrusu DB'deki Setting'tir
 * (bkz. {@link com.beautysalonapp.modules.notification.NotificationTriggers},
 * {@link com.beautysalonapp.backup.application.BackupService}).
 */
@Component
@Order(45)
public class SettingsDefaults implements ApplicationRunner {

    private final SettingService settings;
    private final AppProperties props;

    public SettingsDefaults(SettingService settings, AppProperties props) {
        this.settings = settings;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedIfMissing("notification.managerEmail", props.getNotification().getManagerEmail(),
                "Gün sonu raporu ve sistem uyarılarının gideceği yönetici e-postası");
        seedIfMissing("backup.secondaryDir", props.getBackup().getSecondaryDir(),
                "Yedek ikincil hedefi — USB veya ağ klasörü yolu (fidye yazılımına karşı en az bir "
                        + "hedef çıkarılabilir/ağ dışı olmalı; boş bırakılırsa yalnızca birincil klasöre yazılır)");
    }

    private void seedIfMissing(String key, String initialValue, String description) {
        if (settings.get(key).isEmpty()) {
            settings.put(key, initialValue == null ? "" : initialValue, description, false);
        }
    }
}
