# foal-gym-v0: training report

## Command, worlds, wall time

```
uv run python train_foal.py --logdir runs/foal-getup \
  --overrides='{"max_iterations": 9500, "save_interval": 100}'
```

- 4,096 worlds (fixed by the Nov 9 restart notes).
- 9,500 PPO iterations, 24 steps/env per iteration, 0.02 s control timestep (6 s
  episodes = 301 control steps).
- Wall time: 41 minutes (started 09:51, finished 10:32).
- Checkpoints every 100 iterations: `model_0.pt` … `model_9499.pt` (96 files).

## Does he get up? No.

20 random drops (same reset distribution as training: height 0.4-0.8 m,
uniform random orientation, random joint angles) per checkpoint, run to the
full 6 s episode, checked at the end (torso height > 0.7 m **and** torso
up-axis world-z > 0.5 = "still up at 6 s"):

| checkpoint | iter | standing rate | mean final height (m) | mean up-z | mean feet-on-ground frac |
|---|---|---|---|---|---|
| `model_0.pt`    | 0    | 0/20 (0%) | 0.150 | -0.00 | 0.50 |
| `model_3000.pt` | 3000 | 0/20 (0%) | 0.150 | -0.05 | 0.475 |
| `model_6000.pt` | 6000 | 0/20 (0%) | 0.158 | 0.04 | 0.50 |
| `model_9499.pt` | 9499 | 0/20 (0%) | 0.159 | 0.04 | 0.50 |

**0% standing at every age, including the untrained checkpoint.** Training
made essentially no difference to the outcome: trained or not, he settles
flat on the ground (torso height ≈ 0.15 m, which is resting height — the torso
capsule radius — not standing height) and stays there. A per-step trace of
`model_9499` on 20 drops shows he's not even struggling and failing — he falls
during the first ~1 s (gravity, mostly unopposed) and then is **completely
static** for the remaining 5 s, height pinned at exactly 0.15 m:

```
t=0.00s  mean height=0.584   (post-drop, mid-air/settling)
t=0.50s  mean height=0.233
t=1.00s  mean height=0.180
t=1.50s  mean height=0.166
t=2.00s .. t=5.98s   mean height=0.159  (unchanged, to 3 decimal places)
```

One drop in 20 (at `model_6000`/`model_9499`) ends partway up (height 0.32 m,
up-z 0.81) — on his chest/knees, not standing — but this looks like it depends
on which side he happened to land on more than anything learned; it's not
repeatable across checkpoints or seeds in any way that looks intentional.

## Reward and episode-length curves

