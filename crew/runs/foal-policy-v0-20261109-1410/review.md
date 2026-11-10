# Review: foal-policy-v0 (20261109-1410)

## Conformance summary

Gate: FAIL once (gate-1: 4 failures), then PASS (gate-2: 782 tests / 4676 assertions, 0 warnings
— re-ran `.crew/gate.sh` myself and got the identical total). Note: `gate.sh` only `tail -40`s the
node test output for the transcript, so `gate-1.txt`/`gate-2.txt` (and my own fresh run) never
print "Testing pm.creature-brain-test"/"...-mujoco-test" even though both genuinely ran — I
confirmed this by compiling and running `out/test.js` directly, which does print them, with the
same final counts; this is pre-existing `gate.sh` behavior, not a round defect.

Boundary table (spec §5): builder touched exactly `resources/creatures/horse.edn` (the `:brain`
block only — diffed byte-for-byte against spec §1, identical), `src/pm/creature.cljc` (`brain` +
`brain-problems`), new `src/pm/creature_brain.cljs`, `src/pm/creature_spike.cljs`, `src/pm/ui.cljs`,
`public/index.html`; tester touched the two new test files plus `creature_test.cljs` (V1–V3 only,
confirmed via diff — no existing case edited). None of the "not" files (`dog.edn`,
`creatures/bodies/*.xml`, `public/creatures/horse/*`, `creatures/**/*.py`, `creature_motion.cljs`)
were touched.

Checks from the task, verified directly:
- **Obs/action math matches `policy.json` and the reference exactly.** `observation`,
  `action->ctrl`, `quat->xmat`, `gravity-b`, `quat-integrate` and `drop-pose` in
  `src/pm/creature_brain.cljs` were hand-traced term by term against spec §3.3–§3.6's formulas —
  all exact. More importantly, the tests don't just assert against the spec's numbers: B4 parses
  the *real* `public/creatures/horse/policy.json`; B10/B11 run the observation/action functions
  against *all 16* real samples in `public/creatures/horse/policy_reference.json` (both ages,
  drop/chest/standing starts) read via `fs`, not a hand-typed fixture, and assert the spec's
  tolerances (1e-5 obs, 1e-6 action); R2/R3 feed those same real reference samples through the
  *real* onnxruntime-web (`js/require`, not a mock) and check `onnx_action` parity (1e-4); R5/R6
  replay the reference's `fixed_chest_rollout_3s_cpu` trace through real MuJoCo + real ONNX and
  track its height curve (1 cm / 1 mm as spec splits by formula). This is about as close to "wired
  to the real artifacts" as a test suite gets.
- **The control schedule can't drift with frame pacing — confirmed in real end-to-end use,
  with one real but inert deviation in the pure layer (see Finding 1).** `plan`, `add-owed`,
  `schedule-init`, `restart` match spec §3.5 exactly, hand-traced against B12/B13's worked states.
  R9 drives the schedule through *real* async MuJoCo+ONNX inference under three different frame
  patterns (`[25]`, `[8 9 7 25 1 0 3]`, `[1]`) plus the plain loop, and asserts the 150 recorded
  observation vectors are bit-identical across all four and that nothing was dropped — this is the
  test that actually proves the quest's "frame pacing must never change the schedule" requirement,
  and it passes for real, correctly-threaded usage.
- **Age 0 doesn't stand, 9499 does, in the real-MuJoCo test.** R7 (age 9499) asserts `h > 0.65`
  and `up > 0.9` for every one of the last 50 control steps and at the final step, against the
  *real* onnxruntime-web + MuJoCo rollout from the reference chest start. R8 (age 0) asserts the
  opposite: never both conditions hold, and the final state is clearly down (`h < 0.4`, `up <
  0.5`). R10 extends this to 10 random east-bank drops per age (not just the one reference start):
  all 10 stand at age 9499, 0 of 3 stand at age 0.
- **The species without `:brain` is untouched.** `dog.edn` has no diff. R11 asserts
  `(c/problems dog)` is `[]` and `(cb/brain-request dog "?pony=dog")` is `nil`. In
  `creature_spike.cljs`, `(if (= :policy (:kind br)) (start-policy! …) (start-twitch! …))` routes
  dog (whose `br` is always `nil`) into `start-twitch!`, which is the pre-existing twitch/reflex
  code moved verbatim (confirmed by diff: the only change inside it is `:brain br` added to the
  status map, and `cb/hud-segment nil` is `""`, so the HUD text is unaffected) — matches "today's
  code runs byte-for-byte as now" exactly.
- **No species names in code.** `grep -rniE 'horse|dog|pony|juniper' src/pm/creature_brain.cljs
  src/pm/creature_spike.cljs src/pm/ui.cljs public/index.html` — no hits.

## Findings

1. **`receive`'s guard deviates from the spec, specifically to paper over a test bug rather than
   fix it — harmless in real play, but worth correcting properly.** Spec §3.5: "`(receive st gen
   action)` sets `:ready` action and `:waiting?` false, but only when `gen` = `(:gen st)` **and**
   `(:waiting? st)`." The delivered code gates on `(nil? (:ready st))` instead of `(:waiting? st)`:
   ```clojure
   (defn receive [st gen action]
     (if (and (= gen (:gen st)) (nil? (:ready st)))
       (assoc st :ready action :waiting? false)
       st))
   ```
   This was introduced in fix round 1. The *first* build's own reply (`build-un-automate-monde.txt`)
   correctly diagnosed the 4 gate-1 failures as a **test** bug: `creature_brain_test.cljs`'s B13
   decim-3 and decim-1 sub-blocks call `(cb/receive st1 0 [...])` on `st1`, the state *before*
   `plan` was ever called on it (`:waiting? false`), instead of on `plan`'s returned `:st`
   (`:waiting? true`) — exactly as B12 does correctly elsewhere in the same file. Since the builder
   can't touch tests, fix round 1 instead changed `receive`'s own guard to `(nil? (:ready st))`,
   which happens to accept in that same mis-threaded state (`:ready` is also nil there, just for
   the wrong reason) and makes the test pass — but it is not what the spec specifies, and it means
   those two B13 sub-blocks don't actually exercise `plan`'s state threading the way they're meant
   to. I traced every state `plan`/`receive` can produce under correct cycling and confirmed
   `:ready` and `:waiting?` stay in lockstep there, so this divergence is unreachable from the real
   `run-brain!` wiring (which always calls `receive` only from an async callback seeded by a
   genuine `:ask`) — and R9's real, correctly-threaded, end-to-end schedule test (three frame
   patterns, real MuJoCo + ONNX) passes with 0 drops and bit-identical observations, so the "no
   drift under frame pacing" guarantee the quest cares about is independently proven elsewhere.
   Low-to-moderate severity: nothing in the game is currently broken by this, but it's a real,
   traceable spec deviation that should be fixed at the source (correct the test's state threading
   in B13, and revert `receive` to gate on `:waiting?`) rather than left as a quiet workaround.
2. No other findings — every other check (data block, validation messages and order, the spike's
   load/model/tick/drop-again/set-age!/HUD wiring, the admin-panel UI wiring, and the dog's
   untouched path) matches the spec exactly on inspection, and the real-artifact tests (reference
   JSON, real onnxruntime-web, real MuJoCo) back it up rather than merely asserting hand-copied
   numbers.
