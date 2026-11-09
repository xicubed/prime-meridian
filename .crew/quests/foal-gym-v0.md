# Quest: the foal learns to get up (foal gym v0, on the GPU box)

**Game:** Prime Meridian. **Asked by:** Michael, Nov 8: "let's get that omarchy box going! train up
the foal to walk: it's just flopping its legs, not trying to prop itself up or anything useful. The
head looks good though, with the tiredness." **Led by:** Hal. **Done by:** le-coureur-omy, on
omarchy (RTX 5070 Ti), in `~/src/prime-meridian/creatures/`. Plan of record:
`docs/ideas-animal-motion.md` §"Nov 8" (M-9), TASKS M-10. Order there: head up (done, by hand) →
**onto the chest → stand** (this quest) → walk (next).

## The moment we're training for

Dropped on the grass in any pose, the foal works out how to get his legs under him: rolls onto his
chest, props up on his front legs, pushes up behind, wobbles, stands. A learned policy, not
hand-written. Checkpoints along the way are his "ages": early ones flail, later ones get up.

## What exists

- `creatures/` is a `uv` project on MuJoCo Playground 1.0 (MuJoCo Warp physics + rsl_rl PPO on
  PyTorch); `uv sync` is done on this box and `uv run python smoke.py` passes.
- **The body:** `creatures/bodies/horse.xml` (generated from `resources/creatures/horse.edn`
  by the game; a capsule foal, ~1.0 m at the withers, hinge joints with limits, one position
  actuator per joint, a free joint on the torso, a ground plane). Use it as is (load it with
  `mujoco.MjSpec.from_file`); never hand-edit it. It's z-up, metres, timestep 0.002 s.
- **How Playground 1.0 builds a task:** read
  `.venv/lib/python3.13/site-packages/mujoco_playground/tasks/dm_control_suite/cartpole.py` (devices,
  observations, rewards, resetters, sim config) and `tasks/whole_body/tracking/` (a legged body:
  terminations, body pose features, position-controlled joints), `_src/rl/env_registry.py`
  (`register(env_id, runner_cfg, env_cfg_fn)`), `_src/rl/rsl_rl/env_rl_config.py` (runner configs) and
  `rl/rsl_rl/train.py` / `play.py` / `_src/rl/rsl_rl/checkpoint.py` (training, checkpoints,
  any ONNX export). Follow their patterns; don't fork the library.

## Build

1. `creatures/foal/` (a small Python package): the task config `foal_getup_env()` and a runner
   config, registered as `FoalGetUp` from our own entry point (`creatures/train_foal.py`, which
   registers then calls Playground's train main), so `uv run python train_foal.py --logdir
   runs/foal-getup` trains it.
2. **Observations (only what his body can feel):** joint angles and speeds, gravity direction in
   the torso frame, torso angular velocity, the last action. No world position, no cheats.
3. **Reset:** drop him from 0.4–0.8 m in a random orientation (on his side, back, chest), with
   random joint angles, so he learns to get up from anything.
4. **Reward (stand up), weights in one place:** torso height toward standing height (~0.85 m for
   the torso centre; check it in the body), torso upright (its up axis vs world up), head above the
   torso, all four lower legs' ends on the ground once up, small penalties for joint speed, action
   rate and energy. No reward for anything but getting up and staying up. Episodes ~6 s; end early
   only on NaN.
5. **Train** on this GPU: start small (a few minutes) to prove the loop, then a real run of up to
   **45 minutes** wall clock with as many parallel worlds as the 16 GB card allows. Save
   checkpoints every so often (their "ages").
6. **Export** the trained actor to ONNX (`creatures/runs/foal-getup/policy_<iter>.onnx`, several
   ages) plus `policy.json`: the exact observation layout, joint order, action → actuator-target
   scaling, control timestep. The browser will run this with onnxruntime-web next to MuJoCo
   WebAssembly, so the layout must be exact.

## Rules

- Work only in `creatures/` (and `creatures/runs/`, which is gitignored). Don't touch the game
  (`src/`, `resources/`, `public/`), `.crew/`, or the system. `uv add` of a pure-Python package is OK
  if Playground lacks something (say which and why); nothing outside the venv, no `sudo`, no
  driver or CUDA changes.
- No `git commit`, `git push` or `git add`: Hal fetches your files and commits them on the Mac.
- No whole-disk `find`; keep scratch in `creatures/runs/` or your own scratchpad.
- If something in Playground 1.0 blocks you for more than ~20 minutes, stop and write down what
  and why rather than working around the library.

## Done when (write it in `creatures/runs/foal-getup/REPORT.md`)

The training command, wall time, worlds, iterations; the reward / episode-length / torso-height
curve at a few points (numbers); for 3–5 checkpoint ages, from 20 random drops each: how often he
ends standing (torso up, torso height > 0.7 m, still up at 6 s); the exported files and
`policy.json`; and anything that looked like "the RL walk" (odd gaits, exploits: e.g. balancing
on his knees) honestly described. Reply in your pane with the REPORT's summary, `git status
--short creatures/`, and a last line containing only `foal-gym-done`.

## Restart notes (Nov 9)

The first attempt was lost with the box's drive; rebuild from this quest.

- **Worlds: 4,096.** Use that for the proof loop and the real run. Don't search for the GPU's
  maximum; Michael raises it later if training is too slow.
- **Milestones: stop and reply at each one,** with `milestone: <name>` on its own line, then wait for
  Hal before continuing. Hal copies `creatures/` (not `runs/`) to the Mac and commits it each time:
  1. `task-built`: the env builds, resets and steps on the GPU.
  2. `loop-proven`: a short training run completes with checkpoints.
  3. `first-checkpoint`: the real run's first saved checkpoint.
  4. `exported`: the ONNX files, `policy.json` and `REPORT.md`.
- **If anything looks like a drive or I/O error** (`Input/output error`, read-only filesystem): stop
  immediately and say so. Don't retry.
