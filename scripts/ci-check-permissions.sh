#!/usr/bin/env bash
# Fails if an APK requests or declares a permission outside config/allowed-permissions.txt, or declares an
# accessibility service or device admin. This is what keeps "no internet, no ads, no telemetry" true.
# Usage: scripts/ci-check-permissions.sh <apk>... (needs the Android SDK build tools for aapt2)
set -euo pipefail
allow_file="$(dirname "$0")/../config/allowed-permissions.txt"
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
aapt2="$(ls -d "$sdk"/build-tools/*/ 2>/dev/null | sort -V | tail -1)aapt2"
[ -x "$aapt2" ] || { echo "::error::aapt2 not found under $sdk/build-tools"; exit 1; }
[ "$#" -gt 0 ] || { echo "::error::No APK given"; exit 1; }

fail=0
for apk in "$@"; do
  pkg="$("$aapt2" dump packagename "$apk")"
  allowed="$(grep -vE '^\s*(#|$)' "$allow_file" | sed "s/{applicationId}/$pkg/g")"
  dump="$("$aapt2" dump permissions "$apk")"
  # "uses-permission: name='X'" (requested) and "permission: X" (declared by the app or a library).
  found="$( { echo "$dump" | grep -E "^uses-" | grep -oE "name='[^']+'" | sed -E "s/name='(.*)'/\1/"
              echo "$dump" | grep -E "^permission: " | sed -E 's/^permission: *//' | awk '{print $1}'; } | sort -u)"
  echo "### Permissions in $(basename "$apk") ($pkg)" >> "${GITHUB_STEP_SUMMARY:-/dev/null}"
  if [ -z "$found" ]; then
    echo "None." >> "${GITHUB_STEP_SUMMARY:-/dev/null}"
  fi
  while IFS= read -r perm; do
    [ -n "$perm" ] || continue
    if grep -qxF "$perm" <<< "$allowed"; then
      echo "- \`$perm\`" >> "${GITHUB_STEP_SUMMARY:-/dev/null}"
      echo "ok: $perm"
    else
      echo "- \`$perm\` **not allowed**" >> "${GITHUB_STEP_SUMMARY:-/dev/null}"
      echo "::error title=Permission not allowed::$(basename "$apk") asks for $perm. Add it to config/allowed-permissions.txt only with a written reason."
      fail=1
    fi
  done <<< "$found"
  echo "::notice title=Permissions in $(basename "$apk")::${pkg}: $(paste -sd ' ' <<< "${found:-none}")"
  manifest="$("$aapt2" dump xmltree --file AndroidManifest.xml "$apk")"
  for banned in android.permission.BIND_ACCESSIBILITY_SERVICE android.permission.BIND_DEVICE_ADMIN \
                android.accessibilityservice android.app.action.DEVICE_ADMIN_ENABLED; do
    if grep -qF "$banned" <<< "$manifest"; then
      echo "::error title=Banned component::$(basename "$apk") declares $banned. No accessibility service and no device admin (AGENTS.md)."
      fail=1
    fi
  done
done
exit "$fail"
