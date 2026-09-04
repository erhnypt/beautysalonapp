# ADR 0005 — Paketleme: jpackage + jlink, yerleşik servis

- **Durum:** Kabul edildi
- **Tarih:** 2026-09-04

## Bağlam
Plan §5: tek `.msi` / `.dmg`, gömülü JRE, arka planda servis, "İleri→İleri→Bitti".
§15: `packaging/{windows,macos,scripts}`.

## Kararlar

1. **Çıktı** JDK 21 `jpackage` ile üretilir; JRE `jlink` ile küçültülür.
   Modül seti `java.se` + kripto/charset/zipfs/localedata jdk.* modülleri — Spring'in
   yansıması nedeniyle dar `jdeps` tespiti yerine geniş ama güvenli küme.
   Fat-jar non-modüler çalıştırılır: `--main-class org.springframework.boot.loader.launch.JarLauncher`.

2. **Servis** için jpackage 21+ `--launcher-as-service` (yerleşik). Plan "WinSW / launchd"
   diyordu; `--launcher-as-service` her iki platformu tek bayrakla, ek indirme olmadan
   çözüyor. WinSW (`windows/winsw.xml`) ve launchd (`macos/launchd.plist`) referans
   olarak korunur — log rotasyonu / restart politikası isteyen ekipler için.

3. **Veri kökü** paketli modda uygulama tarafından çözülür
   (`AppProperties.resolvePackagedDataDir`, `-Dbeautysalonapp.packaged=true`): Windows
   `%ProgramData%\BeautySalonApp`, macOS `/Library/Application Support/BeautySalonApp`.
   `application-packaged.yml` profili H2 yolu + log dosyası + 127.0.0.1 bağlanmayı ayarlar.

4. **Sessiz kurulum** property'leri (`LICENSE_KEY` vb.) `windows/wix/overrides.wxi`
   içinde iskele olarak var; property→`activation.properties` yazan WiX CustomAction
   paketleme mühendisince tamamlanacak. Alternatif: uygulama ilk-açılış sihirbazı +
   lisans yükleme ekranı zaten aktivasyonu karşılıyor.

5. **İmzalama/notarization** ayrı script (`sign.sh`, `notarize.sh`), gerçek sertifika
   gerektirir (Apple Developer $99/yıl, Windows EV ~$300/yıl). Bu adım bu ortamda
   üretilemez; dosyalar + talimat hazır.

6. **Java 21'e geçiş** (ADR 0002): paketleme JDK 21 ister. Uygulama kaynak/hedef
   uyumluluğu 17'de kalabilir; jpackage 21 JDK'sı 17 bytecode'unu sorunsuz paketler.

## Sonuçlar
- `packaging/scripts/build-all.sh` tek komutla bu OS için artefakt üretir (`packaging/dist/out`).
- Windows'ta WiX 3.14, macOS'ta Xcode CLT kurulu olmalı.
- Çapraz derleme yok → CI'da windows + macos runner matrisi gerekir.
