# Faz 7 — Sertleştirme ve Yayın

Teknik plan §17. Bu faz yeni iş modülü eklemez; var olan ürünü yayına hazırlar.

| Başlık | Durum | Çıktı |
|---|---|---|
| Performans (10 yıllık veri, 500k hareket) | ✅ araç + test hazır | `PerfDataGenerator`, `PerformanceBudgetTest`, `docs/perf/README.md` |
| Güvenlik gözden geçirme | ✅ yapıldı + düzeltmeler | `docs/guvenlik/guvenlik-gozden-gecirme.md` + kod |
| macOS imzalama / notarization, Windows code signing | ⛔ sertifika gerektirir | Betikler Faz "paketleme"de hazır (`packaging/scripts/sign.sh`, `notarize.sh`) |
| Kullanıcı kılavuzu, kurulum dokümanı | ✅ | `docs/kullanim-kilavuzu.md`, `docs/kurulum.md` |
| Eğitim videoları | ⛔ bu ortamda üretilemez | Kılavuz senaryo iskeleti sağlıyor |
| Pilot müşteri geri bildirimleri | ✅ süreç kurgusu | `docs/pilot/pilot-plani.md`, `geri-bildirim-formu.md` |

---

## 1. Performans

`beautysalonapp.perf.seed=true` ile çalışan `PerfDataGenerator`, açılışta tohumlanan
referans veriyi (birim/depo/kasa/kart) okuyup üzerine 10 yıllık sentetik hareket basar
(varsayılan 500k `stock_movement`, 120k randevu, 40k fatura, 100k kasa, 150k cari hareket).
Toplu JDBC `batchUpdate` (5000'lik parti). Üretim derlemesinde **asla** etkin değildir.

`PerformanceBudgetTest` (`@Tag("perf")`, `mvn test`'ten dışlı) indirgenmiş ölçeği izole
bellek-içi H2'ye tohumlar, en ağır rapor/liste sorgularını ölçer. Referans (Apple M-serisi):
günlük dashboard ~40 ms, saf liste sorguları < 1 ms — hedef 300 ms altında.

Çalıştırma: `./mvnw -Pperf test -Dtest=PerformanceBudgetTest` · gerçek 500k: `docs/perf/README.md`.

## 2. Güvenlik — bu fazda kapatılanlar

| Kod değişikliği | Dosya |
|---|---|
| CSP + `Referrer-Policy: no-referrer` + `Permissions-Policy` başlıkları, `frameOptions=deny` | `config/SecurityConfig.java` |
| Oturum çerezi `SameSite=Strict` + `HttpOnly` açıkça | `application.yml` |
| Paketlenmiş üründe Swagger/OpenAPI kapalı | `application-packaged.yml` |
| SMS/e-posta NoOp loglarında PII maskeleme (gövde/adres yalnızca DEBUG) | `NoOpSmsProvider`, `NoOpEmailSender` |
| `apply-update.sh`: SHA-256 zorunlu + Ed25519 `.sig` doğrulaması (openssl round-trip'li) | `packaging/scripts/apply-update.sh` |
| CI: Dependabot (haftalık) + PR'da `dependency-review-action` | `.github/dependabot.yml`, `.github/workflows/ci.yml` |

Kalan maddeler (kod dışı / operasyon) `docs/guvenlik/guvenlik-gozden-gecirme.md` §10'da.

## 3. Zaten sağlam bulunanlar

BCrypt(12), brute-force kilidi (5/15 dk), oturum sabitleme koruması, token CSRF, alan
şifreleme (AES-256-GCM) + blind index, guardlı tek giden HTTP istemcisi + ArchUnit,
hard-delete yasağı + audit, `LOCKED`'te bile dışa aktarma, İYS izin kontrolü.

---

## 4. Yayın Öncesi Kontrol Listesi (release checklist)

### Derleme ve imza
- [ ] `LICENSE_PUBLIC_KEY` ortam değişkeni **gerçek** lisans sunucusu anahtarıyla ayarlı
- [ ] `build-all.sh` çıktısında "GELİŞTİRME modu" uyarısı **yok**
- [ ] Üretilen jar'da `application-packaged.yml` içindeki `@LICENSE_PUBLIC_KEY@` **değiştirilmiş**
- [ ] Uygulama açılışında "GELİŞTİRME MODU (tüm modüller açık)" logu **yok**
- [ ] macOS: `sign.sh` + `notarize.sh` başarılı, `stapler validate` geçiyor
- [ ] Windows: `signtool verify /pa` geçiyor, SmartScreen uyarısı kabul edilebilir
- [ ] `checksums.txt` üretildi ve yayın notuna eklendi

### Test
- [x] `./mvnw test` — 171 test yeşil (server); `-Pperf` ile 172, `-Ppg` ile +3 (Docker)
- [x] Migration testi (`MigrationTest`): tüm Flyway betikleri boş DB'ye temiz, sürüm boşluğu yok, idempotent
- [x] Lisans testi (`LicenseLifecycleTest`): sahte saat + tüm durum geçişleri
      (ACTIVE/EXPIRING/GRACE/READ_ONLY/LOCKED/TAMPERED/REVOKED/SUSPENDED, çevrimdışı grace)
- [x] PostgreSQL entegrasyon (`PostgresIntegrationTest`, Testcontainers): `common/` şeması
      PG 16'ya uygulanır, açılış tohumlayıcıları koşar, `NUMERIC(19,4)` ölçeği korunur — CI'da `-Ppg` job
- [ ] `./mvnw -Pperf test` — performans bütçesi loglandı, aşım yok (CI'da `perf-budget` job)
- [ ] license-server `./mvnw test` — yeşil (6 test)
- [x] E2E (Playwright/Chromium): giriş → zorunlu parola değişimi → Günlük Analiz →
      6 ana modül ekranı hatasız yüklenir → **cari (müşteri) oluşturma (form → POST → liste)** → çıkış.
      İstemci konsol/pageerror hataları boş olmalı. `web/e2e/critical-path.spec.ts`, CI'da `e2e` job.
      Derin akış (randevu→GELDI→stok/prim→tahsilat→rapor) sonraki adım — pilotla genişletilecek.
- [ ] Gerçek 500k tohumlamayla liste ekranları elle ölçüldü (p95 < 300 ms)
- [ ] Temiz Windows VM + temiz macOS'ta kurulum + ilk giriş + yedek/geri-yükle çalıştı

### Güvenlik
- [x] Bağımlılık taraması — Dependabot + `dependency-review-action` (`.github/`)
- [x] `application-packaged.yml`: swagger kapalı, `server.address=127.0.0.1`
- [x] `apply-update` Ed25519 imza doğrulaması tamamlandı (`BSA_UPDATE_PUBKEY_B64URL`)
- [ ] Şifreleme anahtarı OS keystore'dan besleniyor (kurulum kimliğinden türetme **değil**)
- [ ] Veri kökü ACL'leri doğrulandı (yalnızca hizmet hesabı + yöneticiler yazabilir)
- [ ] Dependabot PR'larının kritik/yüksek açığı kapattığı doğrulandı
- [ ] (Önerilir) Bağımsız sızma testi raporu

### KVKK / hukuk
- [ ] Rıza ve aydınlatma metinleri avukat + KVKK danışmanı onaylı
- [ ] "Verinin işletmede kaldığını belgeleyen teknik doküman" hazır (VERBİS yükü için)
- [ ] Anonimleştirme ve veri taşınabilirliği (dışa aktarma) elle test edildi

### Operasyon
- [ ] Lisans sunucusu üretim ortamında ayakta, yedeği + anahtar dosyası güvende
- [ ] Destek kanalı + SLA yayınlandı
- [ ] Sürüm notu / changelog hazır
- [ ] Geri alma planı (önceki sürüme dönüş) yazılı

### Dokümantasyon
- [ ] `docs/kurulum.md`, `docs/kullanim-kilavuzu.md` sürümle güncel
- [ ] ADR'ler güncel (0001–0005)
- [ ] `docs/modules/*` her modül için mevcut

---

## 5. Bu Ortamda Üretilemeyen / Sonraki Adım

- Gerçek imzalı installer (Apple Developer $99/yıl, Windows EV ~$300/yıl — plan Risk #2)
- Eğitim videoları
- Canlı pilot ve muhasebe mutabakatı (süreç `docs/pilot/` altında kurgulandı)
- E2E'nin **derin akışı** (randevu oluştur → GELDI → stok sarfı + prim + tahsilat → rapor) —
  ilk smoke E2E kuruldu; veri-giriş adımları pilot geri bildirimiyle genişletilecek
- `release.yml` çalıştırması için sertifika secret'ları (aksi halde imzasız artefakt)
