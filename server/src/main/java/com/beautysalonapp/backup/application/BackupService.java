package com.beautysalonapp.backup.application;

import com.beautysalonapp.audit.application.AuditService;
import com.beautysalonapp.backup.ArchiveCrypto;
import com.beautysalonapp.backup.domain.BackupManifest;
import com.beautysalonapp.backup.infrastructure.BackupLog;
import com.beautysalonapp.backup.infrastructure.BackupLogRepository;
import com.beautysalonapp.config.AppProperties;
import com.beautysalonapp.core.error.BusinessRuleException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Yedekleme motoru (§11).
 *
 * <ul>
 *   <li>İçerik: H2 {@code SCRIPT} dökümü + {@code attachments/} + {@code config/} (lisans hariç) + manifest.</li>
 *   <li>Şifreleme: {@link ArchiveCrypto} (PBKDF2 + AES-256-GCM).</li>
 *   <li>Rotasyon: GFS (son N günlük / N haftalık / N aylık).</li>
 *   <li>Doğrulama: checksum + geçici bellek-içi H2'ye geri yükleme denemesi.</li>
 *   <li>Geri yükleme: mevcut veriyi önce yedekle → {@code DROP ALL OBJECTS} + {@code RUNSCRIPT} → Flyway migrate.</li>
 * </ul>
 */
