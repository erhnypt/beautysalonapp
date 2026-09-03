# ADR 0004 — Yedek arşiv biçimi: uygulamaya özgü AES-GCM kap

- **Durum:** Kabul edildi (v1)
- **Tarih:** 2026-09-04

## Bağlam
Plan §11.1 "AES-256 şifreli ZIP" istiyor. Java standart kütüphanesi şifreli ZIP
üretmez; standart AES-ZIP için `zip4j` gibi bir bağımlılık gerekir (CLAUDE.md:
yeni bağımlılık için önce sor).

## Karar
v1'de yedek şu biçimde saklanır:

```
düz ZIP (manifest.json + db/dump.sql + attachments/ + config/)
  → ArchiveCrypto.encrypt: "BSABKP1" || salt(16) || iv(12) || AES-256-GCM(ciphertext+tag)
  → dosya: *.bsa
```

- Anahtar: PBKDF2-HMAC-SHA256 (210k tur) ile paroladan türetilir.
- DB dökümü H2 `SCRIPT DROP TO` çıktısıdır (taşınabilir SQL). PostgreSQL profili
  eklendiğinde `pg_dump` yolu bu sınıfa eklenecek.
- `license.lic` ve `install-id` yedeğe **girmez** (§11.1; makineye özgü / gizli).
- Geri yükleme yalnızca uygulama sihirbazından: `DROP ALL OBJECTS` + dökümü çalıştır
  + Flyway migrate. İşlem sonrası yeniden başlatma önerilir.

## Sonuçlar
- Yedek 7-Zip ile açılamaz; yalnızca uygulama geri yükler. Müşteriye bu açıkça
  anlatılır; "tam veri dışa aktarma" ihtiyacı ayrıca Excel/CSV/PDF dışa aktarma
  ile karşılanır (§6.4).
- Faz 7'de standart AES-ZIP istenirse `zip4j` (Apache-2.0) değerlendirilecek;
  `ArchiveCrypto` arayüzü sabit kalır.
