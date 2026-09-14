# Ticari Elektronik İleti İçin Açık Rıza Metni (TASLAK)

> ⚠️ **TASLAK — hukuki tavsiye değildir.** Kullanıma almadan önce bkz. [README.md](README.md).
> Bu metin KVKK m.5 "açık rıza" ve 6563 sayılı Elektronik Ticaretin Düzenlenmesi
> Kanunu ("**ETK**") + İYS (İleti Yönetim Sistemi) mevzuatına göre taslak olarak
> hazırlanmıştır.

## Metin

> **[İşletme unvanı]** olarak; randevu hatırlatmaları **dışında**, kampanya,
> indirim ve promosyon içerikli SMS ve/veya e-posta göndermemiz için açık rızanızı
> talep ediyoruz. Bu rıza istediğiniz zaman, ücretsiz ve gerekçe göstermeksizin
> geri alınabilir (aşağıdaki kanallardan veya İYS üzerinden "RET" bildirimiyle).
>
> ☐ SMS ile ticari ileti almak istiyorum.
> ☐ E-posta ile ticari ileti almak istiyorum.
>
> Bu onayı vermemeniz veya geri almanız hizmet almanızı **etkilemez**; randevu
> hatırlatma gibi hizmete ilişkin bilgilendirmeler (ticari ileti sayılmaz) bu
> onaydan bağımsız, ilgili iletişim kanalı onayınız dahilinde gönderilmeye devam eder.

## Uygulamadaki karşılığı

| Alan | Anlamı |
|---|---|
| `party.sms_consent` / `party.email_consent` | Yukarıdaki onay kutularının karşılığı, kanal bazlı |
| `party.consent_date` | Onayın alındığı tarih |
| `party.iys_status` (`IZINLI`/`IZINSIZ`/`BILINMIYOR`) | İYS'den sorgulanan/bildirilen resmi izin durumu |
| `ConsentPolicy` (kod) | `iys_status != IZINLI` olan tarafa kampanya SMS/e-postası **kod düzeyinde** engellenir |

## Netleştirilmesi gerekenler (avukat/KVKK danışmanı ile)

1. Onayın **hangi kanaldan** alınacağı — kağıt form mu, uygulama ekranında dijital
   onay kutusu mu, her ikisi mi. (Uygulama şu an yalnızca onay **durumunu** saklar;
   onayın alınma anındaki ispat/kayıt mekanizması — imza, IP, zaman damgası —
   işletme sürecine göre ayrıca kurulmalıdır.)
2. **İYS'ye bildirim yükümlülüğü** — İşletme kendi IYS hesabından mı bildirecek,
   yoksa toplu SMS/e-posta sağlayıcısı mı üstlenecek (bkz. teknik plan §20 madde 9).
3. Geri alma (ret) talebinin işlenme süresi ve kanalı.
4. Randevu hatırlatmasının "ticari ileti" sayılmadığına dair gerekçenin (bilgilendirme
   amaçlı, satış/pazarlama içermiyor) somut örneklerle desteklenmesi — ETK m.6
   istisnası kapsamına net biçimde girdiğinin teyidi.

---
*Bu metin [tarih] itibarıyla taslak sürüm 0.1'dir.*
