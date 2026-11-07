# Quest: characters on the walkers (Polybot and Astronaut, retargeted)

**Game:** Prime Meridian. **Asked by:** Michael ("ya you could bring in a CC0 one"), so the
pipeline for his brother's own crew character is proven before he models it. **Led by:** Hal.
Follows `motion-bricks-v0` and `avoid-v0`.

## The moment we're building

With `?bricks`, Un Automate is **Polybot** and Le Testeur is the **Astronaut**: real characters
(Polygonal Mind's 100 Avatars, CC0) walking on MotionBricks' generated motion, retargeted
onto their Mixamo skeletons, instead of grey mannequins. Same routes, same avoidance, same
shared model. Their size matches the motion (the port scales a character so its legs match,
otherwise the steps slide), then the whole figure gets the display scale below. Without
`?bricks`, or if a character file fails to load, the walker falls back (mannequin, or the
simple figure) and the HUD says why.

## What exists (read first)

- `public/assets/characters/{polybot,astronaut}.glb` + `LICENSE.md` (Hal copied them; CC0).
- `public/vendor/three-addons-r186/loaders/GLTFLoader.js` (MIT, unmodified) and the import map
  `"three/addons/" → /vendor/three-addons-r186/`; `public/vendor/three-kit.mjs` now re-exports
  the whole three API from the kit's THREE, so the loader builds objects with the kit's three.
  Load it like the library: `((js/Function. "u" "return import(u)") "three/addons/loaders/GLTFLoader.js")`
  after the kit is ready (bare specifiers resolve through the import map).
- The port's API (`public/vendor/three-motionbricks-0.2.0/index.mjs`):
  `character.setCharacter(gltf.scene, CHARACTER_RIGS.mixamo)` (exported `CHARACTER_RIGS`;
  Mixamo T-pose facing −Z; there's also `mixamoPlusZ`), `RetargetedCharacter`, and its
  `matchSize`. Read the code for what `setCharacter` expects with `rig: 'soma'`.
- `pm.bricks`, `pm.motion`, the avoidance planner, `resources/estate.edn` `:walkers`.
- Gate `.crew/gate.sh`. Don't read `assets/private/`, `public/kit/`, `public/state/`. Don't edit
  `public/vendor/` or `resources/content/*.edn`.

## Data

Each walker may name a character:

```clojure
{:id :un-automate-monde :name "Un Automate" ... :character {:file "/assets/characters/polybot.glb"   :rig :mixamo}}
{:id :le-testeur        :name "Le Testeur"  ... :character {:file "/assets/characters/astronaut.glb" :rig :mixamo}}
```

`:rig` names a key of the port's `CHARACTER_RIGS` (keyword → JS key: `:mixamo` → `"mixamo"`,
`:mixamo-plus-z` → `"mixamoPlusZ"`). A walker without `:character` keeps the mannequin. And
`:motion :display-scale` (default 1.0; Hal will tune it to the world's display rule, ~1.4),
applied to the whole walker object in both modes, also to the walker's radius for avoidance.

## Scope

1. Pure (tested): reading `:character` and `:display-scale` from data (valid/invalid entries,
   defaults), the rig-name mapping, the scaled radius, the HUD text when a character failed.
2. Interop in `pm.bricks`: load the GLTF loader once; load each walker's file; `setCharacter`
   instead of `setMannequin`; on any error for one walker, that walker keeps the mannequin and
   the HUD lists it; shadows like the mannequin; the name label above the character's head.
3. Display scale on the walker object (both modes) and in the planner.

## Out of scope

Facial expressions, head look, fingers, the artist's own character, sitting, foot IK.

## Done when

The gate passes with the new tests. Live (Hal checks with `?bricks`): Polybot and the Astronaut
walk the bank with generated motion, feet on the grass and not sliding, upright and facing where
they walk, name tags over their heads, still passing each other on their right; 60 fps; HUD
`motion: bricks … · 2 walkers` with no character errors.
