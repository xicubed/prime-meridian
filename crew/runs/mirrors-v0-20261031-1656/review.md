# Review: mirrors-v0 (2026-10-31 17:07 round)

## Conformance summary

Verified independently (re-ran `.crew/gate.sh`, didn't just trust `gate-note.md`):
`Build completed (0 warnings)` for both `:app` and `:test`; `Ran 54 tests containing 222
assertions. 0 failures, 0 errors.` — matches the gate note exactly, and 54 = the 32 pre-existing
`world_test.cljs` tests (file untouched, confirmed via `git diff --stat`) + the 22 new
`t01`..`t22` in `plan_test.cljs`, all present and matching spec §7 verbatim (shared defs, test
bodies, and expected values hand-checked against §7 line by line).

Boundary table (spec §0): builder touched exactly `resources/estate.edn`, `src/pm/world.cljs`,
`src/pm/plan.cljs` (new), `src/pm/three.cljs`, `src/pm/scene.cljs` (new), `src/pm/core.cljs` —
no more, no less. Tester touched exactly `test/pm/plan_test.cljs`; `world_test.cljs` untouched.
`estate.edn`'s diff is `:placed` additions only, nothing else changed. `world.cljs`'s diff is
exactly `fill` going public with a docstring, behavior untouched.

Purity/leakage checks (grepped `src/`, not just the diff): no `:flora`/`:juniper`/axolotl/horse/
worm/hay/jar/bale token anywhere in `src/`; `pm.plan` has no three.js, `js/Date`, or atoms;
`pm.scene` never references `js/THREE`, the npm `"three/webgpu"` module, or `window.hiroshi` —
every constructor goes through `pm.three`'s `(three)`/`use-three!` indirection, confirmed by
grep (`THREE\.` only appears in two docstrings, never a call) and by reading `mount!`/`update!`
(mount builds once behind the `!mounted` guard; `update!` only calls `t/set-label-text!` on
existing sprites for changed keys — never creates/removes meshes or labels, as required). No
`clojure.core` name shadowing found in the new files. Scaffold path (`start-scaffold!`,
`build-scene!`, `reload!`) is byte-identical to before; `scene/update!`/`mount!` no-op without a
mount, so `frame`'s new unconditional `scene/update!` call is safe for the scaffold.

Hand-traced a sample of §7 against the implementation logic (not just "tests pass"): t01/t03/t04
(data + shape), t09/t11 (idle fallback, missing field keeps placeholder), t14/t15 (second mirror,
same code path, store item), t18 (malformed `:placed` entries — short `:at`, negative size,
string color, missing `:look`) all match `pm.plan`'s `scene-plan`/`normalize-*` behavior exactly.

One pre-existing/out-of-window item excluded from scope per the skill's guidance: none — both
`.crew/roles/*.md` diffs land inside this round's build-step window (16:56 `spec` step shows 0
modified files; the 17:03 timestamp is mid-`build`), so they're flagged below rather than waved
off as a prior round's leftover.

## Findings

1. `.crew/roles/builder.md` / `.crew/roles/tester.md` — both modified during this round's build
   step (mtime 17:03, inside the 17:01–17:07 window), but neither file is in spec §0's boundary
   table and the builder's reply explicitly disclaims touching them ("pre-existing from crew
   tooling"). Before committing, confirm who actually made this edit (concurrent manager/architect
   change on the shared tree, vs. an agent silently exceeding its boundary) — if an agent's edit,
   that's a real `.must-not-touch` violation worth a role-card reminder; if the manager's own
   change, it's fine but shouldn't ride in silently under this round's diff.
2. `crew/runs/mirrors-v0-20261031-1656/replies/build-le-testeur.txt` — 0 bytes, yet `log.md`
   records le-testeur as "blocked" for this step while `test/pm/plan_test.cljs` was in fact
   written completely and correctly (all 22 tests match spec §7). Minor process gap: the tester's
   final reply never actually landed, so the "blocked" status in `log.md` is misleading relative
   to the real (correct) outcome — worth checking why the reply capture came back empty.

No findings against the game-code diff itself (`estate.edn`, `world.cljs`, `plan.cljs`,
`three.cljs`, `scene.cljs`, `core.cljs`): naming, purity, the three.js indirection, and the
no-per-frame-rebuild requirement all match spec and quest.
