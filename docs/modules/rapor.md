# Modül: Raporlama / Günlük Analiz (`modules.reporting`) + Yedekleme (`backup`)

Plan §11, §14

## Yedekleme (`com.beautysalonapp.backup`)
- `ArchiveCrypto` — PBKDF2 + AES-256-GCM kap (bkz. docs/adr/0004).
- `BackupService`
  - `createBackup(trigger)` → H2 `SCRIPT DROP TO` dökümü + `attachments/` + `config/`
    (lisans hariç) + `manifest.json` → ZIP → şifreli `*.bsa`. İkincil hedefe kopya.
  - GFS rotasyonu: `retentionDaily` / `retentionWeekly` / `retentionMonthly`.
  - `verify(name)` → checksum + geçici bellek-içi H2'ye dökümü yükleyip tablo/satır sayımı.
  - `restore(bytes)` → önce PRE_RESTORE yedeği → `DROP ALL OBJECTS` + döküm + Flyway migrate
    + attachments geri yaz. `@Transactional` DEĞİL (DDL geri alınamaz); JPA oturumu temizlenir.
  - `@Scheduled` günlük yedek (`beautysalonapp.backup.cron`, vars. 23:00); Pazartesi son yedeği doğrular.
- `backup_log` tablosu (V9): BACKUP | RESTORE | VERIFY kayıtları.
- REST `/api/v1/backup/{status,list,history,run,verify/{name},download/{name},restore}`.
  Bu yollar lisans kısıtlamasından muaf (§6.4 — LOCKED'ta bile yedek/dışa aktarma açık).
- `restore` yönetici parolası doğrulaması ister.

### Ayarlar (`beautysalonapp.backup.*`)
`dir`, `password`, `scheduled-enabled`, `cron`, `retention-daily/weekly/monthly`, `secondary-dir`.
Parola boşsa kurulum kimliğinden türetilir (yalnızca geliştirme). **Üretimde parola
belirlenip yazdırılmalı** — kaybolursa yedek açılamaz.

## Günlük Analiz (`com.beautysalonapp.modules.reporting`)
- `ReportService` — modüller arası kuplajı önlemek için `JdbcTemplate` ile native
  sorgu (tarih aritmetiği Java'da; H2/PostgreSQL uyumu).
- `today()` → `DailyDashboard`: fatura+randevu cirosu, ödeme türü dağılımı (Nakit/Kart/Havale),
  bugünün randevu durum sayıları, yeni müşteri, tahsilat/gider, uyarılar (vadesi gelen
  taksit + tutar, kritik stok, bu hafta çek), son 30 gün ciro trendi, hizmet dağılımı,
  personel doluluk.
- `endOfDaySummary()` → gün sonu e-posta metni (§14.2).
- REST `GET /api/v1/dashboard/today`, `GET /api/v1/dashboard/end-of-day`.

### v2 notları
Rapor merkezinin özet tabloları / materialized view'ları (§14.3), PDF (JasperReports) /
Excel (POI) çıktıları, kaydedilebilir filtre setleri Faz 7 sonrası.
