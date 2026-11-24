# Review — blueprint-draft-v0 (2026-11-24 12:08)

## Scope
`git status --short`: new `src/pm/lot.cljc`, `scripts/blueprint_draft.clj`, `test/pm/lot_test.cljs`,
plus edits to `bb.edn` and `resources/estate.edn`. Diffed `bb.edn`/`resources/estate.edn` with
`git diff`, read the three new files in full, and read `spec.md` (540 lines) and the quest in
full. No pre-existing/unrelated dirt in the diff — both edited files' hunks are exactly the lines
the spec's boundary table allows (the `blueprint-draft` bb task; the `:build-rules` key with its
comment, nothing else in `estate.edn`).

## Verified independently
- Re-ran `bash .crew/gate.sh` myself (not just read `gate-note.md`): **PASS, 0 warnings, 1437
  tests / 7917 assertions**, matching `gate-note.md` exactly. `pm.lot-test` appears in the
  compiler's namespace list and its own `Testing pm.lot-test` line is in the output.
- `grep -c "^(deftest" test/pm/lot_test.cljs` → 40, matching spec §6's "40 cases" and every t01–
  t40 label is present under the headings the spec groups them into.
- `pm.lot.cljc`: requires only `clojure.string` and `pm.blueprint-file` (confirmed in the `ns`
  form); grepped for `js/Date`, `atom`, `slurp`, `spit`, `println` — zero real hits (only the
  docstring's own prose mentions "no atoms, no js/Date"). No `::k` keys in either new source file.
- Rules come from the passed-in map everywhere: `spot`'s destructure (`max-spread-m gap-m
  route-m`) and `place`'s (`step search-m yaws`) all read off the `rules` arg; `resources/estate.edn`
  only supplies `:build-rules` data, never consulted inside `pm.lot` itself.
- `feet`/`turn`/highest-foot-`y` match the quest/spec formulas exactly: grounded test is
  `|ay − sy/2| <= grounded-m + eps`; narrow one-foot vs. wide five-foot split at `narrow-m`;
  `turn` is `[(+ (* c dx) (* s dz)) (+ (* (- s) dx) (* c dz))]` with cos/sin rounded to 12
  decimals; `spot`'s success branch returns `:y (apply max ys)`. Hand-checked `look-radius`
  against t07/t13's worked numbers (base √1.36/2 ≈ 0.58309519; jar √0.0128/2×4 ≈ 0.22627417) —
  both match the implementation's formula and the test's expected value.
- Every refusal reason in the quest's list is reachable and reads as a line a player can parse:
  canopy/bushes/rocks/creek/no-ground via `cell-reason` (§3.1's five strings), "too steep",
  "too close to <word>", "on a walker's path", unknown-landmark, no-ground-map, and
  "nothing touches the ground" each has its own exercising test (t16, t17, t18/t19, t29–t36) and
  all pass against the real `spot`/`place` code, not a mock.
- `spot`'s check order is ground → steep → too-close → route, matching §3.12 and the quest; the
  code's `cond`/`if` chain was read directly, not inferred from test names.
- `scripts/blueprint_draft.clj` hands the full blueprint to the **existing**
  `scripts/blueprint_add.clj` via a temp file + `p/shell`, never writes `state/` itself — one
  writer, one validator, as required. `PM_GROUND_FILE`/`PM_BLUEPRINTS_FILE` are both honoured
  (`ground-file`/`blueprints-file` helpers check the env var before the default path).
  `--dry-run` only calls `println`; the temp-file/`blueprint_add.clj` branch is structurally in
  the `else` of `if dry-run`, so dry-run writes nothing — confirmed by reading the branch, and
  `builder-notes.md` documents a real hand run against the live ground map with `diff` showing
  the temp copy unchanged.
- `test/pm/lot_test.cljs` builds every grid/estate fixture inline (§5) and never opens
  `state/ground.edn`; the only two appearances of the string `"state/"` are inside expected
  refusal-message literals (`no-ground-map`'s text), not a file read.
- No person/pet/member/prop name appears in the two production files (`src/pm/lot.cljc`,
  `scripts/blueprint_draft.clj`) — grepped for Michael, Hal, Pip, Wren, Flora, Phoenix and
  specific prop ids, zero hits outside doc comments quoting the spec itself. `test/pm/lot_test.cljs`
  does use "Pip"/"Wren"/"Flora" names, but only as the architect's own §5 fixture data (literally
  copied from `spec.md`'s own E2 estate) — that's test data exercising the "both mirrors" case,
  not production code naming a real crew member.
- `git diff --stat -- package.json package-lock.json` is empty: no npm deps added.

## Findings
None rise above a style nit. The implementation is a faithful, hand-traceable port of the
spec's formulas (feet, turn, spot's check order, refusal tie-break, fmt, the world-anchored
candidate grid), the gate is genuinely green on a fresh run, and every boundary-table/purity/
naming constraint in the quest holds.
