# creatures/: the creature pipeline

Teaches a species to move. A species is data (`resources/creatures/<kind>.edn`, coming next:
body, joints, gait, rewards); this folder trains its walking policy with physics RL on a GPU
machine and exports two small files the game loads: the body (MuJoCo model) and the policy
(`policy.onnx`, run in the browser by onnxruntime-web next to MuJoCo's WebAssembly build).
Plan and background: `docs/ideas-animal-motion.md` (M-9).

- **Runs on a GPU box** (for the author, the Omarchy machine, RTX 5070 Ti). Never on the Mac,
  and never by the game: the game only loads the exported files.
- **Python because the RL stack is Python-only** (MuJoCo Playground: MuJoCo Warp physics +
  rsl_rl PPO on PyTorch). It lives in its own `uv` venv (`.venv/`, gitignored); the game stays
  ClojureScript.
- **Every species uses the same code path:** horse first, a second species (dog) from day one.

## On the GPU box

```bash
cd ~/src/prime-meridian/creatures
uv sync                        # first time: a few GB (PyTorch, CUDA libs, MuJoCo Warp)
uv run python smoke.py         # GPU check + a short stock training run (Cartpole)
```

`smoke: PASS` means the stack works on this GPU. `runs/` (checkpoints, TensorBoard logs) is
gitignored.
