#!/usr/bin/env bash
# Kod imzalama (§7.2). GERÇEK SERTİFİKA GEREKİR — bu ortamda üretilemez.
#
#   macOS:   "Developer ID Application" + "Developer ID Installer" sertifikaları
#            (Apple Developer Program — $99/yıl). Keychain'de yüklü olmalı.
#   Windows: EV Code Signing sertifikası (~$300/yıl), donanım token'ı veya
#            Azure Trusted Signing. signtool.exe PATH'te olmalı.
#
# Kullanım:  bash sign.sh <artefakt-dizini>
set -euo pipefail

OUT="${1:?artefakt dizini}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OS="$(uname -s)"

if [ "$OS" = "Darwin" ]; then
  : "${MAC_APP_IDENTITY:?örn: 'Developer ID Application: Şirket Adı (TEAMID)'}"
  : "${MAC_INSTALLER_IDENTITY:=}"
  ENTITLEMENTS="$ROOT/packaging/macos/entitlements.plist"

  APP="/Applications/BeautySalonApp.app"
  if [ -d "$APP" ]; then
    echo "==> .app derin imzalama"
    # Gömülü tüm dylib/jli/jspawnhelper dahil derin imzalama, hardened runtime:
    find "$APP/Contents" -type f \( -name "*.dylib" -o -name "*.jnilib" -o -perm -111 \) -print0 \
      | xargs -0 -I{} codesign --force --timestamp --options runtime \
          --entitlements "$ENTITLEMENTS" --sign "$MAC_APP_IDENTITY" "{}" || true
    codesign --force --timestamp --options runtime \
      --entitlements "$ENTITLEMENTS" --sign "$MAC_APP_IDENTITY" "$APP"
    codesign --verify --deep --strict --verbose=2 "$APP"
  fi

  for dmg in "$OUT"/*.dmg; do
    [ -f "$dmg" ] || continue
    echo "==> DMG imzalama: $(basename "$dmg")"
    codesign --force --timestamp --sign "$MAC_APP_IDENTITY" "$dmg"
  done

elif [[ "$OS" == MINGW* || "$OS" == MSYS* || "$OS" == CYGWIN* ]]; then
  : "${WIN_SIGN_SHA1:?imzalama sertifikası SHA1 parmak izi (veya /f pfx + /p şifre kullanın)}"
  TS_URL="${WIN_TIMESTAMP_URL:-http://timestamp.digicert.com}"
  for msi in "$OUT"/*.msi "$OUT"/*.exe; do
    [ -f "$msi" ] || continue
    echo "==> Authenticode imzalama: $(basename "$msi")"
    signtool sign //sha1 "$WIN_SIGN_SHA1" //fd sha256 //tr "$TS_URL" //td sha256 //v "$msi"
    signtool verify //pa //v "$msi"
  done
else
  echo "İmzalama bu OS için yapılandırılmadı: $OS"
fi
echo "==> İmzalama tamamlandı."
