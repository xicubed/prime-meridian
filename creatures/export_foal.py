#!/usr/bin/env python
"""Exports FoalGetUp checkpoints to ONNX plus a policy.json sidecar.

    uv run python export_foal.py runs/foal-getup/model_0.pt ... --logdir=runs/foal-getup
    uv run python export_foal.py runs/foal-getup-r2/model_0.pt ... --logdir=runs/foal-getup-r2

Writes <logdir>/policy_<iter>.onnx for each checkpoint given, and one
<logdir>/policy.json describing the (checkpoint-independent) layout:
observation order, joint order, and the action -> actuator-target mapping,
read directly off the registered env so it can't drift from foal/task.py.
The layout is identical between v1 and v2 (only rewards/resets differ), so
no --variant flag is needed here.
"""
import argparse
import dataclasses
import json
import re

import foal.task  # noqa: F401  (side effect: registers "FoalGetUp")
from mujoco_playground._src import xpu
from mujoco_playground._src.rl import env_registry
from mujoco_playground._src.rl.rsl_rl import checkpoint
from mujoco_playground._src.rl.rsl_rl import vecenv_wrapper
from rsl_rl import runners as rsl_rl_runners

_CKPT_RE = re.compile(r"model_(\d+)\.pt$")


def export_one(ckpt_path: str, device_str: str, logdir: str) -> str:
  entry = env_registry.get("FoalGetUp")
  agent_cfg = entry.runner_cfg
  agent_cfg.nworld = 2  # irrelevant for export; just needs to build.

  env = entry.env_builder(nworld=2, device=device_str)
  wrapped = vecenv_wrapper.RslRlVecEnvWrapper(
      env, clip_actions=agent_cfg.clip_actions, cfg=agent_cfg.vecenv_wrapper_cfg
  )
  runner = rsl_rl_runners.OnPolicyRunner(
      wrapped, dataclasses.asdict(agent_cfg), device=device_str
  )
  runner.load(checkpoint.read_checkpoint(ckpt_path), map_location=device_str)

  it = _CKPT_RE.search(ckpt_path).group(1)
  filename = f"policy_{it}.onnx"
  runner.export_policy_to_onnx(logdir, filename=filename)
  return f"{logdir}/{filename}"


def write_policy_json(device_str: str, logdir: str) -> None:
  from foal import task as foal_task

  joint_ranges = foal_task._joint_ranges(foal_task._load_base_scene())
  scale = ((joint_ranges[:, 1] - joint_ranges[:, 0]) / 2.0).tolist()
  offset = ((joint_ranges[:, 1] + joint_ranges[:, 0]) / 2.0).tolist()
  lo = joint_ranges[:, 0].tolist()
  hi = joint_ranges[:, 1].tolist()

  obs_layout = [
      {"name": "joint_pos", "size": foal_task.NUM_JOINTS, "order": list(foal_task.JOINT_NAMES)},
      {"name": "joint_vel", "size": foal_task.NUM_JOINTS, "order": list(foal_task.JOINT_NAMES)},
      {"name": "gravity_b", "size": 3, "note": "gravity direction (unit vector, world -z) rotated into the torso body frame"},
      {"name": "ang_vel", "size": 3, "note": "torso angular velocity, torso (body) frame"},
      {"name": "last_action", "size": foal_task.NUM_JOINTS, "order": list(foal_task.JOINT_NAMES), "note": "previous raw policy output, pre scale/offset/clip"},
  ]
  obs_dim = sum(t["size"] for t in obs_layout)

  policy_json = {
      "task": "FoalGetUp",
      "control_timestep": foal_task._CONTROL_TIMESTEP,
      "simulation_timestep": foal_task._SIMULATION_TIMESTEP,
      "episode_length_secs": foal_task._EPISODE_LENGTH_SECS,
      "joint_order": list(foal_task.JOINT_NAMES),
      "observation": {
          "total_dim": obs_dim,
          "input_name": "obs",
          "layout": obs_layout,
          "note": "Concatenate groups in this order into one (1, "
          f"{obs_dim}) float32 tensor; the ONNX graph's own "
          "obs_normalizer (running mean/std, baked in at export) "
          "handles normalization -- feed it raw, unnormalized values.",
      },
      "action": {
          "output_name": "actions",
          "size": foal_task.NUM_JOINTS,
          "order": list(foal_task.JOINT_NAMES),
          "note": "Raw policy output is in roughly [-1, 1] (it is what "
          "action_rate/history are computed on). To get the actuator "
          "ctrl target: target = clip(action * scale + offset, lo, hi), "
          "per joint, using the arrays below (same order as joint_order).",
          "scale": scale,
          "offset": offset,
          "ctrlrange_lo": lo,
          "ctrlrange_hi": hi,
      },
  }
  with open(f"{logdir}/policy.json", "w") as f:
    json.dump(policy_json, f, indent=2)


def main():
  parser = argparse.ArgumentParser()
  parser.add_argument("checkpoints", nargs="+")
  parser.add_argument("--logdir", default="runs/foal-getup")
  args = parser.parse_args()

  device_str = xpu.resolve_device(None)
  for ckpt_path in args.checkpoints:
    path = export_one(ckpt_path, device_str, args.logdir)
    print(f"exported {ckpt_path} -> {path}")
  write_policy_json(device_str, args.logdir)
  print(f"wrote {args.logdir}/policy.json")


if __name__ == "__main__":
  main()
