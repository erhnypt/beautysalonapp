# Modül: E-posta & SMS Bilgilendirme (`modules.notification`)

Öncelik **P2** · Plan §10.8, §10.9 · Veri modeli §9.10

## Kapsam
Ortak altyapı: `notification_template` + `notification_queue` + `notification_log` +
zamanlayıcı. Kanal soyutlaması: `SmsProvider` / `EmailSender` (adaptör değiştirilebilir).

## Değişmez kurallar
- **İzinsiz ticari ileti engellenir (kod düzeyinde).** `ConsentPolicy`:
  - KAMPANYA (ticari): yalnızca `iys_status = IZINLI` VE ilgili kanal onayı (`sms_consent`/`email_consent`).
  - RANDEVU_HATIRLATMA / TAKSIT / BORC: "bilgilendirme" sayılır; yine de kanal onayı aranır.
  - GUNLUK_RAPOR: yalnızca iç kullanıcı (yönetici e-postası), müşteri onayı aranmaz.
- **İdempotent:** aynı `(party, type, channel, gün)` için ikinci kayıt kuyruğa girmez.
- Başarısız gönderim: exponential backoff, 3 deneme, sonra FAILED.
- **Tam Çevrimdışı Mod**'da modül kapalı; alternatif "yazdırılabilir hatırlatma listesi".
- Giden SMS/SMTP tek dış çağrıdır; `OutboundHttpGuard` allowlist'inden geçer (SMS HTTP).

## Veri modeli
```
notification_template  type, channel = SMS | EMAIL, subject, body ({ad} {tarih} {tutar} …), active
notification_queue     party_id?, to_address, channel, template_id, type,
                       scheduled_at, status = PENDING | SENT | FAILED | SKIPPED,
                       attempts, last_error, dedup_key (uq)
notification_log       queue_id, sent_at, channel, to_masked, type, provider_ref
```

## Tetikleyiciler
| Tetik | Zamanlama |
|---|---|
| Doğum günü | Her sabah 09:00 |
| Evlilik yıldönümü | 09:00 |
| Randevu hatırlatma | Randevudan `reminderHours` önce (ayar) |
| Taksit | Vadeden 3 gün önce + vade günü + gecikmede haftalık |
| Borç bakiyesi | Aylık, ayın N'i, bakiye > eşik |
| Kampanya | Manuel, segment seçimli |
| Günlük rapor | Her gece 23:30, yönetici e-postası |

## Port
```java
interface NotificationPort {
    void notifyAppointmentBooked(long appointmentId);   // ileride kullanılabilir
    void enqueue(NotificationType type, NotificationChannel channel, Long partyId,
                 String toOverride, Map<String,String> vars, Instant scheduledAt);
}
```

## Endpoint taslağı
```
GET/POST /api/v1/notifications/templates
GET      /api/v1/notifications/queue?status=
POST     /api/v1/notifications/test          ({templateId, to})
POST     /api/v1/notifications/campaign      ({templateId, segment, scheduledAt})
POST     /api/v1/notifications/process-now   (kuyruğu hemen işle — bakım)
GET      /api/v1/notifications/reminder-list?date=   (çevrimdışı: yazdırılabilir liste)
```
