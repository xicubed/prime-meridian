# Review: body-v2 (20261109-1129)

## Conformance summary

Gate: PASS on the first attempt (re-ran `bash .crew/gate.sh` myself: 753 tests / 4368 assertions,
0 warnings, matching `gate-note.md`/`gate-1.txt` exactly).

This round is "data only" per spec §0 — confirmed: `git status` shows no `src/` changes at all,
only `resources/creatures/{horse,dog}.edn`, the two regenerated `creatures/bodies/*.xml`, and the
three test files the tester is allowed to touch. None of the "must not edit" files (`:kp`,
`test/pm/creature_effort_test.cljs`, `test/pm/creature_reflex_test.cljs`, `creatures/foal/*`,
`creatures/reports/*`, `scripts/*`, `package.json`, `public/*`) were touched.

Checks from the task, verified directly:
- **Species data.** `diff resources/creatures/horse.edn <(sed spec §2.1)` and the same for dog:
  **zero diff** — both files are byte-for-byte the spec's "copy verbatim" text, including the 18
  joints' names, parent bodies, axes and ranges exactly as spec §2.3's table lists them (splay
  axes `[1 0 0]`/`[-1 0 0]` by side, swing `[0 1 0]` −60…75°, `spine-bend` `[0 0 1]` −25…25°,
  `spine-twist` `[1 0 0]` −20…20°).
- **Generated MJCF.** `shasum -a 256 creatures/bodies/{horse,dog}.xml` matches the spec's declared
  hashes exactly (`2e3ed43e…`/`0492a913e…`), and — to rule out a hand-edit that happens to match —
  I ran `bb creature-mjcf horse`/`dog` myself just now from the current (unmodified) source and
  diffed the output against the committed files: **identical**, confirming they really were
  produced by the tool from this data, not edited by hand.
- **The root body is still `:torso`.** Both EDN files keep `:torso` as `(first :bodies)` with no
  `:parent`; `:hind` is a new sibling body (`:parent :torso`, `:at [0 0 0]`) that the hind legs
  hang from, exactly as spec §1/§3 describe. Confirmed in the test diff (C13, B3) and by hand
  against the EDN itself.
- **Nothing reads joints by a fixed count or a species name.** No source file in this round
  needed a change (confirmed empty `src/` diff) — which is itself the proof: `pm.creature`,
  `pm.creature-motion` and `pm.creature-spike`'s existing, already-reviewed count-based logic
  (`joints`, `counts`, `reflexes`, `mjcf`, `rest-bottom`, `spawn`, `body-parts`, `stiffness`,
  `limp`, `actions->ctrl`) now runs against 18-joint/13-body data it had never seen and produces
  every number the spec hand-checked (counts `{:nbody 13 :njnt 19 :nu 18 :nq 25 :nv 24 :ngeom
  13}`, masses, MJCF element counts and line contents) — confirmed against the updated C14–C28,
  F5, R1/R2/R5/R8/R9 cases, all of which generalized by `(count …)` rather than a literal `12`/`18`
  anywhere a species name would have had to appear.
- **The old real-MuJoCo checks still run unchanged in intent.** R3, R6, R7 are re-verified
  unchanged (no diff). R5 switched its hard-coded `12`/`19` to `nu`/`(+ 7 nu)`. R9's "not limited"
  loop is `(range 4 18)` (was `(range 4 12)`) — still exactly the 4 neck/head actuators force-
  capped, the other 14 (was 8) not. R10–R13 keep their original assertions and add a parallel
  `:hind` check (capsule-clearance of the new hind body) alongside the existing `:torso` one —
  confirmed none of the original bounds (clears ≥ 2, held-s ≥ 20, peak-force windows, final
  energy) were loosened to accommodate the new body.
- **`:kp` wasn't silently raised.** `horse.edn` still reads `:kp 150` (unchanged from before this
  round); `dog.edn` still `:kp 40`. The spec's and quest's instruction to leave it for Michael was
  followed to the letter.
- **The stand-hold measurement is real** — S1 and S2 genuinely build the standing model at
  `:drop-m 0`, zero every ctrl, step 2500/15000/5000 times, and assert the exact bounds the
  architect's own hand-run produced (horse 0.70≤h≤0.84 & tilt≤20 at 5 s; dog 0.42≤h≤0.445 & tilt≤1;
  `[false false false true]` for horse at kp 150/175/200/225; dog holds at 40) — this is not
  faked or loosened to pass.

## Findings

1. **S1/S2 measure correctly but never report, despite the spec asking for it twice.** Spec §6
   S1 says "Print one line per species, e.g. `stand-hold horse kp 150: torso 0.803 m, tilt 0.2°
   after 5 s`" and S2 says "Print `stand-hold finding: horse needs :kp 225 (has 150); dog holds at
   40`" — and the quest's own "Done when" asks for "the stand-hold measurement above, with its
   numbers in the spec and run log." `grep -n 'console.log\|println' test/pm/creature_mujoco_test.cljs`
   returns **nothing**: `s1-stand-hold-quest-criterion` and `s2-smallest-holding-kp` only assert
   bounds, they never print the measured `h`/`tilt`/`kp` values. The numbers currently live only
   in `spec.md` (the architect's own scratchpad run, not reproduced at test time) and in this
   round's `log.md`/`gate-note.md` only as "PASS" with no figures. If the body or `:kp` changes
   later, a future gate run would silently keep passing or failing without ever telling anyone
   the actual torso height, tilt, or which `:kp` held — exactly the kind of regression this test
   was added to catch early. Low severity (the measurement and its bounds are correct and will
   fail loudly if the body gets weaker), but it's a concrete, checkable miss against the spec's
   explicit instruction and worth a one-line `(js/console.log …)` fix in each test before the
   round's "stand-hold" finding is considered durably recorded anywhere but this spec file.
2. No other findings — every other check (data files, generated XML with independent
   regeneration, the 18-joint table, the kinematic splay/fold/spine-sign hand-numbers in R15–R17,
   mass/length within 5%, and the boundary table) matches the spec exactly on direct inspection.
