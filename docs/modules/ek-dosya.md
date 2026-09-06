# Modül: Dosya Eki (`com.beautysalonapp.attachment`)

Öncelik **P2** · Plan §9.1, §10.12 (taranmış sözleşme), §8.3 (KVKK özel nitelikli veri)

## Amaç

Polimorfik dosya eki: müşteri fotoğrafı, taranmış imzalı sözleşme nüshası vb. Diğer tüm
modüllerin (`party`, `contract`, …) ortak kullandığı, kendi başına bağımsız bir modül —
hiçbir iş modülüne bağımlı değildir (CLAUDE.md #5), tersine bağımlılık da yok.

> Bu, Faz 0'dan beri `attachment` tablosu DB şemasında duran ama hiç servis/uç noktası
> olmayan bir özellikti (plan §9.1) — Faz 8'de tamamlandı.

## Veri modeli (V1'den beri var, yeni migration gerekmedi)
```
attachment   owner_type (PARTY|CONTRACT), owner_id,
             file_name (orijinal ad, yalnızca görüntüleme), content_type, size_bytes,
             storage_path (sunucu tarafında üretilen, DB dışı gerçek disk yolu), sha256
```

## İş kuralları

- **Dosyanın kendisi veritabanında değil** `<dataDir>/attachments/<ownerType>/<ownerId>/`
  altında durur; DB yalnızca referans (yol, boyut, sha256) tutar (plan §8.2).
- **Yol geçişi (path traversal) önlemi** — güvenlik gözden geçirme madde C5:
  - Diskteki gerçek dosya adı **her zaman** sunucu tarafında üretilen bir `UUID` +
    temizlenmiş orijinal ad birleşimidir; kullanıcının verdiği ad asla doğrudan dosya
    yoluna karışmaz.
  - `sanitizeFilename`: yol ayırıcıları (`/`, `\`) ve `..` dizilerini tamamen atar,
    yalnızca `[A-Za-z0-9._-]` bırakır.
  - Hem yüklemede hem indirmede, üretilen/okunacak yolun `attachments/` kök dizininin
    **gerçekten altında** kaldığı `normalize()` + `startsWith()` ile ikinci kez doğrulanır.
  - `ownerType` sabit bir enum'dur (`PARTY`, `CONTRACT`) — dizin yoluna asla serbest
    metin karışmaz.
- **Boyut sınırı:** 100 MB (`spring.servlet.multipart.max-file-size` ile aynı sınır,
  servis katmanında da ayrıca doğrulanır).
- **Silme:** mali kayıt değildir (CLAUDE.md #3 kapsamı dışı) — yumuşak silme (`deleted=true`)
  kullanılır, fiziksel dosya diskte kalır (disk temizliği ayrı, gelecekteki bir bakım işi).
- Yedekleme: `attachments/` dizini `BackupService` tarafından otomatik olarak yedeğe dahil
  edilir (zaten Faz 5'ten beri).

## Yetkilendirme notu

`ownerType` birden fazla varlık türünü kapsadığından ekleme/indirme/silme
`PARTY_EDIT`/`CONTRACT_EDIT` (veya `_VIEW`) yetkilerinden **en az birini** ister — tam
ayrıntılı yetki denetimi yapılmaz. Asıl sınır, geçerli bir `ownerId`'ye erişimin zaten
ilgili modülün kendi ekranından geçmesidir; bu, savunma derinliği katmanıdır.

## Endpoint taslağı
```
GET    /api/v1/attachments?ownerType=&ownerId=       (PARTY_VIEW veya CONTRACT_VIEW)
POST   /api/v1/attachments?ownerType=&ownerId=        (multipart, PARTY_EDIT veya CONTRACT_EDIT)
GET    /api/v1/attachments/{id}/download
DELETE /api/v1/attachments/{id}
```

## Test

- `AttachmentServiceTest` — yükleme/indirme/listeleme tam döngüsü, `../` ve `..\` yol
  geçişi denemelerinin temizlendiği, boş dosya reddi, silinen eke erişilememesi.
- `AttachmentApiTest` — kimlik doğrulama ve yetki sınırları (MockMvc).
