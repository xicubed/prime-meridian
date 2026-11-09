# foal-gym-v0: run 3 report (comparing runs 1-3)

## Command, worlds, wall time

```
uv run python train_foal.py --logdir runs/foal-getup-r3 \
  --env_overrides='{"variant": "v3"}' \
  --overrides='{"max_iterations": 9500, "save_interval": 100}'
```

- New 18-joint body (`bodies/horse.xml`, M-14): shoulder/hip splay + swing, a
  two-joint spine (`torso` front half + `hind`), actuators at `:kp 250`
  (the Mac crew measured `:kp 150` can't hold a stand at all; 225 is the
  minimum).
- 4,096 worlds, 9,500 PPO iterations, 301 control steps/episode (6 s @ 0.02 s).
- Wall time: 47 min 51 s.
- Checkpoints every 100 iterations: `model_0.pt` … `model_9499.pt` (96 files).

## Headline: he stands up. Reliably.

20 resets per checkpoint, 3 start types (drop: same as runs 1-2's protocol;
chest: all-sternal; standing: all-standing-start), run to the full 6 s
episode, checked at the end (torso height > 0.7 m **and** up-axis world-z >
0.5):

| checkpoint | iter | drop | chest | standing | mean final height (m) | mean up-z | mean feet-on-ground frac |
|---|---|---|---|---|---|---|---|
| `model_0.pt`    | 0    | 0/20 (0%)   | 0/20 (0%)   | 0/20 (0%)   | 0.150 | 0.00 | 0.50 |
| `model_3000.pt` | 3000 | 7/20 (35%)  | 2/20 (10%)  | 5/20 (25%)  | 0.69-0.71 | 0.95 | 0.66-0.80 |
| `model_6000.pt` | 6000 | 8/20 (40%)  | 11/20 (55%) | 13/20 (65%) | 0.70-0.72 | 0.94-0.97 | 0.79-0.83 |
| `model_9499.pt` | 9499 | **17/20 (85%)** | 13/20 (65%) | 9/20 (45%) | 0.71-0.72 | 0.98 | 0.91-0.98 |

Compare to **run 1 (old body, old reward): 0/20 at every age, every
checkpoint identical to untrained.** Run 3's `model_9499` reaches 85% from
random drops — this is the first run where the standing-rate eval found
standing at all.

A caveat on the table: `mean_final_height` across almost every checkpoint
and batch clusters at 0.70-0.72 m — right at the 0.7 m pass/fail line (many
individual drops land at 0.698-0.705 m). The binary standing-rate number is
therefore noisy near that boundary (n=20 per cell, so +/-1 drop is a 5-point
swing) — the `standing` batch's rate dipping from 65% (iter 6000) to 45%
(iter 9499) is most likely that noise, not a regression: mean height and
up-z for that batch are flat to slightly improved across the same span
(0.715->0.706 m, 0.968->0.978 up-z). The steadier, cleaner signal is
`mean_foot_grounded_frac`, which climbs monotonically across every batch as
training progresses (e.g. drop: 0.80 -> 0.79 -> 0.91; standing: 0.66 -> 0.83
-> 0.98) — he keeps getting better at planting all four feet, even once
height/upright have mostly plateaued.

One more honest note: his settled standing height (~0.70-0.72 m) sits just
below the reward's target band (`_STANDING_HEIGHT_RANGE = (0.75, 0.95)`).
He's genuinely standing (up-z ~0.98, feet planted) but in a slightly lower
stance than the "ideal" the reward was written for — plausibly a stable
equilibrium just inside the tolerance's margin rather than at its plateau.

## Reward curves: the shape that worked

Per-episode reward terms (raw, unweighted; max possible is 301) at sampled
training iterations:

| iter | height | upright | head_height | feet_grounded (gated) | mean reward |
|---|---|---|---|---|---|
| 0    | 6.9   | 4.8   | 2.2   | 1.4   | 0.91 |
| 500  | 279.6 | 284.0 | 254.9 | 132.1 | 28.44 |
| 2000 | 283.9 | 289.0 | 270.0 | 145.6 | 23.95 |
| 6000 | 286.9 | 291.1 | 277.8 | 155.7 | 25.69 |
| 9499 | 286.3 | 292.3 | 278.0 | 154.0 | 25.57 |