@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final AppProperties props;
    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final BackupLogRepository logs;
    private final ObjectMapper objectMapper;
    private final AuditService audit;
    private final ObjectProvider<Flyway> flyway;

    @PersistenceContext
    private EntityManager entityManager;

    public BackupService(AppProperties props, DataSource dataSource, BackupLogRepository logs,
                         ObjectMapper objectMapper, AuditService audit, ObjectProvider<Flyway> flyway) {
        this.props = props;
        this.dataSource = dataSource;
        this.jdbc = new JdbcTemplate(dataSource);
        this.logs = logs;
        this.objectMapper = objectMapper;
        this.audit = audit;
        this.flyway = flyway;
    }

    // --- durum ---------------------------------------------------------

    public record BackupStatus(Instant lastSuccessfulAt, String lastError, int totalBackups,
                               long totalBytes, boolean scheduledEnabled, String dir) {}

    @Transactional(readOnly = true)
    public BackupStatus status() {
        Instant last = logs.findFirstByKindAndStatusOrderByStartedAtDesc("BACKUP", "OK")
                .map(BackupLog::getStartedAt).orElse(null);
        String err = logs.findFirstByKindAndStatusOrderByStartedAtDesc("BACKUP", "FAILED")
                .filter(l -> last == null || l.getStartedAt().isAfter(last))
                .map(BackupLog::getError).orElse(null);
        List<BackupFileInfo> files = listBackups();
        long bytes = files.stream().mapToLong(BackupFileInfo::sizeBytes).sum();
        return new BackupStatus(last, err, files.size(), bytes,
                props.getBackup().isScheduledEnabled(), props.getBackup().getDir());
    }

    public record BackupFileInfo(String name, long sizeBytes, Instant modifiedAt) {}

    public List<BackupFileInfo> listBackups() {
        Path dir = Path.of(props.getBackup().getDir());
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().endsWith(".bsa"))
                    .map(p -> {
                        try {
                            return new BackupFileInfo(p.getFileName().toString(), Files.size(p),
                                    Files.getLastModifiedTime(p).toInstant());
                        } catch (IOException e) {
                            return new BackupFileInfo(p.getFileName().toString(), 0, Instant.EPOCH);
                        }
                    })
                    .sorted(Comparator.comparing(BackupFileInfo::modifiedAt).reversed())
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    // --- yedek alma --------------------------------------------------

    public BackupLog createBackup(String trigger, String actor) {
        BackupLog entry = logs.save(new BackupLog("BACKUP", trigger, actor));
        Path tmpDir = null;
        try {
            Path backupDir = ensureDir(props.getBackup().getDir());
            tmpDir = Files.createTempDirectory("bsa-backup-");
            Path dumpSql = tmpDir.resolve("dump.sql");

            // 1) DB dökümü (H2 SCRIPT)
            jdbc.execute("SCRIPT DROP TO '" + dumpSql.toAbsolutePath().toString().replace("'", "''") + "'");

            // 2) ZIP oluştur (manifest + db + attachments + config)
            Map<String, String> checksums = new LinkedHashMap<>();
            long[] uncompressed = {0};
            byte[] zipBytes;
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                 ZipOutputStream zip = new ZipOutputStream(bos)) {

                addEntry(zip, "db/dump.sql", Files.readAllBytes(dumpSql), checksums, uncompressed);
                addDirectory(zip, "attachments/", Path.of(props.getDataDir(), "attachments"), checksums, uncompressed);
                addConfig(zip, checksums, uncompressed);

                BackupManifest manifest = new BackupManifest(
                        BackupManifest.CURRENT_FORMAT, appVersion(), schemaVersion(), dbType(),
                        trigger, Instant.now(), checksums, uncompressed[0]);
                byte[] manifestBytes = objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsBytes(manifest);
                ZipEntry me = new ZipEntry("manifest.json");
                zip.putNextEntry(me);
                zip.write(manifestBytes);
                zip.closeEntry();

                zip.finish();
                zipBytes = bos.toByteArray();
            }

            // 3) Şifrele
            byte[] container = ArchiveCrypto.encrypt(zipBytes, backupPassword());
            String fileName = "beautysalonapp-" + LocalDateTime.now().format(STAMP) + "-" + trigger.toLowerCase() + ".bsa";
            Path out = backupDir.resolve(fileName);
            Files.write(out, container);
            String checksum = sha256Hex(container);

            // 4) İkincil hedef
            copyToSecondary(out, fileName);

            entry.ok(out.toString(), (long) container.length, checksum);
            logs.save(entry);

            // 5) Rotasyon
            rotate(backupDir);

            audit.record("BACKUP_CREATE", "BackupLog", entry.getId(),
                    "Yedek alındı: " + fileName + " (" + container.length + " bayt)");
            log.info("Yedek tamamlandı: {}", out);
            return entry;
        } catch (Exception e) {
            log.error("Yedek başarısız", e);
            entry.fail(e.getMessage());
            logs.save(entry);
            audit.record("BACKUP_FAIL", "BackupLog", entry.getId(), "Yedek başarısız: " + e.getMessage());
            return entry;
        } finally {
            deleteQuietly(tmpDir);
        }
    }

    // --- doğrulama -------------------------------------------------

    public record VerifyResult(boolean ok, String message, int tablesRestored, long rowsSample) {}

    public VerifyResult verify(String backupName) {
        Path file = resolveBackup(backupName);
        try {
            byte[] container = Files.readAllBytes(file);
            byte[] zipBytes = ArchiveCrypto.decrypt(container, backupPassword());
            Map<String, byte[]> entries = unzip(zipBytes);

            BackupManifest manifest = objectMapper.readValue(entries.get("manifest.json"), BackupManifest.class);
            for (var ck : manifest.checksums().entrySet()) {
                byte[] data = entries.get(ck.getKey());
                if (data == null || !sha256Hex(data).equals(ck.getValue())) {
                    return new VerifyResult(false, "Checksum uyuşmuyor: " + ck.getKey(), 0, 0);
                }
            }

            // Geçici bellek-içi H2'ye dökümü yükle
            String sql = new String(entries.get("db/dump.sql"), StandardCharsets.UTF_8);
            long rows;
            int tables;
            String url = "jdbc:h2:mem:verify-" + System.nanoTime() + ";MODE=LEGACY;DB_CLOSE_DELAY=0";
            try (var conn = java.sql.DriverManager.getConnection(url, "sa", "");
                 var st = conn.createStatement()) {
                st.execute(sql);
                try (var rs = st.executeQuery(
                        "select count(*) from information_schema.tables where table_schema='PUBLIC'")) {
                    rs.next();
                    tables = rs.getInt(1);
                }
                try (var rs = st.executeQuery("select count(*) from app_user")) {
                    rs.next();
                    rows = rs.getLong(1);
                }
            }

            logs.findFirstByKindAndStatusOrderByStartedAtDesc("BACKUP", "OK")
                    .filter(l -> file.toString().equals(l.getFilePath()))
                    .ifPresent(l -> { l.setVerifiedAt(Instant.now()); logs.save(l); });

            audit.record("BACKUP_VERIFY", "Backup", null,
                    "Doğrulama OK (" + backupName + "): " + tables + " tablo, app_user=" + rows);
            return new VerifyResult(true, "Yedek geçerli", tables, rows);
        } catch (Exception e) {
            log.warn("Yedek doğrulama başarısız: {}", e.getMessage());
            return new VerifyResult(false, e.getMessage(), 0, 0);
        }
    }

    // --- geri yükleme ----------------------------------------------

    /**
     * Geri yükleme. Önce mevcut veriyi otomatik yedekler, sonra dökümü uygular ve Flyway'i çalıştırır.
     * <b>İşlem sonrası uygulamanın yeniden başlatılması önerilir.</b>
     *
     * <p>{@code @Transactional} DEĞİLDİR: {@code DROP ALL OBJECTS} otomatik commit'lenir ve geri
     * alınamaz. İşlem sonrası JPA oturumu {@code em.clear()} ile temizlenir; kayıt yeni bir
     * satır olarak eklenir.
     */
    public void restore(byte[] container, String actor) {
        BackupManifest manifest;
        Map<String, byte[]> entries;
        try {
            byte[] zipBytes = ArchiveCrypto.decrypt(container, backupPassword());
            entries = unzip(zipBytes);
            manifest = objectMapper.readValue(entries.get("manifest.json"), BackupManifest.class);
        } catch (Exception e) {
            writeRestoreLog("FAILED", null, "Yedek okunamadı: " + e.getMessage());
            throw new BusinessRuleException("bad_backup", "Yedek okunamadı: " + e.getMessage());
        }

        if (isNewerSchema(manifest.schemaVersion())) {
            throw new BusinessRuleException("schema_too_new",
                    "Yedek daha yeni bir şema sürümüne ait (" + manifest.schemaVersion()
                            + "). Önce uygulamayı güncelleyin.");
        }
        for (var ck : manifest.checksums().entrySet()) {
            byte[] data = entries.get(ck.getKey());
            if (data == null || !sha256Hex(data).equals(ck.getValue())) {
                throw new BusinessRuleException("bad_checksum", "Yedek bozuk: " + ck.getKey());
            }
        }

        try {
            // Güvenlik: mevcut veriyi yedekle
            createBackup("PRE_RESTORE", actor);

            String sql = new String(entries.get("db/dump.sql"), StandardCharsets.UTF_8);
            jdbc.execute("DROP ALL OBJECTS");
            jdbc.execute(sql); // SCRIPT DROP çıktısı: DROP IF EXISTS + CREATE + INSERT

            Flyway fw = flyway.getIfAvailable();
            if (fw != null) {
                fw.migrate();
            }
            restoreAttachments(entries);

            entityManager.clear(); // yeniden yüklenen DB ile eskimiş yönetilen varlıkları at
            writeRestoreLog("OK", (long) container.length, null);
            audit.record("BACKUP_RESTORE", "Backup", null,
                    "Geri yükleme tamamlandı (yedek tarihi " + manifest.createdAt() + ")");
            log.warn("Geri yükleme tamamlandı — uygulamanın yeniden başlatılması önerilir");
        } catch (Exception e) {
            entityManager.clear();
            writeRestoreLog("FAILED", null, e.getMessage());
            audit.record("BACKUP_RESTORE_FAIL", "Backup", null, "Geri yükleme başarısız: " + e.getMessage());
            if (e instanceof RuntimeException re) {
                throw re;
            }
            throw new BusinessRuleException("restore_failed", "Geri yükleme başarısız: " + e.getMessage());
        }
    }

    private void writeRestoreLog(String status, Long size, String error) {
        try {
            BackupLog l = new BackupLog("RESTORE", "MANUAL", null);
            if ("OK".equals(status)) {
                l.ok(null, size, null);
            } else {
                l.fail(error);
            }
            logs.saveAndFlush(l);
        } catch (RuntimeException ex) {
            log.warn("Geri yükleme kaydı yazılamadı: {}", ex.getMessage());
        }
    }

    // --- zamanlanmış işler ---------------------------------------

    @Scheduled(cron = "${beautysalonapp.backup.cron:0 0 23 * * *}")
    public void scheduledBackup() {
        if (!props.getBackup().isScheduledEnabled()) {
            return;
        }
        createBackup("SCHEDULED", "system");
        // Haftada bir (Pazartesi) son yedeği doğrula
        if (LocalDate.now().getDayOfWeek().getValue() == 1) {
            listBackups().stream().findFirst().ifPresent(f -> verify(f.name()));
        }
    }

    // --- yardımcılar --------------------------------------------

    private void rotate(Path dir) throws IOException {
        var b = props.getBackup();
        List<Path> all;
        try (Stream<Path> s = Files.list(dir)) {
            all = s.filter(p -> p.getFileName().toString().endsWith(".bsa"))
                    .sorted(Comparator.comparing((Path p) -> lastModified(p)).reversed())
                    .toList();
        }
        Set<Path> keep = new HashSet<>();
        Map<String, Path> perDay = new HashMap<>();
        Map<String, Path> perWeek = new HashMap<>();
        Map<String, Path> perMonth = new HashMap<>();
        LocalDate today = LocalDate.now();
        for (Path p : all) {
            LocalDate d = lastModified(p).atZone(ZoneId.systemDefault()).toLocalDate();
            long ageDays = today.toEpochDay() - d.toEpochDay();
            String dayKey = d.toString();
            String weekKey = d.get(ChronoField.ALIGNED_WEEK_OF_YEAR) + "-" + d.getYear();
            String monthKey = d.getYear() + "-" + d.getMonthValue();
            if (ageDays <= b.getRetentionDaily()) {
                perDay.putIfAbsent(dayKey, p);
            } else if (ageDays <= b.getRetentionWeekly() * 7L + b.getRetentionDaily()) {
                perWeek.putIfAbsent(weekKey, p);
            } else if (ageDays <= b.getRetentionMonthly() * 31L + 60) {
                perMonth.putIfAbsent(monthKey, p);
            }
        }
        keep.addAll(perDay.values());
        keep.addAll(perWeek.values());
        keep.addAll(perMonth.values());
        for (Path p : all) {
            if (!keep.contains(p)) {
                try {
                    Files.deleteIfExists(p);
                    log.info("Rotasyon: eski yedek silindi {}", p.getFileName());
                } catch (IOException e) {
                    log.warn("Eski yedek silinemedi: {}", p, e);
                }
            }
        }
    }

    private void copyToSecondary(Path source, String fileName) {
        String secondary = props.getBackup().getSecondaryDir();
        if (secondary == null || secondary.isBlank()) {
            return;
        }
        try {
            Path dir = ensureDir(secondary);
            Files.copy(source, dir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.warn("İkincil hedefe kopyalama başarısız ({}): {}", secondary, e.getMessage());
        }
    }

    private void addConfig(ZipOutputStream zip, Map<String, String> checksums, long[] size) throws IOException {
        Path configDir = Path.of(props.getDataDir(), "config");
        if (!Files.isDirectory(configDir)) {
            return;
        }
        try (Stream<Path> s = Files.walk(configDir)) {
            for (Path p : s.filter(Files::isRegularFile).toList()) {
                String name = p.getFileName().toString();
                if (name.equals("license.lic") || name.equals("install-id")) {
                    continue; // lisans ve makineye özel kimlik yedeğe girmez (§11.1)
                }
                addEntry(zip, "config/" + configDir.relativize(p), Files.readAllBytes(p), checksums, size);
            }
        }
    }

    private void addDirectory(ZipOutputStream zip, String prefix, Path dir,
                              Map<String, String> checksums, long[] size) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path p : s.filter(Files::isRegularFile).toList()) {
                addEntry(zip, prefix + dir.relativize(p), Files.readAllBytes(p), checksums, size);
            }
        }
    }

    private void addEntry(ZipOutputStream zip, String name, byte[] data,
                          Map<String, String> checksums, long[] size) throws IOException {
        String norm = name.replace('\\', '/');
        zip.putNextEntry(new ZipEntry(norm));
        zip.write(data);
        zip.closeEntry();
        checksums.put(norm, sha256Hex(data));
        size[0] += data.length;
    }

    private void restoreAttachments(Map<String, byte[]> entries) {
        Path base = Path.of(props.getDataDir(), "attachments");
        for (var e : entries.entrySet()) {
            if (!e.getKey().startsWith("attachments/")) {
                continue;
            }
            try {
                Path target = base.resolve(e.getKey().substring("attachments/".length()));
                Files.createDirectories(target.getParent());
                Files.write(target, e.getValue());
            } catch (IOException ex) {
                log.warn("Ek dosyası geri yazılamadı: {}", e.getKey(), ex);
            }
        }
    }

    private static Map<String, byte[]> unzip(byte[] zipBytes) throws IOException {
        Map<String, byte[]> out = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new java.io.ByteArrayInputStream(zipBytes))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                out.put(e.getName(), zis.readAllBytes());
            }
        }
        return out;
    }

    private char[] backupPassword() {
        String pw = props.getBackup().getPassword();
        if (pw != null && !pw.isBlank()) {
            return pw.toCharArray();
        }
        String seed = (props.getInstallId() == null ? "beautysalonapp-dev" : props.getInstallId()) + "|backup|v1";
        return seed.toCharArray();
    }

    private Path resolveBackup(String name) {
        Path p = Path.of(props.getBackup().getDir()).resolve(name).normalize();
        if (!p.startsWith(Path.of(props.getBackup().getDir()).normalize()) || !Files.exists(p)) {
            throw new BusinessRuleException("not_found", "Yedek bulunamadı: " + name);
        }
        return p;
    }

    public byte[] readBackup(String name) {
        try {
            return Files.readAllBytes(resolveBackup(name));
        } catch (IOException e) {
            throw new BusinessRuleException("read_error", "Yedek okunamadı: " + e.getMessage());
        }
    }

    private String schemaVersion() {
        try {
            return jdbc.queryForObject(
                    "select max(version) from flyway_schema_history where success = true", String.class);
        } catch (Exception e) {
            return "unknown";
        }
    }

    private boolean isNewerSchema(String backupSchema) {
        if (backupSchema == null || backupSchema.equals("unknown")) {
            return false;
        }
        try {
            return compareVersions(backupSchema, schemaVersion()) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static int compareVersions(String a, String b) {
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            int va = i < pa.length ? Integer.parseInt(pa[i].replaceAll("\\D", "")) : 0;
            int vb = i < pb.length ? Integer.parseInt(pb[i].replaceAll("\\D", "")) : 0;
            if (va != vb) return Integer.compare(va, vb);
        }
        return 0;
    }

    private String dbType() {
        try {
            return dataSource.getConnection().getMetaData().getDatabaseProductName();
        } catch (Exception e) {
            return "H2";
        }
    }

    private String appVersion() {
        String v = getClass().getPackage().getImplementationVersion();
        return v != null ? v : "1.0.0-dev";
    }

    private static Path ensureDir(String dir) throws IOException {
        Path p = Path.of(dir);
        Files.createDirectories(p);
        return p;
    }

    private static Instant lastModified(Path p) {
        try {
            return Files.getLastModifiedTime(p).toInstant();
        } catch (IOException e) {
            return Instant.EPOCH;
        }
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) return;
        try (Stream<Path> s = Files.walk(dir)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) {
        }
    }

    private static String sha256Hex(byte[] data) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte x : d) sb.append(Character.forDigit((x >> 4) & 0xF, 16)).append(Character.forDigit(x & 0xF, 16));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
