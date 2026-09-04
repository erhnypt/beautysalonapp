// BeautySalonApp — PWA kurulabilirlik + statik kabuk önbelleği (mobil görünüm, Faz 8).
//
// KASITLI OLARAK dar kapsam: yalnızca aynı origin'in statik varlıklarını (JS/CSS/ikon)
// "stale-while-revalidate" ile önbekler; hızlı yeniden açılış + çevrimdışı kabuk sağlar.
// /api/** İSTEKLERİ ASLA ÖNBELLEKLENMEZ — bu bir ön muhasebe/randevu uygulaması; bayat kasa
// bakiyesi veya randevu listesi göstermek gerçek zarar verir. Çevrimdışı iş verisi yoktur.

const CACHE = "bsa-shell-v1";

self.addEventListener("install", () => {
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys().then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
  );
  self.clients.claim();
});

self.addEventListener("fetch", (event) => {
  const { request } = event;
  if (request.method !== "GET") return;

  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return;
  if (url.pathname.startsWith("/api/") || url.pathname.startsWith("/actuator/")) return;

  event.respondWith(
    caches.open(CACHE).then(async (cache) => {
      const cached = await cache.match(request);
      const network = fetch(request)
        .then((res) => {
          if (res.ok) cache.put(request, res.clone());
          return res;
        })
        .catch(() => cached);
      return cached || network;
    })
  );
});
