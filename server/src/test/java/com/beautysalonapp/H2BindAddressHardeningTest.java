package com.beautysalonapp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JDBC URL'i {@code AUTO_SERVER=TRUE} ile H2'nin bir TCP dinleyici açmasına yol açar.
 * {@link BeautySalonAppApplication#hardenH2BindAddress()} bu dinleyiciyi loopback'e
 * sabitlemek için {@code h2.bindAddress} sistem özelliğini ayarlar — makine dışından
 * parolasız {@code sa} ile veritabanına erişimi (uygulama/lisans/yetki katmanını atlayan
 * sızma yolu) engeller (§8.1).
 */
class H2BindAddressHardeningTest {

    private static final String PROP = BeautySalonAppApplication.H2_BIND_ADDRESS_PROPERTY;

    private String original;

    @BeforeEach
    void saveProperty() {
        original = System.getProperty(PROP);
        System.clearProperty(PROP);
    }

    @AfterEach
    void restoreProperty() {
        if (original == null) {
            System.clearProperty(PROP);
        } else {
            System.setProperty(PROP, original);
        }
    }

    @Test
    void ayarli_degilse_loopback_e_sabitlenir() {
        BeautySalonAppApplication.hardenH2BindAddress();

        assertThat(System.getProperty(PROP)).isEqualTo("127.0.0.1");
    }

    @Test
    void operator_acikca_ezmisse_dokunulmaz() {
        System.setProperty(PROP, "10.0.0.5");

        BeautySalonAppApplication.hardenH2BindAddress();

        assertThat(System.getProperty(PROP)).isEqualTo("10.0.0.5");
    }
}
