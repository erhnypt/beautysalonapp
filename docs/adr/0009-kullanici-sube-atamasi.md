# ADR 0009 — Kullanıcı-Şube Ataması (Yazma/Parametre Katmanı)

- **Durum:** Kabul edildi (kısmi kapsam, bilinçli)
- **Tarih:** 2026-09-14
- **Önceki karar:** [[0007-sube-yazma-izolasyonu]] — bu ADR'nin açıkça "sonraki karar"
  olarak bıraktığı kullanıcı-şube ataması / şube bazlı yetkilendirme boşluğunu kapatır.
- **Bağlam:** Güvenlik gözden geçirme B5 ("nesne düzeyi yetki / IDOR — herhangi bir
  kullanıcı `X-Branch-Id` başlığıyla herhangi bir şubede işlem yapabilir").

## Karar

**Opt-in, geriye dönük tam uyumlu atama modeli.** `app_user` ↔ `branch` arasında
`user_branch` tablosu (V18): bir kullanıcının satırı **yoksa** kısıtlama yoktur — tüm
mevcut kurulumlar ve testler (hiç kimseye atama yapılmamış) davranışını **birebir**
korur. Bir kullanıcıya bir veya daha fazla şube atanırsa yalnızca o şube(ler)de
çalışabilir:

1. `AppUser.assignedBranchIds` — `@ElementCollection<Long>` (ham id, `Branch` varlığına
   entity referansı değil; `security` modülü `modules.branch`'e entity düzeyinde
   bağımlı olmasın — CLAUDE.md #5). Varlık kontrolü için `modules.branch.application.BranchPort`
   (yeni port, `BranchService` uygular) kullanılır.
2. `BranchContextFilter` — `X-Branch-Id` artık yalnızca "şube var mı" değil "bu
   kullanıcı bu şubede çalışabilir mi" sorusunu da yanıtlar: izinsiz şube → 403
   `access_denied`; kısıtlı kullanıcı başlık göndermezse (global `1L` varsayılanına
   sessizce düşmek yerine) kendi atanmış şubelerinden birine otomatik geçer — aksi
   halde yeni kayıtlar yanlış şubeye (`branch_id`) etiketlenirdi.
3. `UserService.resolveRequestedBranch(username, requestedBranchId)` — açık
   `branchId` **sorgu parametresi** alan tüm rapor uçları (`DashboardController`,
   `PartyController`, `StockController` — toplam 9 metot) bu tek kapıdan geçer. Bunlar
   `X-Branch-Id` başlığından bağımsız çalıştığı için `BranchContextFilter`'ın kapsamı
   dışındaydı; ayrı bir kontrol noktası gerekti.
4. `GET /api/v1/branches` — kısıtlı kullanıcıya yalnızca atanmış şubeleri döner (şube
   seçici erişemediği şubeleri hiç göstermez).
5. `PUT /api/v1/users/{id}/branches` (`USER_EDIT`) — atama CRUD'u; `UsersPage`'de
   "Şubeleri Düzenle".

## Bilinçli olarak kapsam dışı (ayrı, daha büyük bir iş)

ADR 0007'nin işaret ettiği ikinci boşluk **hâlâ kapanmadı**: cari/stok/fatura/randevu
gibi genel **liste** uç noktaları (branchId parametresi almayanlar) hiçbir şube
filtresi uygulamıyor — bu uç noktalar zaten hiçbir zaman şubeye göre süzülmüyordu, bu
ADR onları değiştirmedi. Yani bugün: bir kullanıcı Şube 2'ye kısıtlanmışsa artık
Şube 1'de **işlem yapamaz** ve `X-Branch-Id`/`branchId` ile Şube 1 **raporu** çekemez,
ama örneğin genel cari listesinde Şube 1'de oluşturulmuş bir cari kaydını **görmeye**
devam eder (o liste sorgusu `branch_id` filtrelemiyor). Bunu kapatmak, ADR 0007'nin
zaten belirttiği gibi, her modülün repository/sorgu katmanına şube filtresi eklemeyi
gerektiren ayrı, büyük bir iterasyondur.

## Sonuç

Hiç kimseye atama yapılmayan kurulumlar (bugünün tüm kurulumları) için **hiçbir
davranış değişmedi**. Atama yapılan bir kullanıcı için yazma yolları ve parametreli
raporlar artık gerçek anlamda kısıtlanıyor; genel liste uç noktalarındaki okuma boşluğu
belgelenmiş, ayrı bir sonraki adım olarak kalıyor.

Test: `UserBranchAssignmentTest` (6), `UserServiceTest` şube ataması senaryoları (4).
