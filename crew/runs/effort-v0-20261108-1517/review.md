# Review: effort-v0 (20261108-1517)

## Conformance summary

This supersedes my prior review of this round (below, for context: it found the "limp when not
trying" half of the spec entirely unbuilt despite `GATE: PASS`). Per `log.md`, Hal re-prompted
the builder and tester with the exact missing sections/cases and resumed from `gate`; I
re-reviewed the full diff from scratch against the current `spec.md` rather than trusting that
the resolution happened.

Gate: PASS (re-ran `bash .crew/gate.sh` myself: 742 tests / 4109 assertions, 0 warnings, matching
`gate-note.md`/`gate-1.txt` exactly — up from 735/4078 before the fix, consistent with 7 more
`deftest`s). Counted `deftest` forms directly: `creature_effort_test.cljs` has 21 (E1–E9=9,
T1–T4=4, A1–A3=3, L1=1, H1=1, **K1–K3=3**), `creature_test.cljs` has 48 (27 existing + C28–C40=13
+ F1–F6=6 + **G1–G2=2**), `creature_mujoco_test.cljs` has 13 (R1–R8=8 + R9–R13=5, **including
R12–R13**) — 34 new cases total, exactly matching spec §7's "Count: 34 new numbered cases."
Every case the prior review flagged as missing (G1–G2, K1–K3, R12–R13) is now present.

Boundary table (spec §2) unchanged from the prior review: builder touched exactly
`src/pm/creature.cljc`, `src/pm/creature_motion.cljs`, `src/pm/creature_spike.cljs`, and the two
`resources/creatures/*.edn` files; tester touched the required test files. None of the "nobody
edits" files appear in `git status`.

Checks from the task, re-verified against the current diff:
- **All effort numbers come from species data; no species/body names in code.**
  `grep -rniE 'horse|dog|pony|juniper|neck|head-' src/pm/creature*.cljs src/pm/creature.cljc`
  still shows only the same two pre-existing hits as head-reflex-v0 (`head-up`, the generic
  sensing function, and `:head-level`, the reflex-kind literal) — both specified verbatim by an
  earlier round's spec and already flagged in that round's own review as an architect-naming
  issue, not something introduced here. `:rest-kp`, `:kick-above`, `:strength`, `:torque-nm`, and
  every other effort/limp number live only in `resources/creatures/{horse,dog}.edn`; the code
  reads them generically (`rest-fraction`, `kick-above`, `force-limits` in `mjcf`).
- **A reflex without `:effort` is byte-for-byte the old behavior.** `reflexes` only adds the
  `:effort` key (with `:rest-frac` folded in) via `cond->` when `(:effort r)` is truthy;
  `reflex-controller` only adds `:efforts` when `(some :effort reflexes)`; `act`'s `:reflex`
  branch only re-attaches `:efforts` to `ctrl'` `(if (contains? ctrl :efforts) ...)`, so a
  no-effort reflex's controller map is structurally identical to before. Confirmed against F6
  (0 `forcerange=` in the no-effort MJCF) and A2 (`(cm/reflex-controller inner [r-x11] 5)` has no
  `:efforts` key and equals the 5-less call).
- **The energy state machine (`effort-step`) is pure, takes time as an argument, and has real
  hysteresis.** No atoms, no `js/Date`; `dt` is an explicit argument and a non-finite/non-positive
  `dt` returns the state unchanged (E8). Mode flips only `:active`→`:resting` below `:low` and
  `:resting`→`:active` above `:high` (with a 1e-6 epsilon noted in a comment as float-noise
  tolerance, not a behavioral loosening — the spec itself flags the exact-boundary case as
  unassertable for the same reason); between the thresholds nothing changes, confirmed by E5's
  alternating-amount case staying `:active` throughout.
- **Actuator force limits are generated from data.** `mjcf`'s `force-limits` map is built purely
  from each reflex's own `:effort {:strength :torque-nm}` (`fmt (* strength torque-nm)`) and
  applied only to that reflex's own `:acts`; confirmed against F5/R9 (horse 35 N·m × 4 actuators,
  dog 9 N·m × 4, `actuator_forcelimited` 1 only on those 4, 0 on the rest, in real MuJoCo).
