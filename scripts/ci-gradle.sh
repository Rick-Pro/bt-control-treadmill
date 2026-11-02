#!/usr/bin/env bash
# Runs Gradle. If it fails, the key error lines become GitHub annotations and go to the step summary,
# so a failure can be read from the PR page (and the REST API) without downloading logs.
# Usage: scripts/ci-gradle.sh <gradle args>
set -uo pipefail
log="${RUNNER_TEMP:-/tmp}/gradle-$$.log"
./gradlew --stacktrace "$@" 2>&1 | tee "$log"
status=${PIPESTATUS[0]}
if [ "$status" -ne 0 ]; then
  keys="${log}.keys"
  grep -E "What went wrong|^e: |: Error: |error:|AAPT|Could not |Execution failed|Caused by: |^> " -A4 "$log" \
    | grep -vE "^\s+at |^--$|^\* Try:|Run with --|Get more help|For more on name|Run gradlew tasks" \
    | head -80 > "$keys" || true
  { echo "### Gradle failed"; echo '```'; cat "$keys"; echo '```'; } >> "${GITHUB_STEP_SUMMARY:-/dev/null}"
  # One annotation per 20 lines (GitHub shows at most 10 error annotations per step).
  split -l 20 "$keys" "${keys}.part." 2>/dev/null || true
  for part in "${keys}".part.*; do
    [ -s "$part" ] || continue
    msg=$(sed -e 's/%/%25/g' -e 's/\r/%0D/g' "$part" | awk 'BEGIN{ORS="%0A"} {print}')
    echo "::error title=Gradle failed::${msg}"
  done
fi
exit "$status"
