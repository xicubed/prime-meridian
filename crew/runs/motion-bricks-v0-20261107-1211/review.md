# Review: motion-bricks-v0

Gate re-checked against `gate-1.txt`: PASS, 0 warnings, 260 tests / 1426 assertions. Spec's 58
numbered test cases are all present in `test/pm/motion_test.cljs` (t01–t58) and hand-traced
against a sample (t03/t04/t05 estate data, t18–t22 kinematic arrival, t30/t31 ping-pong order,
t39–t43 ground filtering, t51–t57 HUD text) — implementation matches the spec's formulas, not
just "tests pass."

**Scope note:** `public/index.html` and `public/vendor/three-motionbricks-0.2.0/` show up as
changed/untracked in `git status`, but both have mtime 12:09:42, before this round's `spec` step
started (12:11:36) — pre-existing infra (the import map and Hal's vendored library), not touched
by this round. The builder's own reply flags the same thing. Excluded from the diff below.

## Conformance checks

- **`?bricks`-gated, post-kit-ready load:** `bricks/start!` only imports when
  `(m/bricks-flag? ...)` is true and `(:bricks cfg)` is non-nil (`bricks.cljs:59`), and is wired
  into `pm.core` as the `kit/load!` ready-callback (`core.cljs:407,415`), not called from the
  scaffold path. Confirmed.
- **One shared model:** `chars` built once per walker; only the first calls `.load`, the rest
  `.loadFrom first-c` via `Promise.all` (`bricks.cljs:78-82`). Confirmed.
- **Failure → kinematic + reason:** `onError` wired per-character, the load promise chain has a
  `.catch`, the synchronous path has `try/catch`, and `tick-bricks!` wraps its body in
  `try/catch` — all route to `fallback!`, which is idempotent (`:failed`/`:kinematic` guard),
  hides characters, shows figures, and sets `{:mode :failed :reason (m/short-reason ...)}`.
  Confirmed.
- **No bare `import()`:** only occurrence in `src/` is the `js/Function.` loader
  (`bricks.cljs:63`); grep for `import(` elsewhere in `src/` is empty. Confirmed.
- **Three.js discipline:** `bricks.cljs` calls three.js only through `pm.three` (`t/group`,
  `t/add!`, `t/stand-in`, `t/label`, `t/set-visible!`, `t/set-pos!`, `t/set-yaw!`, `t/remove!`,
  `t/find-meshes`, `t/down-hits`, `t/vec3`) or direct properties on the library's own
  `MotionBricksCharacter`/`.object` (`.getPosition`, `.-idle`, `.moveTo`, `.update`,
  `c.object.position.y` — matching §3's explicit instruction to set that one directly). The new
  `pm.three` functions (§4) are additive only — `git diff --stat` on `three.cljs` shows
  `1 file changed, 54 insertions(+)`, no deletions — and each is `^js`-hinted. Confirmed.
- **`public/vendor` untouched:** confirmed by mtime (above).
- **No walker names in code:** `grep -rn "Un Automate|Le Testeur|un-automate-monde|le-testeur"
  src/` is empty. The strings only appear in `resources/estate.edn` (data) and
  `test/pm/motion_test.cljs` (asserting against that data), which is expected.
- **Pure logic tested:** `pm.motion` has no three.js, atoms, or `js/Date`; its only JS is
  `js/Math`/`js/URLSearchParams` as specced. All 58 spec test cases are present and pass.

## Boundary-table compliance

Builder touched exactly: `src/pm/motion.cljs`, `src/pm/bricks.cljs` (both new), `src/pm/three.cljs`
(additive block only), `src/pm/scene.cljs` (two palette keys, nothing else — diff confirms no
other hunk), `src/pm/core.cljs` (require + 3 call sites, matching §6 exactly), `resources/estate.edn`
(`:motion`/`:walkers` added verbatim from §1.1). Tester touched only `test/pm/motion_test.cljs`.
No off-limits file was edited by this round.

## Findings

None rise above a style nit. Two small observations, not blocking:

1. `bricks.cljs:149` — `tick-bricks!` sets `(or y 0)` into the anchor's position when `:y` is
   nil, same pattern as kinematic, but by the time bricks mode is active `:y` should already be
   settled from the kinematic phase; harmless defensive code, no fix needed.
2. The `!state` defonce atom carries `:root`/`:started?` beyond the four keys §3 lists — a
   reasonable, undocumented-but-harmless extension for the hot-reload no-op check; not a spec
   deviation since §3 says the atom "holds" that shape, not that it's exhaustive.

**Verdict: pass.**
