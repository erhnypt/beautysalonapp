# Kişisel Verilerin İşlenmesine İlişkin Aydınlatma Metni (TASLAK)

> ⚠️ **TASLAK — hukuki tavsiye değildir.** Kullanıma almadan önce bkz. [README.md](README.md).
> Köşeli parantez `[...]` içindeki alanlar işletmeye özgü bilgiyle doldurulmalı ve bir
> avukat/KVKK danışmanı tarafından onaylanmalıdır.

## 1. Veri Sorumlusu

**[İşletme unvanı]**, [adres], ("**İşletme**") 6698 sayılı Kişisel Verilerin
Korunması Kanunu ("**KVKK**") uyarınca veri sorumlusudur. Bu metin KVKK m.10
kapsamında aydınlatma yükümlülüğünün yerine getirilmesi amacıyla hazırlanmıştır.

## 2. İşlenen Kişisel Veri Kategorileri

BeautySalonApp yazılımı üzerinden aşağıdaki veri kategorileri işlenir
(bkz. `docs/04-guvenlik-kvkk.md` §2):

| Kategori | Örnek alan | Not |
|---|---|---|
| Kimlik | Ad soyad, TC kimlik no | TC kimlik no şifrelenir |
| İletişim | Telefon, e-posta, adres | Şifrelenir; arama için tek yönlü (blind index) yöntem kullanılır |
| Müşteri işlem | Randevu geçmişi, satın alınan hizmet/ürün, sözleşme, fatura | |
| Finansal | Tahsilat/ödeme, taksit, çek/POS bilgisi | Kart numarası **saklanmaz** |
| Özel nitelikli | Cilt tipi, alerji, sağlık notu (varsa) | Yalnızca **açık rıza** ile ve şifreli saklanır (KVKK m.6) |
| Pazarlama izni | SMS/e-posta ticari ileti onayı, İYS durumu | Ayrı açık rıza metni ile alınır |

## 3. İşleme Amaçları

- Randevu planlama ve hizmet sunumu
- Satış, tahsilat, fatura ve cari hesap takibi (mevzuat gereği saklama)
- Sadakat/puan programı yürütülmesi
- Müşteriyle randevu hatırlatma gibi hizmete ilişkin bilgilendirme
- Açık rıza verilmişse ticari ileti (kampanya SMS/e-posta) gönderimi
- Yasal yükümlülüklerin yerine getirilmesi (vergi, muhasebe mevzuatı)

## 4. Hukuki Sebep (KVKK m.5)

- Bir sözleşmenin kurulması/ifası (hizmet alımı, satış sözleşmesi)
- Hukuki yükümlülüğün yerine getirilmesi (fatura, muhasebe kayıtları)
- Meşru menfaat (randevu hatırlatma, dolandırıcılık/kötüye kullanım önleme)
- Açık rıza (pazarlama iletişimi, özel nitelikli sağlık/cilt notu — ayrı rıza metni)

## 5. Aktarım

- **Yurt içi:** [SMS sağlayıcısı adı], [e-posta/SMTP sağlayıcısı — varsa], mali müşavir/muhasebe
  bürosu (mevzuat gereği), e-Fatura entegratörü (kullanılıyorsa).
- **Yurt dışı aktarım:** Yazılım tasarımı gereği iş verisi internete gönderilmez
  (bkz. `docs/04-guvenlik-kvkk.md` §9); veri yalnızca işletmenin kendi bilgisayarında
  yerel olarak saklanır. [Eğer bir bulut/yedek hizmeti kullanılıyorsa burada belirtilmelidir.]

## 6. Saklama Süresi

Mali kayıtlar Vergi Usul Kanunu ve Türk Ticaret Kanunu uyarınca [10 yıl] saklanır.
Diğer kategoriler için nihai süreler avukat/KVKK danışmanıyla belirlenmelidir
(bkz. `docs/04-guvenlik-kvkk.md` §6 — bildirim içerik logu örneği).

## 7. İlgili Kişinin Hakları (KVKK m.11)

Kişisel verisi işlenen gerçek kişi, İşletmeye başvurarak:

- Kişisel veri işlenip işlenmediğini öğrenme,
- İşlenmişse buna ilişkin bilgi talep etme,
- İşlenme amacını ve amacına uygun kullanılıp kullanılmadığını öğrenme,
- Yurt içinde/dışında aktarıldığı üçüncü kişileri bilme,
- Eksik/yanlış işlenmişse düzeltilmesini isteme,
- KVKK m.7 şartları oluştuğunda silinmesini/yok edilmesini isteme
  (bu uygulamada **anonimleştirme** olarak uygulanır — mali kayıtlar hard-delete
  yapılamaz, bkz. CLAUDE.md #3),
- Yapılan işlemlerin, verilerin aktarıldığı üçüncü kişilere bildirilmesini isteme,
- Otomatik sistemlerle analiz sonucu aleyhine bir sonuç çıkmasına itiraz etme,
- Zarara uğraması hâlinde zararın giderilmesini talep etme

haklarına sahiptir. Başvurular [başvuru kanalı — e-posta/adres] üzerinden yapılabilir.

## 8. Veri Sorumlusuna Başvuru

[İletişim bilgileri: adres, e-posta, telefon]

---
*Bu metin [tarih] itibarıyla taslak sürüm 0.1'dir. Avukat/KVKK danışmanı onayından
sonra sürüm numarası ve onay tarihi buraya eklenmelidir (bkz. güvenlik gözden geçirme §10).*
