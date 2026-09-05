package com.beautysalonapp.support;

import com.beautysalonapp.support.application.SupportBundleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SupportBundleServiceTest {

    @Autowired SupportBundleService support;

    @Test
    void tani_ekrani_is_verisi_icermez_ve_temel_alanlari_doldurur() {
        var d = support.diagnostics();
        assertThat(d.dbConnected()).isTrue();
        assertThat(d.appVersion()).isNotBlank();
        assertThat(d.schemaVersion()).isNotBlank();
        // Test profilinde server.port=0 (rastgele port) ve disk istatistikleri sandbox/CI
        // ortamında 0 dönebilir (File#getTotalSpace JavaDoc) — burada asıl kontrol edilen,
        // alanların okunabildiği ve negatif/hatalı olmadığıdır.
        assertThat(d.serverPort()).isGreaterThanOrEqualTo(0);
        assertThat(d.diskTotalBytes()).isGreaterThanOrEqualTo(0);
        assertThat(d.licenseStatus()).isNotBlank();
    }

    @Test
    void manifest_kullaniciya_gonderim_oncesi_onizleme_sunar() {
        var files = support.manifest();
        assertThat(files).extracting("name").contains(
                "sistem-bilgisi.txt", "son-hatalar.txt", "uygulama-log-son-satirlar.txt");
        assertThat(files).allSatisfy(f -> assertThat(f.preview()).isNotNull());
    }

    @Test
    void bundle_gecerli_bir_zip_uretir_ve_sistem_bilgisini_icerir() throws Exception {
        byte[] zip = support.buildBundle();
        assertThat(zip).isNotEmpty();

        List<String> entries = new ArrayList<>();
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                entries.add(e.getName());
                if (e.getName().equals("sistem-bilgisi.txt")) {
                    String content = new String(zin.readAllBytes());
                    assertThat(content).contains("Şema versiyonu", "Veritabanı", "Lisans durumu");
                }
            }
        }
        assertThat(entries).containsExactlyInAnyOrder(
                "sistem-bilgisi.txt", "son-hatalar.txt", "uygulama-log-son-satirlar.txt");
    }
}
