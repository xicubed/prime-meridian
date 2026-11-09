#!/usr/bin/env python
"""Evaluates FoalGetUp checkpoints: standing success rate from random drops.

    uv run python eval_foal.py runs/foal-getup/model_0.pt runs/foal-getup/model_3000.pt ...

For each checkpoint, runs N random drops (same reset distribution as training)
for a full 6s episode and reports, at the final step: how many of the N ended
standing (torso height > 0.7m and torso up-axis world-z > 0.5), the mean final
torso height/upright, and mean per-foot ground contact fraction (to check the
"lying with feet on the grass" hypothesis).
"""
import dataclasses
import json
import sys

import foal.task  # noqa: F401  (side effect: registers "FoalGetUp")
from mujoco_playground._src import xpu
from mujoco_playground._src.rl import env_registry
from mujoco_playground._src.rl.rsl_rl import checkpoint
from mujoco_playground._src.rl.rsl_rl import vecenv_wrapper
from reaf.simulation import torch as reaf_torch
from rsl_rl import runners as rsl_rl_runners
import tensordict
import torch

N_WORLDS = 20
SEED = 0


def evaluate(ckpt_path: str, device_str: str) -> dict:
  torch.manual_seed(SEED)

  entry = env_registry.get("FoalGetUp")
  agent_cfg = entry.runner_cfg
  agent_cfg.nworld = N_WORLDS

  env = entry.env_builder(nworld=N_WORLDS, device=device_str)
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
  from reaf.simulation.torch import math as reaf_math

  world_up_z = reaf_math.quat_rotate(quat, up)[:, 2]

  foot_names = ("fl-foot", "fr-foot", "hl-foot", "hr-foot")
  foot_z = torch.stack([f[f"{n}/xpos"][:, 2] for n in foot_names], dim=-1)
  foot_grounded = (foot_z < 0.08).float().mean(dim=-1)  # fraction of 4 feet near ground

  standing = (height > 0.7) & (world_up_z > 0.5)

  return {
      "checkpoint": ckpt_path,
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
  device_str = xpu.resolve_device(None)
  results = []
  for ckpt_path in sys.argv[1:]:
    r = evaluate(ckpt_path, device_str)
    print(json.dumps(r, indent=2))
    results.append(r)
  with open("runs/foal-getup/eval_results.json", "w") as fh:
    json.dump(results, fh, indent=2)


if __name__ == "__main__":
  main()
