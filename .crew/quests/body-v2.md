# Quest: a body that can get up (body v2: shoulders, hips and a spine)

**Game:** Prime Meridian. **Asked by:** Michael, Nov 9, watching the first get-up training run on
omarchy go nowhere: "idk if it would help if his body was more flexible". **Led by:** Hal. Plan of
record: `docs/ideas-animal-motion.md` (M-9). Follows `effort-v0`; feeds the next training run
(`foal-gym-v0`, run 2).

## Why

Run 1 trained 9,500 iterations and the foal never got his torso up. One reason is his body, read
straight from `resources/creatures/horse.edn`:

- Every leg joint is a hinge on the same axis `[0 1 0]`: legs only swing **forward and back**. Lying on
  his side, that swing is parallel to the ground, so his legs can't push into it. The only way to
  roll onto his chest is flinging his neck (the neck has a side bend).
- Upper legs swing only **−45°…45°**: he can't fold his legs under his chest the way a foal does
  before pushing up.
- The torso is **one rigid capsule**: no spine to bend or twist while rolling.

A real foal gets up by rolling onto his chest, tucking his legs under, pushing up with the front
legs, then the hind. Give the body the joints that motion needs; learning it is training's job.

## Decided (don't reopen)

1. **Sideways at each shoulder and hip.** Each upper leg gets two hinges (`:joints`, nearest the
   torso first): a **splay** (abduction/adduction, axis `[1 0 0]`) then the existing **swing**
   (axis `[0 1 0]`). Splay range about **−25°…25°**, sign chosen so + moves the foot outward on both
   sides (say how in the spec). Joint names follow the body: `:fl-splay`, `:fl-swing`, … (the lower
   joints keep their names).
2. **Wider swing:** upper legs about **−60°…75°** (forward/back as now; the spec states which sign
   folds the leg under the chest for front and hind, checked in MuJoCo).
3. **A spine.** Split the torso capsule into a front half and a hind half joined at the middle by
   two hinges: **spine-bend** (side to side, axis `[0 0 1]`, about ±25°) and **spine-twist** (roll,
   axis `[1 0 0]`, about ±20°). The front half stays the root body and keeps the name `:torso` (the
   game's HUD, the rest/limp test and `creature_spike` read the root body as "torso"); the hind half
   is a new body (`:hind`) that the hind legs hang from. Total mass and length stay within 5 % of
   today's.
4. **Same for the dog**, with its own numbers in `dog.edn` (the code is shared; no species names in
   code). Both species stay valid and both bodies are regenerated with `bb creature-mjcf`.
5. **Everything else that reads joints adapts by count, not by name:** the twitch controller, rest
   stiffness and kicks (new joints rest at `:rest-kp` like the rest), the head reflex (unchanged: it
   only drives the neck and head joints), the HUD. Joint count goes from 12 to 18 for each species.

## The strength question (measure, don't guess)

Before training again we need to know whether this body can **hold a stand** at its actuator
strength (`:kp` 150, no force cap on leg actuators today). Add a real-MuJoCo test: place the body
standing (torso centre at standing height, legs straight under it, all targets 0 at full `:kp`),
simulate 5 s, and report the torso height and tilt at the end. If it can't hold (torso < 0.7 m or
tilt > 20°), **don't silently raise `:kp`**: find the smallest `:kp` (in steps of 25) that holds, write
it in the spec and the run log as a finding, and leave `horse.edn`'s `:kp` for Michael to decide.

## Out of scope

Training, rewards, the omarchy task code (Le Coureur adapts it after this round), terrain under the
foal (separate round), walkers avoiding the foal (`creature-obstacle-v0`), visuals beyond the
capsules following the new joints.

## What exists (read first)

`resources/creatures/{horse,dog}.edn`, `src/pm/creature.cljc` (species parse/validation, joints,
masses, rest-bottom), `scripts/creature_mjcf.clj` (`bb creature-mjcf`), `creatures/bodies/*.xml`
(generated, never hand-edit), `src/pm/creature_motion.cljs`, `src/pm/creature_spike.cljs` (root body
xpos is read as the torso), tests `test/pm/creature_test.cljs`, `creature_effort_test.cljs`,
`creature_mujoco_test.cljs` (MuJoCo via `js/require`). The effort-v0 run log for how the limp test is
done. Gate `.crew/gate.sh`. Don't read `assets/private/`, `public/kit/`. **Never send keys or prompts
to crew members; no network; don't start services; no whole-disk `find`; scratch only in your own
scratchpad.**

## Done when

Gate passes, with tests for: both species parse and validate with 18 joints (names, axes, ranges in
radians); the generated MJCF has the splay, swing and spine joints with their actuators; total mass
within 5 % of before; a **kinematic** check that, lying on his side, +splay on the down-side legs
moves the hoof toward the ground (numbers by hand); the **existing** real-MuJoCo checks still pass
(drop, head lift with effort, limp rest with the torso's lowest point within 2 cm of the ground); and
the **stand-hold** measurement above, with its numbers in the spec and run log. Live (Hal): `?pony`
and `?pony=dog` still load, flop and lift their heads; the twitch now also wiggles shoulders and the
spine.
