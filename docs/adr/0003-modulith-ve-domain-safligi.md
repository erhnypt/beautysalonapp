# ADR 0003 — Spring Modulith'in ertelenmesi ve domain saflığı tavizi

- **Durum:** Kabul edildi
- **Tarih:** 2026-09-03

## Bağlam
Plan, modül sınırlarını Spring Modulith ile derleme zamanında zorunlu kılmayı
öneriyor. Ayrıca "entity'ler domain'de, JPA anotasyonları infrastructure'da
(mümkünse ayır)" diyor.

## Karar
1. **Modül sınırı denetimi Faz 0'da ArchUnit ile yapılır.** `ArchitectureTest`
   şu kuralları uygular: `double`/`float` yasağı, domain katmanının Spring
   bileşenlerine bağlanmaması, web katmanına domain bağımlılığı olmaması, giden
   HTTP'nin yalnızca `GuardedRestClient` üzerinden gitmesi. İş modülleri
   eklendikçe `modules.<x>` paketleri arası erişim kuralları da buraya eklenir.
   Spring Modulith runtime doğrulaması Faz 2'de, ilk iş modülüyle birlikte devreye alınır.

2. **Domain entity'leri JPA + Spring Data auditing anotasyonlarını taşır.**
   `BaseEntity` `@Entity` alt sınıflarına `@CreatedBy/@CreatedDate` gibi saf
   *metadata* anotasyonları uygular. Bunlar davranış değil işaretlemedir; ArchUnit
   kuralı bu pakete (`org.springframework.data.annotation`) izin verir ama
   `stereotype/context/beans/web/boot/transaction` bağımlılıklarını yasaklar.
   Tam hexagonal ayrım (ayrı persistence entity + domain model) ileride
   performans/karmaşıklık gerekçesi çıkarsa modül bazında yapılabilir.

## Sonuçlar
- `pom.xml`'e şimdilik `spring-modulith-*` bağımlılığı eklenmedi.
- CLAUDE.md #5 kuralı ArchUnit testleriyle korunur.
