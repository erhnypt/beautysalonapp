package com.beautysalonapp.license;

import com.beautysalonapp.license.crypto.Ed25519KeyProvider;
import com.beautysalonapp.license.domain.Customer;
import com.beautysalonapp.license.domain.Enums.Plan;
import com.beautysalonapp.license.domain.License;
import com.beautysalonapp.license.repo.CustomerRepository;
import com.beautysalonapp.license.repo.SubscriptionRepository;
import com.beautysalonapp.license.service.HeartbeatService;
import com.beautysalonapp.license.service.HeartbeatService.HeartbeatRequest;
import com.beautysalonapp.license.service.LicenseService;
import com.beautysalonapp.license.service.LicenseService.ProvisionCommand;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "beautysalonapp.license.key-file=./target/test-ed25519.key")
class LicenseFlowTest {

    @Autowired LicenseService licenseService;
    @Autowired HeartbeatService heartbeatService;
    @Autowired Ed25519KeyProvider keys;
    @Autowired CustomerRepository customers;
    @Autowired SubscriptionRepository subscriptions;

    private License provisioned() {
        Customer c = new Customer();
        c.setName("Güzellik Merkezi " + System.nanoTime());
        c.setTaxId("1234567890");
        customers.save(c);
        return licenseService.provision(new ProvisionCommand(c.getId(), Plan.PRO,
                "STOCK,PARTY,FINANCE,APPOINTMENT", 3, 1, 10, null, 7, false,
                new BigDecimal("500"), 1));
    }

    private boolean verify(String licenseFile) {
        int dot = licenseFile.indexOf('.');
        byte[] payload = b64(licenseFile.substring(0, dot));
        byte[] sig = b64(licenseFile.substring(dot + 1));
        byte[] pub = Base64.getDecoder().decode(keys.publicKeyBase64());
        Ed25519Signer v = new Ed25519Signer();
        v.init(false, new Ed25519PublicKeyParameters(pub, 0));
        v.update(payload, 0, payload.length);
        return v.verifySignature(sig);
    }

    private static byte[] b64(String s) {
        return Base64.getDecoder().decode(s.replace('-', '+').replace('_', '/'));
    }

    @Test
    void aktivasyon_imzali_lisans_uretir() {
        License lic = provisioned();
        String file = licenseService.activate(lic.getActivationKey(), "FP-AAA", 2);
        assertThat(file).contains(".");
        assertThat(verify(file)).isTrue();
        assertThat(new String(b64(file.substring(0, file.indexOf('.')))))
                .contains(lic.getLicenseId()).contains("\"plan\":\"PRO\"").contains("APPOINTMENT");
    }

    @Test
    void gecersiz_anahtar_reddedilir() {
        assertThatThrownBy(() -> licenseService.activate("BSA-XXXX-YYYY-ZZZZ-0000", "FP", 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void terminal_siniri_asilinca_aktivasyon_reddedilir() {
        License lic = provisioned(); // maxTerminals 3
        licenseService.activate(lic.getActivationKey(), "FP-1", 2);
        licenseService.activate(lic.getActivationKey(), "FP-2", 2);
        licenseService.activate(lic.getActivationKey(), "FP-3", 2);
        assertThatThrownBy(() -> licenseService.activate(lic.getActivationKey(), "FP-4", 2))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void heartbeat_aktif_abonelikte_yeniler_askidayken_suspended_doner() {
        License lic = provisioned();
        licenseService.activate(lic.getActivationKey(), "FP-HB", 2);

        var r1 = heartbeatService.process(new HeartbeatRequest(lic.getLicenseId(), "FP-HB",
                "1.0.0", "Windows 11", null, "nonce-1"), "127.0.0.1");
        assertThat(r1.status()).isEqualTo("ACTIVE");
        assertThat(r1.nonce()).isEqualTo("nonce-1");
        assertThat(verify(r1.license())).isTrue();

        licenseService.suspend(lic.getSubscriptionId());
        var r2 = heartbeatService.process(new HeartbeatRequest(lic.getLicenseId(), "FP-HB",
                "1.0.0", "Windows 11", null, "nonce-2"), "127.0.0.1");
        assertThat(r2.status()).isEqualTo("SUSPENDED");

        licenseService.recordPayment(lic.getSubscriptionId(), new BigDecimal("500"), 1, "test");
        var r3 = heartbeatService.process(new HeartbeatRequest(lic.getLicenseId(), "FP-HB",
                "1.0.0", "Windows 11", null, "nonce-3"), "127.0.0.1");
        assertThat(r3.status()).isEqualTo("ACTIVE");
    }

    @Test
    void iptal_edilen_abonelik_revoked_doner() {
        License lic = provisioned();
        licenseService.activate(lic.getActivationKey(), "FP-C", 2);
        licenseService.cancel(lic.getSubscriptionId());
        var r = heartbeatService.process(new HeartbeatRequest(lic.getLicenseId(), "FP-C",
                "1.0.0", "macOS", null, "n"), "127.0.0.1");
        assertThat(r.status()).isEqualTo("REVOKED");
    }

    @Test
    void transfer_ilk_talep_otomatik_ikincisi_manuel() {
        License lic = provisioned();
        licenseService.activate(lic.getActivationKey(), "FP-OLD", 2);

        var t1 = licenseService.requestTransfer(lic.getLicenseId(), "FP-OLD", "FP-NEW");
        assertThat(t1.autoApproved()).isTrue();
        assertThat(verify(t1.licenseFileOrNull())).isTrue();

        var t2 = licenseService.requestTransfer(lic.getLicenseId(), "FP-NEW", "FP-NEWER");
        assertThat(t2.autoApproved()).isFalse();
        assertThat(t2.pendingRequestId()).isNotNull();

        String file = licenseService.approveTransfer(t2.pendingRequestId(), "admin");
        assertThat(verify(file)).isTrue();
    }
}
