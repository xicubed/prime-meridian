# Spec: kit-materials-v0 (TASKS T-17)

Quest: `.crew/quests/kit-materials-v0.md`. Our props (blueprints and the estate fixtures)
wear the kit's own textured materials, tiled by part size, with rounded `:box` edges.
Everything that can be pure lives in a **new pure namespace `pm.materials`** (no three.js,
no DOM, no js/Date, no atoms) so the tester can test it in node; `pm.three` does the
interop, `pm.scene` wires it in. Nothing names a blueprint, person, pet or member in `src/`.
No npm deps; no `three/examples`; only the current THREE from `(t/three)` (the kit's,
after `use-three!`).

## 0. Who touches what

| Member | May edit | Must not touch |
|---|---|---|
| **builder** (un-automate-monde) | new `src/pm/materials.cljs`; `src/pm/three.cljs` (`material-pairs`, `rounded-box-geometry`, `kit-part-material`, `stand-in` opts, `ghost!`, `unghost!`); `src/pm/scene.cljs` (`mount-item!`, `mount!`, `sync-blueprint-draw!`); `resources/estate.edn`: **the new `:kit-materials` entry and its comment only**; `crew/runs/kit-materials-v0-20261123-2005/builder-notes.md` | `test/`, `docs/`, `TASKS.md`, any other estate key, `pm.bricks`, `pm.creature-spike`, `state/` |
| **tester** (le-testeur) | new `test/pm/materials_test.cljs` (every case in §4); requires only `pm.materials` (and `pm.world` for case 50) | `src/`, `resources/`, docs, existing tests |
| **le-docteur** | `TASKS.md`: a new row T-17 (§5); `docs/GDD.md` §5 Art Direction: one sentence (§5) | code, tests, everything else |

---

## 1. Data (`resources/estate.edn`)

Add, right after `:kit-lights`:
```clojure
 ;; Our props wear the kit's own materials (TASKS T-17, Michael: "they're kinda
 ;; plain"): palette key -> the kit material's name, and :tile-m, the metres one
 ;; texture repeat covers (pm.materials). Any other key stays its flat colour;
 ;; walkers, creatures and the booth's :booth-red/:glass stay painted. Without
 ;; the kit (a public clone) everything is its flat colour.
 :kit-materials {:brown {:name "MAT_Hiroshi_Timber" :tile-m 0.6}
                 :tin   {:name "MAT_Hiroshi_Metal"}}
```
`w/load-estate` passes it through untouched (it keeps every top-level key).

Shapes the pure fns work on (all plain data):
```clojure
part   {:shape :box :size [1.1 0.1 0.4] :at [0 0.45 0] :rot [0 0 0] :color :brown}   ; :glow true on a bulb
kit-materials {:brown {:name "MAT_Hiroshi_Timber" :tile-m 0.6} :tin {:name "MAT_Hiroshi_Metal"}}
index  {"MAT_Hiroshi_Timber" <material> "MAT_Hiroshi_Metal" <material>}   ; name -> anything (a ^js material in the game, a keyword in tests)
kit    {:material <material> :tile-m 0.6}   ; material-for's result; :tile-m nil when not set
```

---

## 2. `pm.materials` (new, pure)

```clojure
(def wind-marker " / wind")   ; the kit's animated node materials carry this in their name: never used
(def bevel-max-m 0.02)        ; the largest edge radius
(def bevel-segments 5)        ; segments per axis of the unit box the rounded box starts from (odd)
```

### 2.1 `(index-materials pairs)` → map
`pairs`: a seq of `[name material]` (from `pm.three/material-pairs`, traverse order).
Returns `{name material}`. Skips a pair whose name isn't a non-blank string, whose name
contains `wind-marker`, or whose material is nil. **First occurrence wins.** nil/empty →
`{}`.

### 2.2 `(props-item? item)` → boolean
True when the scene item is one of our props (a blueprint or an estate fixture), false for
a mirror's item (it has a `:mirror` key: creatures stay painted and square). Only props
get kit materials and rounded boxes.

