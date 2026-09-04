# BeautySalonApp

Güzellik merkezi / kuaför / estetik işletmeleri için masaüstünde çalışan, veriyi
yerelde tutan, tarayıcı arayüzlü ön muhasebe + randevu + CRM + sadakat yazılımı.

> Tam teknik plan: [`beautysalonapp-teknik-plan.md`](beautysalonapp-teknik-plan.md)

## Depo yapısı

| Dizin | İçerik |
|---|---|
| `server/` | Spring Boot backend (REST API + statik SPA sunumu + zamanlanmış işler) |
| `web/` | React + TypeScript + Vite arayüzü |
| `license-server/` | Ayrı deploy edilen lisans/abonelik sunucusu (kendi VPS'inizde) — Ed25519 imzalı `license.lic` üretir |
| `packaging/` | jpackage / jlink / WinSW / launchd paketleme dosyaları + imzalama/notarization/güncelleme betikleri |
| `docs/` | Mimari, veri modeli, modül dokümanları, ADR'ler, kurulum + kullanım kılavuzu, güvenlik gözden geçirme |

> Sentetik demo/performans veri üretici: `server/.../perf/PerfDataGenerator.java`
> (`beautysalonapp.perf.seed=true` — bkz. [`docs/perf/README.md`](docs/perf/README.md)).
> Lisans üretme: `license-server` içindeki `LicenseSigner` + admin paneli.

## Geliştirme ortamı

- **Java 17** (bu makinede `JAVA_HOME=/opt/homebrew/opt/openjdk@17`)
- **Maven 3.9+**
- **Node 20+ / npm 10+**

## Hızlı başlangıç

```bash
# Backend (H2 file mode, sıfır kurulum)
cd server
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run
# → http://localhost:8734  (varsayılan admin: admin / admin123 — ilk girişte değiştirin)

# Frontend (ayrı terminal, geliştirme modu)
cd web
npm install
npm run dev
# → http://localhost:5173  (API çağrıları :8734'e proxy'lenir)
```

Üretim derlemesinde `web` build çıktısı `server/src/main/resources/static` altına
kopyalanır ve tek JAR olarak sunulur.

## Testler

```bash
cd server && JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn verify
```

## Durum — v1 yol haritası tamamlandı

| Faz | Kapsam | Durum |
|---|---|---|
| Faz 0 | Çekirdek: kullanıcı/rol/yetki, ayar, audit, lisans motoru, outbound guard | ✅ |
| Faz 1 | Lisans sunucusu (`license-server/`, Ed25519 + heartbeat + admin panel), jpackage/jlink `.msi`/`.dmg` paketleme (`packaging/`) | ✅ |
| Faz 2 | Cari (müşteri/satıcı/perakende), Stok (çoklu barkod/çapraz birim), Kasa & Gelir-Gider | ✅ |
| Faz 3 | Satış Sözleşmesi + Otomatik Taksitlendirme, Frondex Randevu | ✅ |
| Faz 4 | Fatura (alış/satış/perakende/iade), Çek portföyü, POS mahsuplaşma, Personel + Prim | ✅ |
| Faz 5 | Yedekleme motoru (AES-GCM, GFS rotasyon, doğrulama, geri yükleme), Günlük Analiz dashboard | ✅ |
| Faz 6 | SMS/e-posta bildirim (İYS kontrolü, kuyruk, tetikleyiciler), Kartlı promosyon/sadakat (PPOS) | ✅ |
| Faz 7 | Performans harness (10 yıl / 500k), güvenlik gözden geçirme + sertleştirme, kullanım/kurulum kılavuzu, pilot planı | ✅ |
| Faz 8 (v2, sürüyor) | Banka ekstresi içe aktarma & mutabakat (MT940/CSV) · Merkezi işletme şeması (şube CRUD + Günlük Analiz şube filtresi, bkz. ADR 0006) | ✅ (kısmi kapsam) |

**217 birim/entegrasyon testi yeşil** (`mvn test`). Ayrıca: `-Pperf test` performans bütçesi,
`-Ppg test` PostgreSQL entegrasyonu (Testcontainers, Docker), `web && npm run e2e` Playwright
kritik akışı (giriş → parola değişimi → panel → cari oluşturma → **randevu → GELDI + Tahsil →
Günlük Analiz'e yansıma**). Lisans kademeli kısıtlama
merdiveninin tüm durum geçişleri (`LicenseLifecycleTest`, sahte saat), tüm Flyway
migration'larının temiz uygulanması (`MigrationTest`) ve `common/` şemasının PostgreSQL 16'ya
uygulanması (`PostgresIntegrationTest`) kapsanır. Beş CI job: `server`, `postgres-it`,
`perf-budget`, `e2e`, `license-server`, `web`.
Ana iş akışı uçtan uca çalışır: cari → randevu → `GELDI` (stok sarfı + hizmet bedeli cariye +
prim tahakkuku) → tahsilat; sözleşme → taksit planı → taksit tahsilatı; fatura (KDV/indirim,
stok + cari + kasa tek transaction) → çek/POS.

**Yayına kadar kalanlar** (kod dışı — [`docs/17-faz7-sertlestirme.md`](docs/17-faz7-sertlestirme.md)
kontrol listesi): gerçek imzalama sertifikaları (Apple $99/yıl, Windows EV ~$300/yıl),
bağımsız sızma testi, canlı pilot + muhasebe mutabakatı, eğitim videoları, KVKK metinlerinin
hukukçu onayı.

## CI

`.github/workflows/ci.yml` — her push/PR'da `server` + `license-server` `mvn verify`, `web` build,
PR'larda bağımlılık zafiyet incelemesi. `release.yml` — `v*` etiketinde windows + macOS
runner'larında installer üretir (sertifika secret'ları varsa imzalı). `.github/dependabot.yml`
haftalık bağımlılık güncellemeleri.
