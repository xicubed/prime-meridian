# Review: senses-v0

Gate re-checked against `gate-1.txt`: PASS, 0 warnings, 440 tests / 2043 assertions (up from
397/1826 at the end of `foot-ik-v0`, which is now committed as `fbfbdea`: +43 tests, +217
assertions — exactly `test/pm/senses_test.cljs`'s `t01`–`t43`, one per spec case). All prior test
files are untouched and unaffected.

**Scope note.** This round's start commit (`fbfbdea`) already includes `avoid-v0`, `characters-v0`
and `foot-ik-v0` (all previously reviewed and now committed), so `git diff` here is clean —
no stacked-round reconstruction needed this time. `bb.edn`, `scripts/crew_status.clj` and
`.crew/quests/crew-dots-v0.md`/`.crew/rounds/crew-dots-v0.json` also show as changed/untracked,
but none are in `senses-v0`'s boundary table; their content (a `bb crew-status` task writing
Herdr state to `public/state/crew.edn`, and a quest file for a future `crew-dots-v0` round) is
unrelated to senses, and their mtimes (~16:12) fall in the gap between this round's blocked
"Fix round 1" (16:10:44) and its final gate (16:16:38) — concurrent manager/other-crew work in
the shared tree, not this round's builder/tester. Excluded below. (The gate's first FAIL, a
missing-var typo in an early `senses_test.cljs` draft, was fixed before the final PASS — not a
live finding.)

## Conformance checks

- **Planner uses only noticed walkers:** `plan-all` builds `others` from the same input vector
  as before, then calls `(plan w (noticed w others) statics cfg)` — `noticed` filters to
  `(get-in (:aware w) [(:id %) :noticed])` true, so an unnoticed walker is invisible to `plan`
  even though it's still in the raw `others` list. Confirmed by `t30`–`t32` (two fast walkers
  with empty `:aware` produce no detour at all; giving one of them a pre-seeded
  `:aware {:h seen-1s}` makes only it detour; giving both produces the familiar head-on
  right/right pair from `avoid-v0`'s own numbers).
- **Statics stay always known:** `plan-all`/`plan` never filters `statics` through `noticed` —
  only the `others` (walker) list goes through it. `t33` confirms a static-blocked detour is
  identical whether or not an unnoticed walker is also present in the scene.
- **Cone math and heading convention:** `cone-points`'s formula (`half = sight-deg/2` in rad,
  `a = heading - half + k·step`, point `[x+sight-m·cos a, z-sight-m·sin a]`) hand-reproduces
  spec's worked example exactly — `(cone-points [0 0] 0 8 90 2)` → `[[0 0] [5.6568542 5.6568542]
  [8 0] [5.6568542 -5.6568542] [0 0]]`, matching `t37` and my own independent recomputation
  (k=0 → a=−45° → `[5.657, 5.657]`; k=1 → a=0° → `[8,0]`; k=2 → a=45° → `[5.657,−5.657]`). `sees?`
  uses the same `forward(h)=[cos h, −sin h]` convention as `pm.plan`'s existing `:yaw`, and
  `t08`–`t11` cross-check the ±55° cone boundary against the spec's hand-worked cosines (30.96°,
  53.13° seen; 59.04° not seen).
- **Hearing scale:** `hear-range` = `hear-m × max(1, |vel o| / walk-pace)` with a missing `:vel`
  treated as `[0 0]` — `t12` confirms the 1.2/2.4/3.0 m cases (speed 3 → ×2.5) and the
  missing-`:vel` default.
- **Reaction delay and forgetting are time-based and tested:** `perceive`'s `on'`/`off'`
  accumulate real `dt` (not frame counts), and both thresholds use the `- 1e-9` floating-point
  tolerance the spec calls out (`8 × 0.05 = 0.39999999999999997`). `t15`/`t16` hand-check both the
  0.1 s and 0.05 s step cases land on the exact frame the spec predicts; `t17`–`t20` check
  continuity-breaks-the-count, forgetting after `forget-s`, and re-perceiving inside the forget
  window restarting cleanly.
- **Overtakes are one-sided, head-on still keeps right:** `t39`/`t40` (both directions) confirm
  only the follower ever gets a non-nil `:aim` while the leader's `z` stays within `1e-9` of 0 the
  whole run, and that the leader only notices the follower by `:hear` (with `:glance` true) once
  in range — it never notices by sight since it's facing away. `t41` reproduces `avoid-v0`'s
  original head-on scenario bit-for-bit (same min distance 0.939, same ±z crossing sign) once both
  walkers notice each other at frame 24, confirming perception doesn't change existing avoidance
  behavior once engaged. `t42`'s full estate scenario (2400 frames) checks noticing at frame 8,
  unchanged ping-pong order, and at least one `:glance` frame across the run.
- **No walker names in code:** `grep -rni "un-automate|le-testeur|polybot|astronaut|juniper|flora"`
  across the touched `src/` files is empty; `t42` identifies the estate's two walkers via
  `(:id (first ws0))`/`(:id (second ws0))` rather than hardcoding them, even though the spec only
  required that of `src/`.
- **`public/vendor`/`resources/content` untouched:** `git status --short` on both is empty.
- **Pure, tested:** `senses`, `senses-config`, `forward`, `facing`, `sees?`, `hear-range`,
  `hears?`, `perceive`, `perceive-all`, `noticed` and `cone-points` are all free of three.js,
  atoms, `js/Date` and walker names, and each has direct test coverage; `plan`/`plan-all`/`replan`/
  `advance`'s small diffs (own-speed, `noticed`-filtering, `perceive-all` call) are the only
  changes to existing pure functions, matching spec §3.13 exactly — `follow`, `step`, `hud-text`,
  `steer-command` and all of `pm.avoid` are untouched (confirmed: no diff hunks touch them), and
  `t43` re-checks several of those unchanged surfaces directly.

## Boundary-table compliance

Builder touched exactly: `src/pm/motion.cljs`, `src/pm/bricks.cljs` (`?paths` only — `walker-path-lines!`/
`build-paths!`/`update-paths!`, nothing else in the file changed), `src/pm/three.cljs`
(`set-line-color!` only, additive), `src/pm/scene.cljs` (3 new palette keys only),
`resources/estate.edn` (`:motion :senses`, exactly as specced). Tester touched only
`test/pm/senses_test.cljs`. No off-limits file was edited by this round.

## Findings

None rise above a style nit. `set-line-color!` landed between `aim-bone!` and `tilt-bone!` in
`three.cljs` rather than at the end of the file (where the last two rounds' additions went) —
harmless, just a minor departure from the file's otherwise append-only growth pattern.

**Verdict: pass.**