### 2.3 `(material-for part kit-materials index)` → `{:material m :tile-m t}` | nil
nil when any of: `(:glow part)` truthy (glow parts keep today's flat emissive material);
`(:color part)` not a keyword; `kit-materials` not a map, or its entry for that key not a
map; the entry's `:name` not a string, or containing `wind-marker`; `(get index name)` nil
(the kit isn't up, or the name isn't in it). Otherwise `{:material (get index name)
:tile-m t}` where `t` is the entry's `:tile-m` when it's a finite number > 0, else nil.

### 2.4 `(box-face-repeats size tile-m)` → `{:x [u v] :y [u v] :z [u v]}`
Texture repeats for a box's three face pairs, matching BoxGeometry's UVs (px/nx: u along
z, v along y; py/ny: u along x, v along z; pz/nz: u along x, v along y). With
`size` `[sx sy sz]` (3 finite numbers > 0) and `tile-m` a finite number > 0:
`{:x [(/ sz t) (/ sy t)] :y [(/ sx t) (/ sz t)] :z [(/ sx t) (/ sy t)]}`.
Otherwise (no/bad `tile-m`, bad `size`) every pair is `[1 1]`.

### 2.5 `(shape-repeat shape size tile-m)` → `[u v]`
For the non-box shapes, one repeat for the whole part. `:cylinder` and `:cone`:
`[(/ (* js/Math.PI sx) t) (/ sy t)]` (u around the circumference, v up the height). Any
other shape (`:sphere`, `:capsule`, `:box`, unknown): `[1 1]`. Bad `size`/`tile-m` → `[1 1]`.

### 2.6 `(bevel-radius size)` → number
`(min bevel-max-m (/ (min sx sy sz) 4))` for 3 finite numbers > 0; anything else → `0`.

### 2.7 `(round-box-vertex p size r n)` → `{:pos [x y z] :normal [nx ny nz]}`
One vertex of the rounded box. `p`: a vertex of `BoxGeometry(1,1,1,n,n,n)` (each coord in
[-0.5, 0.5]); `size` `[sx sy sz]` metres; `r` radius metres; `n` the segment count.
With `h = 0.5 / n` and `sgn` = `js/Math.sign`:
- `d` = `p − sgn(p)·h` componentwise, normalized → `:normal` (`[0 0 0]` if its length is 0).
- `box` = `size/2 − r` componentwise.
- `:pos` = `box_i · sgn(p_i) + normal_i · r` componentwise.

The outermost vertices land exactly on today's box faces (`±size/2`), the middle rows of each
face collapse onto its flat part, the rows near an edge bend round it. Same origin, same
bounding box, same face order, same UVs as today's `BoxGeometry(sx, sy, sz)`.

### 2.8 `(missing-names kit-materials index)` → sorted vector of strings
Every distinct `:name` string in `kit-materials`' map entries that is not a key of `index`
(a wind name is always missing). `kit-materials` nil/not a map → `[]`.

---

## 3. Interop and wiring (builder; not node-testable, Hal checks headless)

### 3.1 `pm.three`
- **`(material-pairs root)`** → vector of `[name material]` for every mesh under `root`
  (traverse order; an array material contributes each element). nil root → `[]`.
- **`(rounded-box-geometry [sx sy sz] r)`** → `BoxGeometry(1,1,1,n,n,n)` (`n` =
  `mats/bevel-segments`) whose position and normal attributes are rewritten per vertex
  with `mats/round-box-vertex`; index, groups (materialIndex 0–5) and UVs kept; attributes
  `needsUpdate`, bounding box/sphere recomputed. Core THREE classes only.
- **`(kit-part-material {:keys [material tile-m]} shape size)`** → a material or a
  6-element array. Never returns or mutates the kit's own material.
  - Material without any of `map`/`normalMap`/`roughnessMap`: one `(.clone material)`.
  - `:box` with maps: 3 clones (x, y, z) used as `[mx mx my my mz mz]`; each has its own
    clones of whichever of `map`/`normalMap`/`roughnessMap` exist, with
    `repeat.set(u, v)` from `mats/box-face-repeats`, `wrapS`/`wrapT` = `RepeatWrapping`,
    `needsUpdate` true.
  - Other shapes with maps: one clone, its texture clones repeating `mats/shape-repeat`.
  - The palette colour is never multiplied in (the clone keeps the kit material's colour).
- **`stand-in`** takes two optional opts; without them it's byte-for-byte today's (so
  `pm.bricks` and `pm.creature-spike` don't change):
  - `:bevel r`: on a `:box` with `r` > 0, the geometry is `rounded-box-geometry` and
    `userData.boxSize` = `#js [sx sy sz]`.
  - `:kit {:material :tile-m}`: the mesh's material is `kit-part-material`, otherwise
    today's flat `MeshStandardMaterial`.
- **`ghost!`** (same arities, same look): for each mesh, if `userData.ghostMat` isn't set,
  save the mesh's current material (single or array) there; if it is (ghosting a ghost),
  keep it and dispose the current ghost material. Then set a **new**
  `MeshStandardMaterial {color fill, emissive glow, emissiveIntensity 0.9, transparent true,
  opacity 0.3, depthWrite false, flatShading true}` from `ghost-tints`. Remove any old
  `pm-ghost-edges` child, then add the edges as today (non-raycastable, same colours),
  built from `BoxGeometry(boxSize)` when `userData.boxSize` is set (the outline stays
  today's crisp box, not a line per bevel row), else from the mesh geometry.
- **`unghost!`**: edges removed and disposed as today; for each mesh with
  `userData.ghostMat`: dispose the ghost material, put the saved material back (the very
  same object/array), delete `ghostMat`. A mesh never ghosted keeps its material untouched.
- `light-glows!`, `unlight-glows!` and `sync-glows!` don't change (glow parts never get kit
  materials, see 2.3).

### 3.2 `pm.scene`
- **`mount!`**: *before* adding our root, `index` = `(mats/index-materials (t/material-pairs
  scene))` (so our own clones, which copy the kit's names, are never indexed). When `index`
  is non-empty, `js/console.warn` once per name in `(mats/missing-names (:kit-materials
  world) index)`. With no kit (scaffold) the index is empty and nothing warns. Store
  `{:kit-materials (:kit-materials world) :index index}` as `:kit-mats` in `!mounted`, and
  pass it to every `mount-item!`.
- **`mount-item!`** `[root item kit-mats]`: for each part, when `(mats/props-item? item)`:
  `:bevel (when (= :box (:shape part)) (mats/bevel-radius (:size part)))` and
  `:kit (mats/material-for part (:kit-materials kit-mats) (:index kit-mats))`; for a
  mirror's item neither. Everything else as today (mesh names `"<key>/<i>"`, `:glow`
  userData, `ghost!` when `ghost?`).
- **`sync-blueprint-draw!`**: passes `(:kit-mats @!mounted)` to `mount-item!`.
- Clicks: meshes keep their names and stay raycastable (array materials raycast fine);
  ghost edges stay non-raycastable.

If the rounded box doesn't come out clean with core classes, keep `BoxGeometry` (skip
`:bevel`) and say so in the builder notes; the pure fns and their tests stay.

---

## 4. Test cases (`test/pm/materials_test.cljs`)

Fixtures:
```clojure
(def KM  {:brown {:name "MAT_Hiroshi_Timber" :tile-m 0.6} :tin {:name "MAT_Hiroshi_Metal"}})
(def IDX {"MAT_Hiroshi_Timber" :timber "MAT_Hiroshi_Metal" :metal "MAT_Hiroshi_Stone" :stone})
(def SEAT {:shape :box  :size [1.1 0.1 0.4]   :at [0 0.45 0] :color :brown})
(def LEG  {:shape :box  :size [0.08 0.45 0.08] :at [0.45 0.225 0.15] :color :brown})
(def POST {:shape :box  :size [0.3 2.4 0.3]   :at [0 1.2 0] :color :tin})
(def SHADE {:shape :cone :size [0.7 0.4 0.7]  :at [0 2.75 0] :color :tin})
(def BULB {:shape :sphere :size [0.3 0.3 0.3] :at [0 2.45 0] :color :gold :glow true})
(defn near? [a b] (and (= (count a) (count b)) (every? #(< (js/Math.abs %) 1e-6) (map - a b))))
(defn near-faces? [m e] (every? (fn [k] (near? (get m k) (get e k))) [:x :y :z]))
```
Expected decimals are rounded to 7 places; compare with `near?` (1e-6).

### 4.1 `index-materials`
1. `(m/index-materials [["MAT_Hiroshi_Timber" :t] ["MAT_Hiroshi_Metal" :m]])` → `{"MAT_Hiroshi_Timber" :t "MAT_Hiroshi_Metal" :m}`.
2. First wins: `[["A" :a1] ["A" :a2]]` → `{"A" :a1}`.
3. Wind ignored: `[["MAT_Hiroshi_Foliage / wind" :w] ["MAT_Hiroshi_Stone" :s]]` → `{"MAT_Hiroshi_Stone" :s}`.
4. Bad names and nil materials dropped: `[["" :x] ["  " :x2] [nil :y] [7 :z] ["B" nil]]` → `{}`.
5. `(m/index-materials nil)` → `{}`; `(m/index-materials [])` → `{}`.

### 4.2 `props-item?`
6. `(m/props-item? {:key [:blueprint :bench-x]})` → `true`; `{:key [:workshop :box]}` → `true`.
7. `(m/props-item? {:key [:flora :body] :mirror :flora})` → `false`; the second mirror `{:key [:juniper :body] :mirror :juniper}` → `false`.

### 4.3 `material-for`
8. Mapped, with tile: `(m/material-for SEAT KM IDX)` → `{:material :timber :tile-m 0.6}`; `LEG` → the same.
9. The second key, no tile: `(m/material-for POST KM IDX)` → `{:material :metal :tile-m nil}`; the second shape `SHADE` → the same.
10. Unmapped keys: `(m/material-for (assoc SEAT :color :gold) KM IDX)` → `nil`; `:booth-red` → `nil`; `:glass` → `nil`.
11. Glow part: `(m/material-for BULB KM IDX)` → `nil`; and `(m/material-for (assoc POST :glow true) KM IDX)` → `nil`.
12. No `:kit-materials`: `(m/material-for SEAT nil IDX)` → `nil`; `(m/material-for SEAT {} IDX)` → `nil`.
13. Kit not up: `(m/material-for SEAT KM {})` → `nil`; `(m/material-for SEAT KM nil)` → `nil`.
14. Name not in the kit: `(m/material-for SEAT {:brown {:name "MAT_Nope"}} IDX)` → `nil`.
15. Wind name, even if present in the map: `(m/material-for SEAT {:brown {:name "MAT_Hiroshi_Timber / wind"}} (assoc IDX "MAT_Hiroshi_Timber / wind" :w))` → `nil`.
16. Bad entries: `{:brown "MAT_Hiroshi_Timber"}` → `nil`; `{:brown {:name 7}}` → `nil`; part `:color "brown"` (a string) with KM → `nil`.
17. Bad tile: `{:brown {:name "MAT_Hiroshi_Timber" :tile-m t}}` for `t` in `0`, `-1`, `"0.6"`, `js/NaN` → `{:material :timber :tile-m nil}`.
18. Any other kit material works too (the data decides): `(m/material-for SEAT {:brown {:name "MAT_Hiroshi_Stone" :tile-m 1}} IDX)` → `{:material :stone :tile-m 1}`.

### 4.4 `box-face-repeats`
19. Seat, tile 0.6: `(m/box-face-repeats [1.1 0.1 0.4] 0.6)` → near `{:x [0.6666667 0.1666667] :y [1.8333333 0.6666667] :z [1.8333333 0.1666667]}`.
20. Leg, tile 0.6: `[0.08 0.45 0.08]` → near `{:x [0.1333333 0.75] :y [0.1333333 0.1333333] :z [0.1333333 0.75]}` (the thin leg gets a sliver of grain, not a stretched whole texture).
21. Exact: `(m/box-face-repeats [2 3 4] 1)` → `{:x [4 3] :y [2 4] :z [2 3]}`.
22. No tile = 1×1: `(m/box-face-repeats [1.1 0.1 0.4] nil)` → `{:x [1 1] :y [1 1] :z [1 1]}`.
23. Bad tile: `0`, `-1`, `"0.6"`, `js/NaN` → all `[1 1]`.
24. Bad size with a tile: `nil`, `[1 1]`, `[1 0 1]`, `[1 -2 1]` → all `[1 1]`.

### 4.5 `shape-repeat`
25. `(m/shape-repeat :cylinder [0.08 0.9 0.08] 0.6)` → near `[0.4188790 1.5]` (π·0.08/0.6).
26. `(m/shape-repeat :cone [0.7 0.4 0.7] 1)` → near `[2.1991149 0.4]`.
27. `(m/shape-repeat :sphere [0.3 0.3 0.3] 0.6)` → `[1 1]`; `:capsule` → `[1 1]`; `:box` → `[1 1]`.
28. `(m/shape-repeat :cylinder [0.08 0.9 0.08] nil)` → `[1 1]`; size `nil` with tile 0.6 → `[1 1]`.

### 4.6 `bevel-radius`
29. Seat `[1.1 0.1 0.4]` → `0.02` (0.1/4 = 0.025 > 0.02).
30. Leg `[0.08 0.45 0.08]` → `0.02` (0.08/4 = 0.02 exactly).
31. Post `[0.3 2.4 0.3]` → `0.02`.
32. Thin: `[0.05 1 1]` → `0.0125`; `[0.09 0.024 0.006]` → `0.0015`.
33. Bad: `nil`, `[1 1]`, `[1 0 1]`, `[1 -1 1]`, `[1 js/NaN 1]`, `"x"` → `0`.
34. `(= 0.02 m/bevel-max-m)`, `(= 5 m/bevel-segments)`.

### 4.7 `round-box-vertex` (size `[1 0.5 0.4]`, r `0.02`, n `5`, so box = `[0.48 0.23 0.18]`)
35. Face middle: `p [0.5 0.1 0.1]` → `:pos [0.5 0.23 0.18]`, `:normal [1 0 0]`.
36. Mirrored face middle: `p [0.5 -0.1 -0.1]` → `:pos [0.5 -0.23 -0.18]`, `:normal [1 0 0]`.
37. Top face middle: `p [0.1 0.5 -0.1]` → `:pos [0.48 0.25 -0.18]`, `:normal [0 1 0]`.
38. Edge: `p [0.5 0.5 0.1]` → `:pos [0.4941421 0.2441421 0.18]`, `:normal [0.7071068 0.7071068 0]`.
39. Edge, part-way round: `p [0.5 0.3 0.1]` → `:pos [0.4978885 0.2389443 0.18]`, `:normal [0.8944272 0.4472136 0]`.
40. Corner: `p [0.5 0.5 0.5]` → `:pos [0.4915470 0.2415470 0.1915470]`, `:normal [0.5773503 0.5773503 0.5773503]`.
41. The opposite-sign corner (nothing is specific to +x): `p [-0.5 0.5 -0.5]` → `:pos [-0.4915470 0.2415470 -0.1915470]`, `:normal [-0.5773503 0.5773503 -0.5773503]`.
42. Exact third: `p [0.3 0.5 0.5]` → `:normal [0.3333333 0.6666667 0.6666667]`, `:pos [0.4866667 0.2433333 0.1933333]`.
43. Same extents as today's box: over every surface vertex of the n = 5 unit grid (each coord in `[-0.5 -0.3 -0.1 0.1 0.3 0.5]`, at least one at ±0.5), max |x| = 0.5, max |y| = 0.25, max |z| = 0.2 (within 1e-9), and none exceeds them.
44. r = 0: `(m/round-box-vertex [0.5 0.3 0.1] [1 0.5 0.4] 0 5)` → `:pos [0.5 0.25 0.2]`, `:normal [0.8944272 0.4472136 0]`.
45. The second size (a leg) with its own radius: `(m/round-box-vertex [0.5 0.5 0.5] [0.08 0.45 0.08] 0.02 5)` → `:pos [0.0315470 0.2165470 0.0315470]` (box `[0.02 0.205 0.02]` + 0.0115470).

### 4.8 `missing-names`
46. All found: `(m/missing-names KM IDX)` → `[]`.
47. One missing: `(m/missing-names KM {"MAT_Hiroshi_Metal" :m})` → `["MAT_Hiroshi_Timber"]`; kit not up `(m/missing-names KM {})` → `["MAT_Hiroshi_Metal" "MAT_Hiroshi_Timber"]`.
48. Distinct, sorted, wind always missing, bad entries skipped: `(m/missing-names {:brown {:name "X"} :tin {:name "X"} :gold {:name "W / wind"} :moss "Y" :pink {:name 7}} {"W / wind" :w})` → `["W / wind" "X"]`.
49. `(m/missing-names nil IDX)` → `[]`; `(m/missing-names {} IDX)` → `[]`.

### 4.9 The shipped data
50. `(:kit-materials (w/load-estate w/estate-edn))` → `{:brown {:name "MAT_Hiroshi_Timber" :tile-m 0.6} :tin {:name "MAT_Hiroshi_Metal"}}`.
51. Every palette key the shipped blueprints use maps or stays flat as decided: with that
    `:kit-materials` and `IDX`, each part of `:bench-by-mailbox` gets `{:material :timber :tile-m 0.6}`;
    `:lamp-post-by-canal`'s post and shade get `{:material :metal :tile-m nil}` and its bulb `nil`
    (read the parts from `(:blueprints (w/load-estate w/estate-edn))` by id).

51 numbered cases. No existing test changes (stand-in without the new opts is unchanged).

---

## 5. Docs (le-docteur)

- **TASKS.md**, a new row right after the T-16 row:
  `- [x] Nov 23 (crew round kit-materials-v0) T-17 Our things wear the kit's materials (Michael, Nov 23: "can we texture these items? they're kinda plain"). Benches, the lamp post, and the workshop and mailbox use the kit's own timber and metal (estate.edn \`:kit-materials\`: palette key → kit material), the grain tiled by each part's size so a thin leg isn't stretched, and box edges are slightly rounded. Ghosts look as before; Undo brings the wood back. Without the kit (a public clone) everything keeps its flat colour.`
- **docs/GDD.md §5 Art Direction**, append one sentence to the paragraph:
  `Our own props (blueprints and the estate's fixtures) wear the scene kit's materials (its timber and metal, tiled to each part's size, with softly rounded box edges) so they sit with the cottage and its steps; characters, creatures and the phone booth stay painted, and without the kit everything falls back to the flat palette.`

## 6. Builder note (run dir): what Hal should look at headless

- The creek bench (`[:blueprint :bench-by-mailbox]`) and lamp post close up, built (trial
  or real): timber grain on the seat and legs, not stretched along the legs; the lamp's post
  and shade the kit's metal; the bulb still glows at dusk; box edges softly rounded.
- A pending ghost: the same neon blue with crisp box edges as before; Remove… on a real
  build: the same red removal ghost.
- Undo on a trial: back to the blue ghost; build it again: wood again (the very material
  restored, no blue tint left).
- Clicks still pick each part; the console warns only if a mapped name is missing from the
  kit; the scaffold (no kit) shows flat colours, no warnings.
- Workshop shelves (`:tin`) and the mailbox post (`:brown`) change too (same palette keys);
  creatures, walkers and the booth don't.

## 7. Out of scope

Real models instead of primitives; texturing walkers, creatures or the phone booth; new
materials of our own; per-face UV correction on the 2 cm bevel bands (BoxGeometry's UVs
are kept as they are).
