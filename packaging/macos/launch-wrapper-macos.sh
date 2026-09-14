#!/usr/bin/env bash
# launchd daemon bu betiği çalıştırır (doğrudan jpackage ikilisini değil) —
# System Keychain'deki şifreleme anahtarını okuyup uygulamaya BEAUTYSALONAPP_CRYPTO_KEY_BASE64
# ortam değişkeni olarak geçirir; Spring Boot'un gevşek bağlaması bunu
# beautysalonapp.crypto.key-base64 property'sine eşler (güvenlik gözden geçirme F3,
# bkz. packaging/scripts/wrap-crypto-key-macos.sh).
#
# BİLEREK JAVA_TOOL_OPTIONS/-D KULLANILMIYOR: her JVM başlangıçta
# "Picked up JAVA_TOOL_OPTIONS: ..." satırını stderr'e (→ launchd log dosyasına) basar —
# anahtarı Keychain'e taşımanın tüm amacını, düz metni bir log dosyasına kopyalayarak
# boşa çıkarır. Ortam değişkeni JVM tarafından sessizce okunur, hiçbir yere loglanmaz.
#
# Anahtar henüz keychain'e taşınmamışsa (wrap betiği hiç çalıştırılmamışsa)
# sessizce atlanır — CryptoKeyProvider kendi dosya/türetim sırasına düşer,
# davranış bozulmaz; yalnızca anahtar korumasının bir kademe daha zayıf kalır.
set -euo pipefail

SERVICE="beautysalonapp-field-crypto"
ACCOUNT="beautysalonapp"
SYSTEM_KEYCHAIN="/Library/Keychains/System.keychain"
APP_BIN="/Applications/BeautySalonApp.app/Contents/MacOS/BeautySalonApp"

if command -v security >/dev/null 2>&1; then
  KEY_B64="$(security find-generic-password -a "$ACCOUNT" -s "$SERVICE" -w "$SYSTEM_KEYCHAIN" 2>/dev/null || true)"
  if [ -n "$KEY_B64" ]; then
    export BEAUTYSALONAPP_CRYPTO_KEY_BASE64="$KEY_B64"
  fi
  unset KEY_B64
fi

exec "$APP_BIN" "$@"
