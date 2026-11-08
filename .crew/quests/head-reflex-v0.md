# Quest: the foal lifts its head and looks at the world (head reflex v0)

**Game:** Prime Meridian. **Asked by:** Michael, Nov 8, watching the pony spike: "if it's laying
down it's going to try to raise its head off the ground and look at the world level, instead of
just nodding yes." **Led by:** Hal. Follows `pony-spike-v0` (commit `44a586d`). Plan of record:
`docs/ideas-animal-motion.md` §"Nov 8" (M-9): a newborn foal lifts and levels its head first,
rolls onto its chest, then tries to stand. This round is the first of those, as a hand-written
reflex. Training comes later and will reuse the same data.

## The game moment

`?pony`: the foal drops and flops as now (legs still twitch at random), but within a second or two
it **lifts its head off the grass and holds it level**, eyes on the horizon, whichever way its body
lies (on its side, its chest, half rolled). The head stays steady while the legs kick, the way a
horse's head stays still at a trot. When a kick rolls the body, the head corrects. `?pony=dog`: the
dog does the same, from the same code. No nodding.

## Decided (don't reopen)

- **It's a reflex, declared per species:** a `:reflexes` vector in the species file, e.g.
  `{:kind :head-level :body :head :joints [...] :lift-m 0.3 :gain ... :onset-s 1.5}`. A species
  without one twitches everything, as now. Nothing in code names a species or a body except
  through this data.
- **The neck and head need more freedom:** a body may carry several hinge joints (`:joints [...]`
  as well as today's single `:joint`; both forms valid, MJCF allows several joints per body). Give
  the neck pitch + side-bend and the head pitch + roll, in both species, with ranges a real foal /
  dog could manage. Re-check the masses and counts in the spec.
- **"Level" means:** the head's own up axis points to world up, and the head's lowest point is at
  least `:lift-m` above the ground plane. That's all. (No gaze target yet.)
- **How it steers: Jacobian transpose, generic for any body.** Each physics step, for the reflex
  body: MuJoCo's `mj_jacBody` gives its positional and rotational Jacobians (3 × nv each). The
  errors are `e_rot = head_up × world_up` (rotation that would level it) and `e_lift = max(0,
  lift-m − clearance)` along world up. Joint target change = `gain × (Jr^T e_rot + w_lift × Jp^T
  e_lift)` restricted to the reflex's own dofs, added to their current angles, clamped to range.
  Position actuators track the targets. The reflex's joints leave the twitch noise; every other
  joint keeps twitching. **Strength ramps** from 0 to 1 over `:onset-s` after the drop, so the
  effort shows.
- **Pure where it can be:** the reflex math takes plain arrays (head orientation matrix, clearance,
  the two Jacobians, the reflex dofs' qpos indices and ranges) and returns targets. Only the
  MuJoCo calls live in `pm.creature-spike`. `mj_jacBody(model, data, jacp, jacr, bodyId)` fills
  caller-provided arrays: the architect checks in Node how the bindings want them (typed arrays or
  embind vectors) and pins it in the spec.
- **Same controller interface:** the reflex is a controller layer over the twitch controller
  (`pm.creature-motion/act`), so a trained policy can later replace either.

## Out of scope

Rolling onto its chest, standing, a gaze target ("look at the walker"), ears, the camera, the
kit's meshes, any change to `?pony`'s URL or placement, Juniper's stand-in.

## What exists (read first)

The pony-spike-v0 run (`crew/runs/pony-spike-v0-20261108-1308/`: spec, review, manager notes),
`src/pm/creature.cljc`, `src/pm/creature_motion.cljs`, `src/pm/creature_spike.cljs`,
`src/pm/species.cljs`, `resources/creatures/horse.edn`, `dog.edn`, `scripts/creature_mjcf.clj`,
their tests (`test/pm/creature_*`), `node_modules/@mujoco/mujoco/mujoco.d.ts` (read-only). In the
Node test build, load MuJoCo with `js/require` (see `test/pm/creature_mujoco_test.cljs`: shadow's
node-test can't run `import()`). Gate `.crew/gate.sh`. Don't read `assets/private/`,
`public/kit/`. Don't edit `package.json`, `public/vendor/`, `creatures/` (Hal regenerates
`creatures/bodies/*.xml`). **Never send keys or prompts to crew members; no network; don't start
services; no whole-disk `find`; scratch files go in your own scratchpad, never `rm -rf` outside
it.**

## Done when

Gate passes with tests for: both forms of joints validate (and a bad `:joints` entry gives a clear
error); new counts and masses for horse and dog; `:reflexes` validation (unknown body/joint, bad
numbers → clear error); the reflex math on hand-built cases (a single hinge whose Jacobian is
known: a tilted head gives a target that untilts it, a level head gives no change, clamping at the
range, zero strength before onset, the lift term only when clearance < `:lift-m`); the reflex
joints excluded from twitch; and **in real MuJoCo** (Node): each species dropped on its side
(rolled 90°) with legs at zero and the reflex on, after 3 simulated seconds the head's clearance is
at least 0.7 × `:lift-m` and its up axis is within 20° of world up; with the reflex off, it isn't.
Live (Hal): `?pony` and `?pony=dog` heads lift and stay level while the legs kick; a screenshot
from whatever view Michael has (Hal doesn't move his camera).