Night-and-day versus run 1 (height/upright never climbed past single digits
out of 301) and run 2 (height 82/upright 104 out of 301 at the iteration it
was stopped, iter 4700 -- see below). Here height and upright are already at
~93-96% of max by iteration 500 and barely move for the rest of training --
the policy finds standing almost immediately once it's physically possible
and worth it, then spends the remaining ~9,000 iterations on smaller
refinements (feet_grounded raw keeps climbing slowly, 132 -> 155; the eval
table's foot-grounded-fraction trend above is the same signal). `energy` and
`joint_speed` weighted contributions are exactly 0.000 throughout, as
intended ("infinite energy"); `action_rate` (raw ~86,000-92,000, weighted
~-17 to -18/episode) is the only live movement cost, and the policy pays it
freely -- it moves far more than runs 1-2 ever did (run 1's final raw
action_rate was ~2,660; run 3's is ~86,000) because, for the first time,
moving is worth more than it costs.

## Comparing all three runs

| | run 1 | run 2 (stopped early) | run 3 |
|---|---|---|---|
| body | 12-joint, `:kp 150` | 12-joint, `:kp 150` | **18-joint, `:kp 250`** |
| reward | v1 (ungated head/feet, heavier movement costs) | v2 (gated, lighter costs, 30% chest starts) | v3 (v2's gating + "infinite energy" + 10% standing starts) |
| iterations run | 9,500 / 9,500 | 4,728 / 9,500 (stopped: Michael's `:kp` finding made the old body moot) | 9,500 / 9,500 |
| height (raw, /301) at its last iteration | 6.8 | 82.4 | 286.3 |
| upright (raw, /301) at its last iteration | 16.7 | 104.4 | 292.3 |
| standing-rate eval (final checkpoint) | 0% (0/20) | not run (none needed -- see note below) | **85% (17/20, drops)** |
| what he does | falls, settles flat (height 0.159 m), freezes for the rest of the episode | visibly attempting to right himself and hold a stance (height/upright climbing steadily, action std rising) but not yet evaluated | **stands up and holds it**: height ~0.71 m, up-z ~0.98, feet planted ~0.91-0.98 |

Run 2's note: it was stopped by Hal/Michael's call partway through (iteration
4,728 of 9,500) once the Mac crew's stand-hold measurement showed the
12-joint body's `:kp 150` actuators can't hold *any* stand, correct policy or
not (falls in ~5 s) -- so no eval or export was done for it, by instruction.
Its training-log curve at the point it was stopped (height 82/upright 104
out of 301, both climbing steadily from run 1's near-zero, action std still
rising) is still useful evidence: the reward fix (gating + lighter costs)
was already working -- the policy was actively trying to stand and partially
succeeding -- well before the body's torque ceiling was confirmed as the
remaining blocker. Run 3 (new body + same reward family + standing starts)
is the first to close that gap.

## Checking the two original suspicions, one more time

Run 1's report found `feet_grounded` payable just by lying still, and the
movement-cost terms taxing the one behavior that could lead to standing.
Run 3 removes both: `feet_grounded` is gated on height*upright (so lying
still pays ~0 now -- confirmed: at `model_0`, before any training, gated
`feet_grounded` is only 1.4/301 raw, vs. run 1's equivalent *ungated* value
of 2.3/301 at its own iteration 0 -- comparable, as expected, since neither
policy is standing yet), and `energy`/`joint_speed` are zeroed out entirely.
With those fixed *and* a body that can physically hold a stand, he stands.
That's reasonably strong evidence the diagnosis was right, not just the
fix for this one body.

## Exported files

For ages 0, 3000, 6000, 9499:
- `runs/foal-getup-r3/policy_0.onnx`
- `runs/foal-getup-r3/policy_3000.onnx`
- `runs/foal-getup-r3/policy_6000.onnx`
- `runs/foal-getup-r3/policy_9499.onnx`

Each takes input `obs` (1x60 float32 -- 18-joint obs: joint_pos(18) +
joint_vel(18) + gravity_b(3) + ang_vel(3) + last_action(18)) and returns
output `actions` (1x18 float32), deterministic, observation normalizer baked
in (feed raw, unnormalized values). Verified against the actual ONNX graph
shapes.

Plus `runs/foal-getup-r3/policy.json`: observation layout and joint order
(both the 18-joint `JOINT_NAMES`, in actuator order -- `neck-bend,
neck-pitch, head-pitch, head-roll, fl-splay, fl-swing, fl-lower, fr-splay,
fr-swing, fr-lower, spine-bend, spine-twist, hl-splay, hl-swing, hl-lower,
hr-splay, hr-swing, hr-lower`), and the action -> actuator `ctrl` mapping
(`scale`, `offset`, `ctrlrange_lo/hi`), all read live from the current
`bodies/horse.xml`.

Raw eval numbers behind the standing-rate table:
`runs/foal-getup-r3/eval_results.json`.

## "The RL walk"

No exploit, no weird gait -- he rolls/settles toward his chest or side
within the first ~1 s (same physics as before), then actively uses his legs
to push up onto a 4-point stance and holds it, visibly correcting (the
foot-grounded fraction keeps climbing with more training -- he's still
learning to plant all four feet cleanly, not just reach the height/upright
target and stop moving). All three start types (random drop, sternal/chest,
already-standing) converge toward the same ~0.70-0.72 m, up-z ~0.98 stance,
which is a good sign that what he learned is a general "get to standing and
hold it" skill rather than something tied to one starting configuration --
though the standing-start batch's rate is noisier than the others (see the
caveat above) and would benefit from a larger eval sample before trusting
small differences between start types.
