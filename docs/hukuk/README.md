# Hukuki Metinler — Durum

> **Bu klasördeki hiçbir belge nihai değildir ve hukuki tavsiye niteliği taşımaz.**
> İçerik, KVKK m.10 (aydınlatma) ve m.5 (açık rıza) genel çerçevesine göre hazırlanmış
> **taslaklardır** — bir avukat/KVKK danışmanı tarafından işletmenin gerçek durumuna
> (veri sorumlusu kimliği, VERBİS kaydı, aktarım yapılan üçüncü taraflar, saklama
> süreleri) göre incelenip onaylanmadan **kullanıma alınmamalıdır**. Bkz. CLAUDE.md
> "Değişmez Kurallar" ve `docs/04-guvenlik-kvkk.md`.

## Neden kod değil

Bu depo bir yazılım projesidir; aydınlatma/rıza metinlerinin son hâli **hukuki bir
üründür** ve yazılım geliştirici (veya bir kod asistanı) tarafından tek başına
sonuçlandırılamaz. `docs/guvenlik/guvenlik-gozden-gecirme.md` §10 bunu açıkça
"kod dışı" olarak işaretler.

## Bu klasörde ne var

| Dosya | İçerik |
|---|---|
| [aydinlatma-metni-taslak.md](aydinlatma-metni-taslak.md) | KVKK m.10 aydınlatma metni taslağı — hangi veri, neden, kime aktarılıyor, haklar (m.11) |
| [acik-riza-metni-taslak.md](acik-riza-metni-taslak.md) | SMS/e-posta ticari ileti için açık rıza metni taslağı (İYS uyumlu) |

Taslaklar `docs/04-guvenlik-kvkk.md` §2 (şifrelenen alanlar) ve §4 (rıza/İYS alanları)
ile birebir eşleşecek şekilde, uygulamanın **gerçekten** işlediği veri kategorilerine
göre yazıldı — yani "genel şablon" değil, bu ürünün veri modeline göre başlangıç noktası.

## Bir avukat/KVKK danışmanıyla netleştirilmesi gerekenler

1. **Veri sorumlusu kimliği** — işletmenin unvanı, adresi, VERBİS sicil no (varsa).
2. **Üçüncü taraf aktarımları** — SMS/e-posta sağlayıcısı, ödeme/POS sağlayıcısı,
   e-Fatura entegratörü varsa bunların KVKK sözleşmeleri (veri işleyen sözleşmesi).
3. **Saklama süreleri** — `docs/04-guvenlik-kvkk.md` §6'daki `notification_log` gibi
   henüz otomatikleştirilmemiş kalemler dahil, tüm veri kategorileri için nihai süre.
4. **VERBİS yükümlülüğü** — işletme büyüklüğüne göre kayıt gerekip gerekmediği.
5. **Basılı/dijital sunum şekli** — kağıt formda mı, ekranda onay kutusuyla mı,
   ikisi birden mi.

Son metinler onaylandıktan sonra bu klasöre eklenip (veya `config/reports` altına
yazdırılabilir şablon olarak konup) `docs/04-guvenlik-kvkk.md`'deki referans
güncellenmelidir.
