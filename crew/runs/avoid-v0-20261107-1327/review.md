# Review: avoid-v0

Gate re-checked against `gate-1.txt`: PASS, 0 warnings, 325 tests / 1628 assertions (up from
260/1426 at the end of `motion-bricks-v0`: +65 tests, +202 assertions — matches `avoid_test.cljs`
adding exactly deftest `t01`–`t65`, nothing removed from `motion_test.cljs`). `test/pm/motion_test.cljs`
is untouched (empty `git diff`, mtime predates this round) and its 58 tests still pass unchanged,
satisfying the "existing tests, including the 59 in `motion_test.cljs`, keep passing unchanged"
contract (that file has 58 `deftest`s; the spec's "59" counts the `deftest` symbol in its
`:require` line too — a loose count, not a real test-count mismatch).

**Scope note — concurrent round, not this one's work.** While reviewing, `public/index.html`,
`public/vendor/three-kit.mjs`, `public/assets/characters/` and `public/vendor/three-addons-r186/`
appeared modified/untracked, alongside new `.crew/quests/characters-v0.md` and
`.crew/rounds/characters-v0.json`. Their mtime is 13:51, after `avoid-v0`'s own gate (13:50:25)
and review step (13:50:42) had already started — a different round (`characters-v0`) is running
concurrently in the shared tree (per `AGENTS.md` §"When the crew works at once"). None of this is
`avoid-v0`'s diff; excluded below. `avoid-v0`'s own boundary table forbids `public/**` regardless.

## Conformance checks

- **Pure planner, tested for every quest case:** `pm.avoid` has no requires and uses only
  `js/Math` (confirmed by reading the file); `pm.motion`'s new code adds no three.js/atoms/`js/Date`.
  Hand-verified three formulas directly against the spec's math, independent of the diff:
  `tangent-aim [0 0] [5 0] 3 :right` → `[5.6 4.2]` (α=asin(3/5), len=√16+3=7, dir=[0.8,0.6]×7) and
  `:left` → `[5.6 -4.2]`; `free-goal [0 0] [5 0] 0.3 [{:at [5.2 0] :r 0.5}]` → `k=-0.2+√1=0.8` →
  `[4.2 0]`. Both match `tangent-aim`/`free-goal`'s code and the test file's expectations exactly.
  `test/pm/avoid_test.cljs` has `t01`–`t65`, one per spec case, covering every "Done when" item
  in the quest: head-on (t31, t60), crossing (t32, t61), static-on-line (t24, t62), right-blocked→left
  (t25, t63), both-blocked→wait→go→give-up (t27–t30), target-inside-footprint (t33, t64), clear-path-no-detour
  (t22), footprint yaw/scale (t01–t04).
- **Keep-right consistency:** `plan`'s side order is `[:left :right]` only when `(:side w)` is
  already `:left`, else `[:right :left]` (`motion.cljs`, §3.6 step 5) — every walker tries its own
  right first by default. Confirmed both at the unit level (t31: head-on `A`/`H` both get
  `:side :right`) and at the scenario level (t60: `first-crossing` asserts `za > zh`, i.e. both
  passed on their own right) — this is what prevents the head-on "dance."
- **Detours respect static footprints:** `tangent-aim`'s candidate is rejected when
  `(a/inside? D r statics)` or `(a/path-block P D ...)` is non-nil before being taken (`motion.cljs`
  `plan` step 5) — a detour point inside or blocked by a static is never chosen.
- **Footprints rotate with `:yaw` and scale:** `a/footprint`'s `rotate` call uses three's
  `rotation.y` convention (`[dx·cosθ+dz·sinθ, −dx·sinθ+dz·cosθ]`), applied before scaling by `s`
  and offsetting by `:at` — matches §1.2 exactly, and `estate.edn`'s Juniper (`:yaw 90`) /
  jar (`:scale 4`, with the hand-explained `0.0875×4=0.35` deviation and its comment) are both
  present as specced.
- **No mirror or walker names in code:** `grep -rni "juniper|flora|un-automate|le-testeur" src/`
  is empty; `pm.avoid/statics` only reads `(:mirrors world)`/`:placed`/`:footprint` generically.
- **`moveTo` only on a real aim change (bricks mode):** the old `new-target?`/`moveTo` call is
  gone from `tick-bricks!`; `apply-command!` only calls `.moveTo`/`.stop` when
  `(m/steer-command (:sent view) w)` returns non-nil, and `steer-command` itself returns `nil`
  unless the resend threshold (`resend-m` = 0.5 m) is exceeded or the command kind changes
  (move↔stop) — confirmed in both `swap-in!` (initial command, stored as `:sent`) and
  `tick-bricks!` (`:sent` only updated `(if cmd (assoc view :sent cmd) view)`).
- **`?paths` is debug-only:** gated behind `(m/paths-flag? search)` at `start!`, builds a separate
  `pm-paths` group of `t/polyline`s computed from existing walker/statics state; nothing in the
  planner or rendering of the real walkers reads back from it.
- **`resources/content` untouched:** confirmed (`git status --short resources/content` empty).

## Boundary-table compliance

Builder touched exactly: `src/pm/avoid.cljs` (new), `src/pm/motion.cljs`, `src/pm/bricks.cljs`,
`src/pm/three.cljs` (42 additive insertions only — `polyline`/`set-polyline!`, nothing else
changed), `src/pm/scene.cljs` (3 new palette keys, nothing else), `resources/estate.edn` (§1.1
exactly, including the documented jar-scale deviation). `src/pm/core.cljs` is untouched (none
needed — `bricks/hud-segment` already threads `:detours` through unchanged). `src/pm/world.cljs`
was touched (mtime inside the build window), satisfying the "touch after editing estate.edn"
instruction. Tester touched only `test/pm/avoid_test.cljs`. `TASKS.md` gained new backlog items
(Michael's ideas from watching `motion-bricks-v0`) with mtime between the blocked `spec` step and
`build`'s start — management activity between steps, not a builder/tester boundary violation, and
`TASKS.md` isn't named in the boundary table either way.

## Findings

None rise above a style nit.

1. `bricks.cljs` `build-paths!`/`update-paths!` recompute all 24-point circle polylines for both
   the walker body and every static every frame the walker moves (via `set-polyline!`), which is
   `?paths`-gated and therefore fine for a debug overlay, but worth knowing if `?paths` performance
   ever matters.

**Verdict: pass.**
