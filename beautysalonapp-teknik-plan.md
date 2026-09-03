# BeautySalonApp — Teknik Plan ve Geliştirme Rehberi

> Masaüstünde çalışan, veriyi yerelde tutan, tarayıcı arayüzlü, Windows + macOS destekli,
> uzaktan kurulabilen ve uzaktan durdurulabilen (lisanslı/abonelikli) güzellik merkezi
> yönetim yazılımı.
>
> Bu doküman Claude Code ile geliştirme yapılacak şekilde yazılmıştır.
> Sürüm: 1.0 · Tarih: 2026-09-03

---

## İÇİNDEKİLER

1. [Ürün Özeti ve Hedefler](#1-ürün-özeti-ve-hedefler)
2. [Kritik Kısıtlar ve Tasarım Kararları](#2-kritik-kısıtlar-ve-tasarım-kararları)
3. [Mimari Genel Bakış](#3-mimari-genel-bakış)
4. [Teknoloji Yığını ve Gerekçeleri](#4-teknoloji-yığını-ve-gerekçeleri)
5. [Paketleme, Kurulum ve Güncelleme](#5-paketleme-kurulum-ve-güncelleme)
6. [Lisanslama ve Uzaktan Durdurma Sistemi](#6-lisanslama-ve-uzaktan-durdurma-sistemi)
7. [Üçüncü Parti Lisans Uyumluluğu](#7-üçüncü-parti-lisans-uyumluluğu)
8. [Güvenlik ve KVKK](#8-güvenlik-ve-kvkk)
9. [Veri Modeli](#9-veri-modeli)
10. [Modül Spesifikasyonları](#10-modül-spesifikasyonları)
11. [Yedekleme Sistemi](#11-yedekleme-sistemi)
12. [Merkezi İşletme Sistemi (Çok Şubeli Yapı)](#12-merkezi-i̇şletme-sistemi-çok-şubeli-yapı)
13. [Uzaktan Kurulum ve 7/24 Destek](#13-uzaktan-kurulum-ve-724-destek)
14. [Raporlama ve Günlük Analiz](#14-raporlama-ve-günlük-analiz)
15. [Proje Yapısı (Monorepo)](#15-proje-yapısı-monorepo)
16. [Claude Code ile Geliştirme Rehberi](#16-claude-code-ile-geliştirme-rehberi)
17. [Yol Haritası ve Fazlar](#17-yol-haritası-ve-fazlar)
18. [Test Stratejisi](#18-test-stratejisi)
19. [Risk Listesi](#19-risk-listesi)
20. [Açık Sorular / Karar Bekleyen Konular](#20-açık-sorular--karar-bekleyen-konular)

---

## 1. ÜRÜN ÖZETİ VE HEDEFLER

### 1.1 Nedir?

Güzellik merkezi / kuaför / estetik işletmeleri için **ön muhasebe + randevu + CRM + sadakat**
yazılımı. İşletmenin kendi bilgisayarında çalışır, veriler dışarı çıkmaz, arayüz tarayıcıdan
(`http://localhost:8080`) açılır.

### 1.2 Fonksiyonel Kapsam (Modüller)

| # | Modül | Öncelik |
|---|-------|---------|
| 0 | Çekirdek: kullanıcı/rol, ayarlar, lisans, yedekleme, log | P0 |
| 1 | Stok Takip (çoklu barkod, çoklu birim, vitrin/depo) | P0 |
| 2 | Personel Hesapları (maaş, avans, prim, verimlilik) | P1 |
| 3 | Müşteri / Satıcı Cari Takip (+ perakende cari ayrımı) | P0 |
| 4 | Gelir–Gider Hesapları (hizmet kartları dahil) | P0 |
| 5 | Fatura İşlemleri (alış/satış/perakende + ZK fiş no) | P1 |
| 6 | Kasa Takibi (tahsilat, tediye, döviz, dekont) | P0 |
| 7 | Banka / POS / Çek Hesapları | P1 |
| 8 | E-posta Bilgilendirme | P2 |
| 9 | SMS Bilgilendirme | P2 |
| 10 | Frondex Randevu Takip Sistemi | P0 |
| 11 | Kartlı Promosyon / Sadakat (PPOS) | P2 |
| 12 | Satış Sözleşmesi + Otomatik Taksitlendirme + Vade Takibi | P0 |
| 13 | Günlük Analiz / Raporlama Merkezi | P1 |
| 14 | Merkezi İşletme Yönetimi (çok şube) | P3 |

### 1.3 Ticari Model

- **Kurulum ücreti** (tek seferlik) + **aylık abonelik**.
- Abonelik ödenmezse yazılım kademeli olarak kısıtlanır ve sonunda durur.
- Plan bazlı modül açma/kapama (Basic / Pro / Enterprise).

### 1.4 Kalite Hedefleri

- Tek terminalde 10 yıllık veriyle liste ekranları < 300 ms.
- Kurulum: teknik bilgisi olmayan kullanıcı için tek `.msi` / `.dmg`, "İleri → İleri → Bitti".
- Veri kaybı toleransı sıfır: her gün otomatik yedek + yedek doğrulama.
- Çevrimdışı çalışabilirlik: internet kesildiğinde iş durmaz (grace period).

---

## 2. KRİTİK KISITLAR VE TASARIM KARARLARI

Mimarinin tamamı aşağıdaki üç kararın üstüne kuruluyor. En baştan netleştirin.

### 2.1 Veri Sınırı — Neyin Dışarı Çıkıp Çıkmadığı

**Kural: müşteri bilgileri ve iş verisi internete çıkmaz.** SMS/e-posta bunun istisnası değil;
orada dışarı giden şey bir veritabanı kaydı değil, gönderim anında oluşturulan tek bir mesajdır
(alıcı numarası + metin). Yine de sınırın kodda nerede olduğu belirsiz kalmamalı, çünkü zamanla
"bir rapor da gönderelim" gibi eklemeler bu sınırı fark edilmeden aşındırır. Bu yüzden
**veri sınıflandırması** yapıyoruz:

| Veri sınıfı | Dışarı çıkar mı? |
|---|---|
| Müşteri kayıtları, cari hesaplar, ciro, stok, fatura, randevu geçmişi | **Asla** |
| SMS için: tek bir telefon numarası + tek mesaj metni (gönderim anında) | Evet, sadece SMS sağlayıcısına |
| E-posta için: alıcı adresi + mesaj gövdesi (gönderim anında) | Evet, sadece SMTP sunucusuna |
| Lisans doğrulama: lisans no + makine parmak izi + sürüm | Evet, sadece lisans sunucusuna |

**Kural:** Uygulamanın giden ağ trafiği bir **allowlist** ile sınırlanır. Kod içinde
`OutboundHttpGuard` adında merkezî bir kontrol noktası olur; allowlist dışına HTTP isteği
atılması derleme/testte engellenir.

```
İzinli hedefler (varsayılan):
  - https://license.beautysalonapp.com     (lisans)
  - https://api.<sms-saglayici>.com      (SMS, kullanıcı açarsa)
  - smtp://<musterinin-kendi-smtp>       (e-posta, kullanıcı açarsa)
Bunun dışındaki her şey: RED.
```

Ayrıca opsiyonel bir **"Tam Çevrimdışı Mod"** ayarı bulunur: interneti hiç olmayan
işletmelerde SMS/e-posta modülleri devre dışı kalır ve lisans doğrulama dosya tabanlı yönteme
düşer (bkz. §6.6). İnterneti olan müşteriler için varsayılan kapalıdır.

### 2.2 Yerel Çalışma ↔ Uzaktan Durdurma

Tamamen çevrimdışı bir yazılımı uzaktan anlık durdurmak fiziksel olarak mümkün değil.
Çözüm **imzalı, süreli lisans** + **heartbeat** kombinasyonu:

- Lisans dosyası son kullanma tarihi taşır (örn. 35 gün).
- Uygulama günde bir kez lisans sunucusuna küçük bir "heartbeat" atar, sunucu lisansı yeniler.
- Ödeme yapılmazsa sunucu yenilemeyi keser → lisans süresi dolar → yazılım kısıtlanır.
- Acil durumda sunucu `SUSPENDED` döner → uygulama bir sonraki heartbeat'te kilitlenir.
- İnternet hiç yoksa: müşteriye e-posta/WhatsApp ile aylık `.lic` dosyası gönderilir, elle yüklenir.

**Gecikme kabulü:** "Uzaktan anlık durdurma" değil, "en fazla `graceDays` gecikmeyle durdurma"
garantisi verilir. Bu, tüm masaüstü lisanslama ürünlerinin standardıdır.

### 2.3 Masaüstü Uygulaması ↔ Tarayıcı Arayüzü

Çözüm: **yerel sunucu + tarayıcı arayüzü**. Uygulama makinede bir Windows Service /
launchd daemon olarak çalışır, `127.0.0.1:8734` üzerinde HTTP servis eder. Masaüstünde bir
kısayol tarayıcıyı açar. Bonus: aynı ağdaki ikinci bir terminal (resepsiyon tableti gibi)
`http://192.168.1.10:8734` ile bağlanabilir — ama bu **açıkça izin verilmesi gereken** bir ayardır.

---

## 3. MİMARİ GENEL BAKIŞ

```
┌──────────────────────────────────────────────────────────────────────┐
│  İŞLETME BİLGİSAYARI (Windows / macOS)                               │
│                                                                      │
│   ┌───────────────┐        ┌──────────────────────────────────────┐ │
│   │   Tarayıcı    │ HTTP   │  beautysalonapp-server (Java 21 / Spring)  │ │
│   │  (Chrome/     │◄──────►│                                      │ │
│   │   Safari/Edge)│  :8734 │  ┌────────────────────────────────┐  │ │
│   └───────────────┘        │  │ REST API  (/api/v1/**)         │  │ │
│                            │  │ Statik SPA (React build)       │  │ │
│   ┌───────────────┐        │  │ Zamanlanmış işler (Quartz)     │  │ │
│   │ Barkod okuyucu│        │  │ Lisans motoru                  │  │ │
│   │ Fiş yazıcı    │◄──────►│  │ Yedekleme motoru               │  │ │
│   │ Kart okuyucu  │  local │  │ Bildirim kuyruğu (SMS/mail)    │  │ │
│   └───────────────┘ bridge │  └────────────────────────────────┘  │ │
│                            └───────────────┬──────────────────────┘ │
│                                            │ JDBC                    │
│                            ┌───────────────▼──────────────────────┐ │
│                            │  Veritabanı (PostgreSQL / H2 file)   │ │
│                            │  %PROGRAMDATA%\BeautySalonApp\data         │ │
│                            └───────────────┬──────────────────────┘ │
│                                            │                        │
│                            ┌───────────────▼──────────────────────┐ │
│                            │  Yedekler (yerel + ağ + USB)         │ │
│                            └──────────────────────────────────────┘ │
└──────────────────────────┬───────────────────────────────────────────┘
                           │ Sadece: lisans heartbeat, SMS, SMTP
                           ▼
              ┌────────────────────────────┐
              │ license.sirketiniz.com     │  ← sizin sunucunuz
              │  - lisans üretimi/yenileme │
              │  - abonelik/askıya alma    │
              │  - sürüm/güncelleme kanalı │
              └────────────────────────────┘
```

### 3.1 Katmanlar (Backend)

```
com.beautysalonapp
├── config/           Spring konfigürasyonu, güvenlik, CORS, scheduler
├── core/             Ortak: BaseEntity, para tipi, tarih, hata modeli, audit
├── licensing/        Lisans doğrulama, heartbeat, kilitleme, feature flag
├── security/         Kimlik doğrulama, rol/yetki, oturum, şifreleme
├── backup/           Yedek alma/geri yükleme/doğrulama
├── notification/     SMS + e-posta kuyruğu, şablonlar
├── modules/
│   ├── stock/        (domain, application, infrastructure, web)
│   ├── staff/
│   ├── party/        cari: müşteri + satıcı + perakende cari
│   ├── finance/      gelir-gider, kasa, banka, pos, çek
│   ├── invoice/
│   ├── appointment/
│   ├── contract/     satış sözleşmesi + taksitlendirme
│   ├── loyalty/      kartlı promosyon (PPOS)
│   └── reporting/    günlük analiz, raporlar
└── BeautySalonAppApplication.java
```

Her modül içinde **hexagonal / ports-adapters** dizilimi:
`domain` (saf iş kuralları) → `application` (use-case servisleri) → `infrastructure`
(JPA repository, dış entegrasyon) → `web` (REST controller + DTO).
Modüller birbirini **sadece application katmanındaki arayüzler üzerinden** çağırır.
Bunu `spring-modulith` ile derleme zamanında zorunlu kılın.

---

## 4. TEKNOLOJİ YIĞINI VE GEREKÇELERİ

### 4.1 Önerilen Yığın

| Katman | Seçim | Neden |
|---|---|---|
| Dil / Runtime | **Java 21 (LTS)** | Deneyiminizle birebir örtüşüyor; virtual threads; jpackage ile native paket |
| Framework | **Spring Boot 3.3+** | Modüler yapı, Quartz, validation, güvenlik, olgun ekosistem |
| Modülerlik | **Spring Modulith** | Modül sınırlarını derlemede korur, monolitin çöpe dönmesini engeller |
| Persistence | **Spring Data JPA + Hibernate** | Bildiğiniz araç; ayrıca raporlar için **jOOQ** veya `JdbcTemplate` |
| Migration | **Flyway** | Sürüm yükseltmelerinde şema geçişi zorunlu |
| Veritabanı | **PostgreSQL 16 (portable/embedded)** ana seçenek · **H2 v2 (file)** hafif seçenek | Bkz. §4.2 |
| Frontend | **React 18 + TypeScript + Vite** | Claude Code ile hızlı üretim, geniş ekosistem |
| UI kit | **Tailwind CSS + shadcn/ui** (MIT) | Lisans temiz, özelleştirilebilir |
| Tablo | **TanStack Table** (MIT) | AG Grid Enterprise'dan kaçının (ticari lisans) |
| Grafik | **Recharts** veya **Chart.js** (MIT) | Highcharts/amCharts ticari — kullanmayın |
| State/Data | **TanStack Query + Zustand** | Basit ve yeterli |
| Rapor/PDF | **JasperReports** (LGPL) veya **OpenPDF** (LGPL/MPL) | iText 7 AGPL — kaçının |
| Excel | **Apache POI** (Apache-2.0) | Temiz |
| Barkod | **ZXing** (Apache-2.0) | Barkod üretimi/okuma |
| Kripto | **BouncyCastle** (MIT-benzeri) | Ed25519 imza, AES-GCM |
| Zamanlama | **Quartz** (Apache-2.0) | Yedek, heartbeat, hatırlatma işleri |
| Paketleme | **jpackage** (JDK 21) + **jlink** | Bundled JRE ile .msi ve .dmg |
| JDK dağıtımı | **Eclipse Temurin** veya **BellSoft Liberica** | Gömme hakkı net (bkz. §7) |
| Servis | **WinSW** (Windows) / **launchd** (macOS) | Arka planda otomatik başlatma |
| Test | JUnit 5, Testcontainers, ArchUnit, Playwright | |

### 4.2 Veritabanı Kararı

| Kriter | H2 v2 (file mode) | SQLite | PostgreSQL portable |
|---|---|---|---|
| Kurulum kolaylığı | ⭐⭐⭐ JAR içinde | ⭐⭐⭐ | ⭐⭐ ek servis |
| Çok terminal (LAN) | ⚠️ zayıf | ❌ | ⭐⭐⭐ |
| Bozulma riski | orta | düşük | çok düşük |
| Yedek/geri yükleme | dosya kopyası | dosya kopyası | `pg_dump` |
| Raporlama gücü | orta | orta | ⭐⭐⭐ |
| Lisans | MPL 2.0 / EPL 1.0 ✅ | Public Domain ✅ | PostgreSQL Lic. ✅ |

**Öneri:**
- **Tek terminal / Basic plan → H2 v2 file mode.** Sıfır kurulum, `.mv.db` tek dosya.
- **Çok terminal / Pro–Enterprise → gömülü PostgreSQL.** Kurulum sihirbazı otomatik kurar.

Kod tarafında bu farkı gizlemek için: **JPA + Flyway'in `db/migration/{h2,postgres}` ayrımı** ve
veritabanına özgü SQL'i `@Profile` ile ayırın. Native SQL yazmaktan mümkün olduğunca kaçının.

> **Karar notu:** Baştan PostgreSQL ile tek yol gitmek bakım maliyetini yarıya indirir.
> İki veritabanını da desteklemek "iki ürün" bakmak demektir. Sadece kurulum kolaylığı çok
> kritikse çift destek yapın.

### 4.3 Alternatif Yığın (değerlendirilip elenen)

| Alternatif | Neden elendi |
|---|---|
| Electron + Node/TS | Java deneyiminiz kullanılamaz; RAM tüketimi; 150 MB+ paket |
| Tauri + Rust | Öğrenme eğrisi; ekosistem ön muhasebe için zayıf |
| .NET 8 + Blazor | macOS desteği ve dağıtım Java'dan zahmetli değil ama sizin için yeni |
| JavaFX masaüstü | "Tarayıcıda çalışsın" isteğini karşılamıyor |
| Vaadin (Java-only UI) | Ticari lisans katmanları + Claude Code ile React kadar verimli değil |

---

## 5. PAKETLEME, KURULUM VE GÜNCELLEME

### 5.1 Çıktı Artefaktları

```
beautysalonapp-1.0.0-windows-x64.msi     ← jpackage, bundled JRE, WinSW servisi
beautysalonapp-1.0.0-macos-arm64.dmg     ← jpackage, imzalı + notarized
beautysalonapp-1.0.0-macos-x64.dmg
beautysalonapp-1.0.0.jar                 ← teknik destek / debug için
checksums.txt + imzalar
```

### 5.2 Dizin Yerleşimi

**Windows**
```
C:\Program Files\BeautySalonApp\        binary + JRE (salt okunur)
C:\ProgramData\BeautySalonApp\
    ├─ data\          veritabanı
    ├─ config\        application-local.yml, license.lic
    ├─ backups\       otomatik yedekler
    ├─ logs\
    └─ attachments\   müşteri fotoğrafı, sözleşme taraması
```

**macOS**
```
/Applications/BeautySalonApp.app
/Library/Application Support/BeautySalonApp/{data,config,backups,logs,attachments}
```

### 5.3 Kurulum Akışı (ilk çalıştırma sihirbazı)

1. Dil / bölge / para birimi (TRY varsayılan)
2. İşletme bilgileri (unvan, vergi no, adres, logo)
3. **Lisans anahtarı girişi** → online aktivasyon veya `.lic` dosyası yükleme
4. Veritabanı seçimi (otomatik / gelişmiş)
5. Yönetici kullanıcı oluşturma (güçlü parola zorunlu)
6. Yedekleme klasörü ve saati
7. Opsiyonel: SMS/e-posta ayarları, tarayıcı seçimi
8. Bitiş → servis başlatılır, tarayıcı açılır

### 5.4 Sessiz Kurulum (uzaktan kurulum için)

```powershell
msiexec /i beautysalonapp-1.0.0-windows-x64.msi /qn ^
  LICENSE_KEY=BSA-XXXX-XXXX-XXXX-XXXX ^
  INSTALL_MODE=SINGLE ^
  BACKUP_DIR="D:\Yedek\BeautySalonApp" ^
  /l*v install.log
```

```bash
sudo installer -pkg BeautySalonApp.pkg -target /
sudo /Applications/BeautySalonApp.app/Contents/MacOS/setup-cli \
     --license-key BSA-XXXX-XXXX-XXXX-XXXX --unattended
```

### 5.5 Güncelleme

- Uygulama, lisans heartbeat cevabında `latestVersion` ve `updateUrl` alır.
- Kullanıcıya "Yeni sürüm var" bildirimi; **güncelleme öncesi otomatik yedek zorunlu**.
- Güncelleme paketi indirilir, imzası doğrulanır (Ed25519), servis durdurulur, kurulur, Flyway migration çalışır.
- **Geri alma planı:** başarısız migration'da yedekten otomatik dönüş.
- Zorunlu güncelleme bayrağı (`mandatory: true`) — kritik güvenlik yamaları için.

---

## 6. LİSANSLAMA VE UZAKTAN DURDURMA SİSTEMİ

Bu bölüm ürünün ticari kalbi. Dikkatli uygulayın.

### 6.1 Bileşenler

```
┌────────────────────┐        ┌──────────────────────────┐
│ Lisans Sunucusu    │        │ Müşteri kurulumu         │
│ (Spring Boot,      │        │                          │
│  sizin VPS'inizde) │        │  LicenseService          │
│                    │◄──────►│   ├ doğrula (Ed25519)    │
│  - müşteri/abonelik│  HTTPS │   ├ parmak izi kontrol   │
│  - lisans üretimi  │  günde │   ├ süre + grace kontrol │
│  - askıya al/iptal │  1 kez │   ├ saat manipülasyonu   │
│  - imzalama (Ed25519)       │   └ feature flag dağıtımı│
└────────────────────┘        └──────────────────────────┘
```

### 6.2 Lisans Dosyası Formatı

`license.lic` = Base64(`payload.json`) + `.` + Base64(Ed25519 imza)

```json
{
  "v": 1,
  "licenseId": "LIC-2026-000123",
  "customerName": "Güzellik Merkezi A.Ş.",
  "taxId": "1234567890",
  "plan": "PRO",
  "issuedAt": "2026-09-01T00:00:00Z",
  "notBefore": "2026-09-01T00:00:00Z",
  "notAfter":  "2026-10-06T00:00:00Z",
  "graceDays": 7,
  "modules": ["STOCK","STAFF","PARTY","FINANCE","INVOICE",
              "APPOINTMENT","CONTRACT","REPORTING","SMS","EMAIL"],
  "limits": {
    "maxTerminals": 3,
    "maxBranches": 1,
    "maxActiveUsers": 10,
    "maxCustomers": null
  },
  "machineBinding": [
    { "fpVersion": 2, "hash": "9f2c…", "boundAt": "2026-09-01T10:12:00Z" }
  ],
  "heartbeat": { "required": true, "intervalHours": 24, "endpoint": "https://license.example.com/api/v1/heartbeat" },
  "offlineMode": false
}
```

**Public key uygulamaya gömülü**, private key sadece sizin sunucunuzda. Uygulama içindeki
public key'i doğrudan sabit string yapmayın; obfuscation + integrity check ekleyin (bkz. §6.7).

### 6.3 Makine Parmak İzi (fingerprint)

Tek bir donanım kimliğine bağlamak riskli (RAM değişince lisans kırılır). **3'ten 2 eşleşme**
kuralı kullanın:

| Bileşen | Windows | macOS |
|---|---|---|
| Makine UUID | `wmic csproduct get uuid` / WMI | `IOPlatformUUID` |
| Anakart/seri | `Win32_BaseBoard.SerialNumber` | `IOPlatformSerialNumber` |
| İlk kurulum GUID | uygulama üretir, `config` altında saklar | aynı |

```
fingerprint = SHA-256( normalize(uuid) || "|" || normalize(board) || "|" || installGuid )
partialMatch: 3 bileşenden en az 2'si eşleşiyorsa geçerli say, sunucuya "drift" bildir.
```

Donanım tamamen değişirse → **lisans transferi** akışı: müşteri panelden/telefonla talep eder,
siz eski bağlamayı iptal eder yeni lisans üretirsiniz. Ayda 1 otomatik transfer hakkı verin,
fazlası manuel onaya düşsün.

### 6.4 Kademeli Kısıtlama (degradation ladder)

| Durum | Koşul | Davranış |
|---|---|---|
| `ACTIVE` | Lisans geçerli | Tam işlevsellik |
| `EXPIRING` | Bitişe ≤ 7 gün | Üstte sarı uyarı bandı, günlük hatırlatma |
| `GRACE` | `notAfter` geçti, `graceDays` içinde | Kırmızı bant + her girişte modal; işlem yapılabilir |
| `READ_ONLY` | Grace bitti / sunucu `SUSPENDED` | Yeni kayıt/işlem YOK. Görüntüleme, rapor, **yedek alma ve veri dışa aktarma VAR** |
| `LOCKED` | READ_ONLY'den 30 gün sonra veya `REVOKED` | Sadece: lisans girişi ekranı + **tam veri dışa aktarma (Excel/CSV/PDF/yedek)** |
| `TAMPERED` | Saat geri alma / imza bozulması tespiti | READ_ONLY + zorunlu online doğrulama |

> **Etik ve hukuki kural — pazarlık konusu değil:** Hiçbir durumda müşterinin verisi
> silinmez, şifrelenip rehin alınmaz veya erişilemez hale getirilmez. `LOCKED` durumunda dahi
> müşteri kendi verisini eksiksiz dışa aktarabilmelidir. Bu hem KVKK (veri taşınabilirliği)
> gereği hem de sözleşme hukuku açısından sizi korur. Sözleşmenizde bu maddeyi açıkça yazın.

### 6.5 Heartbeat Protokolü

**İstek** (`POST /api/v1/heartbeat`) — gönderilen veri minimumda tutulur:
```json
{
  "licenseId": "LIC-2026-000123",
  "fingerprint": "9f2c…",
  "appVersion": "1.4.2",
  "os": "Windows 11 / 10.0.22631",
  "lastHeartbeatAt": "2026-09-02T06:00:00Z",
  "counters": { "activeUsers": 4, "terminals": 2 },
  "nonce": "b7a1…"
}
```
> İçinde müşteri adı, ciro, randevu, stok gibi **hiçbir iş verisi yoktur.** Bunu ürün
> dokümantasyonunda ve satış sözleşmesinde açıkça belirtin; en güçlü satış argümanınız bu.

**Cevap**
```json
{
  "status": "ACTIVE",             // ACTIVE | SUSPENDED | REVOKED
  "license": "<yeni imzalı lisans dosyası>",
  "serverTime": "2026-09-03T06:00:03Z",
  "message": null,                 // müşteriye gösterilecek metin (ör. ödeme hatırlatma)
  "latestVersion": "1.4.3",
  "mandatoryUpdate": false,
  "nonce": "b7a1…"                 // istekle aynı olmalı (replay koruması)
}
```

Kurallar:
- Heartbeat **başarısız olursa iş durmaz** — sadece `lastSuccessfulHeartbeat` güncellenmez.
- Sunucu yanıtı **imzalı** olmalı; nonce eşleşmesi zorunlu (replay saldırısı koruması).
- Rastgele jitter (0–120 dk) ekleyin ki tüm kurulumlar aynı anda vurmasın.
- Sunucu erişilemezken 3 kez üst üste başarısızlık → kullanıcıya bilgi mesajı (suçlayıcı değil, bilgilendirici).

### 6.6 Tam Çevrimdışı Mod

İnterneti hiç olmayan müşteriler için:
- `offlineMode: true` lisans üretilir, `notAfter` = 1 ay sonrası.
- Her ay yeni `.lic` dosyasını e-posta/WhatsApp ile gönderirsiniz; kullanıcı
  **Ayarlar → Lisans → Dosyadan Yükle** ile yükler.
- Ödeme durursa yeni dosya göndermezsiniz → süre dolar → kademeli kısıtlama devreye girer.
- Bu modda `graceDays` daha uzun tutulur (örn. 14 gün), çünkü dosya gecikmesi olabilir.

### 6.7 Kurcalamaya Karşı Önlemler

| Tehdit | Önlem |
|---|---|
| Sistem saatini geri alma | DB'de ve `config` altında AES-GCM ile şifreli **monotonic clock**: gördüğü en büyük zamanı saklar. `now < lastSeen - 6h` ise `TAMPERED`. Heartbeat cevabındaki `serverTime` ile senkron. |
| `license.lic` kopyalama (2. makineye) | machineBinding + sunucu tarafında aynı `licenseId` için farklı fingerprint sayısı > `maxTerminals` ise uyarı/askıya alma |
| Lisans dosyasını elle düzenleme | Ed25519 imza doğrulaması |
| DNS ile lisans sunucusunu sahteleme | Sertifika pinning + yanıt imzası |
| JAR decompile + kontrol atlama | ProGuard/obfuscation, lisans kontrollerini **birden çok noktaya** dağıtma, sınıf bütünlük hash'i |
| DB'ye elle SQL ile lisans yazma | Lisans DB'de değil imzalı dosyada; DB'deki kayıt sadece cache |

> **Gerçekçi ol:** Java bytecode yeterince ısrarlı biri tarafından kırılabilir. Amaç
> "kırılamaz" değil, "kırmak, aylık ücreti ödemekten daha zahmetli" olmasıdır. Enerjinizin
> çoğunu ürün kalitesine ve destek ilişkisine harcayın; küçük işletme müşterileri yazılım
> kırmaz, zaten sizden destek almaya devam etmek ister.

### 6.8 Lisans Sunucusu — Minimum Kapsam

```
Entity: Customer, Subscription, License, LicenseBinding, HeartbeatLog, Invoice
Endpoints:
  POST /api/v1/activate      (ilk aktivasyon: key → lisans dosyası)
  POST /api/v1/heartbeat
  POST /api/v1/transfer      (makine değişikliği talebi)
  GET  /api/v1/updates/latest
Admin panel:
  - Müşteri listesi, abonelik durumu, son heartbeat zamanı
  - "Askıya al" / "Yeniden aktive et" / "İptal et" butonları
  - Plan ve modül düzenleme
  - Ödeme takibi (manuel işaretleme yeterli; ileride sanal POS entegrasyonu)
```
Bu sunucu **ayrı bir repo** olmalı. Private key'i sunucu diskinde düz tutmayın; en azından
KMS/HSM veya parola korumalı keystore + ortam değişkeni kullanın.

---

## 7. ÜÇÜNCÜ PARTİ LİSANS UYUMLULUĞU

Ticari, kapalı kaynak bir ürün satacaksınız. Bu bölüm hukuki risk yönetimidir.

### 7.1 Kırmızı Çizgiler

| Kaçınılacak | Neden |
|---|---|
| **AGPL-3.0** (iText 7, bazı grafik kütüphaneleri, MongoDB SSPL) | Ağ üzerinden servis verirken kaynak kodu açma zorunluluğu doğurabilir |
| **GPL-3.0** (istisnasız) | Türev eser zorunluluğu |
| **Oracle JDK** ticari dağıtım | NFTC lisansı ticari dağıtımda ücretli |
| **Highcharts, AG Grid Enterprise, DevExpress, Syncfusion** | Ticari lisans ücreti gerekir (bilinçli alacaksanız ayrı) |
| Lisanssız fontlar / ikon setleri | Telif ihlali |

### 7.2 Güvenli Liste

| Kütüphane | Lisans | Not |
|---|---|---|
| Spring Boot, Spring Data | Apache-2.0 | ✅ |
| Hibernate ORM | LGPL-2.1 / Apache-2.0 (6.x) | ✅ değiştirmeden kullanın |
| PostgreSQL + JDBC | PostgreSQL License / BSD | ✅ |
| H2 | MPL-2.0 / EPL-1.0 | ✅ değiştirmeden kullanın |
| SQLite (Xerial JDBC) | Public Domain / Apache-2.0 | ✅ |
| Flyway Community | Apache-2.0 | ✅ (Teams sürümü ücretli) |
| Apache POI, Commons | Apache-2.0 | ✅ |
| OpenPDF | LGPL / MPL | ✅ dinamik bağlayın (jar olarak) |
| JasperReports Library | LGPL-3.0 | ✅ kütüphane olarak; Studio ayrı |
| ZXing | Apache-2.0 | ✅ |
| BouncyCastle | MIT-benzeri | ✅ |
| React, Tailwind, shadcn/ui, TanStack, Recharts, Zustand, date-fns | MIT | ✅ |
| Lucide icons | ISC | ✅ |
| Eclipse Temurin JDK | GPLv2 + Classpath Exception | ✅ gömülebilir |
| BellSoft Liberica | GPLv2+CE | ✅ |

### 7.3 Uygulanacak Süreç

1. `gradle-license-report` veya `license-maven-plugin` ile her build'de **SBOM** üretin.
2. CI'da yasaklı lisans listesi kontrolü — AGPL/GPL tespitinde build fail.
3. Ürünle birlikte `LICENSES/THIRD-PARTY.txt` dağıtın (LGPL ve Apache-2.0 bunu gerektirir).
4. LGPL bileşenler için: statik link yapmayın, kullanıcının kütüphaneyi değiştirebileceği
   şekilde ayrı JAR olarak paketleyin, yerini dokümante edin.
5. Yazı tipleri: sadece SIL OFL veya Apache lisanslı fontlar (Inter, Roboto, DejaVu Sans).
   Fatura/PDF çıktılarında Türkçe karakter için **DejaVu Sans** güvenli tercih.
6. `BeautySalonApp` marka adı için TÜRKPATENT marka araştırması yapın; "Frondex" alt sistem
   adı da dahil.

---

## 8. GÜVENLİK VE KVKK

### 8.1 Uygulama Güvenliği

- **Bağlanma adresi varsayılan `127.0.0.1`.** LAN erişimi ayrıca açılmalı ve açılınca uyarı verilmeli.
- Kimlik doğrulama: kullanıcı adı + parola (Argon2id veya BCrypt cost≥12), oturum çerezi
  `HttpOnly; SameSite=Strict`. Kritik roller için opsiyonel PIN/2. faktör.
- Rol tabanlı yetki: `ADMIN`, `MUDUR`, `KASIYER`, `PERSONEL`, `RAPOR_OKUYUCU`.
  Yetkiler **modül × işlem (gör/ekle/düzenle/sil/rapor)** matrisi.
- **Silme yok, iptal var.** Mali kayıtlarda hard delete yasak; `voided` + `voidReason` + kim/ne zaman.
- Tam **audit log**: her mali işlem, fiyat değişikliği, indirim, kasa açılışı, yedek geri yükleme.
- Rate limit ve brute-force koruması giriş ekranında.
- CSRF koruması; SPA için token tabanlı.
- Loglara PII yazmayın (TC kimlik, telefon, e-posta maskelensin).

### 8.2 Veri Güvenliği

- **Diskte şifreleme:** hassas alanlar (TC kimlik no, telefon, sağlık/alerji notu) uygulama
  katmanında AES-256-GCM ile şifrelensin. Anahtar OS keystore'da (Windows DPAPI / macOS Keychain).
- Yedek dosyaları **AES-256 şifreli ZIP** (parola kurulum sırasında belirlenir; kaybolursa
  yedek açılmaz — kullanıcıya bunu açıkça anlatın ve parolayı yazdırıp saklamasını isteyin).
- Ek dosyalar (müşteri fotoğrafı, sözleşme taraması) `attachments` altında, DB'de sadece referans.

### 8.3 KVKK Uyumu (Türkiye)

Güzellik merkezi müşteri verisi topluyorsunuz ve bunların bir kısmı (alerji, cilt durumu,
uygulanan işlem) **özel nitelikli kişisel veri** sayılabilir. Ürün bunları destekleyecek:

| Gereklilik | Ürün karşılığı |
|---|---|
| Açık rıza | Müşteri kartında rıza metni sürümü + tarih + imza (dijital/tarama) alanı |
| Aydınlatma yükümlülüğü | Yazdırılabilir aydınlatma metni şablonu |
| SMS/e-posta ticari ileti | **İYS (İleti Yönetim Sistemi)** izin durumu alanı; izinsiz numaraya kampanya SMS'i gönderimi kod düzeyinde engellensin |
| Veri taşınabilirliği | Müşterinin tüm verisini tek tıkla dışa aktarma |
| Unutulma hakkı | "Müşteriyi anonimleştir" işlemi (mali kayıtlar korunur, kimlik alanları maskelenir) |
| Saklama süresi | Politika ayarı + süresi dolan kayıtlar için uyarı raporu |
| Veri sorumlusu | Verinin işletmenin kendi makinesinde kaldığını belgeleyen teknik doküman (sizin için VERBİS yükünü ciddi azaltır) |

> Not: Ben avukat değilim; bu maddeler mimariye yön vermek için var. Sözleşme ve KVKK
> metinlerini mutlaka bir avukatla ve tercihen bir KVKK danışmanıyla nihaileştirin.

---

## 9. VERİ MODELİ

Ortak alanlar (tüm tablolarda): `id BIGINT` (veya `UUID`), `created_at`, `created_by`,
`updated_at`, `updated_by`, `version` (optimistic lock), `branch_id`, `deleted` (soft delete).
Para alanları: `NUMERIC(19,4)` + ayrı `currency CHAR(3)`. Asla `double` kullanmayın → `BigDecimal`.

### 9.1 Çekirdek

```
branch                 şube/işletme (merkezi yönetim için)
app_user               kullanıcı
role, permission, user_role
setting                anahtar-değer ayarlar (şube bazlı)
audit_log              kim, ne zaman, hangi kayıt, eski/yeni değer
sequence_counter       belge no üretimi (fatura, dekont, sözleşme)
attachment             dosya ekleri
license_state          lisans cache + monotonic clock (şifreli)
```

### 9.2 Cari / Party

```
party                  ortak taban: MUSTERI | SATICI | PERSONEL | PERAKENDE
  ├ code, title, first_name, last_name, tax_id/tc_no(enc), phone(enc), email(enc)
  ├ birth_date, wedding_anniversary, gender, notes
  ├ sms_consent, email_consent, iys_status, consent_date
  ├ risk_limit, default_discount_rate, price_list_id
party_address
party_note             müşteri özel notları (alerji, cilt tipi — özel nitelikli, şifreli)
party_account          cari hesap başlığı (NORMAL | PERAKENDE ayrımı buradan)
party_transaction      cari hareket: date, doc_type, doc_id, debit, credit, description
```

> **Perakende cari ayrımı (Madde 3):** `party_account.account_kind = RETAIL` olan hesaplar
> normal cari raporlarına karışmaz; ayrı ekran, ayrı bakiye, ayrı ekstre. Perakende faturaları
> ve promosyon işlemleri bu hesapla ilişkilendirilir.

### 9.3 Stok

```
item                   ürün/hizmet kartı: code, name, type(EMTIA|HIZMET), vat_rate,
                       base_unit_id, category_id, brand, is_service, is_active
item_barcode           item_id, barcode, unit_id, is_primary      ← ÇOKLU BARKOD
unit                   birim tanımı (ADET, KOLİ, ML, GR, SEANS…)
item_unit              item_id, unit_id, factor(NUMERIC 19,6), barcode, price
                       ← ÇAPRAZ BİRİM FORMÜLASYONU (1 KOLİ = 12 ADET)
warehouse              depo/vitrin: type(SHOWCASE|WAREHOUSE|CONSUMPTION)
stock_level            item_id, warehouse_id, unit_id, quantity  (materialized)
stock_movement         tarih, item, warehouse, giriş/çıkış, miktar, birim, maliyet,
                       kaynak belge (fatura/randevu/sarf/devir/sayım)
price_list, price_list_line
inventory_count        sayım fişi + satırları
```

**Çapraz birim formülasyonu kuralı:** Tüm stok hareketleri **base unit** cinsinden saklanır;
giriş/çıkış ekranında seçilen birim `factor` ile çarpılır. Böylece 1 kolinin 12 adet olduğu
tek yerde tanımlanır ve raporlar tutarlı olur.

### 9.4 Personel

```
staff                  party'ye bağlı; unvan, işe giriş, IBAN(enc), sınıf/kademe
staff_class            personel sınıfı (kıdem/branş) → prim oranları
salary_period          dönem, brüt/net, kesintiler
staff_advance          avans: tarih, tutar, kasa/banka, kapanan dönem
commission_rule        prim kuralı: hizmet/ürün/ciro bazlı, oran veya tutar, kademe
staff_commission       hesaplanan prim satırları (kaynak: randevu/satış)
staff_performance_mv   verimlilik özeti (materialized view / özet tablo)
```

### 9.5 Finans

```
account                hesap planı: KASA | BANKA | POS | CEK | GELIR | GIDER
  ├ kind, currency, is_commission_bearing, commission_rate, bank_info
cash_transaction       tahsilat/tediye/virman/döviz al-sat
  ├ date, account_id, party_id, amount, currency, fx_rate, description, doc_no
voucher (dekont)       şablon bazlı çıktı; işlem sonu otomatik üretim
pos_slip               pos hesabı, slip no, tutar, komisyon, valör, mahsup durumu
cheque                 çek/senet: no, banka, vade, tutar, portföy durumu
                       (PORTFOYDE|CIROLANDI|TAHSIL|KARSILIKSIZ|IADE)
cheque_movement        çek hareketi geçmişi
income_expense_card    gelir/gider kartı — aynı zamanda hizmet alış/satış kartı olabilir
fx_rate                döviz kuru (elle veya manuel liste; internet yoksa elle giriş)
```

### 9.6 Fatura

```
invoice                type(ALIS|SATIS|PERAKENDE|IADE_ALIS|IADE_SATIS)
  ├ series, number, date, party_id, warehouse_id, currency, fx_rate
  ├ cash_register_receipt_no        ← YAZARKASA FİŞ NO
  ├ subtotal, discount_total, vat_total, grand_total, status
invoice_line           item, quantity, unit, unit_price, discount, vat_rate, total
invoice_payment        peşin/kredi kartı/çek/vadeli dağılımı
```

### 9.7 Randevu (Frondex)

```
service_definition     hizmet: süre(dk), fiyat, gerekli kaynak, sarf reçetesi
service_recipe         hizmet başına tüketilen stok (item, miktar) → otomatik sarf
resource               oda/koltuk/cihaz
appointment            party_id, staff_id, resource_id, service_id,
                       start_at, end_at, status(PLANLANDI|ONAYLANDI|GELDI|GELMEDI|IPTAL),
                       notes, source(TELEFON|YERINDE|ONLINE), reminder_sent_at
appointment_series     seri/paket randevular (10 seans gibi)
staff_shift            personel çalışma takvimi / izin
waitlist               bekleme listesi
```

### 9.8 Sözleşme ve Taksitlendirme

```
sales_contract         party_id, date, total_amount, down_payment,
                       installment_count, first_due_date, period(AYLIK|HAFTALIK),
                       interest_rate, status, signed_document_id
contract_line          satılan hizmet/paket/ürün satırları (seans adedi dahil)
installment            contract_id, seq, due_date, amount, paid_amount,
                       paid_at, status(BEKLIYOR|ODENDI|GECIKMIS|IPTAL)
installment_payment    tahsilat bağlantısı (kasa/banka/pos/çek)
```

**Otomatik taksitlendirme algoritması:**
```
kalan = toplam - peşinat
taksitTutari = ROUND(kalan / adet, 2)
son taksit = kalan - (taksitTutari * (adet-1))     ← kuruş farkı son taksite
vadeler = ilkVade, ilkVade+1ay, …  (ay sonu düzeltmesi: 31 Ocak → 28/29 Şubat)
```
Bu mantık **saf domain sınıfında** olmalı ve %100 birim test kapsamında olmalı.

### 9.9 Sadakat / Promosyon (PPOS)

```
loyalty_card           kart no, manyetik/çip id, party_id, durum, bakiye
loyalty_program        kazanım kuralı (harcama başı puan, kategori çarpanı, kampanya)
loyalty_transaction    kazanım/harcama/iptal, kaynak belge, puan, tutar karşılığı
promotion              kampanya: tarih aralığı, koşul, ödül (indirim/puan/hediye)
```

### 9.10 Bildirim

```
notification_template  tip(DOGUM_GUNU|YILDONUMU|RANDEVU_HATIRLATMA|BORC|TAKSIT|KAMPANYA|GUNLUK_RAPOR)
                       kanal(SMS|EMAIL), konu, gövde (değişkenli: {ad}, {tarih}, {tutar})
notification_queue     alıcı, kanal, şablon, planlanan zaman, durum, deneme sayısı, hata
notification_log       gönderim geçmişi (KVKK: içerik saklama süresi ayarlanabilir)
```

---

## 10. MODÜL SPESİFİKASYONLARI

Her modül için: **ekranlar → iş kuralları → raporlar** formatında.

### 10.1 Stok Takip

**Ekranlar**
- Ürün/Hizmet Kartı listesi (arama: kod, ad, barkod, kategori, marka)
- Kart detayı: sekmeler → Genel · Barkodlar · Birimler · Fiyatlar · Stok Durumu · Hareketler
- Stok Giriş/Çıkış fişi, Depolar arası transfer, Sayım
- Vitrin ↔ Depo transferi (hızlı ekran)
- Kritik stok listesi

**İş kuralları**
- Bir ürünün N barkodu olabilir; her barkod bir birime bağlıdır (koli barkodu ≠ adet barkodu).
- Barkod okutulduğunda: barkod → (item, unit) çözülür, miktar o birimde girilir, base unit'e çevrilir.
- Negatif stok: ayarla engellenebilir/uyarı verilebilir.
- Maliyet yöntemi: **Ağırlıklı ortalama** (varsayılan) — FIFO opsiyonel ama v1'de yapmayın.
- Hizmet satışında `service_recipe` üzerinden otomatik sarf düşümü (opsiyonel, ayarla açılır).

**Raporlar:** Stok durum, hareket dökümü, kritik stok, devir hızı, ölü stok, envanter değeri,
depo/vitrin karşılaştırma.

### 10.2 Personel Hesapları

**Ekranlar:** Personel kartı, sınıf tanımları, maaş dönemi, avans fişi, prim kuralları,
prim tahakkuk ekranı, personel cari ekstresi, verimlilik paneli.

**İş kuralları**
- Personel aynı zamanda bir `party`dir → cari hesabı vardır (avans borç, maaş alacak).
- Prim kuralı sırası: personel özel kuralı > sınıf kuralı > genel kural.
- Prim tetikleyicileri: randevu tamamlandı (`GELDI`), ürün satışı, hizmet satışı, ciro eşiği.
- Prim **tahakkuk** ve **ödeme** ayrı: tahakkuk cariye borç yazar, ödeme kasadan çıkar.
- Maaş dönemi kapandıktan sonra o döneme hareket girilemez (kilit).

**Raporlar:** Personel verimliliği (randevu sayısı, ciro, ortalama sepet, doluluk %),
prim dökümü, maaş/avans mutabakatı, personel bazlı hizmet dağılımı.

### 10.3 Müşteri / Satıcı Cari Takip

**Ekranlar:** Cari liste (Müşteri / Satıcı / **Perakende** sekmeleri), cari kart,
cari ekstre, borç-alacak mahsuplaşma, yaşlandırma, toplu SMS/e-posta seçimi.

**İş kuralları**
- `RETAIL` hesaplar ayrı defterde tutulur, normal cari bakiyelerine karışmaz.
- Risk limiti aşımında satışta uyarı (ayarla: uyar / engelle).
- Çek bakiyesi cari bakiyeden ayrı **"Risk Bakiyesi"** olarak gösterilir (Madde 7 gereği).
- Cari bakiye = `SUM(debit) - SUM(credit)`; performans için özet tablo + gece yeniden hesap kontrolü.

**Raporlar:** Ekstre, borç/alacak listesi, yaşlandırma (0-30-60-90+), en çok harcayan müşteriler,
kayıp müşteri (X gündür gelmeyen), doğum günü listesi.

### 10.4 Gelir–Gider Hesapları

- Ağaç yapılı gelir/gider kart planı (ör. `600 Gelirler > 600.01 Hizmet Geliri`).
- Her kart doğrudan **kâr-zarar** kriteridir; kart bazında bütçe tanımı.
- Gider kartı aynı zamanda **hizmet alış/satış kartı** olarak işaretlenebilir → faturada kalem olur.
- Tüm modüller (kasa, banka, fatura, personel) işlem yaparken bir gelir/gider kartına bağlanır.
- **Rapor:** Gelir-gider tablosu, kâr-zarar özeti, bütçe/gerçekleşen, kart bazlı trend.

### 10.5 Fatura İşlemleri

- Alış / Satış / Perakende / İade türleri.
- Perakende faturada **yazarkasa fiş numarası** alanı zorunlu-opsiyonel (ayar).
- Perakende faturalar günlük stok satışıyla kontrollü çalışır: gün sonu "fişli satış ↔ stok
  çıkışı" mutabakat raporu.
- KDV oranları çoklu; tevkifat v1'de kapsam dışı (v2).
- Fatura → cari hareket + stok hareketi + gelir/gider kaydı **tek transaction** içinde.
- İptal edilen fatura silinmez; ters kayıt üretir.
- **e-Arşiv/e-Fatura:** v1'de kapsam dışı, ama `invoice` tablosunda `einvoice_uuid`,
  `einvoice_status` alanlarını şimdiden bırakın. v2'de bir entegratör (Uyumsoft, Logo, Foriba…)
  eklenebilir — bu internet gerektirir, opsiyonel modül olur.

### 10.6 Kasa Takibi

- Çoklu kasa (TL kasa, döviz kasası, personel kasası).
- İşlemler: Tahsilat, Tediye, Kasa→Kasa virman, Kasa→Banka, **Döviz alım/satım**.
- Döviz alım-satımında kur farkı otomatik gelir/gider kartına yazılır.
- Gün başı/gün sonu kasa sayımı; fark varsa "kasa farkı" gider/gelir kaydı + gerekçe zorunlu.
- **Dekont tasarımcısı:** firmaya özel şablon (logo, alanlar, dipnot), işlem sonunda tek tıkla
  yazdır/PDF. JasperReports şablonları `config/reports` altında, güncellemede korunur.

### 10.7 Banka / POS / Çek

**Banka:** Ticari, döviz, kredi (taksitli kredi takibi), POS hesapları. Hesap → gelir/gider
kartı eşlemesi. Banka hareketi girişi manuel (banka entegrasyonu = internet, v2 opsiyonel).

**POS:**
- POS slibi kaydı: tutar, taksit sayısı, komisyon oranı, valör günü.
- Komisyonlu/komisyonsuz hesap seçimi → komisyon otomatik gider kartına, net tutar banka hesabına.
- Mahsuplaşma ekranı: banka ekstresine göre "geldi" işaretleme.
- Rapor: bekleyen POS alacakları, valör takvimi, komisyon maliyeti.

**Çek:**
- Müşteri çeki (alınan) / firma çeki (verilen).
- Portföy durumu geçişleri: `PORTFOYDE → BANKAYA_TAHSILE | CIROLANDI → TAHSIL_EDILDI | KARSILIKSIZ | IADE`.
- Her geçiş `cheque_movement` kaydı üretir, cari ve kasa/banka etkisi otomatik.
- Vade takvimi ekranı (haftalık/aylık görünüm).
- **Müşteri Risk Bakiyesi** = portföydeki + tahsile verilen çek toplamı; cari kartta ayrı satır.

### 10.8 & 10.9 E-posta ve SMS Bilgilendirme

Ortak altyapı: `notification_template` + `notification_queue` + Quartz zamanlayıcı.

**Otomatik tetikleyiciler**
| Tetik | Zamanlama |
|---|---|
| Doğum günü | Her sabah 09:00, o günün doğum günleri |
| Evlilik yıldönümü | Aynı |
| Randevu hatırlatma | Randevudan X saat önce (ayar: 24s / 2s) |
| Borç bakiyesi | Aylık, ayın N'i, bakiyesi > eşik olanlara |
| Taksit ödeme | Vadeden 3 gün önce + vade günü + gecikmede haftalık |
| Kampanya | Manuel, segment seçimli |
| Günlük rapor (yönetici) | Her gece 23:30, e-posta |

**Kurallar**
- **İzinsiz gönderim engellenir.** `sms_consent=false` veya `iys_status != IZINLI` olan
  müşteriye ticari ileti (kampanya) gönderilemez — kod düzeyinde bloklanır.
  Randevu hatırlatma "bilgilendirme" sayılır ama yine de onay alanı tutulur.
- Gönderim kuyruklu ve idempotent: aynı müşteriye aynı gün aynı şablon 2 kez gitmez.
- Başarısız gönderim: exponential backoff, 3 deneme, sonra hata olarak işaretle.
- Kredi/bakiye takibi: SMS sağlayıcı bakiyesi düşünce yöneticiye uyarı.
- Bu modüller internet gerektirir. "Tam Çevrimdışı Mod" seçilirse kapalı gelir; alternatif
  olarak "yazdırılabilir hatırlatma listesi" sunulur.
- SMS sağlayıcı arayüzü soyutlansın (`SmsProvider` interface) → NetGSM, İletimerkezi, Twilio
  vb. adaptörleri değiştirilebilir olsun.

### 10.10 Frondex Randevu Takip Sistemi

**Ekranlar**
- **Takvim:** gün / hafta / ay görünümü; personel bazlı kolonlar (resource view);
  sürükle-bırak taşıma; renk kodlu durumlar.
- Hızlı randevu oluşturma (müşteri ara/oluştur → hizmet → personel → saat).
- Personel iş listesi (o günün işleri, sıralı).
- Bekleme listesi, çakışma kontrolü.
- Seans paketi takibi: "10 seans aldı, 4 kullandı, 6 kaldı".

**İş kuralları**
- Çakışma kontrolü: aynı personel + aynı kaynak (oda/cihaz) aynı anda 2 randevu alamaz.
- Hizmet süresi + hazırlık/temizlik payı (buffer) hesaba katılır.
- Randevu `GELDI` işaretlenince: hizmet satışı, stok sarfı, prim tahakkuku, sadakat puanı zinciri tetiklenir.
- `GELMEDI` sayısı takip edilir; müşteri kartında "no-show" skoru.
- Randevu geçmişi müşteri kartında tam görünür (hangi hizmet, kim yaptı, ne kadar ödedi).

**Raporlar:** Doluluk oranı (personel/kaynak/saat dilimi), no-show oranı, hizmet dağılımı,
en yoğun saatler, personel bazlı randevu → ciro dönüşümü.

### 10.11 Kartlı Promosyon Sistemi (PPOS)

- Manyetik/çipli kart okuyucu desteği. **Kart okuyucu bir HID klavye emülatörü ise** ekstra
  sürücü gerekmez (önerilen). PC/SC çip okuyucu gerekiyorsa küçük bir yerel köprü servisi gerekir.
- Kart → müşteri eşleşmesi; kart kaybında yeni karta bakiye devri.
- Kazanım kuralları: harcama başına puan, kategori çarpanı, kampanya dönemi bonusu.
- Harcama: puanla ödeme (kısmi), puan → TL dönüşüm oranı ayarı.
- Puan zaman aşımı (örn. 12 ay) + uyarı SMS'i.
- Diğer modüllerle entegrasyon: satış ekranında kart okutulunca bakiye ve kullanılabilir puan görünür.
- Rapor: puan yükümlülüğü (muhasebeye kaynak), kart kullanım oranı, kampanya etkinliği.

### 10.12 Satış Sözleşmesi ve Otomatik Taksitlendirme

- Sözleşme oluşturma sihirbazı: müşteri → paket/hizmetler → toplam → peşinat → taksit sayısı
  → vade planı önizleme → onay.
- Sözleşme PDF çıktısı (imza alanlı), taranmış imzalı nüsha yükleme.
- Taksit takvimi ekranı; gecikmiş taksitler kırmızı.
- Otomatik hatırlatma (SMS/e-posta) entegrasyonu.
- Erken ödeme / yeniden yapılandırma / sözleşme iptali (kalan seans mahsuplaşması).
- Rapor: vade takvimi, tahsilat performansı, gecikme yaşlandırma, sözleşme kârlılığı.

---

## 11. YEDEKLEME SİSTEMİ

### 11.1 Gereksinimler

| Özellik | Detay |
|---|---|
| Otomatik yedek | Günlük (varsayılan 23:00) + uygulama kapanışında + güncelleme öncesi |
| Manuel yedek | Tek tık, ilerleme çubuğu |
| İçerik | DB dump + `attachments` + `config` (lisans hariç) + sürüm bilgisi |
| Format | AES-256 şifreli ZIP; içinde `manifest.json` (sürüm, şema versiyonu, checksum) |
| Hedefler | Yerel klasör (zorunlu) + ağ klasörü/USB (opsiyonel, çoklu hedef) |
| Rotasyon | Son 7 günlük + son 4 haftalık + son 12 aylık (GFS) |
| **Doğrulama** | Her yedek sonrası: checksum + geçici DB'ye restore denemesi (haftada 1) |
| Geri yükleme | Sihirbaz: yedek seç → uyarı → mevcut veriyi de yedekle → restore → migration |
| Bildirim | Yedek başarısızsa ekranda kalıcı kırmızı uyarı + yöneticiye e-posta |
| Rapor | "Son başarılı yedek: 2 saat önce" göstergesi ana ekranda |

### 11.2 Dikkat Edilecekler

- Yedek alırken uygulamayı kilitlemeyin; PostgreSQL için `pg_dump`, H2 için `BACKUP TO` komutu
  tutarlı anlık görüntü verir.
- **Yedek klasörü asla kurulum dizininin içinde olmasın** (kaldırma işleminde silinir).
- Fidye yazılımına karşı: en az bir hedef çıkarılabilir/ağ dışı olsun. Kullanıcıya bunu anlatın.
- Yedek şifre parolası kaybolursa yedek açılamaz → kurulum sırasında parolayı yazdırma ekranı gösterin.
- Geri yükleme işlemi **audit log'a** yazılır ve yönetici parolası ister.

---

## 12. MERKEZİ İŞLETME SİSTEMİ (ÇOK ŞUBELİ YAPI)

Bu, "veri lokalde kalacak" kısıtıyla en çok gerilen özellik. Üç seçenek:

| Seçenek | Nasıl | Artı | Eksi |
|---|---|---|---|
| **A. Tek merkez + LAN/VPN** | Tüm şubeler merkezdeki tek DB'ye bağlanır (site-to-site VPN veya WireGuard) | Anlık tutarlılık, tek doğruluk kaynağı | Hat koparsa şube çalışamaz |
| **B. Şube bağımsız + periyodik konsolidasyon** | Her şube kendi DB'sinde; gece merkeze imzalı, şifreli **özet paket** aktarılır (dosya/VPN) | Şube kesintiden etkilenmez | Merkez verisi T-1 günlük |
| **C. Hibrit** | Operasyon şubede, raporlama merkezde (B) + kritik kartlar (ürün, fiyat, personel) merkezden şubeye push | En pratik | En çok geliştirme |

**Öneri: v1'de yapmayın. v2'de B ile başlayın.**

B için tasarım:
- Her şube `branch_id` ve **kendi belge no serisi** kullanır (çakışma olmaz).
- Tüm PK'ler `UUID` veya `(branch_id, sequence)` bileşik → birleştirmede çakışma olmaz.
  → **Bu yüzden v1'de bile `branch_id` alanını her tabloya koyun ve ID stratejisini şimdi seçin.**
- Gece işi: şubede `consolidation_export` üretir (satış, tahsilat, stok özeti, personel performansı),
  AES ile şifreler, imzalar, ağ klasörüne/SFTP'ye bırakır.
- Merkez: paketleri toplar, `central_*` tablolarına yazar, konsolide dashboard sunar.
- Merkez → şube yönü: fiyat listesi, ürün kartı, kampanya tanımı push edilir.

---

## 13. UZAKTAN KURULUM VE 7/24 DESTEK

### 13.1 Uzaktan Erişim Araçları

| Araç | Lisans | Not |
|---|---|---|
| **RustDesk (self-hosted)** | AGPL-3.0 — **ayrı ürün olarak kullanın, kodunuza bağlamayın** | Kendi relay sunucunuz; ücretsiz; önerilen |
| **MeshCentral** | Apache-2.0 | Self-hosted, web tabanlı, ajan kurulabilir |
| AnyDesk / TeamViewer | Ticari | Lisans ücreti var, en kolay yol |

> **AGPL uyarısı:** RustDesk'i müşterinin makinesine ayrı bir uygulama olarak kurmak sorun
> değildir. Ama kodunu kendi ürününüze gömerseniz AGPL yükümlülüğü doğar. Gömmeyin.
> Güvenli tercih: **MeshCentral (Apache-2.0)**.

### 13.2 Uzaktan Destek Özellikleri (ürünün içinde)

- **Destek Paketi Oluştur:** loglar + sistem bilgisi + şema versiyonu + son hatalar → tek ZIP.
  **İçinde iş verisi olmaz**, kullanıcı önce içeriği görür sonra gönderir/kaydeder.
- **Tanı ekranı:** DB bağlantısı, disk alanı, son yedek, lisans durumu, servis durumu, port durumu.
- **Uzaktan komut YOK.** Uygulamanın içine "sunucudan gelen komutu çalıştır" özelliği koymayın —
  bu bir arka kapıdır ve hem güvenlik hem hukuki risktir. Lisans durumu dışında sunucu
  uygulamaya emir vermemelidir.
- Yerleşik yardım: modül bazlı kısa videolar/dokümanlar (paket içinde, internet gerekmez).

### 13.3 Destek Süreci

```
Seviye 1: Uygulama içi yardım + SSS (offline)
Seviye 2: Telefon/WhatsApp + Destek Paketi analizi
Seviye 3: MeshCentral ile ekran paylaşımı (müşteri onayı ile, oturum kaydı tutulur)
Seviye 4: Geliştirici müdahalesi / hotfix sürümü
```
SLA'yı abonelik planına bağlayın (Basic: mesai içi, Pro: 7/24 kritik arıza).

---

## 14. RAPORLAMA VE GÜNLÜK ANALİZ

### 14.1 Günlük Analiz Ekranı (ana sayfa dashboard)

```
┌─ BUGÜN ────────────────────────────────────────────────┐
│ Ciro: 12.450 ₺   Nakit: 4.200  Kart: 6.100  Havale: 2.150│
│ Randevu: 18 planlı · 14 geldi · 2 gelmedi · 2 iptal      │
│ Yeni müşteri: 3      Tahsilat: 8.900 ₺   Gider: 1.240 ₺  │
├─ UYARILAR ─────────────────────────────────────────────┤
│ ⚠ 4 taksit bugün vadesi doldu (3.200 ₺)                  │
│ ⚠ 7 ürün kritik stok seviyesinde                         │
│ ⚠ 2 çek vadesi bu hafta                                  │
│ ✓ Son yedek: bugün 03:00 (başarılı)                      │
├─ GRAFİKLER ────────────────────────────────────────────┤
│ Son 30 gün ciro trendi · Personel doluluk · Hizmet dağılımı│
└────────────────────────────────────────────────────────┘
```

### 14.2 Gün Sonu Raporu (yöneticiye e-posta, 23:30)

Ciro özeti, ödeme türü dağılımı, kasa mutabakatı, personel performansı, iptal/iade listesi,
yarının randevuları, kritik uyarılar.

### 14.3 Rapor Altyapısı

- Raporlar **okuma modelinden** çalışsın: ağır sorgular için özet tablolar (gece yenilenir)
  veya PostgreSQL materialized view.
- Çıktı formatları: Ekran, PDF (JasperReports), Excel (Apache POI), CSV.
- Her rapor: tarih aralığı + şube + personel + kategori filtreleri, kaydedilebilir filtre setleri.
- Rapor tasarımları `config/reports` altında — müşteriye özel dekont/fatura tasarımı yapılabilsin
  ve güncellemede ezilmesin.

---

## 15. PROJE YAPISI (MONOREPO)

```
beautysalonapp/
├── CLAUDE.md                    ← Claude Code için ana talimat dosyası
├── README.md
├── docs/
│   ├── 00-vizyon.md
│   ├── 01-mimari.md
│   ├── 02-veri-modeli.md
│   ├── 03-lisanslama.md
│   ├── 04-guvenlik-kvkk.md
│   ├── 05-kurulum-paketleme.md
│   ├── adr/                     ← Architecture Decision Records
│   └── modules/
│       ├── stok.md  personel.md  cari.md  finans.md  fatura.md
│       ├── randevu.md  sozlesme.md  sadakat.md  bildirim.md  rapor.md
├── server/                      ← Spring Boot backend
│   ├── build.gradle.kts
│   └── src/main/java/com/beautysalonapp/…
│   └── src/main/resources/
│       ├── application.yml
│       ├── db/migration/{common,h2,postgres}/V___.sql
│       └── static/              ← web build çıktısı buraya kopyalanır
├── web/                         ← React + TS + Vite
│   ├── package.json
│   └── src/{app,features,components,lib,hooks,types}
├── license-server/              ← AYRI DEPLOY (kendi VPS'iniz)
├── packaging/
│   ├── windows/{jpackage.conf, winsw.xml, wix-fragment.wxs}
│   ├── macos/{Info.plist, launchd.plist, entitlements.plist}
│   └── scripts/{build-all.sh, sign.sh, notarize.sh}
├── tools/
│   ├── license-cli/             ← lisans üretme/imzalama CLI (geliştirici aracı)
│   └── demo-data/               ← demo veri üretici
└── .github/workflows/ci.yml
```

---

## 16. CLAUDE CODE İLE GELİŞTİRME REHBERİ

### 16.1 `CLAUDE.md` (repo köküne koyun)

```markdown
# BeautySalonApp — Claude Code Talimatları

## Ürün
Güzellik merkezi yönetim yazılımı. Masaüstünde çalışır, veri yereldedir,
arayüz tarayıcıdadır. Windows + macOS. Lisanslı/abonelikli ticari üründür.

## Değişmez Kurallar
1. Hiçbir iş verisi internete gönderilmez. Yeni bir HTTP çağrısı ekliyorsan
   OutboundHttpGuard allowlist'inden geçmeli ve gerekçesini PR açıklamasına yaz.
2. Para: her zaman BigDecimal + NUMERIC(19,4). double/float YASAK.
3. Mali kayıtlarda hard delete YASAK. İptal = ters kayıt + voidReason.
4. Her şema değişikliği bir Flyway migration'dır. Var olan migration DÜZENLENMEZ.
5. Modüller birbirine sadece application katmanı arayüzleriyle erişir.
   ArchUnit testleri bunu doğrular; testi zayıflatarak geçmeye çalışma.
6. Kullanıcıya görünen tüm metin Türkçe ve i18n dosyasından gelir. Kodda
   hardcoded Türkçe string olmaz.
7. Tarih/saat: DB'de UTC (Instant), sunumda Europe/Istanbul.
8. Lisans kontrolünü kaldırma, gevşetme veya bypass eden kod yazma.
9. LOCKED durumda bile veri dışa aktarma çalışmalıdır. Bu davranışı bozma.
10. KVKK: PII loglara yazılmaz. Yeni alan eklerken şifreleme gerekip
    gerekmediğini docs/04-guvenlik-kvkk.md'ye göre değerlendir.

## Teknoloji
Java 21, Spring Boot 3.3, Spring Modulith, JPA/Hibernate, Flyway,
PostgreSQL 16 (+H2 file mode), React 18 + TS + Vite + Tailwind + shadcn/ui,
TanStack Query/Table, Gradle Kotlin DSL, JUnit 5 + Testcontainers + ArchUnit.

## Kod Standartları
- Paket: com.beautysalonapp.modules.<modul>.{domain,application,infrastructure,web}
- Entity'ler domain'de, JPA anotasyonları infrastructure'da (mümkünse ayır).
- Controller ince olur; iş mantığı application servisinde.
- DTO'lar record; MapStruct ile eşleme.
- Her use-case için birim test; her REST endpoint için @SpringBootTest slice test.
- Yeni modül eklerken docs/modules/<modul>.md dosyasını da güncelle.

## Komutlar
./gradlew build            # derleme + test
./gradlew test             # testler
./gradlew :server:bootRun  # geliştirme sunucusu
npm --prefix web run dev   # frontend dev
./gradlew licenseReport    # 3. parti lisans raporu
./packaging/scripts/build-all.sh  # dağıtım paketleri

## Yapmadan Önce Sor
- Yeni bir 3. parti bağımlılık eklemek (lisansını yaz)
- Veri modelinde geriye dönük uyumsuz değişiklik
- Yeni bir dış ağ çağrısı
- Lisanslama veya güvenlikle ilgili herhangi bir değişiklik
```

### 16.2 Çalışma Yöntemi

**Doküman önce, kod sonra.** Her modül için önce `docs/modules/<modul>.md` dosyasını
Claude Code ile yazdırın, siz gözden geçirin, sonra kodu ondan üretin. Doküman değişmeden
kod yazdırmayın — aksi halde 14 modülde tutarsızlık kaçınılmaz.

**Modül başına döngü:**
```
1. docs/modules/X.md  → ekranlar, kurallar, tablolar, endpoint listesi
2. Flyway migration   → şema
3. domain + application + test  → iş mantığı (önce test yazdırın)
4. infrastructure + web  → repository + REST
5. web/src/features/X → React ekranları
6. E2E test (Playwright) → kritik akış
7. docs güncelle, PR
```

**Etkili prompt kalıpları:**
```
"docs/modules/randevu.md'yi oku. Sadece domain ve application katmanını yaz.
 Çakışma kontrolü kuralı için önce başarısız testleri yaz, sonra implementasyonu.
 JPA'ya dokunma."

"installment üretim algoritmasını yaz. Kuruş farkı son taksite gider,
 ay sonu vade düzeltmesi var. 31 Ocak → 28/29 Şubat senaryosu dahil
 en az 12 birim test yaz."

"stock modülünde çapraz birim dönüşümü için ArchUnit + birim testleri yaz.
 Sadece test yaz, üretim kodunu değiştirme."
```

**Bağlam yönetimi:** Tek seferde tek modül. `/clear` ile modüller arasında bağlamı temizleyin.
Büyük refactor'lardan önce mutlaka commit alın.

### 16.3 Yararlı Slash Komutları (`.claude/commands/`)

| Komut | İçerik |
|---|---|
| `/yeni-modul <ad>` | Doküman iskeleti + paket yapısı + migration şablonu üretir |
| `/migration <aciklama>` | Sıradaki Flyway sürüm numarasıyla dosya oluşturur |
| `/rapor <ad>` | Rapor sorgusu + DTO + endpoint + React ekranı iskeleti |
| `/lisans-kontrol` | Lisans kontrol noktalarının hâlâ yerinde olduğunu denetler |
| `/kvkk-denetim` | Yeni alanlarda PII/şifreleme eksiği tarar |
| `/surum-cikar` | Sürüm no artırma, changelog, paketleme betiği |

---

## 17. YOL HARİTASI VE FAZLAR

Tahminler tek geliştirici (siz) + Claude Code varsayımıyla, tam zamanlı çalışma.

### Faz 0 — Temel (2–3 hafta)
- Repo, Gradle, CI, `CLAUDE.md`, doküman iskeleti
- Spring Boot + Modulith + Flyway + veritabanı seçimi
- Kullanıcı/rol/yetki, ayarlar, audit log
- React kabuk: layout, navigasyon, tema, tablo/form bileşen kütüphanesi
- **Çıktı:** Giriş yapılabilen boş uygulama

### Faz 1 — Lisans + Paketleme (2 hafta) ⚠️ Erken yapın
- Lisans sunucusu (minimum), Ed25519, parmak izi, heartbeat, kademeli kısıtlama
- jpackage ile .msi ve .dmg, servis kurulumu, sessiz kurulum
- **Çıktı:** Gerçek bir makineye kurulup lisansla açılıp kapanabilen iskelet
- **Neden erken?** Paketleme ve lisanslama en çok sürpriz çıkaran kısımdır. Sona bırakırsanız
  proje 3 ay gecikir.

### Faz 2 — Çekirdek Ticari (4–5 hafta)
- Cari (müşteri/satıcı/perakende), Stok (çoklu barkod + çoklu birim), Gelir-gider, Kasa
- **Çıktı:** Basit satış ve tahsilat yapılabiliyor

### Faz 3 — Randevu + Sözleşme (3–4 hafta)
- Frondex randevu takvimi, hizmet tanımları, personel vardiyası
- Satış sözleşmesi + otomatik taksitlendirme + vade takibi
- **Çıktı:** Güzellik merkezinin ana iş akışı çalışıyor → **ilk pilot müşteri buradan sonra**

### Faz 4 — Finans Derinleşme (3 hafta)
- Fatura, Banka/POS/Çek, personel hesapları ve prim

### Faz 5 — Yedekleme + Raporlama (2–3 hafta)
- Yedekleme motoru + doğrulama + geri yükleme sihirbazı
- Günlük analiz dashboard + rapor merkezi + PDF/Excel çıktıları
- **Not:** Yedekleme aslında Faz 2'de basit haliyle olmalı; burada olgunlaştırılıyor.

### Faz 6 — Bildirim + Sadakat (2–3 hafta)
- SMS/e-posta altyapısı, şablonlar, İYS izin kontrolü
- Kartlı promosyon (PPOS)

### Faz 7 — Sertleştirme ve Yayın (3 hafta)
- Performans (10 yıllık demo veriyle), güvenlik gözden geçirme
- macOS imzalama + notarization, Windows code signing sertifikası
- Kullanıcı kılavuzu, eğitim videoları, kurulum dokümanı
- Pilot müşteri geri bildirimleri

### Faz 8+ (v2)
Merkezi işletme sistemi, e-Fatura entegrasyonu, banka ekstre içe aktarma,
online randevu portalı (ayrı ürün), mobil görüntüleme.

**Toplam gerçekçi tahmin: 5–7 ay** (v1, tek geliştirici). Tüm 14 modülü aynı anda hedefleyip
3 ayda bitirmeyi planlarsanız ürün yarım kalır.

---

## 18. TEST STRATEJİSİ

| Katman | Araç | Kapsam hedefi |
|---|---|---|
| Domain birim testi | JUnit 5 + AssertJ | %90+ — özellikle taksit, prim, stok birim dönüşümü, cari bakiye |
| Mimari testi | ArchUnit | Modül sınırları, katman ihlalleri, `double` kullanımı yasağı |
| Repository/entegrasyon | Testcontainers (PostgreSQL) | Kritik sorgular |
| API testi | `@SpringBootTest` + MockMvc | Tüm endpoint'ler, yetki senaryoları |
| Migration testi | Flyway + boş DB + eski DB | Her sürüm yükseltmesi |
| Lisans testi | Sahte saat, bozuk imza, süresi geçmiş lisans, fingerprint değişimi | Tüm durum geçişleri |
| Yedek/geri yükleme | Otomatik: yedek al → yeni DB'ye restore → veri karşılaştır | Her build |
| E2E | Playwright (Chromium + WebKit) | Ana akışlar: randevu → satış → tahsilat → rapor |
| Kurulum testi | Temiz Windows VM + temiz macOS | Her sürüm, manuel checklist |
| Performans | 10 yıllık sentetik veri (500k hareket) | Liste ekranları < 300 ms |

**Kritik kural:** Mali hesaplama içeren hiçbir kod testsiz merge edilmez. Ön muhasebede tek
kuruşluk hata müşteri güvenini bitirir.

---

## 19. RİSK LİSTESİ

| # | Risk | Etki | Önlem |
|---|---|---|---|
| 1 | Kapsam çok geniş (14 modül), proje bitmez | Yüksek | Fazlara böl, Faz 3 sonunda pilot müşteriye çık |
| 2 | macOS notarization ve Windows code signing gecikmesi | Orta | Sertifikaları **Faz 1'de** alın (Apple Developer $99/yıl, EV code signing ~$300/yıl) |
| 3 | Lisans sistemi kırılır | Orta | Kabul edilebilir; destek ilişkisiyle telafi. Aşırı mühendislik yapmayın |
| 4 | Yedek bozuk çıkar, müşteri veri kaybeder | **Kritik** | Otomatik restore doğrulama, çoklu hedef, ekranda "son yedek" göstergesi |
| 5 | Mali hesaplama hatası | **Kritik** | BigDecimal, kapsamlı test, muhasebeci ile mutabakat testi |
| 6 | KVKK ihlali (izinsiz SMS, özel nitelikli veri) | Yüksek | Kod düzeyinde izin kontrolü, şifreleme, hukuki danışmanlık |
| 7 | H2 veritabanı bozulması | Yüksek | PostgreSQL'e geçiş yolu hazır olsun; sık yedek |
| 8 | Müşteri "bulut istiyorum" der | Orta | Mimariniz zaten sunucu-istemci; bulut sürümü v3'te mümkün |
| 9 | Tek geliştirici bağımlılığı | Yüksek | Dokümantasyon disiplini, temiz mimari, ADR'ler |
| 10 | Rakipler (çok sayıda hazır salon yazılımı var) | Orta | Farklılaşma: yerel veri + çevrimdışı çalışma + taksitli sözleşme takibi + Türkçe destek |
| 11 | Kart okuyucu / yazıcı donanım uyumsuzluğu | Orta | HID klavye emülatörü cihazları önerin; desteklenen donanım listesi yayınlayın |
| 12 | Seçilecek ürün adı veya "Frondex" alt sistem adı marka çakışması | Orta | TÜRKPATENT araştırması, ad kesinleşmeden kod/paket adı sabitlenmesin |

---

## 20. AÇIK SORULAR / KARAR BEKLEYEN KONULAR

Kodlamaya başlamadan önce bunları netleştirin:

1. **Veritabanı:** Tek yol PostgreSQL mi, yoksa H2+PostgreSQL çift destek mi?
   (Öneri: PostgreSQL tek yol — bakım maliyeti yarıya iner.)
2. **Terminal sayısı:** Tipik müşteride kaç bilgisayar var? 1 ise mimari çok basitleşir.
3. **e-Fatura:** v1'de gerekli mi? Müşteri kitleniz e-Arşiv mükellefi mi?
   (Çoğu güzellik salonu 2026 itibarıyla e-Arşiv kapsamında — bunu doğrulayın, kapsamdaysa
   v1'de en azından entegratör alanları ve bir entegrasyon planı gerekir.)
4. **Yazarkasa (ÖKC) entegrasyonu:** Fiş numarası elle mi girilecek, cihazla mı konuşulacak?
   Cihaz entegrasyonu ciddi bir iş kalemi ve marka bazlı SDK gerektirir.
5. **Fiyatlandırma:** Kurulum ücreti ve aylık abonelik rakamları? Plan başına modül dağılımı?
6. **Grace period:** Kaç gün? (Öneri: 7 gün grace + 30 gün read-only + sonra locked)
7. **Marka adı:** "BeautySalonApp" ve "Frondex" için marka araştırması yapıldı mı?
8. **Pilot müşteri:** Faz 3 sonunda gerçek bir salonda test edecek bir müşteriniz var mı?
   Yoksa şimdiden bulun — ürünün kaderini bu belirler.
9. **SMS sağlayıcı:** Siz mi toplu kontrat yapıp müşteriye satacaksınız, yoksa müşteri kendi
   hesabını mı girecek? (İkincisi hukuki olarak sizin için daha temiz.)
10. **Destek modeli:** 7/24 vaadi tek kişiyle sürdürülebilir mi? Planlara göre kademelendirin.

---

## EK A — HIZLI BAŞLANGIÇ KOMUTLARI

```bash
# Proje iskeletini oluştur
mkdir beautysalonapp && cd beautysalonapp
git init

# Claude Code ile başla
claude
> "docs/ altındaki plan dosyalarını oku. Faz 0 için Gradle multi-module
>  yapısını (server + web) ve Spring Boot 3.3 + Modulith + Flyway iskeletini kur.
>  Henüz iş modülü yazma, sadece çekirdek: config, security, audit, settings."
```

## EK B — ÖNCELİKLİ DOKÜMAN ÜRETİM SIRASI

```
1. docs/00-vizyon.md          ← bu dosyadan türetin
2. docs/01-mimari.md
3. docs/03-lisanslama.md      ← ticari model buna bağlı, erken netleştirin
4. docs/02-veri-modeli.md
5. docs/04-guvenlik-kvkk.md
6. docs/modules/*.md          ← modül modül, geliştirme sırasıyla
```

---

*Bu doküman yaşayan bir belgedir. Her mimari karar için `docs/adr/` altına bir ADR ekleyin
ve bu dosyayı güncel tutun.*
