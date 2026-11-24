# Builder note — booth-model-v0 (TASKS T-18)

What was built: `src/pm/models.cljs` (new, pure: `model-spec`,
`material-override`, `material-props`). `src/pm/plan.cljs`'s
`phone-booth-item` now carries `:model` (`md/model-spec`) when
`:phone-booth` has a usable one, else no `:model` key; new
`model-label-at` raises the sign to clear the model's top, never lowers
it. `src/pm/three.cljs` gained `load-model` (cached `GLTFLoader.loadAsync`
per URL), `model-meshes` (flattens the parsed gltf into active-THREE
meshes: geometry copied attribute-by-attribute with the source's
`matrixWorld` baked in, materials rebuilt as `MeshStandardMaterial` via
`pm.models/material-props`, base-colour maps rebuilt as active-THREE
textures) and `add-y!`; `light-glows!`/`unlight-glows!`/`sync-glows!` now
key off `userData.lit` (set regardless of whether a mesh gets its own
light) instead of `userData.light`, so a `:light false` glow (the
TELEPHONE signs) still gets its own material and follows the lantern's
emissive at dusk without costing a point light -- a mesh without the new
userData (the lamp post bulb) behaves exactly as before. `src/pm/scene.cljs`'s
`mount-item!` kicks off `(t/load-model url)` for an item with `:model` and
hands the result to the new private `swap-in-model!`, which removes and
disposes the look's meshes, adds the model's meshes under the same group
(same name/key -- clicks still resolve), applies `:kit`/`:glow` overrides
per mesh name, re-ghosts/re-lights, and raises the sign if needed.
`resources/estate.edn` gained `:phone-booth`'s `:model`.

What Hal should check headless:
- The booth in the kit: the red box replaces the three boxes at the same
  spot and yaw, door facing as before, standing on the ground (no
  `:scale`); console shows no warning.
- The panes: the model's own semi-transparent glass; the phone visible
  inside; nothing behind the glass vanishes (depthWrite off).
- Jacked out (click the booth): the sign shows the reason above the roof,
  not inside it; click it again: jacked back in.
- Night: the ceiling lamp glows and lights the inside, a soft pool on the
  ground through the glass, reaching about 3 m; the four TELEPHONE panels
  glow but add no light (one new point light for the booth); by day
  neither shows. The lamp post bulb still works as before.
- A broken URL (try it in a scratch copy, not in the repo): the boxes
  stay, one console warning.

Gate: 0 warnings, 1397 tests (46 new, `pm.booth-model-test`).
