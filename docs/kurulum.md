# BeautySalonApp — Kurulum Dokümanı

Sürüm 1.0 · Windows 10/11 · macOS 12+

Teknik plan §5, §7.2. Paketleme ayrıntısı: `packaging/README.md`, `docs/adr/0005-paketleme.md`.

---

## 1. Sistem Gereksinimleri

| | Minimum | Önerilen |
|---|---|---|
| İşletim sistemi | Windows 10 64-bit / macOS 12 | Windows 11 / macOS 14 |
| RAM | 4 GB | 8 GB+ |
| Disk | 2 GB boş | 10 GB+ (yedekler için) |
| Ekran | 1366×768 | 1920×1080 |
| Tarayıcı | Chrome / Edge / Safari güncel | — |
| İnternet | Kurulum + ilk aktivasyon için | Günlük nabız için (zorunlu değil) |

Gömülü Java çalışma ortamı (JRE) installer ile gelir — ayrıca Java kurmanıza **gerek yoktur**.

---

## 2. Kurulum — Windows

1. `BeautySalonApp-1.0.0-windows-x64.msi` dosyasını çalıştırın.
   - İmzasız sürümde SmartScreen uyarısı çıkarsa **Ek bilgi › Yine de çalıştır**.
2. Sihirbaz: kurulum klasörü (varsayılan `C:\Program Files\BeautySalonApp`), masaüstü/başlat
   menüsü kısayolu.
3. Kurulum bir **Windows hizmeti** (`BeautySalonApp`) oluşturur ve otomatik başlatır
   (`127.0.0.1:8734` dinler).
4. Masaüstü kısayolu tarayıcıyı `http://localhost:8734` adresine açar.

### Sessiz / toplu kurulum

```powershell
# Yönetici PowerShell
.\packaging\windows\silent-install.ps1 -Msi .\BeautySalonApp-1.0.0-windows-x64.msi `
    -LicenseKey BSA-XXXX-XXXX-XXXX-XXXX -InstallMode SINGLE `
    -BackupDir 'D:\Yedek\BeautySalonApp'
```

veya doğrudan: `msiexec /i BeautySalonApp-1.0.0-windows-x64.msi /qn`

---

## 3. Kurulum — macOS

1. `BeautySalonApp-1.0.0.dmg` dosyasını açın, uygulamayı **Applications** klasörüne sürükleyin.
2. İlk açılışta Gatekeeper uyarısı çıkarsa: **Sistem Ayarları › Gizlilik ve Güvenlik › Yine de Aç**
   (notarize edilmiş sürümde uyarı çıkmaz).
3. Uygulama bir **launchd servisi** kurar (`com.beautysalonapp.app`), oturum açılışında başlar.
4. Uygulama simgesi / Dock kısayolu tarayıcıyı `http://localhost:8734` adresine açar.

---

## 4. Kurulum Sonrası Yerleşim

| | Windows | macOS |
|---|---|---|
| Program + JRE (salt okunur) | `C:\Program Files\BeautySalonApp\` | `/Applications/BeautySalonApp.app` |
| **Veri kökü** | `C:\ProgramData\BeautySalonApp\` | `/Library/Application Support/BeautySalonApp/` |
| Alt klasörler | `data\` `config\` `backups\` `logs\` `attachments\` | aynı |

Veri kökü `-Dbeautysalonapp.packaged=true` ile `AppProperties.resolvePackagedDataDir()`
tarafından çözülür; `packaged` profili H2 dosya yolunu, log dosyasını ve 127.0.0.1
bağlanmasını ayarlar.

> **İzinler:** Veri klasörüne yalnızca hizmet hesabı ve Yöneticiler yazabilmelidir. Kurumsal
> kurulumda BT ekibi bu ACL'yi doğrulamalıdır (bkz. `docs/guvenlik/guvenlik-gozden-gecirme.md` I2).

---

## 5. İlk Yapılandırma Sihirbazı

İlk açılışta sırayla:

1. **Yönetici parolası** — varsayılan `admin/admin123` ise değiştirilmesi zorunludur.
2. **İşletme bilgileri** — ünvan, adres, vergi dairesi/no (fatura başlığı).
3. **Yedekleme**
   - Yedek klasörü (varsayılan `…\BeautySalonApp\backups`)
   - **Yedek parolası** — belirleyin, yazın, işletme dışında saklayın.
     *Parola kaybolursa yedekler açılamaz.*
   - İkincil hedef (USB/ağ) — önerilir.
4. **Lisans** — lisans sunucusundan aldığınız `license.lic` dosyasını yükleyin
   (Ayarlar › Lisans › "Lisans yükle"). Çevrimiçiyseniz anahtarla da aktive edilebilir.
5. **Kullanıcılar** — her çalışan için ayrı hesap + rol.
6. (İsteğe bağlı) **LAN erişimi** — çok terminalli kullanım için.

---

## 6. PostgreSQL'e Geçiş (çok terminal / büyük veri)

Varsayılan H2 tek bilgisayar içindir. Çok kullanıcı veya 5+ yıl yoğun veri için:

1. PostgreSQL 16 kurun, bir veritabanı + kullanıcı oluşturun.
2. Hizmeti `postgres` profili ile başlatın; `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` verin.
3. Flyway şemayı hedef veritabanında kurar. Mevcut H2 verisini taşımak için destek ile çalışın.

Ayrıntı: teknik plan §4.2, Risk #7.

---

## 7. Güncelleme

1. Program, nabız cevabında yeni sürüm (`latestVersion`) bilgisini alır ve kullanıcıya bildirir.
2. **Güncellemeden önce otomatik yedek zorunludur.**
3. `apply-update` akışı: yedek → indir → SHA-256 (+ Ed25519 imza) doğrula → hizmeti durdur →
   yeni `.msi`/`.dmg` sessiz kur → hizmet yeniden başlar.
4. Açılışta Flyway migration çalışır; başarısızsa **yedekten otomatik dönülür**.
5. `mandatoryUpdate: true` → kritik güvenlik yaması, ertelenemez.

---

## 8. Kaldırma

- **Windows:** *Ayarlar › Uygulamalar* → BeautySalonApp → Kaldır. Veri kökü
  (`C:\ProgramData\BeautySalonApp`) **silinmez** — elle silin veya arşivleyin.
- **macOS:** Uygulamayı Çöp'e atın; `sudo launchctl bootout system /Library/LaunchDaemons/com.beautysalonapp.app.plist`.
  `/Library/Application Support/BeautySalonApp` elle silinir.

**Kaldırmadan önce mutlaka güncel bir yedek alın ve dışarı kopyalayın.**

---

## 9. Sorun Giderme

| Sorun | Kontrol |
|---|---|
| Tarayıcı `localhost:8734`'e bağlanamıyor | Hizmet durumu (Windows *Hizmetler* / `launchctl print`). Log: `…\BeautySalonApp\logs\beautysalonapp.log` |
| Port 8734 kullanımda | Başka bir uygulama portu tutuyor. Destek ile port değişikliği. |
| "GELİŞTİRME MODU" bandı görünüyor | Lisans public key gömülü değil — yanlış/eksik build. Doğru imzalı installer'ı kullanın. |
| Migration hatası açılışta | Yedekten otomatik dönülür; log'u destekle paylaşın. |
| Yedek alınamıyor | Yedek klasörü izinleri / disk dolu. Ekrandaki "son yedek" tarihini izleyin. |
