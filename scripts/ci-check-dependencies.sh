#!/usr/bin/env bash
# Fails if the release app's runtime classpath has a dependency outside config/allowed-dependency-groups.txt.
# Usage: scripts/ci-check-dependencies.sh (run from the repo root, after the app has built)
set -euo pipefail
allow_file="$(dirname "$0")/../config/allowed-dependency-groups.txt"
mapfile -t patterns < <(grep -vE '^\s*(#|$)' "$allow_file")
tree="$(./gradlew -q :app:dependencies --configuration releaseRuntimeClasspath)"
# Lines look like "+--- group:artifact:version", "\--- group:artifact -> version" or "+--- project ':core'".
# "(c)" lines are constraints and "(n)" lines are not resolved, so neither ships.
coords="$(echo "$tree" | grep -E -- '--- ' | grep -vE '\((c|n)\)\s*$' | sed -E 's/^.*--- //' \
  | sed -E "s/^project '?([^' ]+)'?.*/project \\1/; t; s/^([^: ]+):([^: ]+).*/\\1:\\2/" | sort -u)"
[ -n "$coords" ] || { echo "::error::No dependencies listed. Did the app module load?"; exit 1; }

fail=0
summary="${GITHUB_STEP_SUMMARY:-/dev/null}"
echo "### Release runtime dependencies" >> "$summary"
while IFS= read -r c; do
  group="${c%%:*}"
  ok=0
  for p in "${patterns[@]}"; do
    if [[ "$p" == *:* || "$p" == project\ * ]]; then
      [[ "$c" == $p ]] && ok=1
    else
      [[ "$group" == $p ]] && ok=1
    fi
    [ "$ok" -eq 1 ] && break
  done
  if [ "$ok" -eq 1 ]; then
    echo "- \`$c\`" >> "$summary"
  else
    echo "- \`$c\` **not allowed**" >> "$summary"
    echo "::error title=Dependency not allowed::$c is not in config/allowed-dependency-groups.txt. Add it only with a written reason."
    fail=1
  fi
done <<< "$coords"
echo "$coords"
echo "::notice title=Release runtime dependencies ($(wc -l <<< "$coords"))::$(paste -sd ' ' <<< "$coords")"
exit "$fail"
