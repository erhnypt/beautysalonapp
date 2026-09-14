# Bağımsız Sızma Testi — Kapsam Belgesi (Taslak)

> Bu belge bir sızma testi raporu **değildir** — bir sızma test firmasına/uzmanına
> verilecek **kapsam brifi**dir. Güvenlik gözden geçirme §10'daki "bağımsız sızma
> testi" kalemi, bir kod asistanının kendi kendini test etmesiyle karşılanamaz;
> bu belge o adımı hızlandırmak için hazırlandı. Firma seçimi, sözleşme, NDA ve
> bulguların değerlendirilmesi işletmenin kendi sorumluluğundadır.

## 1. Hedef ve amaç

BeautySalonApp: yerelde (müşterinin kendi bilgisayarında) çalışan, tarayıcı
arayüzlü bir güzellik salonu yönetim yazılımı. Test, aşağıdaki güvenceleri
bağımsız olarak doğrulamalı:

1. `127.0.0.1`/LAN dışından erişilebilir bir açık yok (varsayılan kurulum).
2. Kimlik doğrulama ve yetkilendirme (rol/izin matrisi, PIN 2. faktör) atlanamaz.
3. Hiçbir iş verisi (müşteri listesi, ciro, stok) internete çıkmıyor — yalnızca
   lisans sunucusu ve canlı döviz kuru (Yahoo Finance) allowlist'te, ikisi de
   tek yönlü ve iş verisi taşımıyor.
4. Şifrelenmiş alanlar (TC no, telefon, e-posta, IBAN, özel nitelikli notlar)
   veritabanı dosyasına doğrudan erişimde okunamıyor.
5. Lisans/kısıtlama mekanizması (LOCKED/READ_ONLY) makul çabayla atlanamıyor.

## 2. Kapsam içi

| Bileşen | Nasıl test edilir |
|---|---|
| `server` (Spring Boot, `:8734`) | Kimlik doğrulama, oturum/CSRF, RBAC, girdi doğrulama, dosya yükleme (`/api/v1/attachments`), IDOR (§B5 — bkz. not) |
| `web` (React SPA) | XSS, CSRF, istemci tarafı yetki kontrolü kaçakları |
| Paketlenmiş kurulum (.msi/.dmg) | Servis ayrıcalığı (I1), veri dizini ACL (I2), yerel yükseltme (privilege escalation) |
| H2 veritabanı dosyası + yedek (`.bsa`) arşivi | Çalınan dosyadan veri kurtarma denemesi |
| `license-server` (ayrı repo/servis) | **Kapsam dışı** varsayılan — istenirse ayrıca kapsanmalı, bu depoda değil |

**§B5 notu:** kullanıcı-şube ataması var (`docs/adr/0009-kullanici-sube-atamasi.md`)
ama **genel liste uç noktaları henüz şube filtresi uygulamıyor** — bilinen, belgeli
bir sınırlama; test bunu yeni bir bulgu olarak değil bilinen kapsam olarak
işaretlemeli (aksi hâlde rapor zaten bilinen bir boşluğu tekrar edecek).

## 3. Kapsam dışı (varsayılan)

- Fiziksel güvenlik (ofise/bilgisayara fiziksel erişim).
- Sosyal mühendislik / phishing (istenirse ayrıca fiyatlandırılıp eklenebilir).
- `license-server` ve lisans imzalama anahtarının kendisi (ayrı, daha kısıtlı erişimli sistem).
- DoS/kaynak tükenme testleri (tek makinede çalışan yerel bir uygulama; müşteri
  deneyimini bozmadan test edilebilecek bir hazırlık/onay gerekir).

## 4. Test ortamı

Testçiye şu şekilde bir kurulum sağlanmalı (üretim verisi **değil**):

- Paketlenmiş bir `.msi`/`.dmg` kurulumu (gerçek build; geliştirme modunda değil —
  geliştirme modunda lisans imza doğrulaması kapalıdır, gerçekçi değildir).
- En az şu rollerde birer test kullanıcısı: ADMIN, MUDUR (PIN akışını da kapsasın),
  KASIYER, PERSONEL, RAPOR_OKUYUCU.
- Sentetik/uydurma müşteri, cari, fatura verisi (gerçek müşteri PII'si **asla**
  test ortamına konmamalı).
- İki şube tanımlı, birine kısıtlanmış bir kullanıcı (§B5 senaryosu için).

## 5. Bilinen/kabul edilen riskler (yeniden raporlanmasın)

`docs/guvenlik/guvenlik-gozden-gecirme.md`'de ✅/⚠️ olarak zaten kayıtlı ve kabul
edilmiş kalemler — özellikle:

- G4: lisans sistemi motive/yetenekli bir saldırgan tarafından kırılabilir (kabul edilen risk, aşırı mühendislik yapılmadı).
- Genel liste uç noktalarının şube filtresi yokluğu (§B5, yukarıda not edildi).
- Bootstrap admin ilk parolasının log dosyasına yazılması (A9 — tek seferlik, zorunlu değişimle telafili).

Bu kalemler için bulgu raporlanabilir (ikinci bir göz her zaman değerlidir) ama
"kritik/acil" önceliklendirilmemeli — belgeli, bilinçli kararlar.

## 6. Rapor formatı

Her bulgu için: CVSS benzeri önem derecesi, tekrar üretme adımları, etkilenen
dosya/uç nokta, önerilen düzeltme. `docs/guvenlik/guvenlik-gozden-gecirme.md`
formatındaki tabloya (✅/⚠️/❌ + kanıt) eklenebilecek şekilde teslim edilmesi tercih edilir.
