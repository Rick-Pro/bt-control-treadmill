#!/usr/bin/env bash
# Release workflow, first step: checks everything before anything is built, and lists every problem at once.
# Usage: scripts/release-preflight.sh vX.Y.Z
# Env: GITHUB_REF (must be refs/heads/main), GH_TOKEN and GH_REPO for gh, and the four release key settings:
# secrets STRIDE_KEYSTORE_BASE64 and STRIDE_KEYSTORE_PASSWORD, variables STRIDE_KEY_ALIAS and
# STRIDE_CERT_SHA256. It only checks that they are set and never prints them.
set -euo pipefail
tag="${1:-}"
root="$(cd "$(dirname "$0")/.." && pwd)"
repo="${GH_REPO:-Rick-Pro/stride-android}"
problems=()

if [ "${GITHUB_REF:-}" != "refs/heads/main" ]; then
  problems+=("Run it from main: in Run workflow, pick main under Use workflow from. This run used ${GITHUB_REF:-no branch}.")
fi
version="$(tr -d '[:space:]' < "$root/VERSION")"
if [[ ! "$tag" =~ ^v[0-9]+\.[0-9]{1,3}\.[0-9]{1,3}$ ]]; then
  problems+=("'$tag' is not a version tag. Use the form v$version, with minor and patch 999 or less.")
elif [ "v$version" != "$tag" ]; then
  problems+=("The VERSION file says $version, but the run asked for $tag. The release PR bumps VERSION first.")
fi
if [ -n "$tag" ]; then
  if gh release view "$tag" -R "$repo" >/dev/null 2>&1; then
    problems+=("Release $tag already exists. Pick a new version.")
  elif gh api "repos/$repo/git/ref/tags/$tag" >/dev/null 2>&1; then
    problems+=("Tag $tag already exists. Pick a new version.")
  fi
fi

missing=()
[ -n "${STRIDE_KEYSTORE_BASE64:-}" ] || missing+=("secret STRIDE_KEYSTORE_BASE64")
[ -n "${STRIDE_KEYSTORE_PASSWORD:-}" ] || missing+=("secret STRIDE_KEYSTORE_PASSWORD")
[ -n "${STRIDE_KEY_ALIAS:-}" ] || missing+=("variable STRIDE_KEY_ALIAS")
[ -n "${STRIDE_CERT_SHA256:-}" ] || missing+=("variable STRIDE_CERT_SHA256")
if [ "${#missing[@]}" -gt 0 ]; then
  list="$(printf '%s, ' "${missing[@]}")"
  problems+=("The release key is not set up. Missing: ${list%, }. Run scripts/make-release-key.sh once on your own computer; it sets all four (README, Releasing a new version).")
elif [[ ! "$(tr -d ': ' <<< "$STRIDE_CERT_SHA256")" =~ ^[0-9a-fA-F]{64}$ ]]; then
  problems+=("The variable STRIDE_CERT_SHA256 is not a 64-character certificate SHA-256. Run scripts/make-release-key.sh again: it reuses the key and sets it.")
fi

if [ "${#problems[@]}" -gt 0 ]; then
  summary="${GITHUB_STEP_SUMMARY:-/dev/null}"
  echo "### Release stopped before building" >> "$summary"
  for p in "${problems[@]}"; do
    echo "::error title=Release stopped before building::$p"
    echo "- $p" >> "$summary"
  done
  exit 1
fi
echo "Ready to release $tag (VERSION $version) from $(git -C "$root" rev-parse --short HEAD 2>/dev/null || echo main)."
