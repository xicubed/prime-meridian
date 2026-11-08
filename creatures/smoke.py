"""GPU smoke test for the creature pipeline (run on the GPU box, not the Mac).

    uv run python smoke.py            # check the GPU, then a short Cartpole training run
    uv run python smoke.py --check    # only check the GPU

Proves PyTorch, NVIDIA Warp and MuJoCo Warp all see the GPU, then trains MuJoCo
Playground's stock CartpoleBalance for a few iterations. Logs land in runs/smoke/.
"""
import json
import pathlib
import subprocess
import sys

HERE = pathlib.Path(__file__).resolve().parent


def check() -> bool:
    ok = True
    import torch
    if torch.cuda.is_available():
        p = torch.cuda.get_device_properties(0)
        print(f"torch {torch.__version__}: {p.name}, {p.total_memory // 2**20} MiB, sm_{p.major}{p.minor}")
        x = torch.randn(1024, 1024, device="cuda")
        print(f"  matmul ok: {float((x @ x).sum()):.1f}")
    else:
        print(f"torch {torch.__version__}: NO CUDA GPU visible"); ok = False
    import warp as wp
    wp.init()
    dev = [d for d in wp.get_devices() if d.is_cuda]
    print(f"warp {wp.__version__}: {[d.name for d in dev] or 'NO CUDA device'}")
    ok = ok and bool(dev)
    import mujoco, mujoco_warp  # noqa: F401
    print(f"mujoco {mujoco.__version__}, mujoco_warp imported")
    return ok


def train() -> int:
    logdir = HERE / "runs" / "smoke"
    cmd = [sys.executable, "-m", "mujoco_playground.rl.rsl_rl.train",
           "--env=CartpoleBalance", f"--logdir={logdir}",
           "--overrides=" + json.dumps({"max_iterations": 50})]
    print("training:", " ".join(cmd))
    return subprocess.call(cmd)


if __name__ == "__main__":
    if not check():
        sys.exit("GPU check failed: fix this before training")
    if "--check" not in sys.argv:
        rc = train()
        print("smoke: PASS" if rc == 0 else f"smoke: training exited {rc}")
        sys.exit(rc)
    print("smoke: GPU check PASS")
