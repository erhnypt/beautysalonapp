#!/usr/bin/env bash
# BeautySalonApp — tam dağıtım derlemesi (§5.1).
#
#   1. Frontend build → server/src/main/resources/static
#   2. server: mvn clean verify → tek fat-jar
#   3. license public key'i application-packaged.yml'ye göm (varsa)
#   4. jlink ile minimal JRE
#   5. jpackage: bu makinenin OS'una göre .msi / .dmg / .deb
#
# Ön koşullar:
#   - JAVA_HOME = Java 21 JDK (jpackage + jlink)
#   - Node 20+ / npm
#   - Windows: WiX Toolset 3.x (jpackage MSI için); macOS: Xcode CLT
#
# Ortam değişkenleri (opsiyonel):
#   APP_VERSION            (varsayılan: server/pom.xml <version>, -SNAPSHOT kırpılır)
#   LICENSE_PUBLIC_KEY     lisans sunucusunun Ed25519 public key'i (Base64) — build'e gömülür
#   SIGN=1                 imzalama adımını çağır (packaging/scripts/sign.sh)
#   NOTARIZE=1             macOS notarization (packaging/scripts/notarize.sh)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DIST="$ROOT/packaging/dist"
STATIC="$ROOT/server/src/main/resources/static"
: "${JAVA_HOME:?JAVA_HOME Java 21 JDK dizinine ayarlanmalı}"
export JAVA_HOME

APP_VERSION="${APP_VERSION:-$(sed -n 's/.*<version>\(.*\)-SNAPSHOT<\/version>.*/\1/p;s/.*<version>\([0-9.]*\)<\/version>.*/\1/p' "$ROOT/server/pom.xml" | head -1)}"
APP_VERSION="${APP_VERSION:-1.0.0}"
OS="$(uname -s)"
echo "==> BeautySalonApp $APP_VERSION — $OS"

# --- 1. Frontend --------------------------------------------------------------
echo "==> [1/5] Frontend build"
cd "$ROOT/web"
npm ci --no-audit --no-fund
npm run build
rm -rf "${STATIC:?}"/*
mkdir -p "$STATIC"
cp -R "$ROOT/web/dist/." "$STATIC/"
touch "$STATIC/.gitkeep"

# --- 2. server --------------------------------------------------------------
echo "==> [2/5] server build (verify + package)"
cd "$ROOT/server"
./mvnw -q clean verify
JAR="$ROOT/server/target/beautysalonapp.jar"
[ -f "$JAR" ] || { echo "HATA: $JAR üretilmedi"; exit 1; }

# --- 3. Public key gömme -----------------------------------------------------
if [ -n "${LICENSE_PUBLIC_KEY:-}" ]; then
  echo "==> [3/5] Lisans public key gömülüyor"
  TMP="$(mktemp -d)"
  ( cd "$TMP" && "$JAVA_HOME/bin/jar" xf "$JAR" BOOT-INF/classes/application-packaged.yml )
  sed -i.bak "s|@LICENSE_PUBLIC_KEY@|$LICENSE_PUBLIC_KEY|" "$TMP/BOOT-INF/classes/application-packaged.yml"
  ( cd "$TMP" && "$JAVA_HOME/bin/jar" uf "$JAR" BOOT-INF/classes/application-packaged.yml )
  rm -rf "$TMP"
else
  echo "==> [3/5] LICENSE_PUBLIC_KEY verilmedi — GELİŞTİRME modu gömülü kalır (uyarı)"
fi

# --- 4. Runtime ------------------------------------------------------------
echo "==> [4/5] jlink runtime"
OUT_DIR="$DIST/runtime" bash "$ROOT/packaging/scripts/jlink-runtime.sh"

# --- 5. jpackage --------------------------------------------------------------
echo "==> [5/5] jpackage"
rm -rf "$DIST/input" "$DIST/out"
mkdir -p "$DIST/input" "$DIST/out"
cp "$JAR" "$DIST/input/beautysalonapp.jar"

COMMON_ARGS=(
  --name "BeautySalonApp"
  --app-version "$APP_VERSION"
  --vendor "BeautySalonApp"
  --description "Güzellik Merkezi Yönetim Yazılımı"
  --copyright "© BeautySalonApp"
  --input "$DIST/input"
  --main-jar "beautysalonapp.jar"
  --main-class "org.springframework.boot.loader.launch.JarLauncher"
  --runtime-image "$DIST/runtime"
  --dest "$DIST/out"
  --java-options "-Dspring.profiles.active=packaged"
  --java-options "-Dbeautysalonapp.packaged=true"
  --java-options "-Xss1m"
  --java-options "-XX:MaxRAMPercentage=50"
  --launcher-as-service
)

case "$OS" in
  Darwin)
    ICON="$ROOT/packaging/macos/BeautySalonApp.icns"
    [ -f "$ICON" ] && COMMON_ARGS+=(--icon "$ICON")
    "$JAVA_HOME/bin/jpackage" "${COMMON_ARGS[@]}" \
      --type dmg \
      "@$ROOT/packaging/macos/jpackage.args"
    ;;
  Linux)
    "$JAVA_HOME/bin/jpackage" "${COMMON_ARGS[@]}" --type deb \
      --linux-shortcut --linux-menu-group "Office"
    ;;
  MINGW*|MSYS*|CYGWIN*)
    ICON="$ROOT/packaging/windows/BeautySalonApp.ico"
    [ -f "$ICON" ] && COMMON_ARGS+=(--icon "$ICON")
    "$JAVA_HOME/bin/jpackage" "${COMMON_ARGS[@]}" \
      --type msi \
      --resource-dir "$ROOT/packaging/windows/wix" \
      "@$ROOT/packaging/windows/jpackage.args"
    ;;
  *)
    echo "Bilinmeyen OS: $OS"; exit 1 ;;
esac

echo "==> Artefaktlar: $DIST/out"
ls -la "$DIST/out"

# --- imzalama / notarization (opsiyonel) ---
if [ "${SIGN:-0}" = "1" ]; then
  bash "$ROOT/packaging/scripts/sign.sh" "$DIST/out"
fi
if [ "${NOTARIZE:-0}" = "1" ] && [ "$OS" = "Darwin" ]; then
  bash "$ROOT/packaging/scripts/notarize.sh" "$DIST/out"
fi

# --- checksums ---
( cd "$DIST/out" && for f in *; do [ -f "$f" ] && shasum -a 256 "$f"; done > checksums.txt )
echo "==> checksums.txt yazıldı"
