#!/usr/bin/env bash
# Builds every HearAI artifact and stages them in ./release-artifacts/.
# Requires: JDK 17, Android SDK (platform 34 + build-tools 34.0.0), Node 18+,
#           a signing keystore at ./release.keystore.
#
#   APK   -> release-artifacts/HearAI-<ver>.apk           (signed)
#   Win   -> release-artifacts/HearAI-Desktop-Setup-<ver>.exe   (NSIS, x64)
#   macOS -> release-artifacts/HearAI-Desktop-<ver>-arm64.dmg
#            release-artifacts/HearAI-Desktop-<ver>-x64.dmg
#
# electron-builder downloads its own bundled wine for the Windows target, so a
# Mac or Linux host can produce the .exe. The builds are unsigned (no Apple/MS
# certificate) — users get the usual unknown-source / Gatekeeper prompt.
set -euo pipefail
cd "$(dirname "$0")/.."

: "${JAVA_HOME:=/opt/homebrew/opt/openjdk@17}"
: "${ANDROID_HOME:=/opt/homebrew/share/android-commandlinetools}"
export JAVA_HOME ANDROID_HOME
export ANDROID_SDK_ROOT="$ANDROID_HOME"

APP_VER="1.0"                # app/build.gradle.kts versionName
DESK_VER="0.1.0"             # desktop/package.json version
BT="$ANDROID_HOME/build-tools/34.0.0"
KS="release.keystore"
KS_PASS="${HEARAI_KS_PASS:-hearai-release}"

out=release-artifacts
rm -rf "$out"; mkdir -p "$out"

echo "==> Android release APK"
./gradlew :app:assembleRelease

if [ ! -f "$KS" ]; then
  echo "!! $KS not found — generate one with:"
  echo '   keytool -genkeypair -v -keystore release.keystore -alias hearai -keyalg RSA \'
  echo '     -keysize 2048 -validity 10000 -storepass <pw> -keypass <pw> -dname "CN=HearAI, O=HearAI, C=IN"'
  exit 1
fi
"$BT/zipalign" -f -p 4 app/build/outputs/apk/release/app-release-unsigned.apk "$out/HearAI-$APP_VER.apk"
"$BT/apksigner" sign --ks "$KS" --ks-key-alias hearai \
  --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" "$out/HearAI-$APP_VER.apk"
"$BT/apksigner" verify "$out/HearAI-$APP_VER.apk"

echo "==> Desktop — Windows exe"
( cd desktop
  [ -d node_modules ] || npm ci
  ./node_modules/.bin/electron-builder --win --x64
)
cp "desktop/dist/HearAI Desktop Setup $DESK_VER.exe"       "$out/HearAI-Desktop-Setup-$DESK_VER.exe"

echo "==> Desktop — macOS .app (unpacked), ad-hoc sign, then dmg"
# electron-builder 25 won't ad-hoc sign, and an UNSIGNED arm64 app triggers the
# "is damaged and can't be opened" Gatekeeper dead-end. So: build --dir, sign with
# the ad-hoc identity (`-`) ourselves — that gets a valid signature and the milder,
# bypassable "unidentified developer" prompt — then pack a plain UDZO dmg.
( cd desktop && ./node_modules/.bin/electron-builder --mac --dir )
for pair in "mac-arm64:arm64" "mac:x64"; do
  dir="${pair%%:*}"; arch="${pair##*:}"
  app="desktop/dist/$dir/HearAI Desktop.app"
  codesign --force --deep --sign - "$app"
  codesign --verify --deep --strict "$app"
  stage="$(mktemp -d)"; cp -R "$app" "$stage/"; ln -s /Applications "$stage/Applications"
  hdiutil create -volname "HearAI Desktop" -srcfolder "$stage" -ov -format UDZO \
    "$out/HearAI-Desktop-$DESK_VER-$arch.dmg" >/dev/null
  rm -rf "$stage"
done

( cd "$out" && shasum -a 256 HearAI-* > SHA256SUMS.txt )
echo "==> Done:"
ls -lh "$out"
