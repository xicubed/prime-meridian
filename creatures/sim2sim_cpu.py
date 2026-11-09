#!/usr/bin/env python
"""Sim-to-sim check: runs checkpoint 9499's exported ONNX policy against
plain MuJoCo (CPU, `mujoco.mj_step`) instead of the mujoco_warp (GPU)
backend training/eval used, since the browser will run plain MuJoCo C
(WASM). Everything except the physics stepping reuses the real task code
(`foal.task`) so the only thing under test is the physics engine itself:
reset distributions (`reset_foal`), the observation layout, and the
action -> ctrl mapping are the exact same functions/formulas as training.

    uv run python sim2sim_cpu.py

Writes runs/foal-getup-r3/SIM2SIM.md.
"""
import json
import types

import foal.task as foal_task
import mujoco
import numpy as np
import onnxruntime as ort
from reaf.simulation.torch import math as reaf_math
import torch

LOGDIR = "runs/foal-getup-r3"
CKPT_TAG = "9499"
N = 20
SEED = 0

mj_model = mujoco.MjModel.from_xml_path(str(foal_task._BODY_PATH))

joint_ranges = foal_task._joint_ranges(foal_task._load_base_scene())
_scale = (joint_ranges[:, 1] - joint_ranges[:, 0]) / 2.0
_offset = (joint_ranges[:, 1] + joint_ranges[:, 0]) / 2.0
_lo, _hi = joint_ranges[:, 0], joint_ranges[:, 1]

JOINT_Q_ADR = [int(mj_model.joint(jn).qposadr[0]) for jn in foal_task.JOINT_NAMES]
JOINT_V_ADR = [int(mj_model.joint(jn).dofadr[0]) for jn in foal_task.JOINT_NAMES]
_root = mj_model.joint(foal_task.ROOT_JOINT_NAME)
FQ, FV = int(_root.qposadr[0]), int(_root.dofadr[0])

CONTROL_SUBSTEPS = round(foal_task._CONTROL_TIMESTEP / foal_task._SIMULATION_TIMESTEP)
EPISODE_STEPS = round(foal_task._EPISODE_LENGTH_SECS / foal_task._CONTROL_TIMESTEP)

ort_sess = ort.InferenceSession(f"{LOGDIR}/policy_{CKPT_TAG}.onnx")


def ctrl_from_action(action: np.ndarray) -> np.ndarray:
  return np.clip(action * _scale.numpy() + _offset.numpy(), _lo.numpy(), _hi.numpy())


def compute_obs(qpos: np.ndarray, qvel: np.ndarray, last_action: np.ndarray) -> np.ndarray:
  """Same 60-vector as foal.task's observation_adapter, computed from raw
  qpos/qvel instead of REAF device features -- joint_pos/joint_vel are
  direct qpos/qvel slices (see JOINT_Q_ADR/JOINT_V_ADR); gravity_b and
  ang_vel use the free joint's own pos/quat/qvel, matching
  obs_gravity_b/obs_torso_ang_vel in foal/task.py exactly (reusing
  reaf_math.quat_rotate_inverse so the rotation formula can't drift from
  what trained the policy).
  """
  joint_pos = qpos[JOINT_Q_ADR]
  joint_vel = qvel[JOINT_V_ADR]
  quat = torch.tensor(qpos[FQ + 3:FQ + 7], dtype=torch.float32).unsqueeze(0)
  gravity = torch.tensor([[0.0, 0.0, -1.0]])
  gravity_b = reaf_math.quat_rotate_inverse(quat, gravity)[0].numpy()
  ang_vel = qvel[FV + 3:FV + 6]  # already torso-local frame (free-joint qvel convention)
  return np.concatenate([joint_pos, joint_vel, gravity_b, ang_vel, last_action]).astype(np.float32)


def standing_check(qpos: np.ndarray) -> tuple[float, float, bool]:
  height = float(qpos[FQ + 2])
  quat = torch.tensor(qpos[FQ + 3:FQ + 7], dtype=torch.float32).unsqueeze(0)
  up = torch.tensor([[0.0, 0.0, 1.0]])
  up_z = float(reaf_math.quat_rotate(quat, up)[0, 2].item())
  return height, up_z, (height > 0.7 and up_z > 0.5)


