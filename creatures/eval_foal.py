#!/usr/bin/env python
"""Evaluates FoalGetUp checkpoints: standing success rate from random drops.

    uv run python eval_foal.py runs/foal-getup/model_0.pt ... \
        --variant=v1 --logdir=runs/foal-getup
    uv run python eval_foal.py runs/foal-getup-r3/model_0.pt ... \
        --variant=v3 --logdir=runs/foal-getup-r3

For each checkpoint, runs N resets for a full 6s episode and reports, at the
final step: how many of the N ended standing (torso height > 0.7m and torso
up-axis world-z > 0.5), the mean final torso height/upright, and mean
per-foot ground contact fraction (to check the "lying with feet on the
grass" hypothesis).

Unless --sternal-fraction/--standing-fraction are given explicitly, each
checkpoint is evaluated once per start type the variant uses: v1 is
drop-only; v2 adds an all-sternal ("chest") batch; v3 adds an all-standing
batch too. Each batch forces one start type for all N resets, regardless of
what fraction the variant trained with.
"""
import argparse
import dataclasses
import json

import foal.task  # noqa: F401  (side effect: registers "FoalGetUp")
from mujoco_playground._src import xpu
from mujoco_playground._src.rl import env_registry
from mujoco_playground._src.rl.rsl_rl import checkpoint
from mujoco_playground._src.rl.rsl_rl import vecenv_wrapper
from reaf.simulation import torch as reaf_torch
from reaf.simulation.torch import math as reaf_math
from rsl_rl import runners as rsl_rl_runners
import tensordict
import torch

N_WORLDS = 20
SEED = 0


def evaluate(
    ckpt_path: str,
    device_str: str,
    variant: str,
    sternal_fraction: float,
    standing_fraction: float,
) -> dict:
  torch.manual_seed(SEED)

  entry = env_registry.get("FoalGetUp")
  agent_cfg = entry.runner_cfg
  agent_cfg.nworld = N_WORLDS

  env = entry.env_builder(
      nworld=N_WORLDS,
      device=device_str,
      variant=variant,
      sternal_fraction=sternal_fraction,
      standing_fraction=standing_fraction,
  )
  wrapped = vecenv_wrapper.RslRlVecEnvWrapper(
      env, clip_actions=agent_cfg.clip_actions, cfg=agent_cfg.vecenv_wrapper_cfg
  )
  runner = rsl_rl_runners.OnPolicyRunner(
      wrapped, dataclasses.asdict(agent_cfg), device=device_str
  )
  runner.load(checkpoint.read_checkpoint(ckpt_path), map_location=device_str)
  policy = runner.get_inference_policy(device=device_str)

  raw_env = wrapped.unwrapped
  raw_env.auto_reset_mode = reaf_torch.AutoResetMode.DISABLED
  action_key = next(iter(raw_env.action_spec()))

  num_steps = round(6.0 / raw_env.device_layer.control_timestep)

  timestep = raw_env.reset()
  for _ in range(num_steps):
    obs = timestep.observation
    with torch.inference_mode():
      action = policy(tensordict.TensorDict(obs, batch_size=[N_WORLDS]))
    timestep = raw_env.step({action_key: action})

  f = raw_env._latest_features  # pylint: disable=protected-access
  height = f["torso/xpos"][:, 2]
  quat = f["torso/xquat"]
  up = torch.zeros_like(f["torso/xpos"])
  up[:, 2] = 1.0
  world_up_z = reaf_math.quat_rotate(quat, up)[:, 2]

  foot_names = ("fl-foot", "fr-foot", "hl-foot", "hr-foot")
  foot_z = torch.stack([f[f"{n}/xpos"][:, 2] for n in foot_names], dim=-1)
  foot_grounded = (foot_z < 0.08).float().mean(dim=-1)  # fraction of 4 feet near ground

  standing = (height > 0.7) & (world_up_z > 0.5)

  return {
      "checkpoint": ckpt_path,
      "variant": variant,
      "sternal_fraction": sternal_fraction,
      "standing_fraction": standing_fraction,
      "n_drops": N_WORLDS,
      "standing_count": int(standing.sum().item()),
      "standing_rate": float(standing.float().mean().item()),
      "mean_final_height": float(height.mean().item()),
      "min_final_height": float(height.min().item()),
      "max_final_height": float(height.max().item()),
      "mean_world_up_z": float(world_up_z.mean().item()),
      "mean_foot_grounded_frac": float(foot_grounded.mean().item()),
      "per_drop_height": [round(x, 3) for x in height.tolist()],
      "per_drop_up_z": [round(x, 3) for x in world_up_z.tolist()],
  }


def main():
  parser = argparse.ArgumentParser()
  parser.add_argument("checkpoints", nargs="+")
  parser.add_argument("--variant", default="v1", choices=["v1", "v2", "v3"])
  parser.add_argument("--sternal-fraction", type=float, default=None)
  parser.add_argument("--standing-fraction", type=float, default=None)
  parser.add_argument("--logdir", default="runs/foal-getup")
  args = parser.parse_args()

  if args.sternal_fraction is not None or args.standing_fraction is not None:
    batches = [("eval", args.sternal_fraction or 0.0, args.standing_fraction or 0.0)]
  elif args.variant == "v3":
    batches = [("drop", 0.0, 0.0), ("chest", 1.0, 0.0), ("standing", 0.0, 1.0)]
  elif args.variant == "v2":
    batches = [("drop", 0.0, 0.0), ("chest", 1.0, 0.0)]
  else:
    batches = [("drop", 0.0, 0.0)]

  device_str = xpu.resolve_device(None)
  results = []
  for ckpt_path in args.checkpoints:
    for label, sternal_fraction, standing_fraction in batches:
      r = evaluate(ckpt_path, device_str, args.variant, sternal_fraction, standing_fraction)
      r["label"] = label
      print(json.dumps(r, indent=2))
      results.append(r)

  out_path = f"{args.logdir}/eval_results.json"
  with open(out_path, "w") as fh:
    json.dump(results, fh, indent=2)
  print(f"wrote {out_path}")


if __name__ == "__main__":
  main()
