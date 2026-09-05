#!/usr/bin/env bash
# Plan §7.3: yasaklı lisans (AGPL/GPL) taraması. license-maven-plugin'in ürettiği
# THIRD-PARTY.txt dosyasını tarar; LGPL ve "GPL2 w/ CPE" (Classpath Exception) gibi
# güvenli varyantları hariç tutar (bkz. plan §7.1 — Eclipse Temurin JDK örneği).
#
# Kullanım: check-licenses.sh <THIRD-PARTY.txt> [<THIRD-PARTY.txt> ...]
set -euo pipefail

if [ "$#" -eq 0 ]; then
  echo "Kullanım: $0 <THIRD-PARTY.txt> [...]" >&2
  exit 2
fi

FAIL=0
for FILE in "$@"; do
  if [ ! -f "$FILE" ]; then
    echo "UYARI: dosya bulunamadı, atlanıyor: $FILE" >&2
    continue
  fi
  HITS="$(grep -iE "GPL|Affero" "$FILE" | grep -viE "LGPL|Lesser|CPE|Classpath" || true)"
  if [ -n "$HITS" ]; then
    echo "HATA: $FILE içinde yasaklı lisans (AGPL/GPL) tespit edildi:" >&2
    echo "$HITS" >&2
    FAIL=1
  else
    echo "OK: $FILE — yasaklı lisans yok ($(grep -c '^     (' "$FILE" || echo 0) bağımlılık tarandı)"
  fi
done

exit "$FAIL"
