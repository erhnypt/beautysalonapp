#!/usr/bin/env bash
# Güncelleme uygulama — macOS/Linux referansı (§5.5).
# Uygulama içi "Yeni sürüm var" akışı bunu (veya .ps1 karşılığını) çağırır.
#
#   1. GÜNCELLEME ÖNCESİ ZORUNLU YEDEK  (uygulamanın /api/v1/backup/run ucu)
#   2. Paketi indir  (heartbeat cevabındaki updateUrl)
#   3. SHA-256 + Ed25519 imza doğrula  (checksums.txt + .sig, gömülü public key ile)
#   4. Servisi durdur → yeni .dmg/.msi'yi sessiz kur → servis yeniden başlar
#   5. Uygulama açılışta Flyway migration çalıştırır; başarısızsa yedekten döner
set -euo pipefail

URL="${1:?güncelleme paketi URL'i}"
EXPECTED_SHA="${2:?beklenen sha256}"
WORK="$(mktemp -d)"
PKG="$WORK/update.dmg"

echo "==> [1/4] Güncelleme öncesi yedek"
curl -fsS -X POST -u "$BSA_ADMIN" http://127.0.0.1:8734/api/v1/backup/run >/dev/null \
  || { echo "Yedek başarısız — güncelleme iptal"; exit 1; }

echo "==> [2/4] İndiriliyor"
curl -fL --retry 3 -o "$PKG" "$URL"

echo "==> [3/4] Doğrulama"
ACTUAL_SHA="$(shasum -a 256 "$PKG" | awk '{print $1}')"
[ "$ACTUAL_SHA" = "$EXPECTED_SHA" ] || { echo "SHA-256 uyuşmuyor!"; exit 1; }
# TODO: .sig dosyasını Ed25519 ile doğrula (gömülü lisans public key'i).

echo "==> [4/4] Kurulum (yönetici gerekebilir)"
case "$(uname -s)" in
  Darwin)
    MP="$(hdiutil attach -nobrowse -noverify "$PKG" | tail -1 | awk '{print $NF}')"
    sudo installer -pkg "$MP"/*.pkg -target / 2>/dev/null \
      || sudo cp -R "$MP"/BeautySalonApp.app /Applications/
    hdiutil detach "$MP" >/dev/null
    ;;
  Linux)
    sudo dpkg -i "$PKG" ;;
esac
rm -rf "$WORK"
echo "==> Güncelleme tamam. Servis yeniden başlıyor..."
