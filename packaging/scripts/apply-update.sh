#!/usr/bin/env bash
# Guncelleme uygulama — macOS/Linux referansi (SS5.5).
# Uygulama ici "Yeni surum var" akisi bunu (veya .ps1 karsiligini) cagirir.
#
#   1. GUNCELLEME ONCESI ZORUNLU YEDEK  (uygulamanin /api/v1/backup/run ucu)
#   2. Paketi + imzasini indir  (heartbeat cevabindaki updateUrl)
#   3. SHA-256 (zorunlu) + Ed25519 imza (public key verildiyse) dogrula
#   4. Servisi durdur -> yeni .dmg/.msi'yi sessiz kur -> servis yeniden baslar
#   5. Uygulama acilista Flyway migration calistirir; basarisizsa yedekten doner
set -euo pipefail

URL="${1:?guncelleme paketi URL i}"
EXPECTED_SHA="${2:?beklenen sha256}"
# Ed25519 dogrulama icin (opsiyonel ama uretimde onerilir):
#   BSA_UPDATE_PUBKEY_PEM    -> hazir PEM public key dosyasi, VEYA
#   BSA_UPDATE_PUBKEY_B64URL -> uygulamaya gomulu ham 32 bayt anahtarin base64url u
PUBKEY_PEM="${BSA_UPDATE_PUBKEY_PEM:-}"
PUBKEY_B64URL="${BSA_UPDATE_PUBKEY_B64URL:-}"

WORK="$(mktemp -d)"
PKG="$WORK/update.dmg"
SIG="$WORK/update.sig"
trap 'rm -rf "$WORK"' EXIT

b64_decode() {
  # base64url -> binary. GNU: base64 -d ; BSD/macOS: base64 -D
  local s
  s="$(printf '%s' "$1" | tr '_-' '/+')"
  case $(( ${#s} % 4 )) in
    2) s="${s}==" ;;
    3) s="${s}=" ;;
  esac
  printf '%s' "$s" | base64 -d 2>/dev/null || printf '%s' "$s" | base64 -D
}

b64_encode() {
  base64 2>/dev/null || base64 -b 0
}

echo "==> [1/4] Guncelleme oncesi yedek"
curl -fsS -X POST -u "$BSA_ADMIN" http://127.0.0.1:8734/api/v1/backup/run >/dev/null \
  || { echo "Yedek basarisiz — guncelleme iptal"; exit 1; }

echo "==> [2/4] Indiriliyor"
curl -fL --retry 3 -o "$PKG" "$URL"
curl -fL --retry 3 -o "$SIG" "${URL}.sig" 2>/dev/null || : > "$SIG"

echo "==> [3/4] Dogrulama"
ACTUAL_SHA="$(shasum -a 256 "$PKG" | awk '{print $1}')"
if [ "$ACTUAL_SHA" != "$EXPECTED_SHA" ]; then
  echo "SHA-256 uyusmuyor!"
  exit 1
fi
echo "    SHA-256 OK"

# Ham 32 baytlik anahtardan PEM uret (sabit 12 baytlik Ed25519 SPKI oneki)
if [ -z "$PUBKEY_PEM" ] && [ -n "$PUBKEY_B64URL" ]; then
  PUBKEY_PEM="$WORK/pubkey.pem"
  printf '302a300506032b6570032100' | xxd -r -p > "$WORK/pubkey.der"
  b64_decode "$PUBKEY_B64URL" >> "$WORK/pubkey.der"
  echo "-----BEGIN PUBLIC KEY-----" > "$PUBKEY_PEM"
  b64_encode < "$WORK/pubkey.der" >> "$PUBKEY_PEM"
  echo "-----END PUBLIC KEY-----" >> "$PUBKEY_PEM"
fi

if [ -n "$PUBKEY_PEM" ] && [ -s "$SIG" ]; then
  b64_decode "$(cat "$SIG")" > "$WORK/update.sigbin"
  if openssl pkeyutl -verify -pubin -inkey "$PUBKEY_PEM" -rawin \
        -in "$PKG" -sigfile "$WORK/update.sigbin" >/dev/null 2>&1; then
    echo "    Ed25519 imza OK"
  else
    echo "Ed25519 imza DOGRULANAMADI — guncelleme iptal"
    exit 1
  fi
else
  echo "    Ed25519 atlandi (public key veya .sig yok); SHA-256 ile devam"
fi

echo "==> [4/4] Kurulum (yonetici gerekebilir)"
case "$(uname -s)" in
  Darwin)
    MP="$(hdiutil attach -nobrowse -noverify "$PKG" | tail -1 | awk '{print $NF}')"
    sudo installer -pkg "$MP"/*.pkg -target / 2>/dev/null \
      || sudo cp -R "$MP"/BeautySalonApp.app /Applications/
    hdiutil detach "$MP" >/dev/null
    ;;
  Linux)
    sudo dpkg -i "$PKG"
    ;;
esac
echo "==> Guncelleme tamam. Servis yeniden basliyor..."
