# ADR 0008 — Şube Mimarisinin Plan §12'den Sapması (dokümantasyon, kod değişikliği yok)

- **Durum:** Kabul edildi (riskin kaydı; mimari henüz değiştirilmedi)
- **Tarih:** 2026-09-06
- **İlgili kararlar:** [[0006-merkezi-sube]], [[0007-sube-yazma-izolasyonu]]

## Bağlam

Plan §12 ("Merkezi İşletme Sistemi — Çok Şubeli Yapı"), çok şubeli mimari için üç
seçenek tanımlar ve aralarında açıkça bir tercih yapar:

| Seçenek | Nasıl | Artı | Eksi |
|---|---|---|---|
| **A. Tek merkez + LAN/VPN** | Tüm şubeler merkezdeki tek DB'ye bağlanır | Anlık tutarlılık | **Hat koparsa şube çalışamaz** |
| **B. Şube bağımsız + periyodik konsolidasyon** | Her şube kendi DB'sinde; gece merkeze imzalı, şifreli özet paket aktarılır | Şube kesintiden etkilenmez | Merkez verisi T-1 |
| C. Hibrit | Operasyon şubede, raporlama merkezde | En pratik | En çok geliştirme |

Plan'ın açık önerisi: **"v1'de yapmayın. v2'de B ile başlayın."** Gerekçe, ürünün temel
mimari kısıtıyla (plan §2.3) doğrudan bağlantılı: bu bir **yerel sunucu + tarayıcı
arayüzü** ürünüdür — her işletme/şube normalde **kendi makinesinde** ayrı bir kurulum
çalıştırır. "Merkezi işletme" özelliği, aksi halde birbirinden bağımsız olan bu yerel
kurulumları konsolide bir görünüm için bağlamak üzere tasarlanmıştır.

ADR 0006 ve 0007 ile teslim edilen şey bu değildir. Uygulanan model: **tek bir sunucu
süreci, tek bir veritabanı**, her tabloda `branch_id` sütunu ve istek başına
`X-Branch-Id` HTTP başlığıyla taşınan bir "aktif şube" bağlamı
(`core.context.BranchContextHolder`). Bu, plan'ın **Seçenek A**'sına (eksi VPN/ağ
katmanı — burada hepsi tek process içinde) çok daha yakındır; Seçenek B'nin "her şube
kendi DB'sinde, bağımsız çalışır" temel özelliğini karşılamaz.

**Önemli:** ADR 0006 ve 0007'nin kendisi dürüsttür ve kapsamlarını net biçimde
sınırlar — ama ikisi de plan §12'nin Seçenek A/B ayrımına **hiç değinmez**. Bu ADR'nin
amacı o boşluğu kapatmaktır: seçilen yaklaşımı plan'ın açık tavsiyesine karşı adlandırmak
ve doğuracağı riski kayıt altına almaktır.

## Karar

Bu ADR **mimariyi değiştirmez** — mevcut kodu (ADR 0006/0007) olduğu gibi bırakır ve
sapmayı belgeler. Amaç, bir sonraki geliştiricinin (veya Claude Code'un) "çok şubeli"
iddiasını plan'daki Seçenek B ile karıştırmamasıdır.

**Kabul edilen risk:** Şu anki mimaride, sunucu süreci/veritabanı erişilemez olduğunda
**hiçbir şube** çalışamaz — bu, Seçenek B'nin varlık sebebi olan "şube kesintiden
etkilenmez" garantisinin tam tersidir. Bu, aşağıdaki dağıtım senaryolarından hangisinin
gerçek kullanım amacı olduğuna bağlı olarak farklı derecede risklidir:

1. **Tüm şubeler fiziksel olarak aynı yerde / tek bir sunucuyu paylaşan küçük bir
   işletme** (ör. aynı bina içinde iki salon) → risk düşük, LAN kesintisi zaten nadir ve
   kısa sürelidir; bu durumda mevcut mimari makul bir mühendislik kısayoludur.
2. **Coğrafi olarak ayrı şubeler, merkezi bir sunucuya WAN/VPN üzerinden bağlanıyor**
   → risk yüksek, tam olarak plan'ın Seçenek A'yı reddetme gerekçesidir ("hat koparsa
   şube çalışamaz"); ürünün "çevrimdışı çalışabilirlik" kalite hedefiyle (plan §1.4)
   doğrudan çelişir.

Bu depo hangi senaryonun hedeflendiğini şu ana kadar netleştirmedi.

## Sonraki adım (karar bekliyor)

Aşağıdakilerden biri seçilmeli ve bir ADR ile kayıt altına alınmalı:

- **(a)** Coğrafi olarak dağıtık şube kullanımı gerçek bir gereksinimse: plan §12
  Seçenek B'ye geçiş planlanmalı (her şube kendi yerel DB'sinde, gece imzalı/şifreli
  `consolidation_export` paketiyle merkeze aktarım). Bu, ADR 0006/0007'nin üzerine
  inşa edilen mevcut `branch_id` altyapısını **atmaz** — Seçenek B'de de her tablo
  `branch_id` taşımaya devam eder (plan §12: "v1'de bile branch_id alanını her tabloya
  koyun") — ama tek-DB varsayımını kaldırıp şube başına ayrı kurulum + senkronizasyon
  katmanı ekler. Büyük bir iştir, ayrı bir faz gerektirir.
- **(b)** Hedef kitle zaten tek-lokasyon veya güvenilir LAN'la bağlı şubelerse: mevcut
  mimariyi resmen benimseyin — bu ADR'yi "kabul edilen mimari" olarak günceleyin, plan
  §12'yi bu ürünün gerçek kullanım profiline göre revize edin ve satış/kurulum
  dokümanlarında "şubeler arasında güvenilir ağ bağlantısı gerekir" uyarısını açıkça verin.

Bu karar plan §20'nin açık sorularından **"2. Terminal sayısı: Tipik müşteride kaç
bilgisayar var?"** ile aynı kategoridedir — teknik değil, iş/pazar kararıdır ve bu depo
içinde tek başına verilemez.
