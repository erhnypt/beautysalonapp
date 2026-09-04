# Pilot Müşteri Planı — Faz 7

Teknik plan §17: "Pilot müşteri geri bildirimleri". Plan Risk #1: *Faz 3 sonunda pilota çık*.
Bu belge pilot sürecini yapılandırır.

---

## 1. Amaç

Gerçek bir güzellik merkezinde 4–6 hafta boyunca ürünü **canlı** kullandırıp:

- Günlük iş akışının kesintisiz döndüğünü doğrulamak (randevu → satış → tahsilat → gün sonu)
- Mali hesaplamaların muhasebe ile **mutabık** olduğunu görmek (plan Risk #5)
- Yedekleme + geri yükleme sürecinin bir kullanıcının elinde çalıştığını görmek (Risk #4)
- Performansı gerçek veriyle ölçmek (liste ekranları < 300 ms — `docs/perf/README.md`)
- Eksik/kafa karıştıran ekranları tespit edip yayından önce düzeltmek

## 2. Pilot İşletme Profili (ideal)

- 2–4 kabin, 3–8 personel, günde 15–40 randevu
- Hâlihazırda bir program kullanıyor (karşılaştırma ve veri göçü senaryosu)
- Sahibi geri bildirime açık, haftada 30 dk ayırabilir
- Tercihen tek bilgisayar (H2) + bir resepsiyon terminali (LAN)

## 3. Ön Koşullar (pilot başlamadan)

- [ ] İmzalı/notarize installer (veya en azından iç dağıtım onaylı derleme)
- [ ] Kurulum dokümanı + kullanım kılavuzu teslim (`docs/kurulum.md`, `docs/kullanim-kilavuzu.md`)
- [ ] Pilot lisansı üretildi (lisans sunucusu → 60 gün, `TRIAL`/`PILOT`)
- [ ] Yedekleme hedefi (USB veya ağ) hazır, yedek parolası yazılı teslim edildi
- [ ] Destek kanalı + SLA belirlendi (ör. mesai içi 4 saat)
- [ ] Geri alma planı: pilot başarısızsa eski programa dönüş, veri kaybı yok

## 4. Takvim

| Hafta | İçerik |
|---|---|
| 0 | Kurulum, kullanıcı/rol tanımı, temel veri (hizmetler, ürünler, personel), 1 saat eğitim |
| 1 | Gözetimli kullanım — her gün kısa telefon; sadece randevu + satış |
| 2 | Tam kullanım — stok, sözleşme, prim; ilk **muhasebe mutabakatı** |
| 3 | Yedekten dönüş **tatbikatı** (test verisiyle); performans ölçümü |
| 4 | Serbest kullanım; günlük geri bildirim formu |
| 5–6 | Değerlendirme, düzeltme turu, yayın kararı |

## 5. Ölçütler (yayın için "geçti" eşiği)

| Ölçüt | Hedef |
|---|---|
| Gün sonu cirosu ile kasa sayımı farkı | 0 (kuruş hatası yok) |
| Muhasebe mutabakatı (aylık) | Farklar açıklanabilir, sistemik hata yok |
| Kritik hata (veri kaybı, yanlış tutar) | 0 |
| Yedekten dönüş tatbikatı | Başarılı, veri bütünlüğü tam |
| Liste ekranı açılışı (gerçek veri) | < 300 ms (p95) |
| Kullanıcı "eski programa dönmek ister miydiniz?" | Hayır |
| Açık P1 hata (yayın engelleyici) | 0 |

## 6. Geri Bildirim Toplama

- Günlük: `docs/pilot/geri-bildirim-formu.md` (kısa, 2 dk)
- Haftalık: 30 dk görüşme, ekran paylaşımıyla
- Program içi hatalar: `logs/beautysalonapp.log` + ekran görüntüsü
- Tüm bulgular tek listede: `id, tarih, modül, önem (P1/P2/P3), açıklama, durum`

## 7. Riskler ve Önlemler

| Risk | Önlem |
|---|---|
| Pilot işletmede veri kaybı | Çift yedek (yerel + USB), her sabah "son yedek" kontrolü, ilk hafta günlük elle yedek |
| Yanlış mali tutar güveni sarsar | Hafta 2'de muhasebeci ile mutabakat; şüpheli her kalem incelenir |
| Kullanıcı direnci | Eğitim + kılavuz + ilk hafta gözetim; eski program paralel açık kalır |
| Performans yetersiz | `PerfDataGenerator` ile önceden 500k testi yapıldı; gerekirse PostgreSQL |
