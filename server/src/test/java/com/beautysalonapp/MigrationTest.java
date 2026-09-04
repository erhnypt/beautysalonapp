package com.beautysalonapp;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Migration testi (plan §18): tüm Flyway betikleri boş bir veritabanına temiz uygulanır,
 * sürüm boşluğu yoktur, {@code validate()} geçer ve ikinci {@code migrate()} no-op'tur.
 *
 * <p>Spring bağlamı yüklemez — saf Flyway + bellek-içi H2 (hızlı). CLAUDE.md #4:
 * var olan migration düzenlenmez; bu test yeni migration eklenince de geçerli kalır.
 */
class MigrationTest {

    private Flyway flyway(String db) {
        return Flyway.configure()
                .dataSource("jdbc:h2:mem:" + db + ";MODE=LEGACY;DB_CLOSE_DELAY=-1", "sa", "")
                .locations("classpath:db/migration/common", "classpath:db/migration/h2")
                .load();
    }

    @Test
    void tum_migrationlar_bos_dbye_temiz_uygulanir() {
        Flyway fw = flyway("mig_clean");
        MigrateResult result = fw.migrate();

        assertThat(result.migrationsExecuted)
                .as("en az V1..V11 çalışmalı")
                .isGreaterThanOrEqualTo(11);
        assertThat(result.success).isTrue();

        MigrationInfo current = fw.info().current();
        assertThat(current).isNotNull();
        assertThat(current.getState().isApplied()).isTrue();
    }

    @Test
    void surum_numaralarinda_bosluk_veya_tekrar_yok() {
        Flyway fw = flyway("mig_gaps");
        fw.migrate();

        int[] versions = Arrays.stream(fw.info().applied())
                .filter(mi -> mi.getVersion() != null)
                .mapToInt(mi -> Integer.parseInt(mi.getVersion().getVersion()))
                .sorted()
                .toArray();

        assertThat(versions).isNotEmpty();
        assertThat(versions[0]).isEqualTo(1);
        for (int i = 1; i < versions.length; i++) {
            assertThat(versions[i])
                    .as("V%d ile V%d arasında boşluk/tekrar var", versions[i - 1], versions[i])
                    .isEqualTo(versions[i - 1] + 1);
        }
    }

    @Test
    void ikinci_migrate_no_op_ve_validate_gecer() {
        Flyway fw = flyway("mig_idem");
        fw.migrate();

        MigrateResult second = fw.migrate();
        assertThat(second.migrationsExecuted).isZero();

        fw.validate(); // uyumsuzlukta istisna fırlatır
    }
}
