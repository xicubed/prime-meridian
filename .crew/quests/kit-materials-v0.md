# Quest: our things wear the kit's materials (TASKS T-17)

**Game:** Prime Meridian. **Asked by:** Michael, Nov 23: "can we texture these items? they're
kinda plain" (the benches and lamp post are flat-coloured boxes). He chose: borrow the kit's own
textured materials and round the box edges, so our things match the cottage and steps.
**Led by:** Hal. Michael reviews by eye.

## What exists (read first)

- `src/pm/three.cljs`: the one three.js wrapper; parts are built from `{:shape :size :at :rot
  :color}` (`:box` via `BoxGeometry`, plus cone/sphere/cylinder/capsule) with
  `MeshStandardMaterial` of a palette colour. `use-three!` routes it through **the kit's THREE**
  (`window.hiroshi.THREE`): never import npm `three` or `three/examples` objects into the kit's
  scene (two module graphs break materials). `ghost!`/`unghost!` store each part's real colours
  in `userData` and restore them; `light-glows!`/`unlight-glows!`.
- `src/pm/scene.cljs` `palette` (`:brown`, `:tin`, `:gold`, `:booth-red`, `:glass`,
  `:stand-in`, …) and `color`.
- The kit's loaded scene has these textured materials (Hal checked headless, each with a 627 px
  colour map, normal map and roughness map, `RepeatWrapping`): `MAT_Hiroshi_Timber`,
  `MAT_Hiroshi_VerandaCedar`, `MAT_Hiroshi_Stone`, `MAT_Hiroshi_Plaster`, `MAT_Hiroshi_Roof`;
  plus untextured `MAT_Hiroshi_Metal`, `MAT_Hiroshi_Lamp`, `MAT_Hiroshi_Dark`. Find a material
  by traversing `hiroshi.scene` meshes once (`material.name`); names with " / wind" are the
  kit's animated node materials: never use those.
- The kit is private (`public/kit/` gitignored): public clones run without it (the scaffold),
  so every look must still work with flat colours.
- Conventions: `.crew/CONVENTIONS.md`; gate `.crew/gate.sh`; `AGENTS.md`. Don't read
  `assets/private/`, `public/kit/` (the material names above are all you need). **Never send
  keys or prompts to crew members; don't start services or the bridge; no whole-disk `find`;
  scratch files only in your own scratchpad; never write `state/` from tests.** No npm deps.

## Decided (don't reopen)

- **The mapping is data**, in `resources/estate.edn`:
  `:kit-materials {:brown {:name "MAT_Hiroshi_Timber" :tile-m 0.6}
                   :tin   {:name "MAT_Hiroshi_Metal"}}`
  (palette key → kit material name, optional `:tile-m` = metres one texture repeat covers).
  Only these two to start; any other palette key stays its flat colour. Agent blueprints get
  textures for free because they use the same palette keys.
- **Per-part material, never the kit's shared one:** each part that maps gets a `.clone()` of the
  kit material, and (when it has maps) clones of its `map`/`normalMap`/`roughnessMap` with
  `repeat` set from the part's size / `:tile-m` along the face's two axes (a pure fn computes
  the repeat from `:size` and `:tile-m`, tested), so a thin leg doesn't stretch the grain. The
  part's palette colour is not multiplied in (the texture carries the colour).
- **Ghosts still work:** `ghost!` must save and replace whatever material a part has (textured
  or flat) and `unghost!` must put back exactly that material, so the neon-blue and red ghosts
  look as today and a built bench shows its wood again. Glow parts (`:glow true`) keep today's
  emissive behaviour.
- **Rounded edges for `:box` parts:** built with the kit THREE's own classes only (e.g.
  `ExtrudeGeometry` of a rounded-rect `Shape` with `bevelEnabled`, or another construction from
  core THREE classes), radius `min(0.02, smallest dimension / 4)` m, origin and size identical to
  today's box (a pure fn for the radius, tested). Same UV convention so the tiling fn applies.
  If no clean construction exists with core classes, keep `BoxGeometry` and say so in the notes
  (don't import examples).
- **Fallback:** before the kit is up, with no `:kit-materials`, or when a named material isn't
  found, parts use today's flat palette colour; nothing throws and the console warns once per
  missing name.
- **Clicks unchanged:** raycasts still hit the parts (ghost edges stay non-raycastable).
- **Everyone's game:** names are data; nothing names a blueprint, person or pet in code.

## Out of scope

Replacing primitives with real models; texturing walkers, creatures or the phone booth
(`:booth-red`, `:glass` stay painted); new materials of our own.

## Done when

Gate passes with tests for: the repeat fn (box faces from `:size` and `:tile-m`; no `:tile-m` =
1×1); the bevel radius fn; the lookup of a part's kit material (mapped key, unmapped key, no
`:kit-materials`, wind materials ignored) as a pure fn over a name→material map. The builder
leaves a note in the run dir: what was built and what Hal should look at headless (the creek
bench and lamp post close up, a ghost, and Undo back to wood).