def sample_initial_states(n: int, sternal_fraction: float, standing_fraction: float):
  """Same reset distributions as training/eval -- reuses foal.task.reset_foal
  itself (CPU torch tensors standing in for REAF's device data), not a
  reimplementation, so the only difference from the Warp eval's reset is
  the RNG draw, not the formula.
  """
  torch.manual_seed(SEED)
  qpos = torch.zeros(n, mj_model.nq)
  qvel = torch.zeros(n, mj_model.nv)
  dc = types.SimpleNamespace(sim=types.SimpleNamespace(data=types.SimpleNamespace(qpos=qpos, qvel=qvel), mj_model=mj_model))
  env_mask = torch.ones(n, dtype=torch.bool)

  sternal_target = sternal_is_random = None
  if sternal_fraction > 0.0:
    sternal_target, sternal_is_random = foal_task._pose_targets(
        foal_task.JOINT_NAMES, joint_ranges,
        foal_task._STERNAL_LOWER_STRENGTH, foal_task._STERNAL_SWING_STRENGTH,
        direction_sign=1.0, non_leg_behavior="random",
    )
  standing_target = standing_is_random = None
  if standing_fraction > 0.0:
    standing_target, standing_is_random = foal_task._pose_targets(
        foal_task.JOINT_NAMES, joint_ranges,
        foal_task._STANDING_LOWER_STRENGTH, foal_task._STANDING_SWING_STRENGTH,
        direction_sign=-1.0, non_leg_behavior="center",
    )

  foal_task.reset_foal(
      dc, env_mask, joint_ranges, foal_task._DROP_HEIGHT_RANGE,
      sternal_fraction=sternal_fraction, standing_fraction=standing_fraction,
      sternal_target=sternal_target, sternal_is_random=sternal_is_random,
      standing_target=standing_target, standing_is_random=standing_is_random,
  )
  return qpos.numpy(), qvel.numpy()


def run_episode(qpos0: np.ndarray, qvel0: np.ndarray, num_steps: int, trace: bool = False):
  data = mujoco.MjData(mj_model)
  data.qpos[:] = qpos0
  data.qvel[:] = qvel0
  mujoco.mj_forward(mj_model, data)

  last_action = np.zeros(foal_task.NUM_JOINTS, dtype=np.float32)
  trace_out = []
  for _ in range(num_steps):
    if trace:
      height, up_z, _ = standing_check(data.qpos)
      trace_out.append({"height": height, "up_z": up_z})
    if np.any(np.isnan(data.qpos)) or np.any(np.isnan(data.qvel)):
      break
    obs = compute_obs(data.qpos, data.qvel, last_action)
    action = ort_sess.run(None, {"obs": obs.reshape(1, -1)})[0][0]
    ctrl = ctrl_from_action(action)
    data.ctrl[:] = ctrl
    for _ in range(CONTROL_SUBSTEPS):
      mujoco.mj_step(mj_model, data)
    last_action = action.astype(np.float32)

  height, up_z, standing = standing_check(data.qpos)
  return height, up_z, standing, trace_out


def eval_batch(label: str, sternal_fraction: float, standing_fraction: float) -> dict:
  qpos_batch, qvel_batch = sample_initial_states(N, sternal_fraction, standing_fraction)
  heights, up_zs, standings = [], [], []
  for i in range(N):
    h, u, s = run_episode(qpos_batch[i], qvel_batch[i], EPISODE_STEPS)[:3]
    heights.append(h)
    up_zs.append(u)
    standings.append(s)
  return {
      "label": label,
      "n": N,
      "standing_count": sum(standings),
      "standing_rate": sum(standings) / N,
      "mean_final_height": float(np.mean(heights)),
      "mean_world_up_z": float(np.mean(up_zs)),
  }


