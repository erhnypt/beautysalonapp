# BeautySalonApp — Kullanım Kılavuzu

Sürüm 1.0 · Güzellik Merkezi Yönetim Yazılımı

Bu kılavuz günlük kullanıcı içindir. Kurulum için `docs/kurulum.md`, teknik ayrıntı için
`docs/` altındaki modül dosyalarına bakın.

---

## 1. Genel Bakış

BeautySalonApp **kendi bilgisayarınızda** çalışır. Verileriniz internete gönderilmez;
bilgisayarınızdaki şifreli bir veritabanında durur. Programı bir **tarayıcıdan** kullanırsınız
(Chrome, Edge veya Safari) — masaüstündeki kısayol tarayıcıyı `http://localhost:8734`
adresine açar.

Arka planda bir **Windows hizmeti / macOS servisi** sürekli çalışır; bilgisayarı açtığınızda
program hazırdır, ayrıca başlatmanız gerekmez.

---

## 2. İlk Giriş

1. Masaüstündeki **BeautySalonApp** kısayoluna çift tıklayın.
2. Açılan ekrana kurulumda belirlenen **kullanıcı adı** ve **parola** ile girin.
   (Varsayılan `admin` / `admin123` ise ilk girişte parola değiştirmeniz istenir — **mutlaka değiştirin**.)
3. İlk kez giriyorsanız sırasıyla:
   - **İşletme bilgileri** (ünvan, adres, vergi no) — Ayarlar › İşletme
   - **Kullanıcılar ve roller** — her çalışana ayrı hesap açın, parola paylaşmayın
   - **Yedekleme parolası ve klasörü** — Ayarlar › Yedekleme (bkz. §9)
   - **Lisans dosyası** — Ayarlar › Lisans › "Lisans yükle"

### Roller

| Rol | Ne yapabilir |
|---|---|
| **ADMIN** | Her şey + kullanıcı yönetimi + ayarlar |
| **MUDUR** | Tüm iş modülleri + raporlar; kullanıcı yönetemez |
| **KASIYER** | Satış, tahsilat, randevu; fiyat/indirim sınırlı |
| **PERSONEL** | Kendi randevuları, müşteri kartı görüntüleme |
| **RAPOR_OKUYUCU** | Yalnızca raporlar (muhasebeci / işletme ortağı için) |

---

## 3. Ana Ekran (Günlük Analiz)

Girişte **bugünün özeti** gelir:

- **Ciro** (fatura + randevu), **tahsilat**, **gider**
- **Ödeme türü dağılımı** (nakit / kart / havale)
- **Randevu durumları** (planlandı / geldi / gelmedi / iptal)
- **Yeni müşteri sayısı**
- **Uyarılar:** vadesi gelen taksitler, kritik stok, bu hafta vadesi dolan çekler
- Son 30 günün **ciro trendi**, **hizmet dağılımı**, **personel doluluğu**

Gün sonunda "Gün Sonu Raporu" ile yöneticiye özet e-posta metni üretebilirsiniz.

---

## 4. Cari (Müşteri / Satıcı / Personel)

**Cari** = hesabı olan herkes. Menü: **Cariler**.

- **Yeni müşteri:** ad-soyad, telefon, doğum tarihi, cinsiyet. Telefon/TC/e-posta **şifreli** saklanır.
- **Rıza (KVKK):** SMS ve e-posta izinlerini müşteriden alıp işaretleyin. İzin yoksa kampanya
  SMS'i **gönderilmez** (program engeller). İYS durumu alanını güncel tutun.
- **Cari ekstresi:** müşterinin borç/alacak hareketleri, bakiye.
- **Risk limiti:** bakiyesi limiti aşan müşteride satışta uyarı.
- **Anonimleştirme:** "Unutulma hakkı" talebinde kimlik alanları maskelenir, mali kayıt korunur.

---

## 5. Stok

Menü: **Stok**.

- **Ürün kartı:** kod, ad, KDV oranı, **temel birim** (adet, ml, gr…). Bir ürünün birden çok
  **birimi** (koli = 12 adet) ve birden çok **barkodu** olabilir.
- **Depolar:** DEPO (ana), VITRIN, SARF (hizmet tüketimi). Stok bir depo–ürün çiftinde tutulur.
- **Hareketler:** alış, satış, sayım, sarf, transfer. Maliyet **ağırlıklı ortalama** ile hesaplanır.
- **Kritik stok:** yeniden sipariş seviyesinin altına düşen ürünler dashboard'da uyarı verir.
- **Negatif stok politikası:** Ayarlar'dan `İZİN VER / UYAR / ENGELLE`.

---

## 6. Randevu (Frondex Takvim)

Menü: **Randevu**.

- **Takvim görünümü:** personel/oda sütunları, sürükle-bırak.
- **Yeni randevu:** müşteri, hizmet, personel, başlangıç saati. Süre hizmetten gelir;
  çakışma kontrolü otomatiktir (tampon süreleriyle birlikte).
- **Durum akışı:** `PLANLANDI → GELDI` (veya `GELMEDI` / `IPTAL`).
- **GELDI** işaretlenince: hizmet reçetesindeki **stoklar otomatik düşer** (ayarla kapatılabilir),
  personel **primi tahakkuk eder**, ciroya yansır.
