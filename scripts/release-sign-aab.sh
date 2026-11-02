#!/usr/bin/env bash
# Release workflow: signs the release App Bundle (.aab) with the release key using jarsigner, then checks it.
# Usage: scripts/release-sign-aab.sh <unsigned aab> <aab to write>
# Env (the same as scripts/release-sign.sh; this script only reads them and prints none of the secret ones):
#      STRIDE_KEYSTORE           path of the decoded PKCS12 release key (in the runner's temp folder)
#      STRIDE_KEYSTORE_PASSWORD  its password (PKCS12 uses one password for the store and the key)
#      STRIDE_KEY_ALIAS          the key's alias
#      STRIDE_CERT_SHA256        the certificate SHA-256 the bundle must carry (colons and case do not matter)
#      STRIDE_VERSION            the X.Y.Z the bundle must carry as its versionName
# Checks: the bundle is a zip with a base manifest, jarsigner verifies the signature, the certificate matches
# STRIDE_CERT_SHA256, and the manifest names the release package (not .debug) and the version. Writes
# <aab>.sha256 in sha256sum format. Needs a JDK (jarsigner, keytool) and unzip.
# On Google Play with Play App Signing this key is the UPLOAD key: Play checks it on upload and re-signs the app
# for users with a key Google holds. The same release key signs the APK on GitHub Releases.
set -euo pipefail
in="${1:?unsigned aab}"
out="${2:?aab to write}"
package="io.github.rickpro.stride"
fail() { echo "::error title=$1::$2"; exit 1; }

for v in STRIDE_KEYSTORE STRIDE_KEYSTORE_PASSWORD STRIDE_KEY_ALIAS STRIDE_CERT_SHA256 STRIDE_VERSION; do
  [ -n "${!v:-}" ] || fail "Missing setting" "$v is empty."
done
want="$(tr -d ': ' <<< "$STRIDE_CERT_SHA256" | tr 'A-F' 'a-f')"
[[ "$want" =~ ^[0-9a-f]{64}$ ]] || fail "Bad STRIDE_CERT_SHA256" "It must be the 64-character certificate SHA-256 that scripts/make-release-key.sh printed."
for t in jarsigner keytool unzip; do
  command -v "$t" >/dev/null 2>&1 || fail "Tool missing" "$t was not found. The release runner has a JDK; unzip comes with the runner image."
done
[ -f "$in" ] || fail "No bundle" "$in does not exist."
unzip -tq "$in" >/dev/null 2>&1 || fail "Bundle is not a zip" "$(basename "$in") is not a valid .aab (a zip file)."
unzip -l "$in" 2>/dev/null | grep -q ' base/manifest/AndroidManifest.xml$' \
  || fail "Not an App Bundle" "$(basename "$in") has no base/manifest/AndroidManifest.xml."

en=(-J-Duser.language=en -J-Duser.country=US) # English output, so the fingerprint line can be found
keytool "${en[@]}" -list -keystore "$STRIDE_KEYSTORE" -storetype PKCS12 -storepass:env STRIDE_KEYSTORE_PASSWORD \
  -alias "$STRIDE_KEY_ALIAS" >/dev/null 2>&1 \
  || fail "Release key does not open" "STRIDE_KEYSTORE_BASE64 does not open with STRIDE_KEYSTORE_PASSWORD, or holds no key named STRIDE_KEY_ALIAS. Run scripts/make-release-key.sh again: it reuses the key and sets all four values."

mkdir -p "$(dirname "$out")"
cp "$in" "$out"
# jarsigner changes the file in place. Passwords come from the environment, never from the command line.
jarsigner "${en[@]}" -keystore "$STRIDE_KEYSTORE" -storetype PKCS12 \
  -storepass:env STRIDE_KEYSTORE_PASSWORD -keypass:env STRIDE_KEYSTORE_PASSWORD \
  -sigalg SHA256withRSA -digestalg SHA-256 "$out" "$STRIDE_KEY_ALIAS" >/dev/null 2>&1 \
  || { rm -f "$out"; fail "Signing failed" "jarsigner could not sign $(basename "$in") with the release key."; }

verify="$(jarsigner "${en[@]}" -verify "$out" 2>&1)" || { rm -f "$out"; fail "Signature does not verify" "jarsigner -verify rejected $(basename "$out")."; }
grep -q 'jar verified' <<< "$verify" || { rm -f "$out"; fail "Signature does not verify" "jarsigner did not say 'jar verified' for $(basename "$out")."; }

got="$(keytool "${en[@]}" -printcert -jarfile "$out" 2>/dev/null | sed -n 's/^[[:space:]]*SHA256:[[:space:]]*//p' | tr -d ': ' | tr 'A-F' 'a-f' | sort -u)"
[ "$got" = "$want" ] || { rm -f "$out"; fail "Wrong certificate" "$(basename "$out") carries certificate ${got:-none}, but STRIDE_CERT_SHA256 is $want. Nothing was published."; }

# The base manifest is binary (protobuf) but its strings are stored as text, so the package and version can be found.
manifest="$(unzip -p "$out" base/manifest/AndroidManifest.xml | LC_ALL=C tr -c '[:print:]' '\n')"
grep -qF "$package" <<< "$manifest" || { rm -f "$out"; fail "Wrong package" "$(basename "$out") does not name $package."; }
! grep -qF "$package.debug" <<< "$manifest" || { rm -f "$out"; fail "Debug bundle" "$(basename "$out") is a debug build ($package.debug)."; }
# The byte after a string in the manifest can be the next field's tag, which may print as a quote or a letter, so the
# version is matched as a whole number-and-dots token, not as a whole line (1.0.0 must not match inside 11.0.0).
version_re="(^|[^0-9.])${STRIDE_VERSION//./\\.}($|[^0-9.])"
grep -qE "$version_re" <<< "$manifest" || { rm -f "$out"; fail "Wrong version" "$(basename "$out") does not carry versionName $STRIDE_VERSION."; }

bytes="$(stat -c %s "$out")"
(cd "$(dirname "$out")" && sha256sum "$(basename "$out")" > "$(basename "$out").sha256")
sha="$(cut -d' ' -f1 "$out.sha256")"
{
  echo "### $(basename "$out")"
  echo "- Version $STRIDE_VERSION, $((bytes / 1024)) KB (an App Bundle for the Play Console, not for installing on a phone)"
  echo "- Bundle SHA-256: \`$sha\`"
  echo "- Certificate SHA-256: \`$got\`"
} >> "${GITHUB_STEP_SUMMARY:-/dev/null}"
echo "::notice title=$(basename "$out")::Version $STRIDE_VERSION, $((bytes / 1024)) KB, SHA-256 $sha, certificate $got"
