#!/usr/bin/env bash
# macOS notarization + stapling (§7.2). Apple Developer hesabı gerekir.
#
# Kimlik (aşağıdakilerden biri):
#   A) notarytool keychain profili:  xcrun notarytool store-credentials "bsa-notary" \
#        --apple-id "you@example.com" --team-id "TEAMID" --password "app-specific-pw"
#      → NOTARY_PROFILE=bsa-notary
#   B) Doğrudan:  NOTARY_APPLE_ID, NOTARY_TEAM_ID, NOTARY_PASSWORD (app-specific)
#
# Kullanım:  bash notarize.sh <artefakt-dizini>
set -euo pipefail

OUT="${1:?artefakt dizini}"
[ "$(uname -s)" = "Darwin" ] || { echo "Yalnızca macOS"; exit 1; }

submit() {
  local file="$1"
  echo "==> Notarization gönderiliyor: $(basename "$file")"
  if [ -n "${NOTARY_PROFILE:-}" ]; then
    xcrun notarytool submit "$file" --keychain-profile "$NOTARY_PROFILE" --wait
  else
    : "${NOTARY_APPLE_ID:?}" "${NOTARY_TEAM_ID:?}" "${NOTARY_PASSWORD:?}"
    xcrun notarytool submit "$file" --apple-id "$NOTARY_APPLE_ID" \
      --team-id "$NOTARY_TEAM_ID" --password "$NOTARY_PASSWORD" --wait
  fi
  echo "==> Stapling"
  xcrun stapler staple "$file"
  xcrun stapler validate "$file"
}

for dmg in "$OUT"/*.dmg; do
  [ -f "$dmg" ] && submit "$dmg"
done

# .app da doğrulansın (staple .app'e de yapılabilir)
[ -d "/Applications/BeautySalonApp.app" ] && xcrun stapler staple "/Applications/BeautySalonApp.app" || true
echo "==> Notarization tamam. 'spctl -a -vv <dmg>' ile kontrol edin."