- **Hatırlatma SMS'i:** randevudan önce (varsayılan 24 saat) izinli müşterilere gider.

---

## 7. Satış, Fatura ve Tahsilat

### Hızlı satış / perakende
Menü: **Fatura › Yeni**. Ürün/hizmet satırları ekleyin, indirim uygulayın, ödemeyi alın
(nakit / kart / havale / çek). Perakendede yazarkasa fiş no istenebilir (ayar).

### Sözleşmeli satış (taksitli paket)
Menü: **Sözleşmeler**. Toplam tutar + peşinat + taksit sayısı girin; program **taksit planını**
üretir (kuruş farkı son taksite, ay sonu vade düzeltmesi dahil). Vadesi gelen/geçen taksitler
dashboard'da uyarı olur.

### Çek ve POS
- **Çek:** portföye alınan/verilen çekler, vade takibi, tahsile verme, karşılıksız işaretleme.
- **POS:** slip tutarı, taksit, komisyon oranı → **net tutar** ve **valör tarihi** hesaplanır.

---

## 8. Personel ve Prim

Menü: **Personel**.

- **Personel kartı:** ünvan, işe giriş, IBAN (şifreli), sınıf, varsayılan prim oranları.
- **Prim kuralları:** hizmet/ürün bazında oran veya tutar; personel sınıfına göre.
- **Prim tahakkuku:** randevu GELDI olunca veya satış onaylanınca birikir; ay sonu dönemi kapatılır.
- **Avans** ve **maaş dönemi** takibi.

---

## 9. Yedekleme (ÇOK ÖNEMLİ)

Menü: **Ayarlar › Yedekleme**.

- **Otomatik yedek:** her gece (varsayılan 23:00) şifreli `.bsa` dosyası. Günlük 7, haftalık 4,
  aylık 12 kopya saklanır.
- **Yedek parolası:** kurulumda belirlenir. **Parolayı kaybederseniz yedek AÇILMAZ.**
  Parolayı yazın ve işletme dışında güvenli bir yerde saklayın.
- **İkincil hedef:** yedekleri ayrıca bir USB disk / ağ klasörüne kopyalayın (Ayarlar'dan yol verin).
- **Geri yükleme:** Ayarlar › Yedekleme › "Yedekten dön" → dosya seç → parola gir.
  Geri yükleme mevcut veriyi **değiştirir**; önce otomatik güvenlik yedeği alınır.
- Ekranda daima **"son başarılı yedek"** tarihi görünür — her sabah kontrol edin.

---

## 10. Lisans Durumları

Program lisans sunucusuyla günde bir "nabız" alışverişi yapar (internet varsa). Durum:

| Durum | Anlamı | Etki |
|---|---|---|
| **ACTIVE** | Normal | Kısıtlama yok |
| **EXPIRING** | Bitişe < 30 gün | Uyarı bandı |
| **GRACE** | Süre doldu, ek süre | Uyarı; çalışmaya devam |
| **READ_ONLY** | Ödeme/yenileme gerekli | Yeni kayıt eklenemez; **görüntüleme + dışa aktarma açık** |
| **LOCKED** | Uzun süre yenilenmedi | Yalnızca **veri dışa aktarma** çalışır |
| **TAMPERED** | Lisans/saat kurcalandı | Destek ile iletişime geçin |

**Her durumda verinize erişebilir ve dışa aktarabilirsiniz.** İnternet yoksa program bir süre
(varsayılan 30 gün) çevrimdışı çalışır.

---

## 11. LAN'dan Erişim (birden çok bilgisayar)

Varsayılan olarak program yalnızca **kurulu olduğu bilgisayardan** açılır. Resepsiyon +
kabin gibi çok terminalli kullanım için: **Ayarlar › Ağ › "Yerel ağ erişimine izin ver"**.
Açıldığında bir güvenlik uyarısı görürsünüz. Diğer bilgisayarlardan
`http://<sunucu-bilgisayar-adı>:8734` ile bağlanılır. Bu senaryoda PostgreSQL'e geçiş önerilir.

---

## 12. Sık Sorunlar

| Belirti | Çözüm |
|---|---|
| Kısayol tarayıcıda "bağlanılamıyor" | Hizmet çalışmıyor olabilir. Windows: *Hizmetler* → **BeautySalonApp** → Başlat. macOS: bilgisayarı yeniden başlatın. |
| "Oturum süresi doldu" | Yeniden giriş yapın; uzun süre işlem yapılmayınca oturum kapanır. |
| Parolamı unuttum | Başka bir ADMIN kullanıcı **Ayarlar › Kullanıcılar**'dan sıfırlar. Tek ADMIN siz iseniz destek ile iletişime geçin. |
| Hesap kilitlendi (5 hatalı deneme) | 15 dakika bekleyin veya ADMIN sıfırlasın. |
| "Yedek parolası hatalı" | Doğru `.bsa` + doğru parola gerekir; parola olmadan geri dönülemez. |
| Rapor ekranı yavaş | Çok büyük veri + tek bilgisayar. Destek ile PostgreSQL geçişini görüşün. |

Destek: (lisans sözleşmenizdeki kanal)
