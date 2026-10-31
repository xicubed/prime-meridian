#!/usr/bin/env bash
# The build gate (crew-round runs this itself after the runner's step):
#   1. `shadow-cljs compile app test` succeeds with 0 warnings in every build
#   2. the unit tests run, at least one exists, and none fail or error
# Exit 0 = PASS. Output ends with one "GATE: ..." line.
set -uo pipefail
export PATH="/opt/homebrew/opt/openjdk@21/bin:/opt/homebrew/bin:$HOME/.local/bin:$PATH"
[ -d /opt/homebrew/opt/openjdk@21 ] && export JAVA_HOME=/opt/homebrew/opt/openjdk@21
cd "$(dirname "$0")/.."

out="$(npx shadow-cljs compile app test 2>&1)"; rc=$?
echo "$out" | tail -40
[ $rc = 0 ] || { echo "GATE: FAIL compile (exit $rc)"; exit 1; }
builds="$(echo "$out" | grep -c 'Build completed')"
clean="$(echo "$out" | grep 'Build completed' | grep -c ' 0 warnings')"
[ "$builds" -ge 2 ] && [ "$builds" = "$clean" ] || { echo "GATE: FAIL warnings ($clean of $builds builds clean)"; exit 1; }

echo "--- node out/test.js"
tout="$(node out/test.js 2>&1)"; trc=$?
echo "$tout" | tail -40
summary="$(echo "$tout" | grep -E '^Ran [0-9]+ tests' || true)"
[ -n "$summary" ] || { echo "GATE: FAIL no test summary"; exit 1; }
echo "$summary" | grep -q '^Ran 0 tests' && { echo "GATE: FAIL no tests"; exit 1; }
[ $trc = 0 ] && echo "$tout" | grep -q '0 failures, 0 errors' || { echo "GATE: FAIL tests ($summary)"; exit 1; }
echo "GATE: PASS — 0 warnings; $summary"
