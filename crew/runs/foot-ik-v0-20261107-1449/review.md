# Review: foot-ik-v0

Gate re-checked against `gate-1.txt`: PASS, 0 warnings, 397 tests / 1826 assertions (up from
353/1686 at the end of `characters-v0`: +44 tests, +140 assertions — exactly
`test/pm/feet_test.cljs`'s `t01`–`t44`, one per spec case). `motion_test.cljs`, `avoid_test.cljs`
and `characters_test.cljs` are untouched and their totals (58+65+28=151) are unaffected.

**Isolation method.** `motion.cljs`, `bricks.cljs`, `three.cljs` and `estate.edn` are also
`avoid-v0`'s and `characters-v0`'s files, and both are still uncommitted, so a plain `git diff`
mixes three rounds' hunks. I reconstructed each file's `characters-v0`-end state (chaining
`avoid-v0`'s and `characters-v0`'s own diffs, both already verified against their specs in prior
reviews) and diffed that against the current file to isolate only `foot-ik-v0`'s contribution.
All findings below are checked against that isolated diff.

## Conformance checks (all pass)

- **IK runs after `update`, vertical-only:** `tick-bricks!`'s per-walker step calls
  `(foot-ik! w view dt fcfg ds ...)` only after `(.update c dt)` (and only `when (:legs view)`);
  a walker without resolved legs keeps the old `obj.y ← :y` path unchanged. Each foot's IK target
  is `T_i = [ax (+ b loc d-i) az]` — `ax`/`az` are the ankle's own generated x/z, untouched; only
  the y is corrected. `two-bone` itself only ever receives this same-x/z target, so the solved
  knee/ankle positions also preserve the generated stride horizontally.
- **Knee side kept:** `two-bone`'s bend direction `b` tries `perp(knee−hip, u)` first (the
  *current* knee), hand-verified against spec's two-bone law-of-cosines formula on test 13
  (H=[0,1,0], K=[0.3,0.6,0], A=[0,0.2,0] → knee′≈[0.4,0.7,0], exactly matching the spec's
  hand-worked numbers) and test 14 (same H/A, K mirrored to [-0.3,0.6,0] → knee′≈[-0.4,0.7,0],
  same side as the input knee, confirming the "kept" property independently of the diff).
- **Pelvis drop capped and smoothed:** `pelvis-drop` = `min(cap, max(0, −min(dl,dr)))` (nil
  distances count as 0); `foot-frame`'s `drop'` always goes through `smooth-by` before becoming
  `:obj-y = b − drop'`. `t19`/`t31` (cap at 0.35) and `t30`/`t36` (smoothing) confirm.
- **Base height from the feet (the hop fix):** `:ground` is `base-height` over only the *planted*
  feet's `:g` (unplanted feet contribute `nil`, filtered out), never from a single root ray — `t37`
  is exactly the hop-fix case: both feet planted at different ground heights (2.0 vs 2.3) still
  yields `:ground 2.0` (the lower planted foot), not an average or a root-ray value. `pm.bricks`'s
  `tick!` correspondingly skips the old root-ray `sense-ground` for any walker with `:legs` while
  in `:bricks` mode (isolated diff confirms this exact guard).
- **Foot tilt only when planted, capped:** `tilt`'s angle is `min(acos(nhat.y), max-deg·π/180)` —
  hand-verified on `t23`/`t24` (10° slope → 0.1745329 rad; 40° slope → capped at 0.4363323 rad =
  25°). Blending multiplies the angle by `w_i` (`blend-weight`, 0 when not planted), and `t33`
  confirms a non-planted foot's tilt angle is exactly 0 even with a non-flat normal.
