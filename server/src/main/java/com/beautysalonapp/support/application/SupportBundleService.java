package com.beautysalonapp.support.application;

import com.beautysalonapp.audit.application.AuditService;
import com.beautysalonapp.backup.application.BackupService;
import com.beautysalonapp.config.AppProperties;
import com.beautysalonapp.licensing.application.LicenseService;
import com.beautysalonapp.licensing.application.LicenseSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Destek Paketi / Tanı ekranı (plan §13.2). İçinde <b>hiçbir iş verisi yoktur</b> —
 * yalnızca sistem/teşhis bilgisi, şema versiyonu ve uygulama logları. Kullanıcı
 * göndermeden/kaydetmeden önce {@link #manifest()} ile içeriği görebilir.
 *
 * <p>"Uzaktan komut YOK" kuralı (plan §13.2): bu servis yalnızca teşhis verisi üretir,
 * sunucudan gelen hiçbir komutu çalıştırmaz.
 */
@Service
public class SupportBundleService {

    private static final int LOG_TAIL_BYTES = 2 * 1024 * 1024; // son 2 MB yeterli
    private static final int LOG_TAIL_LINES = 500;
    private static final int RECENT_ERROR_LINES = 100;

    private final AppProperties props;
    private final BackupService backup;
    private final LicenseService license;
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    @Value("${logging.file.name:}")
    private String logFilePath;

    @Value("${server.port:8734}")
    private int serverPort;

    public SupportBundleService(AppProperties props, BackupService backup, LicenseService license,
                                DataSource dataSource, AuditService audit) {
        this.props = props;
        this.backup = backup;
        this.license = license;
        this.jdbc = new JdbcTemplate(dataSource);
        this.audit = audit;
    }

    public record DiagnosticsSnapshot(
            boolean dbConnected,
            String dbType,
            String appVersion,
            String schemaVersion,
            String os,
            String javaVersion,
            int serverPort,
            long diskFreeBytes,
            long diskTotalBytes,
            Instant lastSuccessfulBackupAt,
            String lastBackupError,
            String licenseStatus,
            Integer licenseDaysRemaining,
            Instant lastLicenseHeartbeatAt,
            boolean logFileConfigured,
            Instant serverTimeUtc
    ) {}

    @Transactional(readOnly = true)
    public DiagnosticsSnapshot diagnostics() {
        boolean dbOk;
        try {
            jdbc.queryForObject("select 1", Integer.class);
            dbOk = true;
        } catch (Exception e) {
            dbOk = false;
        }
        var sys = backup.systemInfo();
        var backupStatus = backup.status();
        LicenseSnapshot lic = license.snapshot();
        File dataDir = new File(props.getDataDir());

        return new DiagnosticsSnapshot(
                dbOk, sys.dbType(), sys.appVersion(), sys.schemaVersion(),
                System.getProperty("os.name", "?") + " " + System.getProperty("os.version", ""),
                System.getProperty("java.version", "?"),
                serverPort,
                dataDir.getUsableSpace(), dataDir.getTotalSpace(),
                backupStatus.lastSuccessfulAt(), backupStatus.lastError(),
                lic.status().name(), lic.daysRemaining(), lic.lastSuccessfulHeartbeatAt(),
                logFilePath != null && !logFilePath.isBlank(),
                Instant.now());
    }

    public record BundleFile(String name, long sizeBytes, String preview) {}

    /** Kullanıcının göndermeden önce göreceği içerik özeti (plan §13.2: "önce içeriği görür"). */
    public List<BundleFile> manifest() {
        String sysInfoText = systemInfoText();
        String errorsText = String.join("\n", recentLogLines(true));
        String fullLogText = String.join("\n", recentLogLines(false));
        return List.of(
                new BundleFile("sistem-bilgisi.txt", sysInfoText.getBytes(StandardCharsets.UTF_8).length,
                        preview(sysInfoText)),
                new BundleFile("son-hatalar.txt", errorsText.getBytes(StandardCharsets.UTF_8).length,
                        preview(errorsText)),
                new BundleFile("uygulama-log-son-satirlar.txt", fullLogText.getBytes(StandardCharsets.UTF_8).length,
                        preview(fullLogText))
        );
    }

    /** Destek paketini tek ZIP olarak üretir. */
    public byte[] buildBundle() {
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(buf, StandardCharsets.UTF_8)) {
                writeEntry(zip, "sistem-bilgisi.txt", systemInfoText());
                writeEntry(zip, "son-hatalar.txt", String.join("\n", recentLogLines(true)));
                writeEntry(zip, "uygulama-log-son-satirlar.txt", String.join("\n", recentLogLines(false)));
            }
            audit.record("SUPPORT_BUNDLE_CREATE", "System", "support",
                    "Destek paketi oluşturuldu (iş verisi içermez)");
            return buf.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Destek paketi oluşturulamadı", e);
        }
    }

    private void writeEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String systemInfoText() {
        DiagnosticsSnapshot d = diagnostics();
        DateTimeFormatter fmt = DateTimeFormatter.ISO_INSTANT;
        StringBuilder sb = new StringBuilder();
        sb.append("BeautySalonApp — Sistem Bilgisi (iş verisi içermez)\n");
        sb.append("Üretim zamanı (UTC): ").append(fmt.format(d.serverTimeUtc())).append('\n');
        sb.append("Uygulama sürümü: ").append(d.appVersion()).append('\n');
        sb.append("Şema versiyonu: ").append(d.schemaVersion()).append('\n');
        sb.append("Veritabanı: ").append(d.dbType()).append(" (bağlantı: ")
                .append(d.dbConnected() ? "OK" : "BAŞARISIZ").append(")\n");
        sb.append("İşletim sistemi: ").append(d.os()).append('\n');
        sb.append("Java sürümü: ").append(d.javaVersion()).append('\n');
        sb.append("Zaman dilimi: ").append(ZoneId.systemDefault()).append('\n');
        sb.append("Servis portu: ").append(d.serverPort()).append('\n');
        sb.append("Disk (boş/toplam): ").append(mb(d.diskFreeBytes())).append(" MB / ")
                .append(mb(d.diskTotalBytes())).append(" MB\n");
        sb.append("Son başarılı yedek: ").append(d.lastSuccessfulBackupAt() == null ? "yok" : fmt.format(d.lastSuccessfulBackupAt())).append('\n');
        if (d.lastBackupError() != null) {
            sb.append("Son yedek hatası: ").append(d.lastBackupError()).append('\n');
        }
        sb.append("Lisans durumu: ").append(d.licenseStatus());
        if (d.licenseDaysRemaining() != null) {
            sb.append(" (kalan gün: ").append(d.licenseDaysRemaining()).append(")");
        }
        sb.append('\n');
        sb.append("Son lisans heartbeat: ")
                .append(d.lastLicenseHeartbeatAt() == null ? "yok" : fmt.format(d.lastLicenseHeartbeatAt())).append('\n');
        sb.append("Log dosyası yapılandırılmış: ").append(d.logFileConfigured() ? "evet" : "hayır (konsola yazılıyor)").append('\n');
        return sb.toString();
    }

    /** Log dosyasının son kısmından, gerekirse yalnızca ERROR satırlarını döner. */
    private List<String> recentLogLines(boolean errorsOnly) {
        if (logFilePath == null || logFilePath.isBlank()) {
            return List.of("(log dosyası yapılandırılmamış — geliştirme modunda konsola yazılıyor)");
        }
        File f = new File(logFilePath);
        if (!f.exists() || !f.isFile()) {
            return List.of("(log dosyası bulunamadı: " + logFilePath + ")");
        }
        Deque<String> tail = new ArrayDeque<>();
        int limit = errorsOnly ? RECENT_ERROR_LINES : LOG_TAIL_LINES;
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            long len = raf.length();
            long start = Math.max(0, len - LOG_TAIL_BYTES);
            raf.seek(start);
            byte[] chunk = new byte[(int) (len - start)];
            raf.readFully(chunk);
            String text = new String(chunk, StandardCharsets.UTF_8);
            for (String line : text.split("\n")) {
                if (errorsOnly && !line.contains("ERROR")) {
                    continue;
                }
                tail.addLast(line);
                if (tail.size() > limit) {
                    tail.removeFirst();
                }
            }
        } catch (IOException e) {
            return List.of("(log dosyası okunamadı: " + e.getMessage() + ")");
        }
        if (tail.isEmpty() && errorsOnly) {
            return List.of("(son loglarda ERROR seviyesinde kayıt yok)");
        }
        return List.copyOf(tail);
    }

    private static String preview(String text) {
        String[] lines = text.split("\n");
        int n = Math.min(lines.length, 8);
        return String.join("\n", java.util.Arrays.copyOfRange(lines, 0, n))
                + (lines.length > n ? "\n…" : "");
    }

    private static long mb(long bytes) {
        return bytes / (1024 * 1024);
    }
}
