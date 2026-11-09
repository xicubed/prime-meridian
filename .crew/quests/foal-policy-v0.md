# Quest: watch the foal get up (the learned policy in the game)

**Game:** Prime Meridian. **Asked by:** Michael, Nov 9, after run 3 on omarchy taught the foal to
stand (17 of 20 random drops end standing): "sounds good start with 1", i.e. see him do it in the
game. **Led by:** Hal. Plan of record: `docs/ideas-animal-motion.md` (M-9). Follows `body-v2` and
`foal-gym-v0` run 3 (`creatures/reports/foal-getup-run3/REPORT.md`).

## The game moment

`?pony`: the foal drops onto the east bank as now, but instead of the hand-made twitch and head
reflex, his **learned brain** drives all 18 joints: he flails, rolls onto his chest, pushes up on his
front legs, then his hind, wobbles and stands. The HUD shows which brain and which **age** (training
checkpoint) is driving, and whether he's up. In the admin panel (ⓘ) Michael can pick an age (0,
3000, 6000, 9499: the untrained one just flops) and **drop him again** to watch another try. Without
`?pony` nothing changes; `?pony=dog` is unchanged (the dog has no learned brain yet).

## Decided (don't reopen)

1. **The brain is species data.** `horse.edn` gets a `:brain` block: kind `:policy`, the directory of
   its files under `public/` (`/creatures/horse/`), the ages available, the default age (9499), and
   the metadata file (`policy.json`). A species without `:brain` keeps the twitch + reflex controller.
   No species names in code. `?brain=twitch` forces the old controller (for comparison);
   `?age=<n>` picks an age.
2. **Same physics as training, or it won't stand.** The body is the same generated MJCF (only the
   ground height/placement differ), timestep 0.002 s, and the policy acts every **10 physics steps**
   (0.02 s), exactly like training: observation at the control boundary, action held for the next 10
   steps. The game's frame pacing (dropped steps under load) must never change that schedule; if
   inference is asynchronous, physics waits at the boundary rather than running ahead with a stale
   action. Say how in the spec.
3. **Observation and action exactly as `policy.json` says** (60 in, 18 out): joint positions and
   velocities in `joint_order`, gravity direction in the torso frame, torso angular velocity in the
   torso frame, the last raw action; actuator target = clip(action × scale + offset, lo, hi). Pure,
   tested functions over MuJoCo arrays (`qpos`, `qvel`, `xmat`), no three.js.
4. **onnxruntime-web 1.31.1** (MIT), already added by Hal: npm dependency, postinstall copies
   `ort.wasm.min.mjs` + `ort-wasm-simd-threaded.{mjs,wasm}` to `public/vendor/onnxruntime/`
   (gitignored), import map entry `"onnxruntime-web"`. Single-threaded (`ort.env.wasm.numThreads = 1`;
   the page isn't cross-origin isolated). Models load with their `.onnx.data` external-data files.
   Node tests load it with `js/require` (it works in Node; Hal checked).
5. **Files, already placed by Hal:** `public/creatures/horse/policy_{0,3000,6000,9499}.onnx` (+
   `.onnx.data`), `policy.json`, and `policy_reference.json` (Python-side reference states from
   Le Coureur on omarchy: qpos/qvel/last_action → obs, ONNX action and ctrl for ages 0 and 9499, plus a
   3 s reference rollout from a fixed chest start).

## Out of scope

Training anything, walking, stamina/fatigue for the learned brain, terrain under him (separate
round), walkers avoiding him (`creature-obstacle-v0`), the dog's brain.

## What exists (read first)

`src/pm/creature_spike.cljs` (MuJoCo setup, the fixed-step loop, the controller interface, HUD),
`src/pm/creature_motion.cljs` (twitch, reflex, effort), `src/pm/creature.cljc`, `resources/creatures/
horse.edn`, `creatures/bodies/horse.xml`, `creatures/foal/task.py` (how training builds observations
and applies actions: the ground truth), `public/creatures/horse/policy.json`, the run-3 report, tests
`test/pm/creature_*_test.cljs` (MuJoCo via `js/require`). Gate `.crew/gate.sh`. Don't read
`assets/private/`, `public/kit/`. **Never send keys or prompts to crew members; no network; don't start
services; no installs; no whole-disk `find`; scratch only in your own scratchpad.**

## Done when

Gate passes, with tests for: the observation builder reproduces every `obs` in
`policy_reference.json` from its qpos/qvel/last_action (to 1e-5); onnxruntime-web on those obs gives
the reference `onnx_action` (to 1e-4) for both ages; the ctrl mapping gives the reference `ctrl`;
the control schedule (obs at the boundary, action held 10 steps) in a pure test; a **real-MuJoCo
rollout** in Node from the reference chest start with age 9499 that tracks the reference torso
height (within 3 cm for the first 1 s) and ends standing (torso > 0.7 m, up-z > 0.9) like the
reference; age 0 from the same start does **not** stand; and the species without `:brain` keeps the
old controller unchanged (existing tests pass). Live (Hal): `?pony` shows him getting up; ages switch;
"drop again" works; `?brain=twitch` still twitches.
