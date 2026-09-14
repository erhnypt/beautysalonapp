# Güvenlik Gözden Geçirme — Faz 7

- **Tarih:** 2026-09-04
- **Kapsam:** `server` (Spring Boot uygulaması) + `license-server` + paketleme
- **Yöntem:** Kod incelemesi + OWASP ASVS L1/L2 kontrol listesi + teknik plan §8 / CLAUDE.md maddeleri
- **Sınıflandırma:** ✅ karşılanıyor · ⚠️ kısmi / izlenmeli · ❌ açık · N/A kapsam dışı

> Bu belge bir sızma testi raporu değildir. Faz 7 çıktısı olarak iç gözden geçirmedir;
> yayından önce bağımsız bir sızma testi önerilir (plan §17).

---

## 1. Kimlik doğrulama ve oturum

| # | Kontrol | Durum | Not / kanıt |
|---|---|---|---|
| A1 | Parola karması güçlü algoritma | ✅ | `BCryptPasswordEncoder(12)` — `SecurityConfig` |
| A2 | Parola politikası (min uzunluk + karma) | ✅ | `UserService.validatePasswordStrength` — ≥8, harf+rakam |
| A3 | Brute-force / hesap kilitleme | ✅ | `UserService`: 5 başarısız → 15 dk kilit; `AppUserDetailsService.accountLocked` |
| A4 | Oturum sabitleme koruması | ✅ | `AuthController.login` → `request.changeSessionId()` |
| A5 | Oturum çerezi `HttpOnly` | ✅ | Servlet konteyner varsayılanı + `server.servlet.session.cookie` |
| A6 | `SameSite=Strict` çerez | ⚠️ | Konteyner varsayılanı `Lax`. **Öneri:** `server.servlet.session.cookie.same-site=strict` `application.yml`'ye eklensin. |
| A7 | Kritik rollerde 2. faktör (PIN) | ✅ (2026-09-14) | ADMIN/MUDUR: parola girişinden sonra oturum `PinPolicy` sentetik yetkisiyle (`PIN_SETUP_REQUIRED`/`PIN_VERIFICATION_REQUIRED`) kısıtlanır — gerçek rol/izin yetkileri yalnızca PIN kurulup/doğrulanınca (`POST /api/v1/auth/pin/setup|verify`) tanınır (`AppUserDetailsService`). 5 yanlış PIN → 15 dk kilit + oturum sonlandırma (`app_user.pin_failed_count/pin_locked_until`, V17 migration). Kurtarma: `PUT /api/v1/users/{id}/pin` (`USER_EDIT`, UsersPage'de "PIN Sıfırla"). KASIYER/PERSONEL/RAPOR_OKUYUCU etkilenmez. Testler: `PinAuthTest`, `PinPolicyTest`, `UserServiceTest` PIN senaryoları, `AuthFlowTest` güncellendi. |
| A8 | İlk girişte zorunlu parola değişimi | ✅ | Bootstrap admin `must_change_password=true`; SPA yönlendirir |
| A9 | Bootstrap admin parolası | ✅ (düzeltildi) | Sabit `admin123` varsayılanı kaldırıldı. `bootstrap-admin-password` boşsa `DataInitializer` rastgele güçlü parola üretir ve açılış loguna **bir kez** yazar; `must_change_password=true`. Kurulum sihirbazı/otomasyon için property'den sabit ilk parola da verilebilir. Regresyon: `BootstrapAdminPasswordTest`. |

## 2. Yetkilendirme

| # | Kontrol | Durum | Not |
|---|---|---|---|
| B1 | Rol tabanlı erişim (`@EnableMethodSecurity`) | ✅ | `SecurityConfig`; rol×izin matrisi `RolePermissionCatalog` |
| B2 | Tüm `/api/**` kimlik doğrulaması ister | ✅ | `authorizeHttpRequests` — `permitAll` yalnızca auth/health/docs/statik |
| B3 | Son etkin yönetici korunur | ✅ | `UserService.setEnabled` → `last_admin` kuralı |
| B4 | Yetki kontrolü controller değil servis katmanında | ✅ | Metot güvenliği + ince controller kuralı (CLAUDE.md) |
| B5 | IDOR (nesne düzeyi yetki) | ✅ (2026-09-14, kısmi) | `docs/adr/0009-kullanici-sube-atamasi.md` — kullanıcı-şube ataması eklendi (boş atama = kısıtlama yok, hiçbir mevcut kurulumun davranışı değişmedi). `BranchContextFilter` artık `X-Branch-Id`'yi kullanıcının atamasına göre reddedebiliyor (403); `UserService.resolveRequestedBranch` aynı kontrolü açık `branchId` parametresi alan 9 rapor uç noktasında da zorluyor. **Kapsam dışı kalan:** genel liste uç noktaları (cari/stok/fatura/randevu listeleri) hâlâ hiçbir şube filtresi uygulamıyor — bu, ayrı bir modül-modül repository refactoru gerektiriyor (ADR 0009 §"kapsam dışı"). Test: `UserBranchAssignmentTest`, `UserServiceTest`. |
| B6 | Swagger UI üretimde açık | ⚠️ | `/swagger-ui/**` `permitAll`. Yerel bağlı (127.0.0.1) olduğu için düşük risk. **Öneri:** `packaged` profilinde `springdoc.swagger-ui.enabled=false`. |
| B7 | Her uç noktada yetki kontrolü | ✅ (düzeltildi) | `GET /api/v1/dashboard/today` Faz 5'ten beri sınıf/metot düzeyinde `@PreAuthorize` olmadan yalnızca kimlik doğrulamalıydı; her rol (PERSONEL dâhil) günlük ciro/tahsilat/gider/ödeme dağılımını okuyabiliyordu. `/end-of-day` ile aynı `REPORTING_VIEW` yetkisine bağlandı; SPA `DashboardPage` yetkisiz kullanıcıya mali kartları göstermeyip bilgi panosu döndürür. Regresyon: `DashboardApiTest` (401/403/200). |

## 3. Girdi doğrulama ve enjeksiyon

| # | Kontrol | Durum | Not |
|---|---|---|---|
| C1 | SQL enjeksiyonu | ✅ | JPA + parametreli `JdbcTemplate` (`ReportService`, `PerfDataGenerator`). String birleştirme ile sorgu yok. |
| C2 | Bean Validation | ✅ | `spring-boot-starter-validation`; DTO'lar `record` + `@Valid` |
| C3 | Kütle atama (mass assignment) | ✅ | Giriş DTO'ları ayrı `record`'lar; entity doğrudan bağlanmıyor |
| C4 | Dosya yükleme boyutu sınırı | ✅ | `spring.servlet.multipart.max-file-size: 100MB` |
| C5 | Dosya yükleme tür/yol kontrolü | ✅ | Faz 8: `AttachmentService.sanitizeFilename` + UUID tabanlı disk adı + kök dizin doğrulaması (`upload`/`download`'da normalize+startsWith kontrolü). `AttachmentServiceTest` yol geçişi (`../`, `..\`) senaryolarını doğruluyor. |
| C6 | XXE / güvensiz deserializasyon | ✅ | Jackson varsayılan; polimorfik tip yok. XML endpoint yok. |

## 4. Web güvenlik başlıkları

| # | Kontrol | Durum | Not |
|---|---|---|---|
| D1 | `X-Content-Type-Options: nosniff` | ✅ | Spring Security varsayılanı |
| D2 | `X-Frame-Options: DENY` | ✅ | `headers.frameOptions(deny)` (Faz 7'de açıkça ayarlandı) |
| D3 | `Content-Security-Policy` | ✅ | Faz 7: `default-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'` (+ `style-src 'unsafe-inline'` bileşen kütüphaneleri için) |
| D4 | `Referrer-Policy: no-referrer` | ✅ | Faz 7'de eklendi |
| D5 | `Permissions-Policy` | ✅ | Faz 7: `geolocation=(), camera=(), microphone=(), payment=(), usb=()` |
| D6 | HSTS | N/A | Yerel HTTP (127.0.0.1). LAN erişimi + TLS açılırsa `application-*.yml`'de etkinleştirilmeli. |
| D7 | CSRF | ✅ | `CookieCsrfTokenRepository` + token handler; SPA `GET /api/v1/auth/csrf` ile alır. Yalnızca login/logout muaf (kimlik öncesi). |
| D8 | CORS | ✅ | Tanımlı değil → tarayıcı çapraz-origin XHR reddeder; SPA aynı origin'den sunulur. |

## 5. Ağ ve dışa veri akışı (CLAUDE.md #1)

| # | Kontrol | Durum | Not |
|---|---|---|---|
| E1 | Varsayılan bağlanma `127.0.0.1` | ✅ | `server.address: 127.0.0.1` |
| E2 | LAN erişimi opt-in + uyarı | ✅ | `beautysalonapp.lan-access-enabled` (varsayılan false) |
| E3 | Tüm giden HTTP tek guardlı istemciden | ✅ | `GuardedRestClient` + `OutboundHttpGuard` allowlist; ArchUnit testi `RestTemplate/RestClient/WebClient` doğrudan kullanımını engeller |
| E4 | Allowlist içeriği | ✅ | `https://license.beautysalonapp.com` (lisans/heartbeat) + `https://query1.finance.yahoo.com` (canlı döviz kuru — 2026-09-10 eklendi). İkisi de tek yönlü: dışarı hiçbir iş verisi/PII gitmez. Yahoo çağrısı yalnızca `GET .../chart/USDTRY=X`; `full-offline-mode` ve `beautysalonapp.fx.enabled=false` ile kapatılır. |
| E5 | `full-offline-mode` | ✅ | SMS/e-posta + heartbeat kapatılabilir |
| E6 | H2 `AUTO_SERVER` dinleyicisi loopback'te | ✅ (düzeltildi) | JDBC URL `AUTO_SERVER=TRUE` kullanıyor; H2 ikinci işlem erişimi için bir TCP soketi açar ve bu `server.address: 127.0.0.1`'e tabi değildir. Parolasız `sa` ile makine dışından tam DB erişimi (uygulama/lisans/yetki katmanını atlar) mümkündü. `BeautySalonAppApplication.hardenH2BindAddress()` `main()` içinde, H2 sınıfları yüklenmeden önce `h2.bindAddress=127.0.0.1` ayarlar (operatör `-Dh2.bindAddress=...` ile ezebilir). Regresyon: `H2BindAddressHardeningTest`. Not: paketleme launcher'ına da `-Dh2.bindAddress=127.0.0.1` eklemek savunma derinliği sağlar. |

## 6. Veri güvenliği ve KVKK (plan §8.2–8.3)

| # | Kontrol | Durum | Not |
|---|---|---|---|
| F1 | Hassas alan şifreleme (AES-256-GCM) | ✅ | `FieldCrypto` — `tc_no`, `phone`, `email`, `iban`, özel nitelikli notlar `enc` |
| F2 | Arama için blind index | ✅ | `party.phone_bi` (HMAC), düz metin telefonla sorgu yok |
| F3 | Şifreleme anahtarı yönetimi | ⚠️→✅ (büyük ölçüde) | `CryptoKeyProvider` öncelik sırası: (1) `crypto.key-base64` (OS kasası bu kanaldan enjekte eder), (2) `<dataDir>/config/field-crypto.key` — yoksa **32 baytlık rastgele** anahtar üretilip 0600 izinle yazılır (eski "kurulum kimliğinden türet" tahmin edilebilirliği kalktı), (3) son çare install-id türetimi + UYARI. Regresyon: `CryptoKeyProviderTest`. **Kalan iş (ops):** anahtar dosyasının DPAPI/Keychain ile sarmalanıp veri dizininden çıkarılması — "çalınan veri dizini" tehdidi için hâlâ gerekli. |
| F4 | Yedek şifreleme (AES-256) | ✅ | `ArchiveCrypto`; parola kaybolursa yedek açılmaz — kurulum dokümanında kullanıcıya açık uyarı |
| F5 | Loglara PII yazılmıyor | ✅ | `NoOpSmsProvider` / `NoOpEmailSender` INFO'da yalnızca maskeli numara/adres + karakter sayısı; tam gövde yalnızca `DEBUG`. `SmtpEmailSender` loglamıyor; `HttpSmsProvider` yalnızca ağ geçidi yanıtını logluyor (gövde değil). 2026-09-10 doğrulaması. |
| F6 | Mali kayıtta hard delete yok | ✅ | `voided` + `void_reason`; ArchUnit + kod incelemesi. Ters kayıt deseni (`reverses_id`). |
| F7 | Tam audit log | ✅ | `AuditService.record` — mali işlem, fiyat/indirim değişimi, yedek geri yükleme; `entity_id`/`summary` uzunluk sınırı |
| F8 | Veri taşınabilirliği (dışa aktarma) | ✅ | `LOCKED` durumda bile çalışır (CLAUDE.md #9) — `LicenseEnforcementFilter` export yolunu muaf tutar |
| F9 | Unutulma hakkı (anonimleştirme) | ✅ | `party.anonymized` alanı + akış; mali kayıtlar korunur |
| F10 | İYS izin kontrolü kod düzeyinde | ✅ | `ConsentPolicy` — izinsiz numaraya kampanya SMS'i engellenir (birim testli) |

## 7. Lisanslama (CLAUDE.md #8)

| # | Kontrol | Durum | Not |
|---|---|---|---|
| G1 | İmza doğrulama (Ed25519) | ✅ | `LicenseVerifier` — gömülü public key; bozuk imza → `TAMPERED` (`LicenseVerifierTest` 4 + `LicenseLifecycleTest`) |
| G2 | Monotonik saat (geri alma tespiti) | ✅ | `license_state` şifreli cache + `MonotonicClock` (AES-256-GCM), 6 saat tolerans |
| G3 | Kademeli kısıtlama | ✅ | `ACTIVE→EXPIRING→GRACE→READ_ONLY→LOCKED→TAMPERED` — tüm geçişler `LicenseLifecycleTest` (11) ile sahte saatle test edildi |
| G4 | Bypass edilebilirlik | ⚠️ (kabul) | Plan Risk #3: lisans kırılabilir, aşırı mühendislik yapılmadı; destek ilişkisiyle telafi |
| G5 | Geliştirme modu üretime sızması | ✅ (düzeltildi) | `PackagedLicenseKeyGuard` (`@Profile("packaged")`) — public key boş veya `@LICENSE_PUBLIC_KEY@` token'ı değişmemişse uygulama açılışta durur; "tüm modüller açık" moduna sessiz düşüş engellendi. Geliştirme profillerinde (varsayılan) guard yüklenmez. Regresyon: `PackagedLicenseKeyGuardTest`. |

## 8. Bağımlılık ve tedarik zinciri

| # | Kontrol | Durum | Not |
|---|---|---|---|
| H1 | Bilinen zafiyet taraması | ✅ | `.github/dependabot.yml` (haftalık maven/npm/actions) + `ci.yml` PR'larında `dependency-review-action` (`fail-on-severity: high`). |
| H2 | Spring Boot güncel yama | ✅ | 3.3.6 (Faz 7 tarihinde güncel 3.3.x) |
| H3 | Kripto kütüphanesi | ✅ | BouncyCastle `bcprov-jdk18on:1.78.1` |
| H4 | Derleme reprodüksiyonu | ✅ | `./mvnw` wrapper sabit sürüm; `finalName` sabit |

## 9. Paketleme / işletim

| # | Kontrol | Durum | Not |
|---|---|---|---|
| I1 | Servis en az yetkiyle çalışır | ⚠️ | `--launcher-as-service` LocalSystem/root kurar. **Öneri:** kurulum sonrası ayrı hizmet hesabı (Windows `NT SERVICE\...`, macOS ayrı kullanıcı) — paketleme mühendisi. |
| I2 | Veri dizini izinleri | ⚠️ | `%ProgramData%\BeautySalonApp` / `/Library/Application Support/...` — yalnızca hizmet hesabı + Administrators yazabilmeli. WiX/pkg CustomAction ile ACL. |
| I3 | İmzalı installer | ❌ (bloke) | 2026-09-14 doğrulaması: `sign.sh`/`notarize.sh` tamam ve doğru — yalnızca gerçek sertifika bekliyor (Apple Developer Program $99/yıl → "Developer ID Application/Installer"; Windows EV Code Signing ~$300/yıl → `signtool`). Bu bir **satın alma/kimlik doğrulama işlemidir**, kod asistanı tarafından tamamlanamaz — plan Risk #2. |
| I4 | Güncelleme paketi bütünlüğü | ✅ | `apply-update.sh`: SHA-256 zorunlu + Ed25519 `.sig` (`BSA_UPDATE_PUBKEY_B64URL` → PEM → `openssl pkeyutl -verify -rawin`). openssl round-trip ile doğrulandı. |

\* Apple Developer Program ($99/yıl), Windows EV Code Signing (~$300/yıl).

---

## 10. Özet — yayın öncesi yapılacaklar

**Kod (Faz 7'de kapatıldı):**
1. ✅ `application.yml` → `server.servlet.session.cookie.same-site=strict` (A6)
2. ✅ `application-packaged.yml` → `springdoc.*.enabled=false` (B6)
3. ✅ `NoOpSmsProvider` / `NoOpEmailSender` — gövde/adres yalnızca `DEBUG` (F5)
4. ✅ `apply-update.sh` → Ed25519 `.sig` doğrulaması (I4)
5. ✅ CI'ya Dependabot + `dependency-review-action` (H1)

**Hâlâ açık (kod, izlenmeli):**
- A6 not: `SameSite=Strict` + LAN erişimi senaryosunda cross-site POST gelmez; sorun değil.

**Faz 8'de kapatıldı:**
- C5: `AttachmentService` dosya adı / yol geçişi sanitizasyonu eklendi ve birim testiyle sabitlendi.

**2026-09-10 güvenlik geçişinde kapatıldı:**
- B7: `GET /api/v1/dashboard/today` → `REPORTING_VIEW` (`DashboardApiTest`).
- E6: H2 `AUTO_SERVER` dinleyicisi loopback'e sabitlendi — `hardenH2BindAddress()` (`H2BindAddressHardeningTest`).
- F3 (büyük ölçüde): `CryptoKeyProvider` — rastgele üretilen 0600 anahtar dosyası varsayılan (`CryptoKeyProviderTest`).
- A9: sabit `admin123` kaldırıldı — rastgele üretilip loglanan ilk parola (`BootstrapAdminPasswordTest`).
- G5: `PackagedLicenseKeyGuard` — `packaged` profilinde gömülü key yoksa açılış durur (`PackagedLicenseKeyGuardTest`).
- F5: doğrulandı (NoOp sağlayıcılar maskeli; tam gövde yalnızca DEBUG).

**2026-09-14 güvenlik geçişinde kapatıldı:**
- A7: kritik rollerde 2. faktör (PIN) — parola sonrası PIN kurulum/doğrulama akışı (`PinAuthTest`, `PinPolicyTest`).
- B5 (kısmi): kullanıcı-şube ataması — `docs/adr/0009-kullanici-sube-atamasi.md` (`UserBranchAssignmentTest`). Genel liste uç noktalarının şube filtresi hâlâ ayrı bir iş (ADR 0009 kapsam dışı bölümü).
- KVKK aydınlatma/açık rıza metin taslakları eklendi (`docs/hukuk/`) — avukat onayı hâlâ gerekli, kod dışı.
- Sertifika/pentest/ACL: kod asistanının tamamlayamayacağı kalemler için durum netleştirildi (aşağıdaki liste, I3/I1/I2 notları).

**Kod dışı (operasyon / tedarik):**
6. Apple + Windows imzalama sertifikaları (I3) — **Faz 1'de sipariş edilmeliydi**
7. Şifreleme anahtar dosyasının DPAPI/Keychain ile sarmalanması (F3 kalan işi — "çalınan veri dizini" tehdidi) + paketleme launcher'ına `-Dh2.bindAddress=127.0.0.1` (E6 savunma derinliği)
8. Hizmet hesabı + veri dizini ACL (I1, I2)
9. Bağımsız sızma testi
10. Avukat + KVKK danışmanı ile rıza/aydınlatma metinleri (plan §8.3 notu) — 2026-09-14: `docs/hukuk/` altına taslaklar eklendi (aydınlatma + açık rıza), avukat onayı hâlâ gerekli

## 11. Faz 8 eklemelerinin KVKK/PII taraması (2026-09-04)

Yeni modüller (`modules.reconciliation`, `modules.branch`, e-Fatura `modules.invoice`
eklentisi) tarandı: hiçbir `log.*` (SLF4J) çağrısı PII yazmıyor; yeni DB alanlarından
hiçbiri plandaki şifreleme tetikleyicilerine (TC no, telefon, sağlık/alerji notu, §8.2)
girmiyor — ayrıntılı gerekçe her modülün kendi dokümanında ("KVKK değerlendirmesi" bölümü,
bkz. `docs/modules/banka-mutabakat.md`, `docs/modules/e-fatura.md`). e-Fatura mevcut
şifreli `Party.taxId/tcNo` alanlarını yalnızca **okur**, yeni düz metin kalıcılık eklemez.
`docs/04-guvenlik-kvkk.md` (CLAUDE.md'nin referans verdiği dosya) artık mevcut — bu
oturumda yazıldı; §10'daki "avukat + KVKK danışmanı" adımı hâlâ kod dışı, açık kalıyor.
