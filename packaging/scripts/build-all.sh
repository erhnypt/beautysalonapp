#!/usr/bin/env bash
# BeautySalonApp — tam derleme: frontend build → server static → çalıştırılabilir JAR.
# jpackage ile .msi/.dmg üretimi Faz 1'de eklenecek (packaging/windows, packaging/macos).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
: "${JAVA_HOME:=/opt/homebrew/opt/openjdk@17}"
export JAVA_HOME

echo "==> Frontend build"
cd "$ROOT/web"
npm ci --no-audit --no-fund
npm run build

echo "==> Statik dosyaları server'a kopyala"
STATIC="$ROOT/server/src/main/resources/static"
rm -rf "${STATIC:?}/"*
mkdir -p "$STATIC"
cp -R "$ROOT/web/dist/." "$STATIC/"
touch "$STATIC/.gitkeep"

echo "==> Server build (verify + package)"
cd "$ROOT/server"
./mvnw -q clean verify

echo "==> Bitti: $ROOT/server/target/beautysalonapp.jar"
