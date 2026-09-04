# BeautySalonApp — Lisans Sunucusu

Ayrı deploy edilen Spring Boot uygulaması (kendi VPS'inizde). Müşteri/abonelik yönetimi,
Ed25519 imzalı lisans dosyası üretimi, günlük heartbeat ile yenileme, askıya alma/iptal,
makine transferi ve güncelleme kanalı. **Hiçbir işletme iş verisi tutmaz.** (Teknik plan §6.)

## Çalıştırma (geliştirme)

```bash
cd license-server
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./mvnw spring-boot:run
# Admin panel:  http://localhost:9000/admin   (admin / admin123)
# İstemci API:  http://localhost:9000/api/v1/**
```

İlk açılışta (`allow-generate: true`) yeni bir Ed25519 anahtar çifti üretilip
`./data/ed25519.key` dosyasına **şifreli** yazılır. Public key log'a ve
`GET /api/v1/public-key` çıktısına yazılır — bunu istemci build'ine gömün
(`beautysalonapp.licensing.public-key-base64`).

## Üretim yapılandırması (ortam değişkenleri)

| Değişken | Açıklama |
|---|---|
| `LICENSE_KEY_PASSWORD` | Ed25519 private key dosyasını çözen parola (zorunlu) |
| `LICENSE_ADMIN_USER` / `LICENSE_ADMIN_PASS` | Admin panel kimliği |
| `beautysalonapp.license.allow-generate=false` | Üretimde anahtar ÜRETME; hazır anahtarı sağlayın |
| `beautysalonapp.license.heartbeat-endpoint` | Lisans dosyasına yazılacak heartbeat URL'i |
| PostgreSQL | `spring.datasource.url=jdbc:postgresql://...` + Flyway `locations` |

Private key'i düz tutmayın: dosya PBKDF2 + AES-256-GCM ile şifrelidir; ideali KMS/HSM.

## İstemci API

| Uç | Açıklama |
|---|---|
| `POST /api/v1/activate` | `{activationKey, fingerprint, fpVersion}` → `{license}` (imzalı `.lic` içeriği) |
| `POST /api/v1/heartbeat` | `{licenseId, fingerprint, appVersion, os, nonce}` → `{status, license, serverTime, message, latestVersion, mandatoryUpdate, nonce, responseSignature}` |
| `POST /api/v1/transfer` | `{licenseId, oldFingerprint, newFingerprint}` → otomatik (ayda 1) veya bekleyen talep |
| `GET  /api/v1/updates/latest` | En güncel `stable` sürüm bilgisi |
| `GET  /api/v1/public-key` | İstemciye gömülecek Ed25519 public key (Base64) |

### Durum mantığı
- Abonelik `ACTIVE` ve ödeme güncel → heartbeat `notAfter`'ı 35 gün ileri taşır (`ACTIVE`).
- Abonelik `SUSPENDED` / `PENDING_PAYMENT` (grace dahil aşıldı) → `SUSPENDED` (yenileme yok → istemci kademeli kısıtlar).
- Abonelik `CANCELLED` veya lisans `REVOKED` → `REVOKED`.
- Terminal sayısı `maxTerminals`'ı aşarsa yeni parmak izi **pasif** eklenir + uyarı mesajı döner.

## Admin panel

- Müşteri ekle, lisans sağla (plan + modüller + limitler + ilk ay), ödeme işle.
- Askıya al / aktive et / iptal et.
- Lisans detayında: bağlamalar, son heartbeat'ler, plan/modül düzenleme.
- Bekleyen makine transferi taleplerini onayla/reddet.
- Güncelleme kanalına yeni sürüm ekle.

## Şema
`customer`, `subscription`, `license`, `license_binding`, `heartbeat_log`,
`transfer_request`, `payment_record`, `app_release` (Flyway `V1__license_schema.sql`).

## Test
```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./mvnw test
```
`LicenseFlowTest` uçtan uca kapsar: aktivasyon + imza doğrulama, terminal sınırı,
heartbeat durum geçişleri (ACTIVE/SUSPENDED/REVOKED), ödeme sonrası geri dönüş, transfer.
