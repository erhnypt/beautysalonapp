#!/usr/bin/env bash
# Spring Boot fat-jar icin minimal, tasinabilir JRE uretir (SS4.1 jlink).
# Cikti: $OUT_DIR (varsayilan packaging/dist/runtime)
#
# JAVA_HOME Java 21 (LTS) JDK dizinine isaret etmelidir - jpackage ve virtual threads icin (ADR 0002).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT_DIR="${OUT_DIR:-$ROOT/packaging/dist/runtime}"

: "${JAVA_HOME:?JAVA_HOME Java 21 JDK dizinine ayarlanmali (jlink/jpackage buradan gelir)}"
JLINK="$JAVA_HOME/bin/jlink"
JMODS="$JAVA_HOME/jmods"

JV="$("$JAVA_HOME/bin/java" -version 2>&1 | head -1)"
case "$JV" in
  *2[1-9].*|*3[0-9].*) : ;;
  *) echo "UYARI: JAVA_HOME surumu 21+ gorunmuyor -> $JV" >&2 ;;
esac

rm -rf "$OUT_DIR"
mkdir -p "$(dirname "$OUT_DIR")"

# Spring Boot genis yansima (reflection) kullanir; modul tespiti kirilgandir.
# java.se tum java.* modullerini toplar; jdk.* modulleri kripto/charset/zip/locale icindir.
MODULES="java.se,jdk.crypto.ec,jdk.crypto.cryptoki,jdk.unsupported,jdk.unsupported.desktop,jdk.management,jdk.management.agent,jdk.naming.dns,jdk.naming.rmi,jdk.zipfs,jdk.charsets,jdk.localedata,jdk.jdwp.agent,jdk.httpserver,jdk.security.auth,jdk.security.jgss"

"$JLINK" \
  --module-path "$JMODS" \
  --add-modules "$MODULES" \
  --output "$OUT_DIR" \
  --strip-debug \
  --no-header-files \
  --no-man-pages \
  --compress zip-6 \
  --include-locales=tr,en

echo "==> Runtime hazir: $OUT_DIR"
du -sh "$OUT_DIR" 2>/dev/null || true
