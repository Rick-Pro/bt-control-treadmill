#!/usr/bin/env bash
# Release workflow: aligns and signs the release APK with the release key, then checks the result.
# Usage: scripts/release-sign.sh <unsigned apk> <apk to write>
# Env: STRIDE_KEYSTORE           path of the decoded PKCS12 release key (in the runner's temp folder)
#      STRIDE_KEYSTORE_PASSWORD  its password (PKCS12 uses one password for the store and the key)
#      STRIDE_KEY_ALIAS          the key's alias
#      STRIDE_CERT_SHA256        the certificate SHA-256 the APK must carry (colons and case do not matter)
#      STRIDE_VERSION            the X.Y.Z the APK must report as its versionName
# Checks: v2 or v3 signature verifies, the certificate matches STRIDE_CERT_SHA256, the package is the release
# id (not .debug), the version matches, and the APK is 8 MB or less. Writes <apk>.sha256 in sha256sum format.
# Needs the Android SDK build tools (zipalign, apksigner, aapt2) and a JDK (keytool). Prints no secret.
set -euo pipefail
in="${1:?unsigned apk}"
out="${2:?apk to write}"
package="io.github.rickpro.stride"
budget=$((8 * 1024 * 1024))
fail() { echo "::error title=$1::$2"; exit 1; }

for v in STRIDE_KEYSTORE STRIDE_KEYSTORE_PASSWORD STRIDE_KEY_ALIAS STRIDE_CERT_SHA256 STRIDE_VERSION; do
  [ -n "${!v:-}" ] || fail "Missing setting" "$v is empty."
done
want="$(tr -d ': ' <<< "$STRIDE_CERT_SHA256" | tr 'A-F' 'a-f')"
[[ "$want" =~ ^[0-9a-f]{64}$ ]] || fail "Bad STRIDE_CERT_SHA256" "It must be the 64-character certificate SHA-256 that scripts/make-release-key.sh printed."
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
tools="$(ls -d "$sdk"/build-tools/*/ 2>/dev/null | sort -V | tail -n 1)"
for t in zipalign apksigner aapt2; do
  [ -x "${tools}$t" ] || fail "Build tools missing" "$t not found under $sdk/build-tools"
done
echo "Build tools: $tools"

keytool -list -keystore "$STRIDE_KEYSTORE" -storetype PKCS12 -storepass:env STRIDE_KEYSTORE_PASSWORD \
  -alias "$STRIDE_KEY_ALIAS" >/dev/null 2>&1 \
  || fail "Release key does not open" "STRIDE_KEYSTORE_BASE64 does not open with STRIDE_KEYSTORE_PASSWORD, or holds no key named STRIDE_KEY_ALIAS. Run scripts/make-release-key.sh again: it reuses the key and sets all four values."

# zipalign must come before apksigner: any change after signing breaks the signature.
aligned="${RUNNER_TEMP:-/tmp}/stride-aligned-$$.apk"
trap 'rm -f "$aligned"' EXIT
"${tools}zipalign" -P 16 -f 4 "$in" "$aligned"
mkdir -p "$(dirname "$out")"
"${tools}apksigner" sign --ks "$STRIDE_KEYSTORE" --ks-type PKCS12 --ks-key-alias "$STRIDE_KEY_ALIAS" \
  --ks-pass env:STRIDE_KEYSTORE_PASSWORD --key-pass env:STRIDE_KEYSTORE_PASSWORD --out "$out" "$aligned"
"${tools}zipalign" -c -P 16 4 "$out" >/dev/null || fail "APK not aligned" "zipalign -c failed on $(basename "$out")."

report="$("${tools}apksigner" verify --verbose --print-certs "$out" 2>/dev/null)" \
  || fail "Signature does not verify" "apksigner verify rejected $(basename "$out")."
grep -E '^(Verifie|Number of signers)|certificate (DN|SHA-256 digest)' <<< "$report" || true # public facts only
grep -Eq 'v[23] scheme.*: true' <<< "$report" || fail "No v2 or v3 signature" "$(basename "$out") has no v2 or v3 signature."
got="$(grep 'certificate SHA-256 digest' <<< "$report" | sed 's/.*: //' | tr 'A-F' 'a-f' | sort -u)"
[ "$got" = "$want" ] || fail "Wrong certificate" "$(basename "$out") carries certificate ${got:-none}, but STRIDE_CERT_SHA256 is $want. Nothing was published."

badging="$("${tools}aapt2" dump badging "$out" 2>/dev/null)" || fail "aapt2 failed" "aapt2 could not read $(basename "$out")."
first="${badging%%$'\n'*}"
echo "$first"
[[ "$first" == *"name='$package' "* ]] || fail "Wrong package" "$(basename "$out") is not $package (a debug build has .debug)."
[[ "$first" == *"versionName='$STRIDE_VERSION'"* ]] || fail "Wrong version" "$(basename "$out") does not report versionName $STRIDE_VERSION."

bytes="$(stat -c %s "$out")"
[ "$bytes" -le "$budget" ] || fail "APK too big" "$(basename "$out") is $((bytes / 1024)) KB, over the 8 MB budget."

(cd "$(dirname "$out")" && sha256sum "$(basename "$out")" > "$(basename "$out").sha256")
sha="$(cut -d' ' -f1 "$out.sha256")"
{
  echo "### $(basename "$out")"
  echo "- Version $STRIDE_VERSION, $((bytes / 1024)) KB"
  echo "- APK SHA-256: \`$sha\`"
  echo "- Certificate SHA-256: \`$got\`"
} >> "${GITHUB_STEP_SUMMARY:-/dev/null}"
echo "::notice title=$(basename "$out")::Version $STRIDE_VERSION, $((bytes / 1024)) KB, SHA-256 $sha, certificate $got"
