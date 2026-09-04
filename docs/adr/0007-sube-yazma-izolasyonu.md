# ADR 0007 — Tam Şube Yazma İzolasyonu

- **Durum:** Kabul edildi
- **Tarih:** 2026-09-05
- **Önceki karar:** [[0006-merkezi-sube]] — bu ADR'nin "sonraki adım" olarak bilinçli
  şekilde kapsam dışı bıraktığı işi tamamlar.

## Bağlam

ADR 0006, şube CRUD'unu ve Günlük Analiz'in **okuma** tarafında isteğe bağlı şube
filtresini teslim etmiş, ama cari/stok/fatura/randevu/sözleşme/sadakat/personel gibi
modüllerin **yazma yollarının** aktif şubeye göre etiketlenmesini "büyük, ayrı bir iş"
olarak ertelemişti. Kullanıcı bu işi tamamlamayı istedi.

İnceleme şunu ortaya çıkardı: ~11 dosyada geçen `private static final Long BRANCH = 1L;`
sabitleri **hiçbir zaman `entity.setBranchId(...)` çağırmıyor** — yalnızca (a) katalog/kod
benzersizliği kontrolünde (ürün/birim/hizmet/sadakat kartı/cari kodu — bunlar **bilinçli
olarak şirket geneli/paylaşımlı** kalmalı) ve (b) belge numarası serisinde kullanılıyor.
Yani asıl sorun bu ~11 servisin yeniden yazılması değil, `BaseEntity`'nin her zaman
sınıf alanı varsayılanı `1L`'i kullanmasıydı — hiçbir entity gerçekte farklı bir şubeye
etiketlenmiyordu.

## Karar

**Servisleri değil, `BaseEntity`'yi düzelt.** Katalog/kod benzersizliği ve belge numarası
serisi mantığı **şirket geneli kalmaya devam eder** (bilinçli, doğru tasarım — değiştirilmedi).

1. `core.context.BranchContextHolder` — modülsüz, saf `ThreadLocal<Long>`. `null` = bağlam
   yok → çağıran taraf `1L` (v1/merkez) varsayar.
2. `BaseEntity.assignActiveBranch()` (`@PrePersist`) — bağlam doluysa `branchId`'yi onunla
   etiketler; boşsa (arka plan işi, test, eski istemci) **davranış birebir aynı kalır**
   (sınıf alanı varsayılanı `1L`).
3. `modules.branch.web.BranchContextFilter` (`@Order(2)`, `LicenseEnforcementFilter`'dan
   hemen sonra) — `X-Branch-Id` başlığını okur, `BranchRepository` ile doğrular, istek
   süresince bağlamı taşır, `finally`'de temizler. Geçersiz/silinmiş şube → HTTP 400
   (`invalid_branch_header`); başlık yoksa davranış değişmez.
4. `StockService.defaultWarehouseId()/consumptionWarehouseId()` ve
   `FinanceService.defaultCashAccountId()` — aktif şube `1` ise (veya bağlam yoksa) **aynen
   eski global çözüme** düşer; gerçek bir şube (`id ≠ 1`) aktifse önce o şubenin kendi
   deposu/kasası aranır, yoksa yine global çözüme düşülür (eski kurulumlarla geriye dönük
   uyumluluk).
5. `BranchService.create()` — yeni şube için otomatik bir depo (`D-<şube kodu>`) ve kasa
   hesabı (`K-<şube kodu>`) açar (`BranchContextHolder`'ı geçici olarak yeni şubeye ayarlayıp
   `makeDefault=false` ile). Kod, şube koduna göre türetildiği için benzersizdir.
6. Frontend: `lib/branch.ts` (aktif şube seçimi, yalnızca tarayıcıda `localStorage`),
   `api.ts` her isteğe `X-Branch-Id` ekler, `BranchSwitcher` (yalnızca 2+ şube varsa
   sidebar'da görünür; seçim değişince sayfa yenilenir — TanStack Query önbelleğinde
   şubesiz alınmış veri kalmasın diye).

## Bilinçli sınırlar (kapsam dışı, dokümante edilmiş)

- **Kod benzersizliği hâlâ şirket geneli değil, branch_id=1 kapsamlı kontrol ediliyor**
  (`findByBranchIdAndCode(BRANCH=1, code)`). Otomatik provizyon (`D-`/`K-` önekleri şube
  koduna göre) çakışmayı yapı gereği önler; ama bir kullanıcı elle yeni bir şube için
  branch-1'de kullanılmayan ama başka bir şubede zaten var olan bir depo/kasa kodu
  girerse, bu kontrol yakalamaz. Gerçek şirket-geneli benzersizlik (tüm şubeler,
  `branch_id` fark etmeksizin) ayrı bir iterasyon.
- Kullanıcı-şube ataması / şube bazlı yetkilendirme hâlâ yok — herhangi bir kullanıcı
  `X-Branch-Id` başlığıyla (yetkisi olduğu sürece) herhangi bir şubede işlem yapabilir.
  Bu, çok şubeli kurulumlarda "kasiyer yalnızca kendi şubesini görsün" ihtiyacını
  karşılamaz; sonraki bir karar.
- Diğer okuma uçları (cari listesi, stok listesi, fatura listesi vb.) hâlâ şube filtresi
  almıyor — yalnızca ADR 0006'daki Günlük Analiz filtresi var. Artık yazma yolları şubeye
  göre etiketlendiği için bu okuma tarafı boşluğu daha belirgin hale geldi; sonraki adım.
- Depolar arası/şubeler arası stok transferi ve konsolide mali rapor hâlâ yok.

## Sonuç

v1 tek-şube kurulumları için **hiçbir davranış değişmedi**: `X-Branch-Id` göndermeyen
bir istemci (eski istemci, arka plan işi, mevcut testlerin tamamı) tamamen eski yolu
izler. Çok şubeli bir kurulum artık ikinci bir şube açtığında o şubede oluşturulan
stok/kasa hareketleri gerçekten o şubenin `branch_id`'siyle yazılır — ADR 0006'nın
uyarısı ("o şubede oluşturulan kayıtlar hâlâ branch_id=1 ile yazılır") artık geçerli değil.

Test: `BranchIsolationTest` (4) — otomatik provizyon, merkez şubede davranış değişmezliği,
yeni şubede kaynak çözümü, bağlamsız oluşturmanın şube 1'de kalması. `BranchContextFilterTest`
(4) — başlık doğrulama senaryoları (yok/geçerli/sayısal-olmayan/bulunamayan).
