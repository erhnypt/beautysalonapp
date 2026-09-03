# BeautySalonApp — Claude Code Talimatları

## Ürün
Güzellik merkezi yönetim yazılımı. Masaüstünde çalışır, veri yereldedir,
arayüz tarayıcıdadır. Windows + macOS. Lisanslı/abonelikli ticari üründür.

## Değişmez Kurallar
1. Hiçbir iş verisi internete gönderilmez. Yeni bir HTTP çağrısı ekliyorsan
   `OutboundHttpGuard` allowlist'inden geçmeli ve gerekçesini PR açıklamasına yaz.
2. Para: her zaman `BigDecimal` + `NUMERIC(19,4)`. `double`/`float` YASAK.
3. Mali kayıtlarda hard delete YASAK. İptal = ters kayıt + `voidReason`.
4. Her şema değişikliği bir Flyway migration'dır. Var olan migration DÜZENLENMEZ.
5. Modüller birbirine sadece `application` katmanı arayüzleriyle erişir.
   ArchUnit testleri bunu doğrular; testi zayıflatarak geçmeye çalışma.
6. Kullanıcıya görünen tüm metin Türkçe ve i18n dosyasından gelir. Kodda
   hardcoded Türkçe string olmaz (log ve exception mesajları hariç).
7. Tarih/saat: DB'de UTC (`Instant`), sunumda `Europe/Istanbul`.
8. Lisans kontrolünü kaldırma, gevşetme veya bypass eden kod yazma.
9. `LOCKED` durumda bile veri dışa aktarma çalışmalıdır. Bu davranışı bozma.
10. KVKK: PII loglara yazılmaz. Yeni alan eklerken şifreleme gerekip
    gerekmediğini `docs/04-guvenlik-kvkk.md`'ye göre değerlendir.

## Teknoloji (fiili durum)
- Java 17 (LTS) — üretim paketlemede Java 21'e yükseltilecek (bkz. `docs/adr/0002`).
- **Maven** multi-module build (plan Gradle diyordu; bkz. `docs/adr/0001`).
- Spring Boot 3.3, Spring Modulith, JPA/Hibernate, Flyway.
- Veritabanı: H2 v2 (file mode) varsayılan · PostgreSQL 16 (`postgres` profili).
- React 18 + TS + Vite + Tailwind + shadcn/ui tarzı bileşenler, TanStack Query/Table.
- Test: JUnit 5 + AssertJ + ArchUnit + Testcontainers + MockMvc.

## Kod Standartları
- Paket: `com.beautysalonapp.modules.<modul>.{domain,application,infrastructure,web}`
- Domain katmanı framework'süz (JPA anotasyonları infrastructure'da entity olarak).
- Controller ince; iş mantığı `application` servisinde.
- DTO'lar `record`.
- Her use-case için birim test; her REST endpoint için MockMvc slice test.
- Yeni modül eklerken `docs/modules/<modul>.md` dosyasını da güncelle.

## Komutlar
```
cd server && ./mvnw verify          # derleme + test  (mvnw yoksa: mvn)
cd server && ./mvnw spring-boot:run # geliştirme sunucusu (:8734)
cd web && npm run dev               # frontend dev (:5173, proxy → :8734)
cd web && npm run build             # üretim build → server/src/main/resources/static
```
`JAVA_HOME=/opt/homebrew/opt/openjdk@17` bu makinede gereklidir.

## Yapmadan Önce Sor
- Yeni bir 3. parti bağımlılık eklemek (lisansını yaz)
- Veri modelinde geriye dönük uyumsuz değişiklik
- Yeni bir dış ağ çağrısı
- Lisanslama veya güvenlikle ilgili herhangi bir değişiklik
