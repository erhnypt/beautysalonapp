# Paketleme — .msi / .dmg üretimi

Teknik plan §5, §7.2, §11.2, §15.

```
packaging/
├── scripts/
│   ├── build-all.sh        frontend → server jar → jlink runtime → jpackage (bu OS için)
│   ├── jlink-runtime.sh    Spring Boot fat-jar'a uygun minimal JRE
│   ├── sign.sh             codesign (macOS) / signtool (Windows) — GERÇEK SERTİFİKA gerekir
│   ├── notarize.sh         xcrun notarytool + stapler (macOS)
│   └── apply-update.sh     çalışan kuruluma güncelleme uygulama (referans)
├── windows/
│   ├── jpackage.args       MSI ek argümanları (@argfile)
│   ├── wix/overrides.wxi   ProgramData dizinleri + sessiz kurulum property'leri (tamamlanacak)
│   ├── winsw.xml           servis alternatifi (jpackage --launcher-as-service yerine)
│   └── silent-install.ps1  msiexec /qn LICENSE_KEY=... sarmalayıcı (§5.4)
└── macos/
    ├── jpackage.args       DMG ek argümanları
    ├── launchd.plist       launchd daemon (referans; jpackage kendisi kurar)
    ├── entitlements.plist  Hardened Runtime izinleri (Java JIT/reflection)
    └── Info.plist.additions LSUIElement, min sürüm vb. eklemeleri
```

## Ön koşullar (build makinesi)

| Araç | Neden |
|---|---|
| **JDK 21** (Temurin / Liberica) — `JAVA_HOME` buna | jpackage + jlink + virtual threads (ADR 0002/0005) |
| Node 20+ / npm | frontend build |
| **Windows:** WiX Toolset 3.14 | jpackage MSI'yi WiX ile üretir |
| **macOS:** Xcode Command Line Tools | codesign / hdiutil / notarytool |
| Linux: `fakeroot`, `dpkg` (opsiyonel) | `.deb` |

jpackage **çapraz derleme yapmaz**: `.msi` yalnızca Windows'ta, `.dmg` yalnızca macOS'ta
üretilir. Her hedef OS için ayrı build makinesi / CI runner gerekir.

## Hızlı kullanım

```bash
export JAVA_HOME=/path/to/jdk-21
export LICENSE_PUBLIC_KEY="<lisans sunucusundan GET /api/v1/public-key>"
bash packaging/scripts/build-all.sh
# → packaging/dist/out/BeautySalonApp-<sürüm>.<msi|dmg>  + checksums.txt
```

İmzalı + notarize edilmiş çıktı (macOS örneği):

```bash
export JAVA_HOME=/path/to/jdk-21
export LICENSE_PUBLIC_KEY="..."
export MAC_APP_IDENTITY="Developer ID Application: Şirket (TEAMID)"
export NOTARY_PROFILE="bsa-notary"       # önceden: xcrun notarytool store-credentials
SIGN=1 NOTARIZE=1 bash packaging/scripts/build-all.sh
```

## Kurulum sonrası yerleşim (§5.2)

| | Windows | macOS |
|---|---|---|
| İkili + JRE (salt okunur) | `C:\Program Files\BeautySalonApp\` | `/Applications/BeautySalonApp.app` |
| Veri kökü | `C:\ProgramData\BeautySalonApp\` | `/Library/Application Support/BeautySalonApp/` |
| Alt dizinler | `data\ config\ backups\ logs\ attachments\` | aynı |

Uygulama, `-Dbeautysalonapp.packaged=true` (launcher tarafından geçilir) ile bu yolları
`AppProperties.resolvePackagedDataDir()` içinde çözer. `spring.profiles.active=packaged`
profili H2 dosya yolunu, log dosyasını ve 127.0.0.1 bağlanmasını ayarlar.

## Servis

`build-all.sh` **jpackage `--launcher-as-service`** kullanır: kurulumda otomatik başlayan
bir Windows servisi / launchd daemon kurar. Daha fazla kontrol isteyen ekipler için
`windows/winsw.xml` ve `macos/launchd.plist` referans olarak verilmiştir.

Servis `127.0.0.1:8734` dinler. Masaüstü kısayolu tarayıcıyı bu adrese açar.

## İmzalama / Notarization — SERTİFİKA GEREKİR

Bu adımlar **gerçek sertifika olmadan tamamlanamaz** ve bu depoda üretilemez:

- **macOS:** Apple Developer Program ($99/yıl) → "Developer ID Application" sertifikası.
  Hardened Runtime + `entitlements.plist` ile imzala, sonra Apple'a notarize ettir,
  DMG'ye staple et. `sign.sh` + `notarize.sh` bu akışı yürütür; kimlik bilgilerini
  ortam değişkeni olarak verin.
- **Windows:** EV Code Signing sertifikası (~$300/yıl, donanım token'ı) veya
  Azure Trusted Signing. `signtool` ile Authenticode + RFC-3161 zaman damgası.
  İmzasız MSI SmartScreen uyarısı verir.

Sertifikaları **Faz 1'de** alın (plan Risk #2) — tedarik + doğrulama haftalar sürebilir.

## Güncelleme (§5.5)

1. Uygulama heartbeat cevabında `latestVersion` + `updateUrl` alır.
2. Kullanıcıya bildirim; **güncelleme öncesi otomatik yedek zorunlu**.
3. `apply-update.sh` / `.ps1`: yedek → indir → SHA-256 + Ed25519 imza doğrula →
   servisi durdur → yeni `.msi`/`.dmg` sessiz kur.
4. Uygulama açılışta Flyway migration çalıştırır; **başarısızsa yedekten otomatik döner**.
5. `mandatoryUpdate: true` → kritik güvenlik yaması, ertelenemez.

## Sürüm

`APP_VERSION` `server/pom.xml`'den okunur (`-SNAPSHOT` kırpılır). `jpackage.args`
içindeki `--win-upgrade-uuid` **sabit kalmalıdır** — değişirse yükseltmeler yan yana
kurulur. `com.beautysalonapp.app` bundle identifier'ı da sabittir.

## CI notu

`.github/workflows/ci.yml` derleme + testi çalıştırır. Paketleme için ayrı bir
`release.yml` gerekir: `windows-latest` + `macos-latest` matrisi, sertifikalar
GitHub Secrets'ta, `build-all.sh` çağrısı, artefaktları Release'e yükleme.
