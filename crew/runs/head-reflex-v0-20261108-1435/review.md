# Review: head-reflex-v0 (20261108-1435)

## Conformance summary

Gate: PASS on the first attempt (704 tests / 3923 assertions, 0 warnings). Re-ran `bash
.crew/gate.sh` myself: identical result, matches `gate-note.md`/`gate-1.txt`.

Boundary table (spec §2): builder touched exactly `src/pm/creature.cljc`,
`src/pm/creature_motion.cljs`, `src/pm/creature_spike.cljs`, and the two
`resources/creatures/*.edn` files (verbatim from spec §3, diffed and confirmed character-for-
character). Tester touched the required updates (C6/C9/C14/C15/C20/C21, M12) plus new C28–C40,
X1–X14, R6–R8 in the four test files, and nothing else. None of the "nobody edits" files
(`package.json`, `public/vendor/`, `creatures/`, `docs/GDD.md`, `assets/private/`, `public/kit/`,
`pm.core`, `pm.three`, `pm.species`, the estate, `bb.edn`) appear in `git status`.

Note on scope: `log.md`'s manager notes record that, before this round's build step, Michael
asked the builder directly to fix a one-frame "creature pops in at the kit origin" visual glitch
in `creature_spike.cljs`'s `start!` (position every group from `mj_forward`'s pose before adding
the root to the scene) — explicitly "out of the round's steps by design... rides with this
round." That diff is in `src/pm/creature_spike.cljs` alongside this round's own reflex wiring; it
reuses the same `mj_forward` call the spec requires anyway and doesn't touch anything the spec
assigns elsewhere, so it isn't a boundary violation, just worth naming so it isn't mistaken for
unreviewed scope creep.

Checks from the task, verified directly:
- **Reflex math is pure and matches the spec's formula.** Hand-traced
  `src/pm/creature_motion.cljs`'s `reflex-targets` against spec §5.2 term by term: `e_rot = [uy,
  -ux, 0]`, `e_lift = max(0, lift-m - clearance)`, `g = Σ jacr[row·nv+d]·e_rot[row] +
  w-lift·jacp[2·nv+d]·e_lift`, `target = clamp(q + strength·gain·g, lo, hi)` — exact. `act`'s new
  `:reflex` branch matches the 5-step algorithm in §5.3 (inner runs first and unconditionally,
  `t` advances only on a finite positive `dt`, `obs` nil makes the layer transparent, each
  reflex's `:acts` indices get overwritten in `a'`). `target->action`, `reflex-strength`,
  `head-up`, `tilt-deg`, `capsule-clearance`, `sense` all match their formulas exactly (cross-
  checked against the X1–X14 hand-built test cases, which match the spec's worked numbers).
- **Reflex joints really leave the twitch noise.** In `act`'s `:reflex` branch, `inner'` (the
  stepped twitch controller) still advances every channel's RNG stream unconditionally — so a
  seed keeps replaying the same legs regardless of which joints a reflex claims — but the final
  action vector `a'` overwrites exactly the reflex's own `:acts` indices with
  `target->action` output, discarding the twitch value at those positions. Confirmed by X12
  (`creature_reflex_test.cljs`): the non-reflex actuators' actions equal the plain twitch step's
  output exactly, the reflex actuator's action is the computed target, and `:inner` still equals
  `(twitch-step tw dt)`.
- **`mj_jacBody` arrays allocated once, not per step.** In `creature_spike.cljs`'s `start!`, one
  `[jp jr]` pair of `DoubleBuffer`s (sized `3·nv`) is created per reflex and stored in `:jacs`;
  `tick!`'s per-step loop reuses the same buffer objects every iteration (only `.GetView` is
  called fresh each step, per the spec's own warning about not holding a view across frames —
  the buffers themselves are not reallocated).
- **MuJoCo objects still freed on failure.** `fail!` now deletes every `[jp jr]` pair in `:jacs`
  (each delete in its own `try`/`catch`) before `data` then `model`, and drops `:jacs` from
  state alongside `:model`/`:data` — matching spec §6 exactly, and consistent with
  pony-spike-v0's existing per-object `try`/`catch` pattern.
