# ADR 0001 — Build aracı: Gradle yerine Maven

- **Durum:** Kabul edildi
- **Tarih:** 2026-09-03

## Bağlam
Teknik plan Gradle (Kotlin DSL) öneriyordu. Geliştirme makinesinde Gradle kurulu
değil ve Gradle wrapper'ı bootstrap etmek için ağdan ikili indirme gerekiyor.
Maven 3.9 ve birden çok JDK (17/24) kurulu ve çalışır durumda.

## Karar
Backend ve `license-server` **Maven** ile derlenir. Maven Wrapper (`mvnw`)
depoya eklenir, böylece Maven kurulu olmayan makinelerde de çalışır.

## Sonuçlar
- Plan metnindeki `./gradlew ...` komutları `./mvnw ...` karşılıklarıyla okunmalı.
- Spring Modulith, Flyway, jpackage entegrasyonlarının hepsinin olgun Maven
  eklentileri var; işlevsel kayıp yok.
- CI iş akışı Maven'e göre yazılır.