`Mean episode length` jumps from ~20 (iter 0, dying early — presumably NaN
from the initial random policy's violent actions) to the full 301 by iter
~500 and stays there for the rest of training. That's not progress toward
standing; `nan_termination=True` is the *only* early-termination condition in
this task, so "full episode length" just means "didn't NaN," which a foal
lying motionless on the ground trivially satisfies.

Per-episode reward terms (raw, unweighted; max possible is 301 — one point per
control step) at the sampled iterations:

| iter | height | upright | head_height | feet_grounded | mean reward |
|---|---|---|---|---|---|
| 0    | 4.9  | 2.2  | 6.8   | 2.3   | -11.75 |
| 500  | 8.6  | 11.6 | 283.8 | 136.9 | 6.40 |
| 2000 | 6.6  | 16.1 | 274.8 | 138.6 | 5.21 |
| 6000 | 6.4  | 14.8 | 278.7 | 136.7 | 5.15 |
| 9499 | 6.8  | 16.7 | 275.6 | 137.3 | 5.53 |

`height` (max 301) never climbs past single digits — he's essentially never
in the standing-height band. `upright` similarly stays under ~7% of max.
Meanwhile `head_height` sits at ~275-284/301 (≈92%) and `feet_grounded` at
~137/301 (≈46%) from iteration ~500 onward and barely moves for the rest of
training. Those two terms, not height/upright, are what the reward curve is
actually tracking.

Looking at the **weighted** contributions (what actually drives the
gradient) at iter 9499: `head_height` +5.51, `feet_grounded` +2.75,
`action_rate` penalty -2.66, `energy` penalty -0.66, `joint_speed` penalty
-0.89, `upright` +1.00, `height` +0.41. Despite `height` and `upright` having
the *highest* weights in `REWARD_WEIGHTS` (3.0 each, vs 1.0 for the other two),
they contribute the *least* to the total, because they're almost never
satisfied. `head_height` and `feet_grounded`, with lower weights, dominate
because they're nearly free.

## Checking Hal and Michael's two suspicions

**1. `feet_grounded` pays him for lying with his feet on the grass — confirmed.**
`reward_feet_grounded` tolerates foot height 0-0.08 m with a 0.1 m margin —
written assuming "feet near the ground" implies "standing on them." But a
foal lying on his side or chest also has most of his feet near the ground
(mean foot-grounded fraction ≈ 0.475-0.5 at the final, motionless state across
all four checkpoints — essentially the same whether he's trained or not). The
reward can't distinguish "standing on all four feet" from "collapsed with
feet splayed on the grass," and the eval numbers show he's doing the latter.

**2. The energy penalty makes moving not worth it — plausible, but `action_rate`
looks like the bigger brake.** The weighted `energy` penalty (-0.66/episode at
iter 9499) is modest next to `action_rate` (-2.66) and `joint_speed` (-0.89).
All three fire on *any* movement, learned or not, while the payoff for
successfully standing (`height` + `upright`, weighted ~0.4+1.0 = ~1.4/episode
even when barely achieved) is small and — per the trajectory trace — requires
sustained multi-second effort with a real risk of never reaching it. Given
`head_height` (+5.5) and `feet_grounded` (+2.75) are already collectable for
free by doing nothing, the arithmetic favors lying still over attempting the
costly, uncertain climb to standing. So: both terms (`action_rate` more than
raw `energy`) are taxing the one behavior (movement) that could lead to the
underpaid behavior (standing), while two unrelated terms pay out regardless.

## What he actually does ("the RL walk")

No walk, no gait, nothing like propping up — he's not exploiting a weird gait
at all, which is in some ways a more boring failure than a knee-balancing
exploit. He:
- Falls/settles under gravity for about the first second (the same whether
  trained or not — the weights plainly aren't steering this).
- Lifts his head (confirmed independently by `head_height` ≈ 92% of max,
  matching the already-good hand-tuned head behavior Michael mentioned).
- Does not move his legs in any purposeful way afterward — torso height is
  frozen to 3 decimal places for the last 5 of 6 seconds in every checkpoint
  tested, trained or not.

The one outlier drop that got to height 0.32 m / up-z 0.81 looks like it rode
an initial-condition bounce rather than a learned recovery — it shows up at
the same rate (1/20) at both `model_6000` and `model_9499`, two checkpoints
3,500 iterations apart with near-identical behavior otherwise.

## Honest read

This run proves the loop end-to-end (env, training, checkpoints, export) but
does not produce a foal that gets up. The reward as written gives him a cheap
way to collect most of the available signal (hold his head up, keep his feet
near the ground — both satisfied by lying still) and only a small, expensive,
uncertain bonus for the behavior we actually want (height + upright while
moving against `action_rate`/`joint_speed`/`energy` penalties). 9,500
iterations never found a path out of that local optimum; given the curves are
already flat by iteration ~500, more time at these weights looks unlikely to
help. The reward shape needs to change before the next run — not just
training longer — most directly by making `feet_grounded` require the torso
to be off the ground first (e.g. gate it on `height`/`upright`, or condition
it on the *rate* of change rather than the raw occupancy), and by checking
whether `action_rate`/`joint_speed` penalties can be loosened without
reintroducing the flailing Michael originally flagged.

## Exported files

For ages 0, 3000, 6000, 9499:
- `runs/foal-getup/policy_0.onnx`
- `runs/foal-getup/policy_3000.onnx`
- `runs/foal-getup/policy_6000.onnx`
- `runs/foal-getup/policy_9499.onnx`

Each takes input `obs` (1×42 float32) and returns output `actions` (1×12
float32), deterministic (distribution mean, not a sample), with the
observation normalizer baked into the graph (feed raw, unnormalized values).

Plus `runs/foal-getup/policy.json` (verified against the ONNX graphs'
actual input/output shapes): observation layout and joint order (both groups
of 12 use `JOINT_NAMES` order from `foal/task.py`), and the action → actuator
`ctrl` mapping (`scale`, `offset`, `ctrlrange_lo/hi`, all read live from
`bodies/horse.xml`'s own joint ranges rather than hardcoded).

Raw eval numbers behind the standing-rate table: `runs/foal-getup/eval_results.json`.
