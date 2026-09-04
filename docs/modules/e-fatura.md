# Modül: e-Fatura Hazırlığı (`modules.invoice` içinde)

Öncelik **P2 (Faz 8)** · Plan §Faz 8+ ("e-Fatura entegrasyonu")

> **Bu bir gerçek GİB/özel entegratör entegrasyonu DEĞİLDİR.** CLAUDE.md #1 gereği hiçbir
> iş verisi internete gönderilmez. Bu modül yalnızca **standarda uygun UBL-TR 1.2 XML üretir**;
> işletme bu dosyayı indirip kendi seçtiği özel entegratörün portalına/API'sine **elle veya
> kendi entegrasyonuyla** yükler. Gerçek gönderim, mali mühür (dijital imza) ve durum takibi
> (GİB/PORTAL) bu ürünün kapsamı dışındadır ve gerçek sertifika + entegratör sözleşmesi
> gerektirir.

## Neden bu kapsam?

Kullanıcıya sorulup onaylandı: gerçek GİB gönderimi (a) işletmenin kendi seçtiği bir özel
entegratörle sözleşme + mali mühür sertifikası gerektirir — bunlar bu ortamda sağlanamaz;
(b) otomatik gönderim CLAUDE.md #1'in "hiçbir iş verisi dışarı gitmez" ilkesiyle doğrudan
çelişir ve `OutboundHttpGuard` allowlist'ine yeni, hassas bir hedef eklemeyi gerektirirdi.
XML üretimi ise değer katar (işletme elle yükleme sürecini hızlandırır) ve tamamen yerel kalır.

## Ne yapıldı

- **`UblTrInvoiceData`** (domain) — girdi modeli: satıcı/alıcı taraf bilgisi, satırlar, toplamlar.
- **`UblTrInvoiceBuilder`** (domain, framework'süz) — JDK'nın DOM API'siyle (yeni bağımlılık
  yok) UBL-TR 1.2 uyumlu `Invoice` XML'i üretir: `cbc:UUID/ID/IssueDate/InvoiceTypeCode`,
  `cac:AccountingSupplierParty` / `AccountingCustomerParty` (VKN veya TCKN ile), `cac:TaxTotal`,
  `cac:LegalMonetaryTotal`, satır başına `cac:InvoiceLine`. Tüm metin alanları DOM üzerinden
  yazıldığı için XML özel karakterleri (`&`, `<`, `"`) otomatik kaçışlanır.
- **`EInvoiceService`** (application, `modules.invoice` içinde) —
  - Yalnızca **satış yönlü, iptal edilmemiş** faturalar için üretim yapar (`SATIS`,
    `PERAKENDE`, `IADE_SATIS`); `ALIS`/`IADE_ALIS` reddedilir (o belge karşı tarafa aittir).
  - Müşteride vergi no **veya** TC kimlik no yoksa reddeder (UBL-TR zorunlu alan).
  - İlk üretimde faturaya **kalıcı bir UUID** atar (`invoice.einvoice_uuid`, V7'den beri
    rezerve edilmiş sütun — yeni migration gerekmedi) ve durumunu `HAZIR` yapar
    (`invoice.einvoice_status`). Sonraki çağrılar aynı UUID ile **idempotent** yeniden üretir.
  - Satıcı bilgisi: faturanın şubesine ait `Branch` kaydından (Faz 8 şube modülü; bulunamazsa
    merkez şubeye düşer) — vergi no, ünvan, adres.
  - Alıcı bilgisi: `PartyDirectory.eInvoiceInfo(partyId)` (CLAUDE.md #5 — cross-module erişim
    yalnızca port üzerinden) — vergi no/TC no + varsayılan adres.
- **`GET /api/v1/invoices/{id}/e-fatura`** — XML'i `Content-Disposition: attachment` ile
  indirir. `INVOICE_EDIT` gerektirir (UUID ataması bir mutasyon içerir).
- **Frontend:** Faturalar listesinde uygun faturalar için "XML" indirme bağlantısı.

## Kapsam dışı (bilinçli, gelecekte ayrı bir karar)

- Gerçek gönderim (SOAP/REST) — bir özel entegratör API'si + kimlik bilgisi gerektirir;
  eklenirse `OutboundHttpGuard` allowlist'ine girmeli ve PR'da gerekçelendirilmeli.
- Dijital imza (mali mühür) — gerçek nitelikli sertifika (e-imza/mali mühür) gerektirir.
- e-Arşiv raporlama, durum sorgulama (kabul/red), iptal/itiraz süreçleri.
- Çoklu döviz e-Fatura (yalnızca `invoice.currency` neyse o yazılır; GİB kuru dönüşüm
  kurallarını uygulamaz).
- KDV muafiyet/istisna kodları (`ISTISNA`, `TEVKIFAT` vb.) — yalnızca standart `SATIS`/`IADE`
  ve `KDV`/`0015` vergi tipiyle üretilir.

## KVKK değerlendirmesi (CLAUDE.md #10)

Bu modül **yeni bir kalıcı depolama alanı eklemez** — `Party.taxId`/`tcNo` zaten
`EncryptedStringConverter` ile şifreli (mevcut alan, `EInvoiceService` yalnızca
`PartyDirectory` portu üzerinden çözülmüş halini okur). Üretilen XML diske yazılmaz;
HTTP yanıtı olarak akışa yazılır ve tarayıcıda indirilir — sunucu tarafında saklanmaz.
`invoice.einvoice_uuid/einvoice_status` kimlik bilgisi değildir (rastgele UUID + durum
metni). `audit_log`'a yazılan özet yalnızca fatura no'sunu içerir, vergi no/ad geçmez.
`docs/04-guvenlik-kvkk.md` bu depoda yok (önceden var olan boşluk, bkz. banka-mutabakat.md).

## Test

- `UblTrInvoiceBuilderTest` (8) — iyi biçimlilik, başlık/taraf/tutar/satır alanları, TCKN/VKN
  ayrımı, özel karakter kaçışlama, satırsız fatura reddi.
- `EInvoiceServiceTest` (5) — üretim + idempotent UUID, vergi no eksikliği reddi, alış
  faturası reddi, bulunamayan fatura.
