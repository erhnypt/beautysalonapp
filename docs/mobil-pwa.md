# Mobil Görünüm & PWA (Faz 8)

Plan §Faz 8+: "Mobil görüntüleme". Ayrı bir mobil uygulama **değil** — mevcut React SPA
duyarlı hale getirildi ve tarayıcıdan "Ana ekrana ekle" ile kurulabilir bir PWA yapıldı.

## Yapılanlar

1. **Duyarlı kenar çubuğu** (`AppLayout.tsx`) — `sm:` (640px) altında gezinme menüsü
   varsayılan gizli; sol üstte hamburger düğmesiyle açılan, arkası kararan bir
   kaydırmalı panel (drawer) olur. Bir bağlantıya tıklamak paneli otomatik kapatır.
   640px ve üzeri (tablet/masaüstü) davranış **değişmedi** — kenar çubuğu her zaman görünür.
2. **PWA kurulabilirlik:**
   - `public/manifest.webmanifest` — ad, tema rengi (`#be185d`), `display: standalone`,
     192/512 px ikonlar (`public/icons/`, mevcut `favicon.svg`'den üretildi).
   - `public/sw.js` — service worker. **Kasıtlı olarak dar kapsam:** yalnızca aynı
     origin'in statik JS/CSS/ikon dosyalarını "stale-while-revalidate" ile önbekler.
     `/api/**` ve `/actuator/**` **asla önbelleklenmez** — bu bir ön muhasebe/randevu
     uygulaması; bayat kasa bakiyesi veya randevu listesi göstermek gerçek zarar verir.
     Çevrimdışı iş verisi yoktur, yalnızca uygulama kabuğu hızlı açılır.
   - `index.html` — manifest bağlantısı, `theme-color`, `apple-touch-icon`,
     `apple-mobile-web-app-*` meta etiketleri.
   - `main.tsx` — service worker yalnızca **üretim derlemesinde** kaydedilir
     (`import.meta.env.PROD`); Vite dev sunucusunun HMR'ıyla çakışmaz.

## Kapsam dışı (bilinçli)

- Sayfa içi tabloların (cari/stok/fatura listeleri) mobilde yatay kaydırma dışında
  yeniden düzenlenmesi — v1'de tarayıcının doğal yatay kaydırmasına bırakıldı.
- Push bildirimleri, arka planda senkronizasyon — bu bir Workbox/PWA yapılandırması
  gerektirir ve iş verisi asenkron güncellenmesi bu ürünün "her zaman güncel yerel
  veri" ilkesiyle gerilir; talep gelirse ayrıca değerlendirilir.
- Gerçek native mobil uygulama (Faz 8+ listesindeki "ayrı ürün" kapsamına daha yakın).

## Test

- Tarayıcı mobil görünüm (375×812) ile uçtan uca doğrulandı: giriş → panel → hamburger →
  drawer açılışı/kapanışı → nav öğesine tıklayınca otomatik kapanma.
- `GET /manifest.webmanifest` → 200, geçerli JSON, 4 ikon girişi.
- `web && npm run build` → `dist/manifest.webmanifest`, `dist/sw.js`, `dist/icons/*`
  doğru kopyalanıyor.
