#!/usr/bin/env python
"""Exports FoalGetUp checkpoints to ONNX plus a policy.json sidecar.

    uv run python export_foal.py runs/foal-getup/model_0.pt runs/foal-getup/model_3000.pt ...

Writes runs/foal-getup/policy_<iter>.onnx for each checkpoint given, and one
runs/foal-getup/policy.json describing the (checkpoint-independent) layout:
observation order, joint order, and the action -> actuator-target mapping,
read directly off the registered env so it can't drift from foal/task.py.
"""
import dataclasses
import json
import re
import sys

import foal.task  # noqa: F401  (side effect: registers "FoalGetUp")
from mujoco_playground._src import xpu
from mujoco_playground._src.rl import env_registry
from mujoco_playground._src.rl.rsl_rl import checkpoint
from mujoco_playground._src.rl.rsl_rl import vecenv_wrapper
from rsl_rl import runners as rsl_rl_runners
import torch

_CKPT_RE = re.compile(r"model_(\d+)\.pt$")


def export_one(ckpt_path: str, device_str: str) -> str:
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
  runner.export_policy_to_onnx("runs/foal-getup", filename=filename)
  return f"runs/foal-getup/{filename}"


def write_policy_json(device_str: str) -> None:
  from foal import task as foal_task

  entry = env_registry.get("FoalGetUp")
  env = entry.env_builder(nworld=2, device=device_str)

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
  with open("runs/foal-getup/policy.json", "w") as f:
    json.dump(policy_json, f, indent=2)


def main():
  device_str = xpu.resolve_device(None)
  for ckpt_path in sys.argv[1:]:
    path = export_one(ckpt_path, device_str)
    print(f"exported {ckpt_path} -> {path}")
  write_policy_json(device_str)
  print("wrote runs/foal-getup/policy.json")


if __name__ == "__main__":
  main()
