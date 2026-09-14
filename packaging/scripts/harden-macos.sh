#!/usr/bin/env bash
# Servis en az yetkiyle çalışsın + veri dizini yalnızca bu hesap/Administrators
# yazabilsin (güvenlik gözden geçirme I1/I2). `jpackage --launcher-as-service`
# launchd daemon'u varsayılan olarak root çalıştırır; bu betik KURULUMDAN SONRA,
# paketleme/kurulum mühendisi tarafından BİR KEZ, sudo ile çalıştırılır.
#
# Kullanım:  sudo bash harden-macos.sh
#
# Bu betik bu oturumda syntax kontrolünden (bash -n) geçirildi ama gerçek bir
# kurulum üzerinde ÇALIŞTIRILMADI — kullanıcının makinesinde sistem kullanıcısı
# oluşturmak ve dosya sahipliğini değiştirmek geri alınması güç bir işlemdir.
# Üretime almadan önce bir test/VM kurulumunda doğrulayın.
set -euo pipefail

SERVICE_USER="_beautysalonapp"
DATA_ROOT="/Library/Application Support/BeautySalonApp"
PLIST="/Library/LaunchDaemons/com.beautysalonapp.app.plist"

if [ "$(id -u)" != "0" ]; then
  echo "HATA: root olarak çalıştırın (sudo bash harden-macos.sh)" >&2
  exit 1
fi

echo "==> [1/3] Servis kullanıcısı: $SERVICE_USER"
if dscl . -read "/Users/$SERVICE_USER" >/dev/null 2>&1; then
  echo "    zaten var, atlanıyor"
else
  # macOS sistem hesapları için ayrılmış UID aralığı (200-400); ilk boş id'yi bul.
  LAST_ID=$(dscl . -list /Users UniqueID 2>/dev/null | awk '{print $2}' | sort -n \
      | awk '$1>=200 && $1<400' | tail -1)
  NEW_ID=$(( ${LAST_ID:-200} + 1 ))
  dscl . -create "/Users/$SERVICE_USER"
  dscl . -create "/Users/$SERVICE_USER" UserShell /usr/bin/false
  dscl . -create "/Users/$SERVICE_USER" UniqueID "$NEW_ID"
  dscl . -create "/Users/$SERVICE_USER" PrimaryGroupID 1
  dscl . -create "/Users/$SERVICE_USER" IsHidden 1
  dscl . -create "/Users/$SERVICE_USER" NFSHomeDirectory /var/empty
  echo "    oluşturuldu (uid=$NEW_ID, giriş yapılamaz — /usr/bin/false)"
fi

echo "==> [2/3] Veri dizini izinleri: $DATA_ROOT"
if [ -d "$DATA_ROOT" ]; then
  chown -R "$SERVICE_USER":admin "$DATA_ROOT"
  chmod -R u=rwX,g=rX,o= "$DATA_ROOT"
  echo "    $SERVICE_USER:admin, o= (başkaları erişemez)"
else
  echo "    UYARI: $DATA_ROOT yok — önce jpackage kurulumunu (.dmg) tamamlayın." >&2
fi

echo "==> [3/3] launchd daemon: $SERVICE_USER olarak çalışacak şekilde ayarlanıyor"
if [ -f "$PLIST" ]; then
  /usr/libexec/PlistBuddy -c "Add :UserName string $SERVICE_USER" "$PLIST" 2>/dev/null \
    || /usr/libexec/PlistBuddy -c "Set :UserName $SERVICE_USER" "$PLIST"
  launchctl unload "$PLIST" 2>/dev/null || true
  launchctl load -w "$PLIST"
  echo "    servis yeniden yüklendi"
else
  echo "    UYARI: $PLIST yok — servis henüz kurulmamış olabilir." >&2
fi

echo "==> Tamam. Servis artık root değil, '$SERVICE_USER' olarak çalışıyor."
