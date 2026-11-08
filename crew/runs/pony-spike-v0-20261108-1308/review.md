# Review: pony-spike-v0 (20261108-1308)

## Conformance summary

Gate: FAILED twice (gate-1, gate-2: 5 failures each) during two fix rounds, then PASS (gate-3,
674 tests / 3760 assertions, 0 warnings). Per `log.md`'s manager notes, both fixes were real and
reasonable: fix round 1 addressed build-script issues; fix round 2 replaced the tester's dynamic
`import()` in `test/pm/creature_mujoco_test.cljs` with `js/require` because shadow-cljs's
`:node-test` vm has no dynamic-import callback (`ERR_VM_DYNAMIC_IMPORT_CALLBACK_MISSING`) —
a documented, necessary deviation from the spec's literal import line, not a defect. `gate-3.txt`
(the actual `bash .crew/gate.sh` transcript) matches `gate-note.md`'s claim exactly: PASS, 0
warnings, 674 tests / 3760 assertions, including `Testing pm.creature-mujoco-test` in the run.

Boundary table (spec §2): builder touched exactly the assigned new/changed files
(`src/pm/creature.cljc`, `src/pm/creature_motion.cljs`, `src/pm/species.cljs`,
`src/pm/creature_spike.cljs`, `src/pm/three.cljs` [`set-quat!` only — confirmed by diff, no other
function touched], `src/pm/core.cljs` [exactly the 3 wiring lines in §6.3], the two new
`resources/creatures/*.edn` files verbatim, `resources/estate.edn` [only the new `:creature-spike`
key], `bb.edn` [only the new task], `scripts/creature_mjcf.clj`). Tester touched only the three
new test files. None of the "nobody edits" files (`package.json`, `public/vendor/`,
`public/index.html`, `resources/content/`, `creatures/`, `docs/GDD.md`, `assets/private/`,
`public/kit/`) appear in `git status`.

Checks from the task, verified directly:
- **Species names confined to data.** `grep -rniE 'horse|dog|pony|juniper' src/pm/creature.cljc
  src/pm/creature_motion.cljs src/pm/creature_spike.cljs src/pm/species.cljs` (this round's own
  new files): only `species.cljs`'s two `rc/inline` paths, exactly as spec'd ("the one place in
  src/ allowed to name a species"). See Finding 1 for the one exception the quest's own broader
  `grep -rniE … src/` gate catches.
- **THREE only through `pm.three`.** `src/pm/creature_spike.cljs` draws exclusively via
  `t/group`, `t/add!`, `t/stand-in`, `t/set-pos!`, `t/set-quat!` — no bare `THREE/...` call
  anywhere in the new interop file. `pm.three`'s diff adds only `set-quat!`, matching the
  boundary table's "add `set-quat!` (only)".
- **MuJoCo objects deleted when replaced/failed.** `fail!` deletes `data` then `model` (each in
  its own `try`/`catch`, so one failing delete doesn't block the other) and detaches `root` from
  its parent, on every failure path (`start!`'s `.catch`, `start!`'s own `try`/`catch`, and
  `tick!`'s `try`/`catch`). v0 never replaces a running sim (`defonce` + `start!`'s no-op guard),
  so `fail!` is the only delete path — matches spec exactly, and there is no path that drops a
  model/data reference without deleting it.
- **Fixed-step cap.** `cm/fixed-steps` caps `steps` at `max-steps` (25) via `(min n cap)` and
  returns the drop count separately; `creature-spike/tick!` loops exactly `steps` times, so a
  stalled frame can never run an unbounded number of `mj_step` calls. Hand-verified against the
  M3 table in both `creature_motion.cljs` and its test.
- **z-up ↔ y-up in one place.** `pm.creature/mj->three`, `three->mj`, `mj-quat->three` are the
  only axis-swap code in the diff; `creature_motion.cljs` and `creature_spike.cljs` both call
  into `pm.creature` for every conversion (`body-pose`, `body-part`) rather than re-deriving it.
- **`?pony` leaves the normal game untouched.** `creature-spike/start!` is a no-op unless
  `spike-request` (built from the URL search string and the estate's `:creature-spike` config)
  returns non-nil; `pm.core`'s only three call sites are gated the same way the spec requires
  (`start-kit!` only — confirmed absent from `start-scaffold!` — plus the `kit/handle` guards on
  `tick!` and the HUD segment). Without `?pony`, nothing is imported, built, or drawn.
- **Pure logic tested.** `test/pm/creature_test.cljs` (C1–C27) and
  `test/pm/creature_motion_test.cljs` (M1–M15) hand-traced against the spec's worked numbers
  (masses, DFS order, frame conversion, twitch PRNG sequence, fixed-step table, stats) — all 42
  non-MuJoCo cases match.
- **The real-MuJoCo test really compiles both bodies.** `test/pm/creature_mujoco_test.cljs`
  `require()`s the actual `@mujoco/mujoco/mujoco.js` package (not a stub), calls
  `.from_xml_string`/`new MjData`/`.mj_step`/`.delete` for real, and R1/R2/R3/R5 each exercise
  both `horse` and `dog` (R4 exercises `dog`'s yaw path) with assertions on real model/data
  fields (`nbody`, `body_mass`, `jnt_range`, `qpos`, `xpos`) — not mocked, and it ran and passed
  as part of gate-3 (`Testing pm.creature-mujoco-test` in the gate output).
- **No live interaction.** Replies show only local tool-permission screens (confirmed by `grep`
  across the new source/test/script files for `rm -rf`, `find /`, `exec`/process-spawn patterns —
  no hits); no network call or service start appears anywhere in the delivered diff.

## Findings

1. The quest's own literal "Done when" check — `grep -rniE 'horse|dog|pony|juniper' src/` matches
   only `src/pm/species.cljs` — currently fails: `src/pm/overlay.cljs:91` has had the phrase "a
   horse fed with a key" in a docstring since an earlier, unrelated round (mirrors/`play-of`,
   predates this round's `git status`). It's not a MuJoCo/species-pipeline reference and not
   touched by this round, but the quest's exact, checkable gate line doesn't hold as written.
   Fix: reword that one docstring phrase (e.g. "a sample creature fed with a key") so the literal
   grep gate passes, or amend the quest's wording to scope the check to the new files.
2. `src/pm/creature_spike.cljs:35-42` gives `start!` an unspecified 3-arity
   (`[h world load-mujoco]`) with a `default-loader` var, beyond the spec's single binding
   2-arity signature (§6.2: "`(start! h world)`"). It's unused by `pm.core`'s wiring and by any
   test in this diff, and is harmless/forward-looking (a seam for future test injection), but it
   is scope the spec didn't ask for in a file the spec otherwise pins exactly. Low severity —
   note for the architect to either bless the extra arity in a follow-up spec or have it trimmed.
3. Per `log.md`'s manager notes, Hal declined a builder-issued `cd /tmp && rm -rf ... && unzip`
   against the shadow-cljs jar and noted an auto-approval script had let a whole-disk `find /`
   through (read-only) before adding it to the stop list. Neither command reached the delivered
   diff (confirmed by grep above), so this is a process note rather than a code finding: it's
   worth the manager double-checking the approval script's stop-list update actually landed
   outside this repo, since the same gap could recur in a future round.

No security- or correctness-relevant defects found in the delivered code itself; the two items
above are a documentation/gate-text mismatch and a minor unspecified-but-harmless API surface.
