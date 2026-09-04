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
| A7 | Kritik rollerde 2. faktör (PIN) | ⚠️ | Şema hazır (`app_user.pin_hash`), akış Faz 8'e ertelendi. Kabul edilebilir (yerel masaüstü, tek işletme). |
| A8 | İlk girişte zorunlu parola değişimi | ✅ | Bootstrap admin `must_change_password=true`; SPA yönlendirir |
| A9 | Bootstrap admin parolası | ⚠️ | `admin/admin123` varsayılan. A8 zorunlu değişim ile telafi. Kurulum sihirbazı ilk parolayı almalı (kurulum dokümanına eklendi). |

## 2. Yetkilendirme

| # | Kontrol | Durum | Not |
|---|---|---|---|
| B1 | Rol tabanlı erişim (`@EnableMethodSecurity`) | ✅ | `SecurityConfig`; rol×izin matrisi `RolePermissionCatalog` |
| B2 | Tüm `/api/**` kimlik doğrulaması ister | ✅ | `authorizeHttpRequests` — `permitAll` yalnızca auth/health/docs/statik |
| B3 | Son etkin yönetici korunur | ✅ | `UserService.setEnabled` → `last_admin` kuralı |
| B4 | Yetki kontrolü controller değil servis katmanında | ✅ | Metot güvenliği + ince controller kuralı (CLAUDE.md) |
| B5 | IDOR (nesne düzeyi yetki) | ⚠️ | Tek işletme / `branch_id=1` sabit; çok şubeli senaryoda (v2) `branch_id` filtresi zorunlu olacak. Şimdilik kabul. |
| B6 | Swagger UI üretimde açık | ⚠️ | `/swagger-ui/**` `permitAll`. Yerel bağlı (127.0.0.1) olduğu için düşük risk. **Öneri:** `packaged` profilinde `springdoc.swagger-ui.enabled=false`. |

## 3. Girdi doğrulama ve enjeksiyon

| # | Kontrol | Durum | Not |
|---|---|---|---|
| C1 | SQL enjeksiyonu | ✅ | JPA + parametreli `JdbcTemplate` (`ReportService`, `PerfDataGenerator`). String birleştirme ile sorgu yok. |
| C2 | Bean Validation | ✅ | `spring-boot-starter-validation`; DTO'lar `record` + `@Valid` |
| C3 | Kütle atama (mass assignment) | ✅ | Giriş DTO'ları ayrı `record`'lar; entity doğrudan bağlanmıyor |
| C4 | Dosya yükleme boyutu sınırı | ✅ | `spring.servlet.multipart.max-file-size: 100MB` |
| C5 | Dosya yükleme tür/yol kontrolü | ⚠️ | Ekler `attachments/` altına yazılıyor; dosya adı sanitizasyonu `AttachmentService`'te doğrulanmalı (yol geçişi). İzleme maddesi. |
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
| E4 | Allowlist içeriği | ✅ | Yalnızca `https://license.beautysalonapp.com` (lisans/heartbeat). İş verisi gönderilmiyor. |
| E5 | `full-offline-mode` | ✅ | SMS/e-posta + heartbeat kapatılabilir |

## 6. Veri güvenliği ve KVKK (plan §8.2–8.3)