- **`mj_jacBody` buffers are still allocated once.** This round's diff to `creature_spike.cljs`
  only added `:rest-frac`/`:kick` to `start!`'s stored state, the 3-arity
  `reflex-controller`/seed call, `:forces` to `obs`, and the `limp`/`stiffness` wrap around the
  ctrl-vals write in `tick!` — the `:jacs` `DoubleBuffer` allocation block (one pair per reflex,
  created once in `start!`, reused every step) is untouched.
- **Limp when not trying is now actually implemented**, and matches the spec's formulas on
  inspection, not just by name:
  - `limp` (`ctrl[k] = q + σ·(target−q)`) and `stiffness` (`:twitch`→1 for a kick else
    `rest-frac`; `:reflex`→inner's stiffness with every reflex `:acts` index forced to 1;
    anything else→`rest-frac` everywhere) match §5.4 exactly, confirmed against K1/K2.
  - The reflex layer's step-5 blend (`final = level·T + (1−level)·(q + σ·(0−q))`, with `σ =
    (:rest-frac e)`) is now present in `reflex-actions`, replacing the prior fix's incomplete
    "fold level into gain" shortcut — confirmed against K3 (`0.3·0.1 + 0.7·0.09 = 0.093`, the
    exact post-step-level blend the spec requires).
  - `creature_spike.cljs`'s `tick!` now writes `(cm/limp tg (.-qpos data) (cm/stiffness c2
    rest-frac kick))` instead of the raw `actions->ctrl` output, and `start!` stores
    `:rest-frac`/`:kick` from `(c/rest-fraction sp)`/`(c/kick-above sp)` — matching §6 exactly.
- **The real-MuJoCo test genuinely shows lift/rest/lift for the horse and a long hold for the
  dog, now with the torso-on-the-ground assertion the spec actually asks for.** R10 (horse)
  tracks `clears ≥ 2`, `:resting` reached and left again, peak force within [34.9, 35+1e-6], and
  — newly present — `torso-ok?` (capsule-clearance of the torso body ≤ 0.02 m for every step with
  t ≥ 3 s, using real `mj_step` data, not asserted in the pre-fix version). R11 (dog) asserts
  `held-s ≥ 20`, never resting, final energy > 0.8, peak force ≤ 9+1e-6, and the same torso
  check. R12 drops both species upright with legs twitching (kicks included) through the full
  `limp`/`stiffness` pipeline and checks the torso stays ≤ 0.02 m for t ≥ 5 s. R13 contrasts the
  dog at full stiffness (torso > 0.3 m, still standing) against rest stiffness (≤ 0.02 m) — the
  "table it replaces" check. All of this runs against the real `@mujoco/mujoco` package via the
  existing `require()`-based `import-mj`, not mocked.

## Findings

No ranked findings rise above a style nit. Every check in the task's list, and every spec
requirement the prior review found missing, now matches on direct inspection of the code and the
real-MuJoCo test results (re-run via the gate), not merely on the gate reporting PASS.

---

## Appendix: prior review (superseded)

The round was initially reviewed before this fix; that review's findings are preserved here for
the record since they drove the fix that produced the code now re-reviewed above.

1. **The round's headline feature — "limp when not trying" — is entirely missing, and the test
   suite was quietly scoped down to avoid exposing it; `GATE: PASS` does not mean this round is
   done.** Per `log.md`'s manager notes, Michael's mid-round ask ("space under his ribcage; all
   the joints need to touch the floor when prone, not just the feet") was important enough that
   Hal stopped the round driver, amended the quest, and had the architect fold it into the spec
   before resuming `build` — yet none of it reached the delivered code (details: no
   `:rest-kp`/`:kick-above` in the species data, no validation/`rest-fraction`/`kick-above`/
   `limp`/`stiffness` functions, `creature_spike.cljs`'s `tick!` unwrapped, and the tester's own
   reply counting only 27 of the spec's 34 new cases while claiming "nothing to change").
2. Everything else delivered at that time (energy/tremor/force-limit machinery) matched the spec
   on inspection.

This has now been fully resolved — see the conformance summary above.
