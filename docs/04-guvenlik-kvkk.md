# Güvenlik ve KVKK Politikası

> CLAUDE.md #10'un referans verdiği belge budur: **"Yeni alan eklerken şifreleme
> gerekip gerekmediğini docs/04-guvenlik-kvkk.md'ye göre değerlendir."** Bu dosya
> plan §8'in (Güvenlik ve KVKK) uygulamaya dönüşmüş halidir — ne yapılacağını değil,
> hangi alanların hangi muameleyi gördüğünü ve yeni bir alan eklerken hangi soruları
> sormanız gerektiğini anlatır.
>
> Kod düzeyindeki kontrollerin madde madde denetim kaydı için:
> [`docs/guvenlik/guvenlik-gozden-gecirme.md`](guvenlik/guvenlik-gozden-gecirme.md)
> (Faz 7 iç gözden geçirme, ✅/⚠️/❌ durumlarıyla).
>
> Bu belge avukat görüşü değildir; sözleşme ve aydınlatma/rıza metinlerini bir
> KVKK danışmanıyla nihaileştirin (plan §8.3 notu, güvenlik gözden geçirme §10 madde 10).

---

## 1. Yeni bir alan eklerken sorulacak sorular

Yeni bir entity alanı (özellikle `party`, `staff`, `contract` gibi kişiyle ilişkili
tablolarda) eklerken şu sırayla karar verin:

1. **Bu alan bir gerçek kişiyi tanımlıyor veya onunla iletişim kurmayı sağlıyor mu?**
   (TC kimlik no, telefon, e-posta, IBAN, adres) → **§2 "Şifrelenmesi zorunlu alanlar"**
   listesine ekleyin, `FieldCrypto` ile şifreleyin.
2. **Bu alan özel nitelikli kişisel veri mi?** (sağlık/alerji/cilt notu, biyometrik/genetik
   veri, dernek/sendika/din/etnik köken bilgisi — KVKK m.6) → Her zaman şifrelenir,
   ayrıca erişimi normal `PARTY_VIEW`'dan daha kısıtlı bir yetkiye bağlamayı değerlendirin.
3. **Bu alan üzerinden arama/filtreleme yapılması gerekiyor mu?** Şifreli bir alanda düz
   metin `LIKE` sorgusu çalışmaz → **§3 "Kör indeks" (blind index)** deseni gerekir
   (bkz. `party.phone_bi`).
4. **Bu alan sadece işletmenin kendi muhasebe/işlem metni mi** (fatura açıklaması, kasa
   hareketi açıklaması, banka ekstresi serbest metni, iptal gerekçesi)? → Şifrelenmez.
   Gerekçe: bu alanlar §8.2'deki tetikleyicilere girmez ve zaten şifrelenmeyen
   `cash_transaction.description` ile aynı niteliktedir (tutarlılık ilkesi — bkz.
   `docs/modules/banka-mutabakat.md` KVKK bölümü, emsal karar).
5. **Bu alan bir `log.*` (SLF4J) çağrısına düşebilir mi?** Asla PII içermemeli — hata
   mesajlarında dahi kimlik/telefon/e-posta yazılmaz, sadece iç ID'ler (`partyId`, `staffId`)
   kullanılır. İstisna: `NoOpSmsProvider`/`NoOpEmailSender` gibi geliştirme amaçlı
   loglayıcılar mesaj gövdesini yalnızca `DEBUG` seviyesinde yazar (Faz 7 düzeltmesi).
6. **Bu alan dışa aktarılabilir kalmalı mı?** Evet — `LOCKED` durumda bile export/backup
   çalışmalıdır (CLAUDE.md #9). Şifreli alanlar export sırasında çözülüp okunabilir
   biçimde sunulur (aksi halde "veri taşınabilirliği" yükümlülüğü karşılanmaz).
7. **Bu alanın saklanma süresi sınırlı mı olmalı?** (bildirim içerik logu gibi) →
   §6 "Saklama süresi" politikasına bakın.

Yeni modül dokümanınızda (`docs/modules/<modul>.md`) kısa bir **"KVKK değerlendirmesi"**
bölümü bırakın — hangi sorulara nasıl cevap verdiğinizi bir sonraki geliştirici (veya
Claude Code) tekrar araştırmasın diye. Mevcut örnekler: `docs/modules/e-fatura.md`,
`docs/modules/banka-mutabakat.md`.

---

## 2. Şifrelenmesi zorunlu alanlar (uygulanmış durum)

| Alan | Tablo | Yöntem | Kod |
|---|---|---|---|
| TC kimlik no | `party.tc_no` | AES-256-GCM | `FieldCrypto` |
| Telefon | `party.phone` | AES-256-GCM + blind index | `FieldCrypto` + `party.phone_bi` |
| E-posta | `party.email` | AES-256-GCM | `FieldCrypto` |
| IBAN | `staff.iban` | AES-256-GCM | `FieldCrypto` |
| Vergi no | `party.tax_id` | AES-256-GCM | `FieldCrypto` |
| Özel nitelikli notlar (alerji, cilt tipi vb.) | `party_note` (kategori bazlı) | AES-256-GCM | `FieldCrypto` |

Anahtar yönetimi: üretimde `beautysalonapp.crypto.key-base64` OS keystore'dan (Windows
DPAPI / macOS Keychain) beslenir; anahtar tanımlı değilse uygulama kurulum kimliğinden
türetilmiş bir anahtarla **yalnızca geliştirme modunda** çalışır ve bunu loga uyarı
olarak yazar (`FieldCrypto` — bkz. ADR 0005 notu). Paketleme bu adımı doğrulamalıdır.

## 3. Kör indeks (blind index) deseni

