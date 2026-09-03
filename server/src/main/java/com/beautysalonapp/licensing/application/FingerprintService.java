package com.beautysalonapp.licensing.application;

import com.beautysalonapp.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Makine parmak izi (§6.3). Üç bileşen: makine UUID, anakart/seri, ilk kurulum GUID.
 * "3'ten 2 eşleşme" kuralı sunucu tarafında uygulanır; istemci bileşenleri ve
 * birleşik hash'i üretir.
 */
@Service
public class FingerprintService {

    private static final Logger log = LoggerFactory.getLogger(FingerprintService.class);

    private final AppProperties props;

    public FingerprintService(AppProperties props) {
        this.props = props;
    }

    public String machineUuid() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return run("cmd", "/c", "wmic csproduct get uuid");
        }
        if (os.contains("mac")) {
            String out = run("/bin/sh", "-c",
                    "ioreg -rd1 -c IOPlatformExpertDevice | awk -F'\\\"' '/IOPlatformUUID/{print $4}'");
            return out;
        }
        // Linux
        return run("/bin/sh", "-c", "cat /etc/machine-id 2>/dev/null || cat /var/lib/dbus/machine-id 2>/dev/null");
    }

    public String boardSerial() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return run("cmd", "/c", "wmic baseboard get serialnumber");
        }
        if (os.contains("mac")) {
            return run("/bin/sh", "-c",
                    "ioreg -rd1 -c IOPlatformExpertDevice | awk -F'\\\"' '/IOPlatformSerialNumber/{print $4}'");
        }
        return run("/bin/sh", "-c", "cat /sys/class/dmi/id/board_serial 2>/dev/null");
    }

    public String installGuid() {
        String id = props.getInstallId();
        return (id == null || id.isBlank()) ? "no-install-guid" : id;
    }

    /** SHA-256( normalize(uuid) | normalize(board) | installGuid ). */
    public String compute() {
        String raw = normalize(machineUuid()) + "|" + normalize(boardSerial()) + "|" + installGuid();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Parmak izi hesaplanamadı", e);
        }
    }

    /** Sunucuya "drift" bildirimi için bileşen hash'leri. */
    public FingerprintComponents components() {
        return new FingerprintComponents(
                sha256(normalize(machineUuid())),
                sha256(normalize(boardSerial())),
                sha256(installGuid()));
    }

    public record FingerprintComponents(String uuidHash, String boardHash, String installHash) {}

    private static String normalize(String s) {
        if (s == null) return "";
        return s.replaceAll("(?i)uuid|serialnumber", "")
                .replaceAll("\\s+", "")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return "";
        }
    }

    private static String run(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            p.waitFor(5, TimeUnit.SECONDS);
            return sb.toString().trim();
        } catch (Exception e) {
            log.debug("Parmak izi komutu başarısız ({}): {}", String.join(" ", cmd), e.getMessage());
            return "";
        }
    }
}