- **`mj_forward` called exactly once**, right after `new MjData`, before any stepping or
  Jacobian call — confirmed single call site in `start!`.
- **The real-MuJoCo test really drops each species on its side and checks clearance/up-axis.**
  `test/pm/creature_mujoco_test.cljs`'s R6 (`run-reflex-on`) builds a real MJCF model with
  `:roll 90` and `:roll -90` for both horse and dog, runs 1500 real `mj_step`s with the actual
  reflex controller (`cm/reflex-controller` wrapping a `:still` inner, legs forced to 0 via the
  `acts` set) and a real per-step `mj_jacBody` call into pre-allocated buffers, then asserts
  `clearance ≥ 0.7·lift-m` and `tilt-deg ≤ 20°` with finite qpos — for all 4 (species × roll-sign)
  combinations. R7 repeats the drop with every actuator held at 0 (no reflex) and asserts the
  opposite (`tilt > 20°`, `clearance < threshold`). R8 hand-verifies the Jacobian columns against
  the spec's §7.3 table for both species and re-pins the `Float64Array`-stays-zero trap. None of
  this is mocked — `import-mj` `require()`s the real `@mujoco/mujoco` package, as in pony-spike-v0.
- **`?pony` still leaves the normal game untouched.** No changes to `pm.core`, and
  `creature-spike/start!`'s flag-gated no-op guard is untouched by this round's diff (only its
  body past the guard changed); the game's own placement still carries no `:roll`, so `spawn`'s
  roll defaults to 0 and reproduces the pre-reflex drop exactly (confirmed by C40: `:roll 0` or
  absent still gives `pos="4.5 8 3.03" quat="1 0 0 0"`, byte-identical to pony-spike-v0).
- **Validation order** (§4.2) re-derived by hand from `problems`: top-level P1–P5, P6, then per
  body P7–P13 followed by the combined P14/P16/P17/P18 joint check (mutually exclusive by
  construction, so folding them into one `joint-problems` helper is behaviorally identical to
  checking them as separate ordered branches), then P19 (duplicate joint names) across all
  bodies, then P15 ("needs at least one joint"), then Q1–Q7 per reflex in the spec's exact
  sub-order. Hand-traced against C29's two-problem case (a joint missing `:name` triggers both
  its own P18 text and a knock-on Q5 "unknown joint" from the reflex that referenced it) — matches.

## Findings

1. The quest's and spec's own literal "Done when" grep — `grep -rniE
   'horse|dog|pony|juniper|neck|head-' src/pm/creature*.cljs src/pm/creature.cljc` should have
   "no hits" — actually has two: `src/pm/creature_motion.cljs`'s function name `head-up` (used
   generically on *any* body id, not just `:head`, per its own docstring: "the body's local up
   axis in world frame") and `src/pm/creature.cljc`'s literal `:head-level` (the reflex `:kind`
   tag, compared in the Q2 validation check). Both names are specified verbatim and bindingly by
   this round's own spec (§5.1's `(head-up xmat id up)` signature; §3.1/§4.2's `:kind
   :head-level`), so the builder and tester correctly implemented what was asked — this is a
   spec/gate-text inconsistency from the architect, not an implementation defect. It's cosmetic
   (a reflex *kind* tag and a generic sensing helper's name, not an actual per-species body
   reference), but it means the stated gate line is false as written. Flag to the architect: either
   rename `head-up`→e.g. `body-up` and the reflex kind tag to something generic (`:level` /
   `:balance`), or narrow the "Done when" grep to exclude the reflex-kind keyword and sensing
   helper the spec itself introduces.
2. No other defects found. The out-of-band `start!` visual fix noted in `log.md` is legitimate
   and correctly scoped (reuses this round's own `mj_forward` call, touches nothing the spec
   assigns elsewhere) — noted above for traceability, not as a finding.
