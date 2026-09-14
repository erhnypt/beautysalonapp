#!/usr/bin/env bash
# launchd daemon bu betiği çalıştırır (doğrudan jpackage ikilisini değil) —
# System Keychain'deki şifreleme anahtarını okuyup uygulamaya
# -Dbeautysalonapp.crypto.key-base64 olarak geçirir (güvenlik gözden geçirme F3,
# bkz. packaging/scripts/wrap-crypto-key-macos.sh).
#
# Anahtar henüz keychain'e taşınmamışsa (wrap betiği hiç çalıştırılmamışsa)
# sessizce atlanır — CryptoKeyProvider kendi dosya/türetim sırasına düşer,
# davranış bozulmaz; yalnızca anahtar korumasının bir kademe daha zayıf kalır.
set -euo pipefail

SERVICE="beautysalonapp-field-crypto"
ACCOUNT="beautysalonapp"
SYSTEM_KEYCHAIN="/Library/Keychains/System.keychain"
APP_BIN="/Applications/BeautySalonApp.app/Contents/MacOS/BeautySalonApp"

KEY_B64=""
if command -v security >/dev/null 2>&1; then
  KEY_B64="$(security find-generic-password -a "$ACCOUNT" -s "$SERVICE" -w "$SYSTEM_KEYCHAIN" 2>/dev/null || true)"
fi

if [ -n "$KEY_B64" ]; then
  export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dbeautysalonapp.crypto.key-base64=$KEY_B64"
fi

exec "$APP_BIN" "$@"
