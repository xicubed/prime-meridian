#!/usr/bin/env python
"""Trains the foal to get up.

    uv run python train_foal.py --logdir runs/foal-getup
    uv run python train_foal.py --logdir runs/foal-getup --overrides='{"max_iterations": 50}'
    uv run python train_foal.py --logdir runs/foal-getup-r2 --env_overrides='{"variant": "v2"}'

Registers "FoalGetUp" (foal/task.py) with MuJoCo Playground's env registry,
then runs Playground's RSL-RL trainer against it.
"""
import sys

from absl import app

import foal.task  # noqa: F401  (side effect: registers "FoalGetUp")
from mujoco_playground.rl.rsl_rl import train as rsl_train

if __name__ == "__main__":
    # Note: must match only the "--env" flag itself, not "--env_overrides"
    # (a prefix match on "--env" wrongly treats that as "--env already given"
    # and silently leaves the trainer on its default env).
    if not any(a == "--env" or a.startswith("--env=") for a in sys.argv[1:]):
        sys.argv.insert(1, "--env=FoalGetUp")
    app.run(rsl_train.main)