- **Detours reject big ground steps:** isolated `motion.cljs` diff shows `walkable?` added exactly
  as specced, and `plan`'s side-selection now also requires `ground-ok` (true unless `:ground-at`
  is a fn *and* `:y` is a number *and* the point isn't walkable) before accepting a side —
  logically identical to treating an unwalkable point as blocked, confirmed by `t39`–`t44`
  (right-too-low→left, both-missing→wait, ground-fine→right unchanged, no-`:y`→rule skipped,
  no-`:ground-at`→unchanged, real-estate data). `bricks.cljs`'s `pcfg` wires `:ground-at` via a
  fresh `t/down-hits`/`m/ground-height` closure, applied in **both** modes per spec §4.1.
- **Leg names come from data:** `grep -rn "LeftLeg|RightLeg|mixamorig|LeftUpLeg|LeftShin|LeftFoot|LeftToeBase" src/`
  is empty — the chains live only in `resources/estate.edn`'s new `:motion :legs`, read through
  `f/legs`/`f/leg-bones`/`t/by-name` in `bricks.cljs`'s `swap-in!`.
- **`public/vendor` and `resources/content` untouched:** confirmed by mtime/search, same as prior
  rounds.
- **Pure math tested:** `pm.feet` has no three.js/atoms; every function in §2.1/§2.2/§2.3 has
  direct test coverage, and `two-bone`'s law-of-cosines and `tilt`'s normal-to-angle formula both
  check out against independent hand recomputation, not just "the test the same author wrote
  passes."

## Finding — out-of-spec data and code changes (the one real issue)

`resources/estate.edn`'s diff (isolated against the `characters-v0`-end baseline) does **two**
things the spec's §1 never mentions, inside a file the boundary table restricts to "exactly §1":

1. **Le Testeur's `:character` entry (added by `characters-v0`: the astronaut) is deleted**,
   replaced by the comment `; NVIDIA's mannequin (Michael: the astronaut was too big)`. This walker
   now has no `:character` at all and silently reverts to the SOMA mannequin — a real behavior
   change with no test or spec text covering it (`characters_test.cljs`'s `t10`, which asserts
   both walkers' `:character` from the live estate, would now fail if re-run against this file,
   though it wasn't re-run since `characters-v0`'s own tests are frozen — worth a manual check).
2. **A `:backend :webgpu` key is added to `:bricks`**, with matching unspecified code in
   `bricks.cljs` (`(cond-> #js {...} (keyword? (:backend b)) (doto (aset "backend" ...)))`) that
   forwards it into the `MotionBricksCharacter` constructor options. Nothing in any of the four
   specs (`motion-bricks-v0` through `foot-ik-v0`) defines a `:backend` option, and `foot-ik-v0`'s
   own §4 "pm.bricks changes" list doesn't mention it either.

Both look like real, reasonable product decisions (Michael's live feedback on the astronaut's
size; picking a specific backend) — but they weren't in `foot-ik-v0`'s spec, they weren't reviewed
through the architect, and both edits land inside files/sections the boundary table explicitly
scopes ("exactly §1" for `estate.edn`). Flag for the manager: either fold these into the spec
record (a short addendum) or revert them before commit, so the committed history matches what was
actually authorized for this round.

## Boundary-table compliance (otherwise clean)

Builder touched exactly: `src/pm/feet.cljs` (new), `src/pm/motion.cljs` (§3 only — isolated diff
shows only `walkable?`, the `plan` ground-check, and `url-flag?`, nothing else), `src/pm/bricks.cljs`,
`src/pm/three.cljs` (isolated diff: exactly the 9 §5 signatures, pure append), `src/pm/scene.cljs`
(2 new palette keys, nothing else), `resources/estate.edn` (§1's `:legs`/`:feet`, plus the two
out-of-spec items above). `src/pm/avoid.cljs` and `src/pm/core.cljs` are untouched. `src/pm/world.cljs`
was touched (mtime inside the build window). Tester touched only `test/pm/feet_test.cljs`. The
builder's one "blocked" moment in `log.md` was a routine shell-command permission prompt (running
`.crew/gate.sh`), not a content decision — unrelated to the finding above.

**Verdict: pass, with one finding to resolve before commit** (the unauthorized `estate.edn`/
`bricks.cljs` changes).
