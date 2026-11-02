#!/usr/bin/env bash
# Stride: makes the one release key and, if you want, stores it in GitHub Actions.
# Run it ONCE, on your own computer (Linux, or macOS, or Git Bash on Windows). Not in a repo or a synced folder.
# Needs keytool (from any JDK 17 or newer) and base64. Optional: the GitHub CLI (gh), signed in as the repo owner.
#
# Usage: scripts/make-release-key.sh [output folder]   (default: ~/stride-release-key)
#        STRIDE_REPO=owner/name sets the secrets on another repo (default: Rick-Pro/stride-android).
#
# What it does:
#   1. Asks for a password twice and does not show it. Make a long one in your password manager and paste it.
#   2. Creates stride-release.p12 (PKCS12, RSA 4096, valid 10,000 days, alias "stride") in the output folder.
#      If that file already exists, it offers to reuse it instead, for example to set the secrets again.
#   3. Prints the certificate SHA-256 fingerprint. It is public: the README and the release notes can show it.
#   4. If gh is signed in, offers to set on the repo:
#        secrets   STRIDE_KEYSTORE_BASE64, STRIDE_KEYSTORE_PASSWORD
#        variables STRIDE_KEY_ALIAS, STRIDE_CERT_SHA256
#      Values go to gh on stdin, so nothing lands on a command line or in your shell history.
# The password is never printed and never written to disk.
set -euo pipefail

repo="${STRIDE_REPO:-Rick-Pro/stride-android}"
out="${1:-$HOME/stride-release-key}"
key_alias="stride"
ks="$out/stride-release.p12"
dname="CN=Stride, O=Rick-Pro"
en=(-J-Duser.language=en -J-Duser.country=US) # keytool output in English, so the fingerprint line can be found

say() { printf '%s\n' "$*"; }
die() { printf 'Stopped: %s\n' "$*" >&2; exit 1; }
ask() {
  local answer
  IFS= read -r -p "$1" answer || return 1
  case "$answer" in y | Y | yes | Yes | YES) return 0 ;; *) return 1 ;; esac
}
read_secret() { # $1 prompt, $2 variable to fill. Input is hidden on a terminal.
  local value
  IFS= read -r -s -p "$1" value || die "No password given. Nothing was changed."
  printf '\n' >&2
  printf -v "$2" '%s' "$value"
}

command -v keytool >/dev/null 2>&1 || die "keytool not found. Install a JDK, for example: sudo apt install openjdk-21-jdk-headless"
command -v base64 >/dev/null 2>&1 || die "base64 not found."

# Never inside a git work tree: a key in a repo folder is one 'git add' away from being committed.
probe="$out"
while [ ! -d "$probe" ]; do probe="$(dirname "$probe")"; done
if command -v git >/dev/null 2>&1 && git -C "$probe" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  die "$out is inside a git repo. Pick a folder outside every repo, for example ~/stride-release-key."
fi

pw=""
if [ -e "$ks" ]; then
  say "A release key already exists: $ks"
  ask "Use it to print the fingerprint and set the GitHub secrets again? [y/N] " \
    || die "Nothing was changed. Making a second key would stop updates from installing over the first one."
  read_secret "Password of that key (hidden): " pw
  KS_PASS="$pw" keytool "${en[@]}" -list -keystore "$ks" -storetype PKCS12 -storepass:env KS_PASS \
    -alias "$key_alias" >/dev/null 2>&1 || die "That password does not open $ks, or it holds no '$key_alias' key."
else
  say "This makes the Stride release key in $out"
  read_secret "New key password, 12 or more characters (hidden): " pw
  [ "${#pw}" -ge 12 ] || die "Use 12 or more characters. Nothing was created."
  read_secret "Same password again (hidden): " pw2
  [ "$pw" = "$pw2" ] || die "The two passwords differ. Nothing was created."
  unset pw2
  umask 077
  mkdir -p "$out"
  chmod 700 "$out"
  KS_PASS="$pw" keytool "${en[@]}" -genkeypair -keystore "$ks" -storetype PKCS12 -alias "$key_alias" \
    -keyalg RSA -keysize 4096 -validity 10000 -dname "$dname" \
    -storepass:env KS_PASS -keypass:env KS_PASS >/dev/null 2>&1 || die "keytool could not create the key."
  chmod 600 "$ks"
  say "Created $ks"
fi

fp_colons="$(KS_PASS="$pw" keytool "${en[@]}" -list -v -keystore "$ks" -storetype PKCS12 -storepass:env KS_PASS \
  -alias "$key_alias" 2>/dev/null | sed -n 's/^[[:space:]]*SHA256:[[:space:]]*//p' | head -n 1)"
fp="$(tr -d ': ' <<< "$fp_colons" | tr 'A-F' 'a-f')"
[[ "$fp" =~ ^[0-9a-f]{64}$ ]] || die "Could not read the certificate fingerprint from $ks."
say ""
say "Certificate SHA-256 (public, fine to share):"
say "  $fp"
say ""

if ! command -v gh >/dev/null 2>&1; then
  say "The GitHub CLI (gh) is not installed, so no secrets were set. Install it, run 'gh auth login', then run"
  say "this script again: it reuses the key above."
elif ! gh auth status >/dev/null 2>&1; then
  say "gh is not signed in, so no secrets were set. Run 'gh auth login', then run this script again: it reuses"
  say "the key above."
elif ask "Set secrets STRIDE_KEYSTORE_BASE64 and STRIDE_KEYSTORE_PASSWORD and variables STRIDE_KEY_ALIAS and STRIDE_CERT_SHA256 on $repo now? [y/N] "; then
  base64 < "$ks" | tr -d '\n\r' | gh secret set STRIDE_KEYSTORE_BASE64 -R "$repo"
  printf '%s' "$pw" | gh secret set STRIDE_KEYSTORE_PASSWORD -R "$repo"
  printf '%s' "$key_alias" | gh variable set STRIDE_KEY_ALIAS -R "$repo"
  printf '%s' "$fp" | gh variable set STRIDE_CERT_SHA256 -R "$repo"
  say "Done: 2 secrets and 2 variables set on $repo. The Release workflow can sign now."
else
  say "No secrets were set. Run this script again when you are ready: it reuses the key above."
fi
unset pw

say ""
say "Back up the key now, before the first release:"
say "  1. Save $ks and its password in your password manager (as an attachment), plus one offline copy."
say "     Not in the shared Drive folder, not in a repo, not in chat."
say "  2. GitHub secrets cannot be read back, so they are not a backup."
say "  3. If this key is lost, every installed copy has to be uninstalled before a new release will install."
say "After the backup you can delete $out from this computer."