| # | Kontrol | Durum | Not |
|---|---|---|---|
| F1 | Hassas alan şifreleme (AES-256-GCM) | ✅ | `FieldCrypto` — `tc_no`, `phone`, `email`, `iban`, özel nitelikli notlar `enc` |
| F2 | Arama için blind index | ✅ | `party.phone_bi` (HMAC), düz metin telefonla sorgu yok |
| F3 | Şifreleme anahtarı yönetimi | ⚠️ | Anahtar yoksa kurulum kimliğinden türetiliyor (**yalnızca geliştirme** uyarısı `FieldCrypto`). Üretim: `beautysalonapp.crypto.key-base64` OS keystore'dan (Windows DPAPI / macOS Keychain) beslenmeli — paketleme mühendisi görevi (ADR 0005'e not eklendi). |
| F4 | Yedek şifreleme (AES-256) | ✅ | `ArchiveCrypto`; parola kaybolursa yedek açılmaz — kurulum dokümanında kullanıcıya açık uyarı |
| F5 | Loglara PII yazılmıyor | ⚠️ | Politika var (CLAUDE.md #10). `NoOpSmsProvider` log satırı mesaj metnini yazıyor (müşteri adı içerebilir). **Öneri:** SMS/e-posta gövdesi log seviyesinde maskelensin veya `DEBUG`'a çekilsin. |
| F6 | Mali kayıtta hard delete yok | ✅ | `voided` + `void_reason`; ArchUnit + kod incelemesi. Ters kayıt deseni (`reverses_id`). |
| F7 | Tam audit log | ✅ | `AuditService.record` — mali işlem, fiyat/indirim değişimi, yedek geri yükleme; `entity_id`/`summary` uzunluk sınırı |
| F8 | Veri taşınabilirliği (dışa aktarma) | ✅ | `LOCKED` durumda bile çalışır (CLAUDE.md #9) — `LicenseEnforcementFilter` export yolunu muaf tutar |
| F9 | Unutulma hakkı (anonimleştirme) | ✅ | `party.anonymized` alanı + akış; mali kayıtlar korunur |
| F10 | İYS izin kontrolü kod düzeyinde | ✅ | `ConsentPolicy` — izinsiz numaraya kampanya SMS'i engellenir (birim testli) |

## 7. Lisanslama (CLAUDE.md #8)

| # | Kontrol | Durum | Not |
|---|---|---|---|
| G1 | İmza doğrulama (Ed25519) | ✅ | `LicenseVerifier` — gömülü public key; bozuk imza → `TAMPERED` |
| G2 | Monotonik saat (geri alma tespiti) | ✅ | `license_state` şifreli cache + monotonic clock |
| G3 | Kademeli kısıtlama | ✅ | `ACTIVE→EXPIRING→GRACE→READ_ONLY→LOCKED→TAMPERED` |
| G4 | Bypass edilebilirlik | ⚠️ (kabul) | Plan Risk #3: lisans kırılabilir, aşırı mühendislik yapılmadı; destek ilişkisiyle telafi |
| G5 | Geliştirme modu üretime sızması | ⚠️ | Public key boşsa "tüm modüller açık". Paketleme adımı `@LICENSE_PUBLIC_KEY@` token'ını gömer; **release checklist'te doğrulama maddesi var** (`docs/17-faz7-sertlestirme.md`). |

## 8. Bağımlılık ve tedarik zinciri

| # | Kontrol | Durum | Not |
|---|---|---|---|
| H1 | Bilinen zafiyet taraması | ⚠️ | CI'da `mvn org.owasp:dependency-check-maven:check` veya GitHub Dependabot etkinleştirilmeli (release öncesi). |
| H2 | Spring Boot güncel yama | ✅ | 3.3.6 (Faz 7 tarihinde güncel 3.3.x) |
| H3 | Kripto kütüphanesi | ✅ | BouncyCastle `bcprov-jdk18on:1.78.1` |
| H4 | Derleme reprodüksiyonu | ✅ | `./mvnw` wrapper sabit sürüm; `finalName` sabit |

## 9. Paketleme / işletim

| # | Kontrol | Durum | Not |
|---|---|---|---|
| I1 | Servis en az yetkiyle çalışır | ⚠️ | `--launcher-as-service` LocalSystem/root kurar. **Öneri:** kurulum sonrası ayrı hizmet hesabı (Windows `NT SERVICE\...`, macOS ayrı kullanıcı) — paketleme mühendisi. |
| I2 | Veri dizini izinleri | ⚠️ | `%ProgramData%\BeautySalonApp` / `/Library/Application Support/...` — yalnızca hizmet hesabı + Administrators yazabilmeli. WiX/pkg CustomAction ile ACL. |
| I3 | İmzalı installer | ❌ (bloke) | Gerçek sertifika gerek* — plan Risk #2. `sign.sh`/`notarize.sh` hazır. |
| I4 | Güncelleme paketi bütünlüğü | ⚠️ | `apply-update.sh` SHA-256 doğrular; Ed25519 `.sig` doğrulaması `TODO` (gömülü lisans public key ile tamamlanacak). |

\* Apple Developer Program ($99/yıl), Windows EV Code Signing (~$300/yıl).

---

## 10. Özet — yayın öncesi yapılacaklar

**Kod (bu depoda kapatılabilir):**
1. `application.yml` → `server.servlet.session.cookie.same-site=strict` (A6)
2. `application-packaged.yml` → `springdoc.swagger-ui.enabled=false` (B6)
3. `NoOpSmsProvider` / e-posta gönderici log satırlarında gövde maskeleme veya `DEBUG` (F5)
4. `apply-update.sh` → Ed25519 `.sig` doğrulaması (I4)
5. CI'ya OWASP Dependency-Check / Dependabot (H1)

**Kod dışı (operasyon / tedarik):**
6. Apple + Windows imzalama sertifikaları (I3) — **Faz 1'de sipariş edilmeliydi**
7. OS keystore'dan şifreleme anahtarı besleme (F3)
8. Hizmet hesabı + veri dizini ACL (I1, I2)
9. Bağımsız sızma testi
10. Avukat + KVKK danışmanı ile rıza/aydınlatma metinleri (plan §8.3 notu)
