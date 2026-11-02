#!/usr/bin/env bash
# Writes APK sizes to the step summary and fails if the release APK is over the 8 MB budget (docs/plan.md section 6).
set -euo pipefail
budget=$((8 * 1024 * 1024))
summary="${GITHUB_STEP_SUMMARY:-/dev/null}"
echo "### APK sizes (release budget 8 MB)" >> "$summary"
fail=0
shopt -s nullglob
release=(app/build/outputs/apk/release/*.apk)
[ "${#release[@]}" -gt 0 ] || { echo "::error::No release APK found"; exit 1; }
for f in app/build/outputs/apk/debug/*.apk "${release[@]}"; do
  bytes=$(stat -c %s "$f")
  line="$(basename "$f"): $((bytes / 1024)) KB"
  echo "- $line" >> "$summary"
  echo "$line"
  sizes="${sizes:-}${sizes:+, }$line"
done
echo "::notice title=APK sizes (release budget 8 MB)::$sizes"
for f in "${release[@]}"; do
  bytes=$(stat -c %s "$f")
  if [ "$bytes" -gt "$budget" ]; then
    echo "::error title=APK too big::$(basename "$f") is $((bytes / 1024)) KB, over the 8 MB budget."
    fail=1
  fi
done
exit "$fail"
