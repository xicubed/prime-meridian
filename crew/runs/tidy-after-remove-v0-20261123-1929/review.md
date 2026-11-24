# Review — tidy-after-remove-v0 (TASKS T-16)

Scope: `git diff` for the 7 modified files (`resources/estate.edn`, `src/pm/blueprint.cljs`,
`src/pm/core.cljs`, `src/pm/pick.cljs`, `src/pm/plan.cljs`, `src/pm/ui.cljs`,
`test/pm/plan_test.cljs`) plus a full read of the one new file (`test/pm/tidy_after_remove_test.cljs`),
against `spec.md` and the quest. `6600d97` (the quest+spec commit, code/tests unchanged) is the
baseline; nothing else is stacked on it.

## Gate — verified independently

Re-ran `bash .crew/gate.sh`: `[:app]`/`[:test]` 0 warnings, `Ran 1300 tests containing 7585
assertions, 0 failures, 0 errors`, `bb content-test` 5/42 — matches `gate-1.txt` exactly.
Structural cross-check: committed `deftest` count at `HEAD` is 1250, working tree is 1300, a
delta of exactly 50 — the new file's own count (L1–L8=8, P1–P12=12, M1–M11=11, R1–R9=9,
G1–G10=10 = 50), matching the spec's own "50 numbered cases" exactly; `t42`/`t43` are rewrites of
existing `deftest`s so correctly add zero to the delta.

## Point-by-point

- **The lobby draws nothing, still works as an errand spot**: `pm.plan/lobby-item` now requires
  `(and (valid-at? at) (some? look))`, so the shipped estate's look-less `:lobby` returns nil
  (`L4`, `t42`) — `pm.scene/with-lobby` (untouched) already conjs only a non-nil item, so no empty
  sign/mesh is drawn. `pm.world/errand-target` (not in this round's diff, confirmed by `git
  status`) still reads `(get-in world [:lobby :at])`, and `:lobby`'s `:at`/`:yaw` are
  byte-identical to before — `L6`/`L7`/`L8` (errand target, already-close, the recorded errand
  event) hand-trace correctly and are untouched logic paths, only exercised against the new data
  shape.
- **Only `:look`/`:text` left `estate.edn`'s `:lobby`**: diffed the one hunk — `:at [2.7 6.18
  -23.6] :yaw 0` are the same literal values as before; only the `:text` line and the `:look`
  map (post + board parts) were removed, plus the comment rewritten to say why. `L4` confirms the
  shipped entry's keys are now exactly `#{:at :yaw}`.
- **Old tests changed only where they counted the lobby item/label**: the `plan_test.cljs` diff
  is confined to `t42`/`t43` (plus the new shared `lobby-LOOK` fixture both use) — no other
  assertion in that file moved, `t41` is untouched, and grepping `test/` for `:lobby` turns up
  nothing outside these two files, so no other suite silently relied on a drawn lobby item.
- **`screen-of`'s `min-y` is pure, tested, and doesn't change the hidden cases**: the 3-arity
  touches no DOM/`js/Date`; hand-traced its branching against `P1`–`P11` — invalid `ndc`/`size`
  or a non-finite `min-y` always returns `base` (literally `(screen-of ndc size)`, the 2-arity's
  own value) unmodified, and when `ndc`/`min-y` are valid but the point fails the new (top-bound-
  dropped) visibility test `(and (<= -1 x 1) (<= -1 y) (<= z 1))`, it *also* returns `base`
  verbatim — so every hidden case (off left/right `P7`, below bottom `P8`, behind camera `P9`) is
  provably identical to the unpinned 2-arity result, not just numerically equal by coincidence.
  `pin-min-y` matches §B.3's rule by hand-trace (`bx` genuinely unchecked in `M6`; `card-h` must
  be finite and `>= 0`; `(<= by 1)`/`(<= bz 1)` gate it to nil otherwise) and its composition with
  `screen-of` (`M10`/`M11`) checks out.
- **The real-sign prefix drop applies to `:real` only and is data-agnostic**: `sign-summary` is
  gated on `(= :real (:status bp))` exclusively (`R8` sweeps every other status plus `nil`,
  unchanged); it reads only `bp`'s own `:summary`/`:status`, never `world`'s `:blueprint-text`
  (`G4`'s `(bp/sign-text {} (assoc BL :status :real))` confirms it works with an empty world).
  `proposed-prefix-re` is `^:private` and generic (no blueprint id or member name baked in).
  `sign-text` now routes every status's first line through `sign-summary` (a no-op for
  non-`:real`), and for `:real` specifically drops the whole second-line/tail logic, matching
  `:pending`/`:approved`/`:removing` signs staying byte-for-byte (`G5`–`G8`). `built-plan` itself
  is untouched — a `:real` item still carries no `:label`/`:text` (`G9`), consistent with the
  spec's explicit note that a real build still draws no sign at all.
- **No member/pet/blueprint named in code; no npm deps**: grepped `src/` for crew codenames and
  specific blueprint ids — the only hit (`pick.cljs`'s pre-existing `le-testeur` docstring)
  predates this round's diff. `package.json` has no diff.
- **Boundary table**: `git status` matches exactly — builder's files (`plan.cljs`'s `lobby-item`,
  `pick.cljs`'s new 3-arity/`pin-min-y`/`card-pin-gap-px`, `ui.cljs`'s `trial-card-height`,
  `core.cljs`'s `trial-card!` only, `blueprint.cljs`'s `sign-summary`/`sign-text`,
  `estate.edn`'s `:lobby` entry only) plus the tester's `plan_test.cljs` (`t42`/`t43` only) and
  new file; nothing in `docs/`, `TASKS.md`, any other `estate.edn` key, or `state/` touched.
  `core.cljs`'s reuse of the pre-existing `t/world-pos` (unmodified `three.cljs`, not in this
  round's diff) stays within the `trial-card!`-only boundary.

## Findings

None. Every new/changed pure fn (`lobby-item`'s look-gate, `screen-of`'s 3-arity, `pin-min-y`,
`sign-summary`, `sign-text`'s real-branch) traces to matching code and a passing, hand-checked
test, including the subtler "hidden case must be byte-identical to the 2-arity result" and
"prefix drop is data-agnostic" requirements this review specifically probed for.
