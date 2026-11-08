# Quest: walkers go around the foal (creature obstacle v0)

**Game:** Prime Meridian. **Asked by:** Michael, Nov 8: "Un Architecte is standing on/in the
foal. Is there no collision detection between them, like between agents? ... we just need to make
the walkers avoid it; the foal can't do anything about it yet." **Led by:** Hal. Follows
`effort-v0`. Placement stays as is (`:creature-spike :at`): don't move the foal.

## The game moment

`?pony`: the foal lies, flops and lifts his head on the east bank. Un Architecte, Le Coureur and the
rest walk their routes as now, but **steer around him**, the way they steer around each other:
they see him (he's in their sight cone) or hear him (close, or kicking), notice after their usual
reaction delay, and pass at a respectful distance. When a kick rolls him into a new spot, they
adjust. Nobody walks through him. Without `?pony` nothing changes.

## Decided (don't reopen)

- **The foal is a moving obstacle the walkers perceive, not a static footprint.** Each frame,
  the creature spike publishes one circle on the ground: centre and radius covering every body's
  capsule in the ground plane (from MuJoCo's body positions + capsule radii, converted to the
  game's x/z), plus its velocity (from the torso). Pure function, tested.
- **Perceived like another walker:** it goes through the same senses (sight cone, hearing scaled
  by its speed, reaction delay, forgetting) as walkers do (`pm.motion` perceive/noticed), and the
  planner treats a noticed one like another walker's circle. It is never planned itself (it has no
  route) and walkers never push it (no contact yet; that's M-5).
- **Plumbing:** `pm.motion`'s `plan-all` / `replan` / `advance` take an optional list of extra
  perceived circles; the walker code path is unchanged when the list is empty (existing tests keep
  passing). `pm.bricks` (which drives the walkers) asks `pm.creature-spike` for the current circle
  each tick. No species names anywhere.
- **`?paths`** (the existing debug overlay) also draws the foal's circle.

## Out of scope

Physical contact (walkers shoving the foal), the foal reacting to walkers, the terrain under the
foal, moving the foal, kinematic `?kinematic` walkers if they don't already use the planner (say in
the spec).

## What exists (read first)

`src/pm/motion.cljs` (plan, plan-all, perceive, noticed, replan, advance), `src/pm/avoid.cljs`,
`src/pm/bricks.cljs`, `src/pm/creature_spike.cljs`, `src/pm/creature_motion.cljs`,
`src/pm/creature.cljc`, `resources/estate.edn` (`:walkers`, `:motion`, `:creature-spike`), the
senses-v0 and avoid-v0 quests, their tests. Gate `.crew/gate.sh`. Don't read `assets/private/`,
`public/kit/`. **Never send keys or prompts to crew members; no network; don't start services; no
whole-disk `find`; scratch files only in your own scratchpad.**

## Done when

Gate passes with tests for: the circle from body positions (a lying and a curled pose, hand
numbers; z-up → game x/z); a walker whose route runs through a perceived circle detours around it
and never comes within its radius + the walker's radius; one coming from behind it with sight only
(silent, still foal) doesn't react until it can see or hear it; an empty extra list gives exactly
today's plans (old tests unchanged); and a lap simulation on the real estate routes with the foal at
its `:creature-spike` spot where no walker enters the foal's circle. Live (Hal): `?pony` and
`?paths`, Un Architecte goes around the foal; screenshot from Michael's own camera.
