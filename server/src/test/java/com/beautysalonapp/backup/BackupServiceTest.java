package com.beautysalonapp.backup;

import com.beautysalonapp.backup.application.BackupService;
import com.beautysalonapp.modules.party.application.PartyService;
import com.beautysalonapp.modules.party.domain.PartyType;
import com.beautysalonapp.modules.party.infrastructure.PartyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

/** Restore testi paylaşılan bellek-içi H2'yi baştan kurduğundan sınıf sonunda context tazelenir. */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BackupServiceTest {

    @Autowired BackupService backup;
    @Autowired PartyService partyService;
    @Autowired PartyRepository parties;

    @Test
    void yedek_alinir_ve_dogrulanir() {
        var log = backup.createBackup("MANUAL", "test");
        assertThat(log.getStatus()).isEqualTo("OK");
        assertThat(log.getFilePath()).endsWith(".bsa");

        var files = backup.listBackups();
        assertThat(files).isNotEmpty();

        var verify = backup.verify(files.get(0).name());
        assertThat(verify.ok()).as("verify message: %s", verify.message()).isTrue();
        assertThat(verify.tablesRestored()).isGreaterThan(10);
        assertThat(verify.rowsSample()).isGreaterThanOrEqualTo(1); // en az bootstrap admin
    }

    @Test
    void geri_yukleme_yedek_anindaki_veriye_doner() {
        String marker = "GERI-YUKLEME-" + System.nanoTime();
        partyService.create(PartyType.MUSTERI, null, marker + "-A", null, null, null, null, null, null);
        long before = parties.count();

        var log = backup.createBackup("MANUAL", "test");
        byte[] bytes = backup.readBackup(
                log.getFilePath().substring(log.getFilePath().replace('\\', '/').lastIndexOf('/') + 1));

        // Yedekten sonra yeni kayıt
        partyService.create(PartyType.MUSTERI, null, marker + "-B", null, null, null, null, null, null);
        assertThat(parties.count()).isEqualTo(before + 1);

        backup.restore(bytes, "test");

        // Yedek anındaki sayıya dönmeli; B kaydı gitmeli
        assertThat(parties.count()).isEqualTo(before);
    }
}
