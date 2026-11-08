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

### Nov 8: the method, the tools, and a creature pipeline

Michael found NVIDIA's walkthrough of training a robot dog to walk
([Isaac Lab + Newton](https://developer.nvidia.com/blog/train-a-quadruped-locomotion-policy-and-simulate-cloth-manipulation-with-nvidia-isaac-lab-and-newton/)):
it is this plan. What it adds:

- **Train → Sim2Sim → deploy.** Their robot's policy must still walk in a second physics engine
  before it goes on hardware. Ours "deploys" to the browser, so train and run in the same physics
  family: **MuJoCo Warp** on the GPU box, **MuJoCo's WebAssembly build** in the game (same model
  file, same contact model). That argues for MuJoCo over Rapier for animals; Rapier can still do
  the people's ragdolls (M-4).
- **Only what the body can feel:** their policy sees only the robot's own sensors. Juniper's
  policy likewise sees only his own body (joint angles, tilt, foot contact), which fits
  per-walker perception becoming personality.
- **Tools (checked Nov 8):** Isaac Lab's Newton backend is still an experimental branch; Newton
  itself is stable (1.6.1). **MuJoCo Playground 1.0** (Google DeepMind; MuJoCo Warp + rsl_rl PPO
  on PyTorch) is the light option, but 1.0 dropped the Go1/Spot quadruped tasks, so Juniper's
  environment is ours to write on its base. The trained network is a small MLP exported to ONNX;
  the game already runs onnxruntime-web for MotionBricks.

Prior work: Red Dead Redemption's horses are motion capture of real horses (a stunt horse,
separate sets with and without a rider) blended and IK'd on terrain, with physics only for
reactions: the look to aim for, not the method. Learned animals: DeepMimic (Peng 2018) and
"Learning Agile Robotic Locomotion Skills by Imitating Animals" (Peng 2020, dog mocap → robot);
DeepMind's dm_control **dog**, virtual rodent and flybody (whole animals in MuJoCo);
AI4Animation's mode-adaptive dog (Zhang, Starke et al. 2018, kinematic); **kine2go** (2026:
retargets horse and dog mocap onto a quadruped and trains imitation PPO, almost our pipeline).
Horse motion data for the imitation stage: the Vienna Horse Data Collection (walk/trot, CC BY-SA:
credit, share derived data alike) and PFERD (Max Planck, 5 horses, 100+ markers, walk to rearing
and kicking; licence not checked yet). For animals with no mocap (Michael's example: a player's
pet lobster), a coding agent can draft the reference motion (MimicAgent, 2026).

**A creature pipeline, not a horse script** (Michael: "someday someone has a pet lobster"):
everyone's game, so a species is data and the pipeline is part of the game.

1. A species file (`resources/creatures/<kind>.edn`): body segments and joints, motion method,
   rewards. A new pet is a new file. The player's twin drafts it once from words ("a lobster"),
   the player approves it like a blueprint (compiled once, never interpreted per frame).
2. The motion method per species, behind the one motion-controller interface: procedural
   (Flora's spine; a lobster's ten legs on a rhythm generator), learned (physics RL, for animals
   that can fall over: Juniper), or clips.
3. Training is a job for whichever machine has a GPU (`creatures/`, a `uv` Python project; a
   `bb creature train <kind>` task on the Mac ships the species there and copies back the body +
   `policy.onnx`). Players without a GPU get pre-trained species from a shared library.
4. Two species from day one: horse, and a dog (checked against dm_control's dog).

Order: GPU smoke test on the box (`creatures/smoke.py`) → MuJoCo WebAssembly capsule pony in the
browser (the no-brain spike above) → the species file + our env → train the foal, checkpoints by
age → imitation stage on horse data.

Related: M-3 (riding Juniper) needs him walking first; M-4 (Rapier ragdolls) shares the physics
engine; M-5 (bump and recover) shares the "physics reacts, motion recovers" pattern.