def main():
  print("mujoco version (plain, CPU):", mujoco.__version__)

  batches = [
      eval_batch("drop", 0.0, 0.0),
      eval_batch("chest", 1.0, 0.0),
      eval_batch("standing", 0.0, 1.0),
  ]

  ref = json.load(open(f"{LOGDIR}/policy_reference.json"))
  fixed = ref["fixed_chest_rollout_3s"]
  qpos0 = np.array(fixed["initial_qpos"])
  qvel0 = np.array(fixed["initial_qvel"])
  _, _, _, cpu_trace = run_episode(qpos0, qvel0, fixed["num_steps"], trace=True)
  warp_trace = fixed["trace"]

  # Same format as fixed_chest_rollout_3s, but stepped on plain MuJoCo CPU
  # (replaying the exact recorded initial_qpos/qvel, not re-rolling the
  # reset) -- the browser runs the same C MuJoCo, so this is the trace it
  # should match closely.
  ref["fixed_chest_rollout_3s_cpu"] = {
      "checkpoint": fixed["checkpoint"],
      "start_type": fixed["start_type"],
      "control_timestep": fixed["control_timestep"],
      "num_steps": fixed["num_steps"],
      "initial_qpos": fixed["initial_qpos"],
      "initial_qvel": fixed["initial_qvel"],
      "mujoco_version": mujoco.__version__,
      "engine": "plain mujoco (CPU)",
      "trace": cpu_trace,
  }
  with open(f"{LOGDIR}/policy_reference.json", "w") as f:
    json.dump(ref, f, indent=2)

  max_diff_1s, max_diff_3s = 0.0, 0.0
  steps_1s = round(1.0 / foal_task._CONTROL_TIMESTEP)
  for i, (w, c) in enumerate(zip(warp_trace, cpu_trace)):
    d = abs(w["height"] - c["height"])
    max_diff_3s = max(max_diff_3s, d)
    if i < steps_1s:
      max_diff_1s = max(max_diff_1s, d)

  warp_eval = json.load(open(f"{LOGDIR}/eval_results.json"))
  warp_by_label = {r["label"]: r for r in warp_eval if r["checkpoint"] == f"{LOGDIR}/model_{CKPT_TAG}.pt"}

  lines = []
  lines.append("# Sim-to-sim: mujoco_warp (GPU, training/eval) vs. plain MuJoCo (CPU)\n")
  lines.append(f"Checkpoint {CKPT_TAG}. MuJoCo version: `{mujoco.__version__}` (plain Python `mujoco` package, CPU).")
  lines.append("Same `bodies/horse.xml`, same reset distributions (`foal.task.reset_foal`, reused directly), same ")
  lines.append("observation/action/ctrl formulas, same exported `.onnx` via `onnxruntime` CPU, same 10 physics ")
  lines.append(f"substeps per control step ({CONTROL_SUBSTEPS} x {foal_task._SIMULATION_TIMESTEP}s = {foal_task._CONTROL_TIMESTEP}s). ")
  lines.append("Only the physics engine differs.\n")
  lines.append("## Standing rate: CPU (plain MuJoCo) vs. GPU (mujoco_warp, from eval_results.json)\n")
  lines.append("| start type | n | CPU standing rate | GPU (Warp) standing rate | CPU mean height (m) | GPU mean height (m) |")
  lines.append("|---|---|---|---|---|---|")
  for b in batches:
    w = warp_by_label.get(b["label"], {})
    lines.append(
        f"| {b['label']} | {b['n']} | {b['standing_rate']:.2f} ({b['standing_count']}/{b['n']}) | "
        f"{w.get('standing_rate', float('nan')):.2f} ({w.get('standing_count', '?')}/{w.get('n_drops', '?')}) | "
        f"{b['mean_final_height']:.3f} | {w.get('mean_final_height', float('nan')):.3f} |"
    )
  lines.append("")
  lines.append("## Fixed chest-start rollout: torso-height trace difference\n")
  lines.append(f"Replaying `policy_reference.json`'s `fixed_chest_rollout_3s.initial_qpos/qvel` exactly (not re-rolling the reset).")
  lines.append(f"- Max |height difference| over the first 1s ({steps_1s} steps): **{max_diff_1s:.5f} m**")
  lines.append(f"- Max |height difference| over the full 3s ({len(warp_trace)} steps): **{max_diff_3s:.5f} m**")
  lines.append(f"- GPU (Warp) final height: {warp_trace[-1]['height']:.4f} m, up-z {warp_trace[-1]['up_z']:.4f}")
  lines.append(f"- CPU (plain) final height: {cpu_trace[-1]['height']:.4f} m, up-z {cpu_trace[-1]['up_z']:.4f}")
  report = "\n".join(lines) + "\n"

  with open(f"{LOGDIR}/SIM2SIM.md", "w") as f:
    f.write(report)
  print(report)


if __name__ == "__main__":
  main()
