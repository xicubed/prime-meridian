# Quest: mirrors v0 (Flora and Juniper in the world)

**Game:** Prime Meridian (`docs/GDD.md` §2.5 mirrors, board item W2-7, C-4).
**Asked by:** Michael. **Led by:** Hal.

## The moment we're building

Today the game knows Flora is hungry, but you only see it in the HUD text.
After this round you see it in the world: Flora is in the creek by the stone
canal, an "I'm hungry" bubble floats over her, and her worm jar stands on the
bank with its sign. Press F and the bubble disappears and the jar's count
drops. Juniper the horse stands in the meadow by the flowers with his own
bubble, from the same code (nothing in code may know what Flora or Juniper is).
Stand-in shapes only; real models come later.

## What exists today (read these)

- `src/pm/kit.cljs`: the world is the licensed Hiroshi's Island kit, booted
  from `/kit/`. When ready it hands back `h` = `window.hiroshi`, with
  `(.-THREE h)`, `(.-scene h)`, `(.-camera h)`, `(.-renderer h)`.
  **Everything added to the kit's scene must be built with the kit's THREE**
  (`(.-THREE h)`), never with the npm `three` our wrapper imports: two
  three.js copies on one page break materials. Don't read the kit's files
  under `assets/private/` or `public/kit/` (licensed; everything you need is
  in this quest).
- `src/pm/three.cljs`: our interop wrapper (scaffold scene, npm `three/webgpu`).
- `src/pm/core.cljs`: `start-kit!` (kit present) vs `start-scaffold!` (no kit,
  a cube); the HUD; the F key; `!world` holds the loaded estate.
- `src/pm/world.cljs`: pure rules, including `(markers world mirror-id now)`
  and `(status-line ...)`. `resources/estate.edn`: the mirrors and `:sounds`.
- The gate is `.crew/gate.sh` (compile app + test, 0 warnings, tests pass).

## Scope (this round)

1. **`pm.three` uses the kit's THREE when there is one**: a way to switch the
   wrapper to another THREE at runtime (called from `start-kit!`), with every
   constructor in the wrapper going through it. The scaffold keeps working
   unchanged with npm three. Add the few helpers this quest needs (a group, a
   few stand-in shapes, a text label that always faces the camera).
2. **Placement as data** in `resources/estate.edn`: each mirror gets a `:home`
   position and a `:look` (stand-in shape, size, colour). Code reads the look;
   it never branches on `:model`. World coordinates are the kit's metres
   (y is up):
   - Flora, in the creek near the stone canal, at the water surface:
     `[-0.5 2.4 -27.0]`; stand-in about 0.5 m long (twice her real size so
     you can see her), pink.
   - Flora's worm jar, on the bank: `[1.8 2.6 -27.0]`; a small jar shape. Its
     sign is where the `:sign` marker shows.
   - Juniper, in the meadow by the flowers: `[7.0 1.45 11.0]`; about horse
     sized (2 m long, 1.6 m tall), brown.
   Put the jar either as part of Flora's mirror (e.g. a second placed item) or
   as its own entry; the spec decides, but the sign must come from Flora's
   rules.
3. **A pure scene plan** (no three.js, unit-tested on Node): from the world
   and `now`, what should be in the scene: each mirror's placed items with
   position, look, and the label text to show (bubble from `:say`, sign from
   `:sign`), empty when no marker holds. Test both mirrors, fed and hungry,
   low and full stock.
4. **`pm.scene`** (three.js side, through the wrapper only): build each
   mirror's objects once into the kit's scene, then each frame update only
   the labels whose text changed (no rebuilding meshes per frame). Labels sit
   above their item. The HUD gains a short readout of what's placed (e.g.
   `scene: 2 mirrors, 3 labels`) so the scene can be checked without seeing it.
5. Without the kit (scaffold), mirrors are not placed; the scaffold is unchanged.

## Out of scope

Real models (Flora's rig, KayKit), animation, swimming, picking with the mouse,
the barn, camera moves.

## Done when

`.crew/gate.sh` passes; the plan is tested for both mirrors; in the running
game with the kit, the HUD readout shows both mirrors placed, and pressing F
changes Flora's bubble and jar label (Hal checks this live).
