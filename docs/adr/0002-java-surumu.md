# ADR 0002 — Java sürümü: geliştirmede 17, pakette 21

- **Durum:** Kabul edildi
- **Tarih:** 2026-09-03

## Bağlam
Plan Java 21 (LTS) istiyor: virtual threads ve `jpackage` olgunluğu için.
Geliştirme makinesinde kurulu LTS sürüm openjdk@17.

## Karar
- Kaynak/hedef uyumluluğu **Java 17**.
- Kod, 21'e geçişi zorlaştıracak API'lerden kaçınır; virtual threads kullanımı
  `spring.threads.virtual.enabled` bayrağıyla soyutlanır (17'de kapalı).
- Faz 1 (paketleme) başlarken toolchain Java 21'e yükseltilir; `maven.compiler.release`
  değeri 21 yapılır, `jlink`/`jpackage` 21 JDK ile çalıştırılır.

## Sonuçlar
- `pom.xml` içinde `<maven.compiler.release>17</maven.compiler.release>`.
- CI matrisi ileride 17 + 21 olarak genişletilebilir.
