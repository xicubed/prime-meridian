# Ideas: natural motion for the animals

Michael, Nov 7 (end of day 21): "do you think we can get natural motion into the animals as well?
there's got to be a way", then: "what if we just rig it and it 'learns to walk' like a real pony...
at first it's unstable, it falls a lot, sits down quite a bit, naps, but slowly over time it learns
to coordinate its limbs". Saved to investigate later; nothing built yet. Tasks: M-7, M-8, M-9 in
`TASKS.md`.

## What doesn't work: MotionBricks

NVIDIA trained MotionBricks on human (SOMA) and robot skeletons only. It can't drive a horse or an
axolotl. Its idea carries over (say where the animal is going, generate the motion instead of looping
clips), but each animal needs its own method.

## Flora: procedural motion from real axolotl behaviour (M-7, the first step)

Flora is real, so her twin should behave like her. Real axolotls:

- spend most of their time still on the bottom, gills fluttering slowly;
- now and then walk along the substrate with a slow, diagonal salamander gait;
- swim a few lazy tail strokes with the legs tucked against the body;
- rise to gulp air at the surface, then drift back down.

All of it can be procedural: a travelling sine wave down the spine for swimming (amplitude growing
toward the tail), phase-offset legs for the walk (diagonal pairs), gill flutter, and a small
behaviour picker (rest / walk / swim / gulp) with realistic dwell times. The same kind of work as
`foot-ik-v0`. Real state can steer it (more active near feeding time).

## Juniper, practical route: clips + our IK (M-8)

A rigged horse with walk, trot, graze and idle clips (free CC0 animal packs have this; check the
licence and the clip list before choosing), blended by speed, with `pm.feet`-style IK so the
hooves land on the slope, plus procedural head bob and tail swish. Behaviour on top: wander, go to
the hay when hungry, graze, doze. About 90% of the way to natural for a fraction of the effort.

Research option: a learned quadruped controller like "Mode-Adaptive Neural Networks for Quadruped
Motion Control" (Zhang, Starke et al., SIGGRAPH 2018; trained on dog motion capture). Possible,
but a research project.

## Juniper learns to walk (M-9, the big one)

Physics-based reinforcement learning, the way DeepMind's and NVIDIA's simulated animals learn:

1. **A simulated body:** rigid capsules for torso, neck, head and legs, hinge/ball joints with
   limits, joint motors (PD targets). The visible horse mesh follows the simulated body.
2. **A small policy network** (an MLP) reads the body's state (joint angles and speeds, body tilt,
   foot contacts) and sets the motor targets. It's rewarded for staying upright, moving forward at
   a target speed and using little energy, and penalised for falling.
3. **Training off-screen on the Omarchy box's RTX 5070 Ti:** GPU physics (MuJoCo MJX, Brax, or
   Isaac Lab) runs thousands of copies in parallel with PPO; a basic walk takes hours, not weeks.
   Needs the box online and Michael's OK to install the tools.
4. **Checkpoints as growing up:** save the policy along the way (hour 1: flops; hour 3: stands,
   wobbles, sits down hard; hour 8: stumbles forward; later: walks). The game plays the checkpoint
   that matches Juniper's age, so over real days he gets visibly steadier. Genuinely learned
   motion, paced so the progression shows.
5. **A behaviour layer for pony life:** after many falls or a long day he sits down or naps;
   scripted *when*, while the physics does the falling.
6. **In the browser:** the trained network is a few small matrix multiplies per frame; physics via
   MuJoCo's WebAssembly build (or Rapier, already on the list for M-4 ragdolls), matching the
   training simulator as closely as possible.

Known catch: RL policies invent odd, jittery gaits ("the RL walk"). Fix it later with an imitation
reward against real horse motion capture (DeepMimic-style), which fits the story: as a foal he
finds his legs, as he grows he moves like a horse.

**Cheapest first test (spike):** a capsule pony in browser physics with no brain, joint motors
twitching at random. That alone is a foal that can't stand, and it shows whether the physics side
works in our world (ground contact on the kit's terrain, stability, cost per frame) before any
training.

Related: M-3 (riding Juniper) needs him walking first; M-4 (Rapier ragdolls) shares the physics
engine; M-5 (bump and recover) shares the "physics reacts, motion recovers" pattern.
