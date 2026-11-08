# Quest: a foal that can't stand yet (pony spike v0)

**Game:** Prime Meridian. **Asked by:** Michael, Nov 8 ("can we apply that method to our horse";
"someday someone has a pet, say lobster ... maybe we do want these bits integrated"). **Led by:**
Hal. Plan of record: `docs/ideas-animal-motion.md` §"Nov 8: the method, the tools, and a creature
pipeline" (M-9, TASKS M-10). This is its browser spike: **no brain, no training.**

## The game moment

Open the game with `?pony`. On the east bank a capsule foal drops onto the grass and flails: its
joints twitch at random, it can't stand, it flops about, it never sinks into the ground or
explodes. `?pony=dog` drops a dog instead, from the same code path. The HUD says what the physics
costs. Without the flag the game is unchanged.

## Decided (don't reopen)

- **Physics is MuJoCo in the browser** (`@mujoco/mujoco` 4.0.0, the same MuJoCo the trainer in
  `creatures/` uses, so a policy trained there will run here). Hal already installed it: npm
  postinstall copies `mujoco.js` + `mujoco.wasm` to `public/vendor/mujoco/`, and the import map in
  `public/index.html` maps `"mujoco"` to it. Load it at runtime the way `pm.bricks` loads its
  libraries (`((js/Function. "u" "return import(u)") "mujoco")`), then `(.default mod)` returns a
  promise of the module. API that works (Hal checked it in Node): `(.from_xml_string (.-MjModel mj)
  xml)`, `(new (.-MjData mj) model)`, `(.mj_step mj model data)`, `(.-qpos data)`, `(.-ctrl data)`,
  `(.-xpos data)` / `(.-xquat data)` (flat arrays, 3 / 4 per body), and `.delete` on model and data
  when done (they live on the WASM heap). Timestep 0.002 s.
- **A species is data:** `resources/creatures/horse.edn` and `resources/creatures/dog.edn`, read
  by one pure namespace that turns a species into a MuJoCo model (MJCF XML string). Nothing in code
  knows "horse" or "dog". The same XML is what the trainer will use later, so add a bb task
  `bb creature-mjcf <kind>` that prints it (`creatures/bodies/<kind>.xml` is where Hal will save it).
- **Body shape (both species):** torso capsule; neck and head; four legs, each an upper and a
  lower segment; hinge joints with limits (hips/shoulders, knees/elbows, neck pitch, a little torso
  roll is NOT needed); one position or motor actuator per joint; a free joint on the torso. Real
  proportions in metres: a **foal** (~1.0 m at the withers, ~0.9 m body), and a mid-size **dog**
  (~0.55 m at the shoulder). Masses from capsule volume × a density in the species file. Collision
  only between the body and the ground (`contype`/`conaffinity` so the legs don't snag each other).
- **The twitch controller:** each actuator gets smooth random noise in [-1, 1] (a seeded value
  that eases toward a new random target every 0.3–0.8 s), scaled by the joint's range. Seed from
  the URL (`?pony=horse&seed=3`) so a flop can be replayed. Pure and tested.
- **Fixed physics step:** accumulate frame dt, step MuJoCo in 0.002 s steps, at most 25 steps per
  frame (drop the rest and count them), so a slow frame never spirals. Pure and tested.
- **Where:** the east bank, base at **[4.5 1.79 -8.0]** (Hal probed it: open ground, terrain at
  1.79 m; it's near Le Coureur's loop, which is fine for a spike). Data in `resources/estate.edn`
  as `:creature-spike {:at [4.5 1.79 -8.0] :yaw 0 :drop-m 0.4}`: MuJoCo's world is a flat ground
  plane at the terrain height under the spot (a 4 m square is plenty); the body starts `:drop-m`
  above it. MuJoCo is z-up, three.js is y-up: convert in one place and test it.
- **Drawing:** each MuJoCo body becomes a group of capsule parts in the game's existing look-part
  style (`pm.plan` parts, colours from the species file), added through the `pm.three` wrapper
  only. Feature code never calls `THREE/...`. Positions and rotations come from `xpos`/`xquat` every
  frame (reuse the objects, never rebuild).
- **HUD (the liveness check):** `pony: horse · 0.9 ms/frame · 8 steps · 0 dropped · torso 0.31 m`
  (physics ms averaged over the last second; torso height above the ground plane). Shown only with
  `?pony`.
- **One motion-controller interface:** fit the spike into how `pm.motion` drives characters (say
  how in the spec), so the trained policy can later replace the twitch controller without touching
  the rest.

## Out of scope

Training, policies, ONNX, terrain-following (a flat plane is fine), contact with walkers or the
kit's meshes, Juniper's existing stand-in (unchanged), sound, any URL other than `?pony`.

## What exists (read first)

`src/pm/bricks.cljs` (runtime `import`, interop style), `src/pm/motion.cljs`, `src/pm/plan.cljs`
(look parts), `src/pm/three.cljs` (the wrapper), `src/pm/scene.cljs`, `src/pm/core.cljs` (frame
loop, URL flags, HUD), `resources/estate.edn`, `bb.edn`, `public/index.html` (import map),
`node_modules/@mujoco/mujoco/mujoco.d.ts` (the API, read-only), `creatures/README.md`,
`docs/ideas-animal-motion.md`. Gate `.crew/gate.sh`. Don't read `assets/private/` or `public/kit/`.
Don't edit `package.json`, `public/vendor/`, `resources/content/`, `creatures/`. **Never send keys
or prompts to crew members; no network; don't start services.**

## Done when

Gate passes with tests for: both species files validate and a broken one gives a clear error;
species → MJCF (body count, joint count and ranges, actuator count, masses > 0, ground plane at the
spot's height) for horse **and** dog; the z-up → y-up conversion; the fixed-step accumulator (incl.
the 25-step cap and dropped count); the twitch noise (seeded and repeatable, always in [-1, 1],
eases between targets); and **the generated XML compiles in real MuJoCo** (a Node test loads
`node_modules/@mujoco/mujoco`, builds both species, steps 2 simulated seconds with zero controls,
and the torso comes to rest above the ground: no NaN, no sinking below the plane). Live (Hal):
`?pony` shows a flopping foal on the east bank, `?pony=dog` a dog, the HUD numbers, and a
screenshot.
