# BeautySalonApp

Güzellik merkezi / kuaför / estetik işletmeleri için masaüstünde çalışan, veriyi
yerelde tutan, tarayıcı arayüzlü ön muhasebe + randevu + CRM + sadakat yazılımı.

> Tam teknik plan: [`beautysalonapp-teknik-plan.md`](beautysalonapp-teknik-plan.md)

## Depo yapısı

| Dizin | İçerik |
|---|---|
| `server/` | Spring Boot backend (REST API + statik SPA sunumu + zamanlanmış işler) |
| `web/` | React + TypeScript + Vite arayüzü |
| `license-server/` | Ayrı deploy edilen lisans/abonelik sunucusu (kendi VPS'inizde) |
| `packaging/` | jpackage / WinSW / launchd paketleme dosyaları |
| `tools/` | Lisans üretme CLI, demo veri üretici |
| `docs/` | Mimari, veri modeli, modül dokümanları, ADR'ler |

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

## Durum

| Faz | Kapsam | Durum |
|---|---|---|
| Faz 0 | Çekirdek: kullanıcı/rol/yetki, ayar, audit, lisans motoru, outbound guard | ✅ |
| Faz 2 | Cari (müşteri/satıcı/perakende), Stok (çoklu barkod/çapraz birim), Kasa & Gelir-Gider | ✅ |
| Faz 3 | Satış Sözleşmesi + Otomatik Taksitlendirme, Frondex Randevu | ✅ |
| Faz 4 | Fatura (alış/satış/perakende/iade), Çek portföyü, POS mahsuplaşma, Personel + Prim | ✅ |
| Faz 1 | Lisans sunucusu (ayrı repo/VPS), jpackage `.msi`/`.dmg` paketleme | ⏳ |
| Faz 5 | Yedekleme motoru (AES-GCM, GFS rotasyon, doğrulama, geri yükleme), Günlük Analiz dashboard | ✅ |
| Faz 6 | SMS/e-posta bildirim (İYS kontrolü, kuyruk, tetikleyiciler), Kartlı promosyon/sadakat (PPOS) | ✅ |
| Faz 7 | Performans, güvenlik gözden geçirme, imzalama/notarization, pilot | ⏳ |

**157 birim/entegrasyon testi yeşil.** Ana iş akışı uçtan uca çalışır: cari → randevu →
`GELDI` (stok sarfı + hizmet bedeli cariye + prim tahakkuku) → tahsilat; sözleşme → taksit
planı → taksit tahsilatı; fatura (KDV/indirim, stok + cari + kasa tek transaction) → çek/POS.
Yol haritası: teknik planın 17. bölümü.
