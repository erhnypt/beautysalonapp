#!/usr/bin/env bash
# Alan şifreleme anahtarını (CryptoKeyProvider, güvenlik gözden geçirme F3) macOS
# System Keychain'e taşır ve düz metin dosyasını siler — "çalınan veri dizini"
# tehdidine karşı asıl kalan iş. KURULUMDAN SONRA, uygulama en az bir kez çalışıp
# `<dataRoot>/config/field-crypto.key` üretildikten sonra, BİR KEZ sudo ile
# çalıştırılır.
#
# Kullanım:  sudo bash wrap-crypto-key-macos.sh
#
# Bu betik `security` komutunun var olduğu ve System keychain'in kilitli olmadığı
# varsayımıyla sözdizimi kontrolünden geçirildi (bash -n); gerçek bir System
# keychain'e YAZILMADI. Kurulum sonrası bir test makinesinde doğrulayın.
set -euo pipefail

DATA_ROOT="/Library/Application Support/BeautySalonApp"
KEY_FILE="$DATA_ROOT/config/field-crypto.key"
SERVICE="beautysalonapp-field-crypto"
ACCOUNT="beautysalonapp"
SYSTEM_KEYCHAIN="/Library/Keychains/System.keychain"

if [ "$(id -u)" != "0" ]; then
  echo "HATA: root olarak çalıştırın (sudo bash wrap-crypto-key-macos.sh)" >&2
  exit 1
fi

if [ ! -f "$KEY_FILE" ]; then
  echo "HATA: $KEY_FILE yok. Uygulamayı en az bir kez çalıştırıp anahtarın" >&2
  echo "      üretilmesini bekleyin (CryptoKeyProvider ilk açılışta üretir)." >&2
  exit 1
fi

if security find-generic-password -a "$ACCOUNT" -s "$SERVICE" "$SYSTEM_KEYCHAIN" >/dev/null 2>&1; then
  echo "HATA: Keychain'de zaten bir kayıt var — önce elle kaldırın:" >&2
  echo "      sudo security delete-generic-password -a $ACCOUNT -s $SERVICE $SYSTEM_KEYCHAIN" >&2
  exit 1
fi

KEY_B64="$(cat "$KEY_FILE")"

echo "==> [1/3] Anahtar System Keychain'e yazılıyor"
security add-generic-password -a "$ACCOUNT" -s "$SERVICE" -w "$KEY_B64" \
  -T /usr/bin/security -T "/Applications/BeautySalonApp.app" "$SYSTEM_KEYCHAIN"

echo "==> [2/3] Round-trip doğrulaması"
READBACK="$(security find-generic-password -a "$ACCOUNT" -s "$SERVICE" -w "$SYSTEM_KEYCHAIN")"
if [ "$READBACK" != "$KEY_B64" ]; then
  echo "HATA: Keychain'den okunan değer yazılanla eşleşmiyor — düz metin dosya SİLİNMEDİ." >&2
  exit 1
fi

echo "==> [3/3] Düz metin anahtar dosyası güvenli şekilde siliniyor"
# Aynı disk üzerinde üzerine yazıp sil (SSD'de tam güvence vermez ama en iyi çaba).
dd if=/dev/urandom of="$KEY_FILE" bs=1 count="$(wc -c < "$KEY_FILE")" conv=notrunc 2>/dev/null || true
rm -f "$KEY_FILE"

cat <<EOF
==> Tamam. Anahtar artık System Keychain'de ($SERVICE / $ACCOUNT).
    launchd daemon'un anahtarı okuyup uygulamaya geçirmesi için ProgramArguments'ı
    doğrudan ikili yerine packaging/macos/launch-wrapper-macos.sh'a yönlendirin
    (bkz. packaging/macos/launchd.plist yorumu).
EOF
