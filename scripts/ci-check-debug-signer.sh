#!/usr/bin/env bash
# Fails unless the APK is signed with the committed debug key app/debug.keystore (decision D6),
# so every test build has the same signature and installs over the last one.
# Usage: scripts/ci-check-debug-signer.sh <apk> (needs the Android SDK build tools for apksigner)
set -euo pipefail
keystore="$(dirname "$0")/../app/debug.keystore"
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
apksigner="$(ls -d "$sdk"/build-tools/*/ 2>/dev/null | sort -V | tail -1)apksigner"
[ -x "$apksigner" ] || { echo "::error::apksigner not found under $sdk/build-tools"; exit 1; }
got="$("$apksigner" verify --print-certs "$1" | grep -m1 'certificate SHA-256 digest' | sed 's/.*: //')"
want="$(keytool -list -v -keystore "$keystore" -storepass android -alias androiddebugkey 2>/dev/null \
  | grep -m1 'SHA256:' | sed 's/.*SHA256: //' | tr -d ':' | tr 'A-F' 'a-f')"
echo "::notice title=Signer of $(basename "$1")::SHA-256 $got"
[ -n "$got" ] && [ "$got" = "$want" ] || { echo "::error::$(basename "$1") is not signed with app/debug.keystore ($want)"; exit 1; }
