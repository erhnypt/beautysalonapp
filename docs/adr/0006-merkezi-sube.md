# ADR 0006 — Merkezi İşletme: Şube Şeması (kapsamlı izolasyon değil)

- **Durum:** Kabul edildi (kısmi kapsam, bilinçli)
- **Tarih:** 2026-09-04

## Bağlam

Plan §Faz 8+ (v2): "Merkezi işletme sistemi". `branch_id` sütunu zaten `BaseEntity`
üzerinden **her** tabloda var (v1'den beri) ve varsayılan olarak `1`dir. Ancak ~10 servis
şubeyi `private static final Long BRANCH = 1L;` ile **sabitliyor**:

```
AppointmentService, ContractService, FinanceService, InvoiceService, LoyaltyService,
PartyService, StockService, SettingService, StockDefaults, FinanceDefaults
```

Bu sabitleri kaldırıp her yazma yolunu (kullanıcının o an çalıştığı şubeye göre) doğru
`branch_id` ile etiketlemek — üstelik kullanıcı-şube ataması, oturum bazlı "aktif şube"
seçimi, ve mevcut ~200 testin bu varsayımla yazılmış olması nedeniyle — **büyük, riskli bir
refactor**dür. Kullanıcı bu iterasyonda kapsamı bilinçli olarak daralttı: **"sadece şemayı
hazırla"**.

## Karar

**Bu fazda yapıldı:**
1. `modules.branch` — şube CRUD'u (`Branch` varlığı zaten V1'den beri var olan `branch`
   tablosuna oturur, yeni migration gerekmedi). Tek merkez şube kuralı (`isHeadquarters`),
   son şube / merkez şube silinemez kuralları.
2. `ReportService.today(Long branchId)` — Günlük Analiz artık **isteğe bağlı** şube
   filtresiyle çalışabilir (`branchId=null` ⇒ tüm şubeler, v1 davranışıyla birebir aynı).
   Filtre saf, denetlenmiş bir `Long`'dan ürediği için ham SQL'e string ekleme güvenlidir
   (kullanıcı girdisi asla karışmaz); `NamedParameterJdbcTemplate`'e geçiş sonraki adımda
   değerlendirilebilir.
3. Frontend: `/subeler` (şube tanımlama), Günlük Analiz'de şube seçici (yalnızca 2+ şube
   varsa görünür).

**Bilinçli olarak YAPILMADI (ayrı, daha büyük bir iş):**
- Cari/stok/fatura/randevu/sözleşme/sadakat/personel modüllerinin **yazma yollarının**
  aktif şubeye göre etiketlenmesi — bu, listelenen ~10 serviste `BRANCH` sabitinin
  kaldırılıp yerine "oturumun aktif şubesi" kavramının (nereden geleceği: kullanıcı-şube
  ataması mı, oturum bazlı seçim mi — ayrıca karar gerektirir) geçirilmesini gerektirir.
- Kullanıcı-şube ataması / şube bazlı yetkilendirme (bir kullanıcı yalnızca kendi şubesini
  mi görür, yoksa merkez tüm şubeleri mi görür).
- Diğer okuma uçlarının (cari listesi, stok, fatura listesi vb.) şube filtresi alması —
  yalnızca Günlük Analiz kapsandı.
- Şubeler arası stok transferi, konsolide mali rapor, şube bazlı fiyat listesi.

## Sonuç

v1 tek-şube kullanıcıları için **hiçbir davranış değişmedi** (`branchId=null` varsayılan).
Çok şubeye geçmek isteyen bir kurulum bugün Şubeler ekranından ikinci şubeyi tanımlayabilir
ve Günlük Analiz'i şubeye göre süzebilir; ancak o şubede oluşturulan cari/randevu/fatura
kayıtları hâlâ `branch_id=1` ile yazılır (yazma yollarının şubelenmesi tamamlanana kadar).
Bu doküman ve `docs/modules/*.md` içindeki notlar bu sınırı açıkça işaretler ki "çok şubeli"
iddiası yanlış anlaşılmasın.

**Sonraki adım (ayrı bir oturum/karar):** aktif şube kavramının kaynağını netleştirip
(kullanıcı profili mi, oturum seçimi mi) yazma yollarını tek tek geçirmek.