Şifreli bir alanda eşitlik araması gerekiyorsa (ör. "bu telefon numarasıyla müşteri var
mı?"), alanın **HMAC**'i ayrı bir sütunda (`_bi` eki) tutulur ve sorgular bu sütuna karşı
çalışır — düz metin telefon numarasıyla asla SQL sorgusu yapılmaz. Yeni bir alan için
aranabilirlik gerekiyorsa aynı deseni uygulayın (`party.phone_bi` örneğini kopyalayın).

## 4. Rıza ve İYS (ticari ileti)

| Gereklilik | Uygulama karşılığı |
|---|---|
| Açık rıza (aydınlatma) | `party.sms_consent` / `party.email_consent` + `party.consent_date` |
| İYS izin durumu | `party.iys_status` (`IZINLI` / `IZINSIZ` / `BILINMIYOR`) |
| İzinsiz ticari ileti engeli | `ConsentPolicy` — kampanya SMS/e-postası `iys_status != IZINLI` olan
| | tarafa **kod düzeyinde** engellenir (birim testli, bkz. `ConsentPolicyTest`) |
| Randevu hatırlatma istisnası | Bilgilendirme sayılır (ticari ileti değil), yine de onay alanı
| | tutulur ve kanal bazlı `sms_consent`/`email_consent` kontrolü yapılır |

Yazdırılabilir aydınlatma metni şablonu ve rıza metni sürümlemesi (plan §8.3) **kod dışı
bir iştir** — hukuki metin bir avukat/KVKK danışmanıyla hazırlanıp `config/reports`
altına şablon olarak eklenmelidir; bu depo yalnızca rıza durumunu ve tarihini saklar.

## 5. Veri taşınabilirliği ve unutulma hakkı

- **Taşınabilirlik:** Tüm dışa aktarma/yedekleme uç noktaları `LOCKED` durumda dahi
  çalışır — `LicenseEnforcementFilter`, `/api/v1/backup/`, `/api/v1/export/` ve
  `/api/v1/support/` öneklerini yazma kısıtlamasından muaf tutar (CLAUDE.md #9). Bu
  davranışı bozacak bir değişiklik yaparsanız `LicenseEnforcementFilterTest`'i güncelleyin.
- **Unutulma hakkı:** `PartyService.anonymize(id, reason)` kimlik alanlarını maskeler
  (ad, telefon, e-posta, TC no, vergi no, doğum tarihi, notlar) ve `party.anonymized=true`
  işaretler. **Mali kayıtlar korunur** — CLAUDE.md #3 (hard delete yasak) ile çelişmez;
  anonimleştirme bir maskeleme işlemidir, silme değildir.

## 6. Saklama süresi

Bildirim gönderim içeriği (`notification_log`) süresiz saklanmamalıdır — plan §9.10 bunu
"içerik saklama süresi ayarlanabilir" olarak işaretler. **Bu, henüz otomatikleştirilmiş bir
temizlik işi (scheduled purge) olarak uygulanmadı** — bir yönetici, kayıtları elle veya
doğrudan veritabanı bakımıyla temizleyebilir. Otomatik bir saklama-süresi işi eklerseniz
bu bölümü güncelleyin.

## 7. Loglara PII yazılmaz (CLAUDE.md #10)

- Uygulama kodu `log.*` çağrılarında kimlik/telefon/e-posta/adres **asla** yazmaz;
  yalnızca dahili ID'ler (`partyId`, `staffId`, `invoiceId` vb.) kullanılır.
- Bildirim sağlayıcı adaptörleri (`NoOpSmsProvider`, `NoOpEmailSender`) mesaj gövdesini/
  alıcı adresini yalnızca `DEBUG` seviyesinde ve maskelenmiş biçimde loglar (ör.
  `***0000` — son 4 hane).
- `audit_log` bir loglama kanalı **değildir**; mali işlem/iptal/yedek geri yükleme gibi
  kayıtları kasıtlı olarak serbest metinle tutar (bkz. §8) ve `entity_id`/`summary` uzunluk
  sınırına tabidir (`AuditService`).

## 8. Denetim (audit) kaydı kapsamı

Her mali işlem, fiyat/indirim değişikliği, kasa açılışı/sayımı, yedek geri yükleme ve
lisans/dönem kapama işlemi `AuditService.record(...)` ile iz bırakır — kim, ne zaman,
hangi kayıt. Yeni bir mali/kritik işlem eklerken audit kaydı eklemeyi unutmayın; bu bir
KVKK gerekliliği değil ama veri sorumlusu olarak hesap verebilirliğinizi (ve VERBİS
yükünüzü azaltan teknik belgeyi, plan §8.3) destekler.

## 9. Yeni bir dış ağ çağrısı eklerken (CLAUDE.md #1)

KVKK ile doğrudan ilişkili: giden HTTP çağrıları `OutboundHttpGuard` allowlist'inden
geçmelidir. SMS/e-posta gönderiminde giden veri **yalnızca gönderim anındaki tek alıcı +
tek mesajdır** — toplu müşteri listesi, ciro, stok gibi hiçbir iş verisi dışarı çıkmaz
(§2.1). Yeni bir entegrasyon eklerken bu sınırı PR açıklamasında gerekçelendirin.

## 10. Açık/kod-dışı kalemler

Aşağıdakiler kasıtlı olarak bu depoda **yoktur** — kod değişikliği değil, operasyon/hukuk
gerektirir (bkz. `docs/guvenlik/guvenlik-gozden-gecirme.md` §10 "kod dışı" listesi):

- Avukat/KVKK danışmanıyla nihai rıza ve aydınlatma metinleri.
- Bağımsız sızma testi.
- OS keystore'dan şifreleme anahtarı besleme (paketleme adımı, ADR 0005 notu).
- Hizmet hesabı + veri dizini ACL sıkılaştırması (I1/I2, paketleme mühendisi görevi).
