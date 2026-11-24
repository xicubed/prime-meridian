# Builder note — kit-materials-v0 (TASKS T-17)

What was built: `src/pm/materials.cljs` (new, pure: `index-materials`,
`props-item?`, `material-for`, `box-face-repeats`, `shape-repeat`,
`bevel-radius`, `round-box-vertex`, `missing-names`). `pm.three/stand-in`
got two new opts (`:bevel`, `:kit`) and stayed byte-for-byte today's
placeholder without them; new `material-pairs`, `rounded-box-geometry`
(core `BoxGeometry` whose attributes are rewritten per `round-box-vertex`
— no `three/examples` needed, so nothing was dropped to plain `BoxGeometry`)
and `kit-part-material`. `ghost!`/`unghost!` now save/restore the mesh's
*real* material object (single or array, textured or flat) in
`userData.ghostMat` instead of recolouring a clone in place; the ghost
material itself is a fresh `MeshStandardMaterial` as before. `pm.scene`
builds the kit-material index once in `mount!` (before adding our root, so
our own clones are never indexed), warns once per missing name, and passes
it through `mount-item!`/`sync-blueprint-draw!`; only props (not mirrors)
get `:bevel`/`:kit`. `resources/estate.edn` gained `:kit-materials`.

What Hal should look at headless:
- The creek bench (`[:blueprint :bench-by-mailbox]`) and lamp post close
  up, built (trial or real): timber grain on the seat and legs, not
  stretched along the thin legs; the lamp's post and shade in the kit's
  metal; the bulb still glows at dusk; box edges softly rounded.
- A pending ghost: the same neon blue with crisp box edges as before;
  Remove… on a real build: the same red removal ghost.
- Undo on a trial: back to the blue ghost; build it again: wood again (the
  very material restored, no blue tint left).
- Clicks still pick each part; the console warns only if a mapped name is
  missing from the kit; the scaffold (no kit) shows flat colours, no
  warnings.
- Workshop shelves (`:tin`) and the mailbox post (`:brown`) change too
  (same palette keys); creatures, walkers and the booth don't.

Gate: 0 warnings, 1351 tests (51 new, `pm.materials-test`).
