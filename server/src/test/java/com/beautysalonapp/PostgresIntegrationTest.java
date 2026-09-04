package com.beautysalonapp;

import com.beautysalonapp.modules.finance.infrastructure.IncomeExpenseCardRepository;
import com.beautysalonapp.modules.stock.infrastructure.UnitRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PostgreSQL entegrasyon testi (plan §18 "Repository/entegrasyon | Testcontainers (PostgreSQL)",
 * Risk #7: "PostgreSQL'e geçiş yolu hazır olsun").
 *
 * <p>Docker gerektirir; {@code @Tag("pg")} ile normal {@code mvn test}'ten dışlıdır —
 * çalıştırma: {@code ./mvnw -Ppg test}. CI'da ayrı job.
 *
 * <p>Doğrular: (1) {@code common/} Flyway betikleri PostgreSQL 16'ya sorunsuz uygulanır,
 * (2) Hibernate {@code postgres} profili + entity eşlemeleri gerçek PG'de çalışır,
 * (3) açılış tohumlayıcıları (StockDefaults/FinanceDefaults) PG'de koşar,
 * (4) {@code NUMERIC(19,4)} para alanı ölçeğini koruyarak gidip gelir.
 */
@SpringBootTest
@ActiveProfiles("postgres")
@Testcontainers
@Tag("pg")
class PostgresIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private DataSource dataSource;

    @Autowired
    private UnitRepository units;

    @Autowired
    private IncomeExpenseCardRepository cards;

    @Test
    void flyway_semasi_postgreste_kurulur() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer applied = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success = true", Integer.class);
        assertThat(applied).as("V1..V11 + sonrası").isGreaterThanOrEqualTo(11);

        String product = jdbc.execute((java.sql.Connection c) ->
                c.getMetaData().getDatabaseProductName());
        assertThat(product).isEqualTo("PostgreSQL");
    }

    @Test
    void acilis_tohumlayicilari_postgreste_kosar() {
        assertThat(units.findByBranchIdAndCode(1L, "ADET")).isPresent();
        assertThat(cards.findByBranchIdAndCode(1L, "600.01")).isPresent();
    }

    @Test
    void numeric_19_4_para_alani_olcegi_korur() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("""
                insert into party (created_at, updated_at, party_type, code, title,
                                   sms_consent, email_consent, iys_status)
                values (now(), now(), 'MUSTERI', 'PG-TEST-1', 'PG Test', false, false, 'BILINMIYOR')
                """);
        Long partyId = jdbc.queryForObject(
                "select id from party where code = 'PG-TEST-1'", Long.class);
        jdbc.update("""
                insert into party_account (created_at, updated_at, party_id, account_kind, currency, opening_balance)
                values (now(), now(), ?, 'NORMAL', 'TRY', ?)
                """, partyId, new BigDecimal("123.4567"));

        BigDecimal back = jdbc.queryForObject(
                "select opening_balance from party_account where party_id = ?", BigDecimal.class, partyId);
        assertThat(back).isEqualByComparingTo("123.4567");
        assertThat(back.scale()).isEqualTo(4);
    }
}
