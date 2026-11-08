# Quest: lifting his head is hard work (effort v0)

**Game:** Prime Meridian. **Asked by:** Michael, Nov 8, watching head-reflex-v0: "his head is up,
just stationary, not like it's struggling to raise." **Led by:** Hal. Follows `head-reflex-v0`
(commit `13494af`). TASKS M-12.

## The game moment

`?pony`: the foal lands and flops. Then he **tries** to lift his head: it comes up slowly, shaking,
sometimes not all the way; he holds it a few seconds with a visible tremor, tires, lets it sink to
the grass, rests, and tries again. Over a minute or two awake the tremor fades and the holds get a
little longer (he's steadying). `?pony=dog`: same code, the dog's own numbers (a grown dog: strong
and steady, so little tremor and long holds; that's the second example).

## Decided (don't reopen)

- **All of it is species data**, an `:effort` map inside the reflex entry, e.g.
  `{:strength 0.35 :tremor-deg 6 :steady-s 90 :energy-s 6 :recover-s 4 :low 0.15 :high 0.6 :seed-salt 7}`.
  A reflex without `:effort` behaves exactly as now (the head-reflex-v0 tests keep passing).
- **Weak muscles:** `:strength` (0–1) caps how hard the reflex can push: scale the reflex's gain
  and clamp each reflex actuator's force to `strength × its force range` (MJCF `forcerange` /
  `forcelimited` on those actuators, generated from the species data). A weak neck lifts slowly and
  may stall below `:lift-m`.
- **Tremor:** seeded smooth noise (reuse the twitch noise) added to the reflex targets, amplitude
  `:tremor-deg` that decays exponentially with time awake, time constant `:steady-s`.
- **Fatigue (energy with hysteresis):** energy 1 → 0. While the reflex is on, it drains at a rate
  set by the reflex actuators' actual effort (sum of |actuator force| / max force, from MuJoCo's
  `actuator_force`), so lifting from flat costs more than holding level; a full-effort hold
  empties it in `:energy-s`. Below `:low` the reflex lets go (strength ramps to 0 over ~0.5 s, the
  head sinks under gravity). Resting, energy refills in `:recover-s`; above `:high` the reflex
  ramps back in and he tries again. Pure state, tested; `now`/time are arguments.
- **Limp when not trying (Michael, Nov 8: "there's space under his ribcage; all the joints
  need to touch the floor when prone, not just the feet").** Today a zero control means "hold the
  joint straight" at full stiffness, so the settled foal stands on straight legs like a table with
  his torso in the air. Muscles that aren't working must be soft: every joint's stiffness comes
  from data (`:kp` as the strong value, `:rest-kp` per species, e.g. 8–15 % of `:kp`), the twitch
  works at rest stiffness except during a kick, and the reflex joints at rest stiffness while he
  rests. Lying down, his legs flop or fold and **his torso and legs lie on the ground**: test it.
  (Ground mismatch with the sloped terrain is a later round; keep the flat plane.)
- **HUD:** the creature line gains `· energy 0.42 · trying|holding|resting` with `?pony`.

## Out of scope

Rolling onto the chest, standing, the legs (still twitching), sound, gaze targets, age/checkpoints
(the `:steady-s` decay is per page load for now).

## What exists (read first)

The head-reflex-v0 run (`crew/runs/head-reflex-v0-20261108-1435/`), `src/pm/creature.cljc`,
`src/pm/creature_motion.cljs`, `src/pm/creature_spike.cljs`, `resources/creatures/*.edn`, their tests
(`test/pm/creature_*`), `node_modules/@mujoco/mujoco/mujoco.d.ts` (read-only). Node tests load MuJoCo
with `js/require` (see `test/pm/creature_mujoco_test.cljs`). Gate `.crew/gate.sh`. Don't read
`assets/private/`, `public/kit/`. Don't edit `package.json`, `public/vendor/`, `creatures/`.
**Never send keys or prompts to crew members; no network; don't start services; no whole-disk
`find`; scratch files only in your own scratchpad.**

## Done when

Gate passes with tests for: `:effort` validation (bad numbers, `:low` ≥ `:high` → clear error);
the generated actuators' force limits; the energy state machine on hand cases (drain at full and
half effort, the hysteresis: no flapping between `:low` and `:high`, recovery time, ramp out/in);
tremor amplitude at t=0, t=`:steady-s` (×1/e) and its seeded repeatability; a reflex without
`:effort` unchanged; and **in real MuJoCo** (Node): the horse dropped on its side with legs at zero,
over 30 simulated seconds the torso capsule's lowest point stays within 2 cm of the ground once settled (no table legs; same for the dog), the head clears `:lift-m` at least twice with a rest (clearance below
half of `:lift-m`) in between; the dog, with its own numbers, holds for at least 20 of the 30 s.
Live (Hal): `?pony` shows tries, shaky holds and rests; Michael's camera is not moved (it's
remembered across reloads now).
