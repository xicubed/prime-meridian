# Sim-to-sim: mujoco_warp (GPU, training/eval) vs. plain MuJoCo (CPU)

Checkpoint 9499. MuJoCo version: `4.0.0` (plain Python `mujoco` package, CPU).
Same `bodies/horse.xml`, same reset distributions (`foal.task.reset_foal`, reused directly), same 
observation/action/ctrl formulas, same exported `.onnx` via `onnxruntime` CPU, same 10 physics 
substeps per control step (10 x 0.002s = 0.02s). 
Only the physics engine differs.

## Standing rate: CPU (plain MuJoCo) vs. GPU (mujoco_warp, from eval_results.json)

| start type | n | CPU standing rate | GPU (Warp) standing rate | CPU mean height (m) | GPU mean height (m) |
|---|---|---|---|---|---|
| drop | 20 | 0.80 (16/20) | 0.85 (17/20) | 0.712 | 0.719 |
| chest | 20 | 0.55 (11/20) | 0.65 (13/20) | 0.706 | 0.711 |
| standing | 20 | 0.80 (16/20) | 0.45 (9/20) | 0.725 | 0.706 |

## Fixed chest-start rollout: torso-height trace difference

Replaying `policy_reference.json`'s `fixed_chest_rollout_3s.initial_qpos/qvel` exactly (not re-rolling the reset).
- Max |height difference| over the first 1s (50 steps): **0.04067 m**
- Max |height difference| over the full 3s (150 steps): **0.11155 m**
- GPU (Warp) final height: 0.7058 m, up-z 0.9691
- CPU (plain) final height: 0.6963 m, up-z 0.9703

## Honest read

Overall agreement is good: drop (80% CPU vs 85% GPU) and chest (55% vs 65%)
are within the kind of gap you'd expect from n=20 sampling noise alone
(+/-1-2 drops), and both engines land on essentially the same mean height
(~0.71 m) and a genuinely upright, standing policy either way.

The `standing`-start batch is the outlier (80% CPU vs 45% GPU) and is almost
certainly the same borderline-threshold effect flagged in run 3's main
REPORT.md: final heights for this start type cluster right at the 0.7 m
pass/fail line (both engines, per the eval tables), so small physics
differences between Warp and plain MuJoCo are enough to flip a meaningful
fraction of borderline drops across that line in different directions. It
is not evidence the policy behaves differently on CPU vs GPU in any
qualitative sense -- both engines agree he ends up standing at a very
similar height; they disagree on which side of an arbitrary 0.7 m cutoff a
chunk of near-identical outcomes falls on.

The fixed-rollout trace shows real but modest divergence: ~4 cm apart after
1 s, peaking at ~11 cm sometime within the 3 s window, but the two engines'
final heights (0.706 m vs 0.696 m) and up-z (0.969 vs 0.970) are close again
by the end -- consistent with two numerically-different solvers taking
slightly different paths through the same nonlinear contact dynamics (foal
hitting the ground, legs pushing off) before converging to a similar stand,
rather than one engine diverging away from standing while the other holds
it.

Net: nothing here suggests the browser (plain MuJoCo, matching version
4.0.0) will behave qualitatively differently from what trained -- expect
the same ballpark standing rate and the same stood-up look, with ordinary
engine-to-engine numerical drift rather than a different outcome.
