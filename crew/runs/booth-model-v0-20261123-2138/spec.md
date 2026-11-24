# Spec: booth-model-v0 (TASKS T-18)

Quest: `.crew/quests/booth-model-v0.md` (**as amended twice**: no glass kit override, the
panes keep the model's own see-through glass; the lamp glows with one small 3 m light, the
signs glow with no light). A prop can carry a `:model` (a GLB): it's drawn from its `:look`
at mount, then swapped in place for the model's meshes once they load. The booth is first.

Pure logic goes in a **new pure namespace `pm.models`** plus two small changes in
`pm.plan`; `pm.three` does the loading/rebuilding (the only place that requires the npm
`GLTFLoader`); `pm.scene` swaps. No test loads `pm.three`/`pm.scene` (none does today), so
the npm loader never enters the node test build. No new npm deps. Nothing names a person, pet,
member or blueprint in `src/`; the booth's data names its own file.

## 0. Who touches what

| Member | May edit | Must not touch |
|---|---|---|
| **builder** (un-automate-monde) | new `src/pm/models.cljs`; `src/pm/plan.cljs` (`phone-booth-item`, new `model-label-at`); `src/pm/three.cljs` (`load-model`, `model-meshes`, `add-y!`, `light-glows!`, `unlight-glows!`, `sync-glows!`); `src/pm/scene.cljs` (`mount-item!` and a private swap fn); `resources/estate.edn`: **the `:phone-booth` entry and its comment only**; `crew/runs/booth-model-v0-20261123-2138/builder-notes.md` | `test/`, `docs/`, `TASKS.md`, other estate keys, `public/assets/props/`, `tools/blender/`, `state/` |
| **tester** (le-testeur) | new `test/pm/booth_model_test.cljs` (every case in §5) | `src/`, `resources/`, docs, existing tests (all stay green unchanged) |
| **le-docteur** | `TASKS.md`: a new row T-18 (§6); `docs/GDD.md` §5 Art Direction: one sentence (§6) | code, tests, everything else |

---

## 1. Data (`resources/estate.edn`, `:phone-booth` only)

```clojure
 ;; The phone booth (GDD §2.3, §4.8): the hardline. ...existing lines...
 ;; Drawn from its :model, the red phone box (TASKS T-18, made by
 ;; tools/blender/phone_booth.py); :look is the fallback until it loads, or if it can't.
 ;; At night its ceiling lamp glows with one small light (3 m) and the TELEPHONE
 ;; panels glow with none; the panes keep the model's own see-through glass.
 :phone-booth {:at [4.9 1.71 -6.6] :yaw 0
               :model {:url "/assets/props/phone-booth.glb"
                       :materials {"lamp" {:glow true :light-m 3}
                                   "sign" {:glow true :light false}}}
               :look {:parts ...unchanged, the same three parts...}}
```
`:at`, `:yaw` and `:look` stay byte-identical.

Shapes the pure fns use:
```clojure
model     {:url "/assets/props/phone-booth.glb" :materials {"lamp" {:glow true :light-m 3} "sign" {:glow true :light false}}}
override  {:kit "MAT_Hiroshi_Glass"} | {:glow true} | {:glow true :light-m 3} | {:glow true :light false} | {:kit "K" :glow true} | nil
src-mat   {:name "glass" :color 0xdde8ee :roughness 0.05 :metalness 0 :emissive 0 :emissive-intensity 1
           :opacity 0.35 :transparent true :side 2 :vertex-colors false :alpha-test 0}   ; read off a parsed material
```

---

## 2. `pm.models` (new, pure; requires `clojure.string`, `pm.materials` for `wind-marker`)

### 2.1 `(model-spec m)` → `{:url s :materials {name entry}}` | nil
- nil unless `m` is a map whose `:url` is a string that starts with `"/"`, does **not**
  start with `"//"`, and is longer than 1 character.
- `:materials` absent/nil → `{}`. Present but not a map → **nil** (the whole model is ignored
  and the `:look` is used). A map → keep only entries whose key is a string and whose
  value is a map (values kept as they are; `material-override` reads them).
- Every other key of `m` is dropped.

### 2.2 `(material-override name materials)` → override map | nil
`name`: a material's name from the model (the contract: `booth-red`, `floor`, `glass`,
`sign`, `letters`, `metal`, `lamp`, `leaf`, `stem`). Case-sensitive exact lookup
`(get materials name)`; nil unless that's a map `e`. The result has:
- `:kit k` when `(:kit e)` is a non-blank string not containing `mats/wind-marker`;
- `:glow true` when `(:glow e)` is exactly `true`; and only then:
  - `:light false` when `(:light e)` is exactly `false` (glows, adds no light);
  - otherwise `:light-m r` when `(:light-m e)` is a finite number > 0 (its light's range);
    no `:light-m` means the kit lamp's own range, as today's `:glow true` parts.
- nil when it would have neither `:kit` nor `:glow`. Other keys are ignored.

### 2.3 `(material-props src)` → map for `MeshStandardMaterial` (camelCase keys, for `clj->js`)
`src` is a plain map read off a parsed material (keys as in §1's `src-mat`). Result keys and
rules:
| key | value |
|---|---|
| `:name` | `(:name src)` if a string, else `""` |
| `:color` | `(:color src)` if an integer, else `0xffffff` |
| `:roughness` | finite → clamped to [0, 1]; else `1` |
| `:metalness` | finite → clamped to [0, 1]; else `0` |
| `:emissive` | integer, else `0` |
| `:emissiveIntensity` | `(:emissive-intensity src)` if finite and ≥ 0, else `1` |
| `:opacity` | `(:opacity src)` if finite in [0, 1], else `1` |
| `:transparent` | `true` when `(:transparent src)` is `true` **or** the opacity above is < 1; else `false` |
| `:depthWrite` | `(not transparent)`: see-through glass never writes depth, so the phone shows inside |
| `:side` | `(:side src)` if one of `0 1 2`, else `0` |
| `:vertexColors` | `(true? (:vertex-colors src))` |
| `:alphaTest` | `(:alpha-test src)` if finite in [0, 1], else `0` |

(The base-colour map is not part of this: `pm.three` rebuilds it, §3.1.)

---

## 3. Interop and wiring (builder; not node-testable, Hal checks headless)

### 3.1 `pm.three`
- Require `["three/examples/jsm/loaders/GLTFLoader.js" :refer [GLTFLoader]]` here only.
  Nothing it returns is ever added to a scene.
- **`(load-model url)`** → a `js/Promise` of the parsed gltf, cached per URL
  (`defonce` atom `{url promise}`; a rejected promise stays cached, so a bad file is tried once).
  Uses `(.loadAsync (GLTFLoader.) url)`.
- **`(model-meshes gltf)`** → a JS array of **active-THREE** (`(three)`) `Mesh`es, flat, at
  the model's origin: `(.updateMatrixWorld (.-scene gltf) true)`, then for every `isMesh`
  under it: a new `BufferGeometry` with each attribute copied (`array` copied, `itemSize`,
  `normalized`) as a new `BufferAttribute`, the index likewise, `groups` copied; then
  `.applyMatrix4` with an active-THREE `Matrix4` from the source mesh's `matrixWorld.elements`
  (so node transforms are baked in); bounding box/sphere computed. Material (each element
  if an array): `(MeshStandardMaterial. (clj->js (models/material-props src)))` where `src` is
  read off the parsed material; when it has a `map`, a new active-THREE `Texture` from
  `map.image` with `flipY false`, `colorSpace` = `SRGBColorSpace`, the source's `wrapS`/`wrapT`,
  `needsUpdate true`. `castShadow`/`receiveShadow` true. Any throw → reject/return nil (the
  caller keeps the look).
- **`(add-y! obj dy)`**: `obj.position.y += dy`.
- **Glow options** (back-compatible: a mesh without the new userData behaves as today):
  - per-mesh userData set by `pm.scene`: `glow` true; `glowLight` false (no light of its own);
    `glowRange` metres.
  - `light-glows!`: for each mesh with `userData.glow` and not yet `userData.lit`: its own
    material as today; unless `userData.glowLight` is `false`, add the `PointLight` as today
    with distance `userData.glowRange` when set (else the kit lamp's distance, else 10), store
    it in `userData.light`; set `userData.lit` true.
  - `unlight-glows!`: for each mesh with `userData.lit` or `userData.light`: remove/dispose the
    light if any, delete `light` and `lit`.
  - `sync-glows!`: for each mesh with `userData.lit`: its light's intensity follows the kit
    lamp's (when it has a light); its material's emissive follows the lantern's (always). So a
    no-light sign still glows at dusk and goes dark by day, and the lamp post bulb (no new
    userData) works exactly as today.

### 3.2 `pm.scene`
- **`mount-item!`**: unchanged drawing of the `:look` parts. Then, when `(:model item)` is
  set: `(.then (t/load-model url) (fn [gltf] (swap-in-model! item gltf)))` with a `.catch`
  that warns once per URL (`defonce` set) and leaves the look.
- **`swap-in-model!`** (private) `[item gltf]`:
  1. `g` = `(t/by-name (mounted-root) (pr-str (:key item)))`; stop if nil or no longer parented.
  2. `meshes` = `(t/model-meshes gltf)`; nil/empty → warn once, keep the look.
  3. `top` = `(t/top-y holder)` of a fresh group holding `meshes` at the origin (the model's
     own height, metres above its base).
  4. Remove and dispose the look's meshes (children named `"<key>/<i>"`); add each model mesh
     to `g`, named `(str (pr-str key) "/" i)` (so `pm.pick/key-of-name` still gives the
     item key: a click on the booth still jacks you back in).
  5. For each mesh, `o` = `(models/material-override (.. mesh -material -name) (get-in item [:model :materials]))`:
     `:kit` → a `.clone` of `(get (:index kit-mats) k)` when found (else keep the model's own,
     warn once per name); `:glow` → `userData.glow` true, `:light false` → `userData.glowLight`
     false, `:light-m` → `userData.glowRange`.
  6. `(when (:ghost? item) (t/ghost! g (:tint item)))`; then `(t/light-glows! g (:kit-lights @!mounted))`.
  7. Label: `new` = `(p/model-label-at item top)`; when the item has a label sprite and
     `dy` = `(- (second new) (second (:label-at item)))` is > 0, `(t/add-y! sprite dy)`.
- Group name, position and yaw never change; the item's plan entry in `!mounted` is untouched.

### 3.3 `pm.plan`
- **`phone-booth-item`**: as today, plus `:model (models/model-spec (:model booth))` when that's
  non-nil (no `:model` key otherwise). `:parts`, `:center`, `:label-at` still come from the
  `:look`. Only the booth's item carries `:model` this round; the scene's swap is general.
- **`(model-label-at item model-top)`** → `[x y z]` | nil: nil without a 3-vector
  `:label-at`; `model-top` not a finite number → `:label-at` unchanged; else `[x (max ly
  (+ ay model-top label-lift)) z]`, where `[x ly z]` is `:label-at` and `ay` is `(:at item)`'s y
  (no valid `:at` → unchanged). It never lowers the sign.

---

## 4. Not changing
Clicks (`pm.pick/booth-key`, `key-of-name`), the crew key's "Phone booth" row, the jack-out
sign text, the lamp post's bulb, ghosts, `pm.materials`, every other prop.

---

## 5. Test cases (`test/pm/booth_model_test.cljs`)

Requires `pm.models :as md`, `pm.plan :as p`, `pm.pick :as pk`, `pm.world :as w`, `pm.content :as c`.
```clojure
(def WE (w/load-estate w/estate-edn))
(def M  {"lamp" {:glow true :light-m 3} "sign" {:glow true :light false}})
(def URL "/assets/props/phone-booth.glb")
(def T0 1800000000000)
(def jo {:event :jack-out :reason :household :who :household :text "Dinner!"
         :source {:agent "twin" :session "s1" :machine "laptop"}})
(def BOX {:phone-booth {:at [1 2 3] :yaw 90
                        :look {:parts [{:shape :box :size [1 2 1] :at [0 1 0] :color :booth-red}]}
                        :model {:url "/m.glb"}}})   ; a second, synthetic booth: nothing is specific to the shipped one
(defn near? [a b] (and (= (count a) (count b)) (every? #(< (js/Math.abs %) 1e-9) (map - a b))))
```

### 5.1 `material-override`
1. Lamp: `(md/material-override "lamp" M)` → `{:glow true :light-m 3}`.
2. Sign: `(md/material-override "sign" M)` → `{:glow true :light false}`.
3. The panes keep the model's glass: `(md/material-override "glass" M)` → `nil`.
4. None for the rest: each of `"booth-red" "floor" "letters" "metal" "leaf" "stem"` with `M` → `nil`.
5. Unknown / bad names: `"nope"`, `"Lamp"` (case-sensitive), `nil`, `7` with `M` → `nil`.
6. No map: `(md/material-override "lamp" nil)` → `nil`; with `{}` → `nil`.
7. Kit (kept for later): `(md/material-override "glass" {"glass" {:kit "MAT_Hiroshi_Glass"}})` → `{:kit "MAT_Hiroshi_Glass"}`.
8. Kit and glow: `{"x" {:kit "K" :glow true}}` for `"x"` → `{:kit "K" :glow true}`.
9. Plain glow (kit lamp's range): `{"x" {:glow true}}` → `{:glow true}`; `{"x" {:glow true :light true}}` → `{:glow true}`.
10. No light wins over a range: `{"x" {:glow true :light false :light-m 3}}` → `{:glow true :light false}`.
11. Bad ranges dropped: `{"x" {:glow true :light-m v}}` for `v` in `0`, `-1`, `"3"`, `js/NaN`, `js/Infinity` → `{:glow true}`.
12. Light options need glow: `{"x" {:light-m 3}}` → `nil`; `{"x" {:light false}}` → `nil`; `{"x" {:glow "yes" :light-m 3}}` → `nil`; `{"x" {:glow false}}` → `nil`.
13. `:light` only turns off on literal `false`: `{"x" {:glow true :light "no" :light-m 2}}` → `{:glow true :light-m 2}`; `{"x" {:glow true :light nil}}` → `{:glow true}`.
14. Bad kit names: `{"x" {:kit ""}}`, `{"x" {:kit "  "}}`, `{"x" {:kit 7}}`, `{"x" {:kit "A / wind"}}` → `nil`; `{"x" {:kit "A / wind" :glow true}}` → `{:glow true}`.
15. Not a map entry / extra keys: `{"x" "MAT_Hiroshi_Glass"}` → `nil`; `{"x" {:kit "K" :foo 1}}` → `{:kit "K"}`.

### 5.2 `model-spec`
16. Shipped shape: `(md/model-spec {:url URL :materials M})` → `{:url URL :materials M}`.
17. No materials: `(md/model-spec {:url "/m.glb"})` → `{:url "/m.glb" :materials {}}`; `{:url "/m.glb" :materials nil}` → the same.
18. Junk entries dropped: `(md/model-spec {:url "/m.glb" :materials {"lamp" {:glow true} :sign {:glow true} "x" "str" 7 {}}})` → `{:url "/m.glb" :materials {"lamp" {:glow true}}}`.
19. Extra keys dropped: `(md/model-spec {:url "/m.glb" :scale 2 :yaw 90})` → `{:url "/m.glb" :materials {}}`.
20. Bad models → nil: `nil`, `"/m.glb"`, `{}`, `{:url nil}`, `{:url 7}`, `{:url "assets/props/phone-booth.glb"}` (relative), `{:url " /m.glb"}`, `{:url "/"}`, `{:url "//cdn.example/m.glb"}`, `{:url "https://example.com/m.glb"}`.
21. Bad materials → the whole model is ignored: `{:url "/m.glb" :materials "lamp"}` → `nil`; `{:url "/m.glb" :materials [["lamp" {:glow true}]]}` → `nil`.

### 5.3 `material-props`
22. The glass stays see-through: `(md/material-props {:name "glass" :color 0xdde8ee :roughness 0.05 :metalness 0 :emissive 0 :emissive-intensity 1 :opacity 0.35 :transparent true :side 2 :vertex-colors false :alpha-test 0})` →
    `{:name "glass" :color 0xdde8ee :roughness 0.05 :metalness 0 :emissive 0 :emissiveIntensity 1 :opacity 0.35 :transparent true :depthWrite false :side 2 :vertexColors false :alphaTest 0}`.
23. Exported opaque-flagged but opacity < 1 → still transparent: `{:name "glass" :opacity 0.35 :transparent false}` → `:transparent true`, `:depthWrite false`, `:opacity 0.35`.
24. Opaque paint: `(md/material-props {:name "booth-red" :color 0xffffff :roughness 0.7 :metalness 0 :opacity 1 :transparent false})` →
    `{:name "booth-red" :color 0xffffff :roughness 0.7 :metalness 0 :emissive 0 :emissiveIntensity 1 :opacity 1 :transparent false :depthWrite true :side 0 :vertexColors false :alphaTest 0}`.
25. Defaults: `(md/material-props {})` → `{:name "" :color 0xffffff :roughness 1 :metalness 0 :emissive 0 :emissiveIntensity 1 :opacity 1 :transparent false :depthWrite true :side 0 :vertexColors false :alphaTest 0}`.
26. Bad values: `(md/material-props {:name 7 :color "red" :roughness 2 :metalness -1 :emissive 1.5 :emissive-intensity -1 :opacity 1.5 :side 5 :alpha-test 2})` →
    `{:name "" :color 0xffffff :roughness 1 :metalness 0 :emissive 0 :emissiveIntensity 1 :opacity 1 :transparent false :depthWrite true :side 0 :vertexColors false :alphaTest 0}`.
27. NaN: `{:roughness js/NaN :opacity js/NaN}` → `:roughness 1`, `:opacity 1`, `:transparent false`.
28. Fully clear: `{:opacity 0}` → `:opacity 0`, `:transparent true`, `:depthWrite false`.
29. Flagged transparent at full opacity: `{:opacity 1 :transparent true}` → `:transparent true`, `:depthWrite false`.
30. Lamp glow material, emissive kept: `{:name "lamp" :color 0xfff4d6 :emissive 0xffd28a :emissive-intensity 2 :roughness 0.4}` → `:emissive 0xffd28a`, `:emissiveIntensity 2`, `:roughness 0.4`, `:transparent false`.
31. Vertex colours: `{:vertex-colors true}` → `:vertexColors true`; `{:vertex-colors "yes"}` → `false`.

### 5.4 The booth item
32. Shipped booth: `(p/phone-booth-item WE)` has `:key [:phone-booth :box]`, `:model {:url URL :materials M}`, 3 `:parts` (the look kept as the fallback), `:label :sign`, `:text ""`, `:label-at` near `[4.9 4.16 -6.6]` (look top = max(2.0, 2.15, 1.85) = 2.15; 1.71 + 2.15 + 0.3), `:center` near `[4.9 2.785 -6.6]`.
33. Jacked out, with the model: `(p/phone-booth-item (w/apply-event WE jo T0))` still has `:key [:phone-booth :box]` and `:model`, and its `:text` is one of `(:jack-out-household c/hal-lines)` filled with `{:text "Dinner!"}` (as `plan_test` t40).
34. Shipped data: `(= #{:at :yaw :look :model} (set (keys (:phone-booth WE))))`; `(get-in WE [:phone-booth :model])` → `{:url URL :materials M}`; `(get-in WE [:phone-booth :model :materials "glass"])` → `nil`.
35. Second booth: `(p/phone-booth-item BOX)` → `:key [:phone-booth :box]`, `:yaw 90`, `:model {:url "/m.glb" :materials {}}`, `:label-at` near `[1 4.3 3]`, `:center` near `[1 3 3]`.
36. Bad model → the look alone: for `bad` in `{:url "m.glb"}`, `{:url "/m.glb" :materials "x"}`, `"x"`:
    `(p/phone-booth-item (assoc-in BOX [:phone-booth :model] bad))` has no `:model` key (`contains?` false) and
    `(= (dissoc (p/phone-booth-item BOX) :model) (p/phone-booth-item (assoc-in BOX [:phone-booth :model] bad)))`.
37. No model: `(contains? (p/phone-booth-item (update BOX :phone-booth dissoc :model)) :model)` → `false`.
38. Only the booth carries a model this round: `(contains? (p/workshop-item (assoc-in WE [:workshop :model] {:url "/m.glb"})) :model)` → `false`.

### 5.5 `model-label-at`
39. Raised above the shipped model: `(p/model-label-at (p/phone-booth-item WE) 2.5)` → near `[4.9 4.51 -6.6]` (1.71 + 2.5 + 0.3 > 4.16).
40. Second booth: `(p/model-label-at (p/phone-booth-item BOX) 2.5)` → near `[1 4.8 3]` (2 + 2.5 + 0.3 = 4.8 > 4.3).
41. Never lowered: `(p/model-label-at (p/phone-booth-item BOX) 1.0)` → near `[1 4.3 3]` (3.3 < 4.3).
42. Any item, not just a booth: `(p/model-label-at {:at [0 0 0] :label-at [0 1.3 0]} 2)` → near `[0 2.3 0]`.
43. Bad top: `model-top` `nil`, `js/NaN`, `"2.5"` → `:label-at` unchanged (`[1 4.3 3]` for BOX's item).
44. No label: `(p/model-label-at {:at [0 0 0]} 2)` → `nil`; bad `:at` `(p/model-label-at {:label-at [0 1.3 0]} 2)` → `[0 1.3 0]`.

### 5.6 Clicks still reach the booth
45. A model mesh's name: `(pk/key-of-name (str (pr-str [:phone-booth :box]) "/" 12))` → `[:phone-booth :box]`, and `(= pk/booth-key [:phone-booth :box])`.
46. A hit chain from a model mesh: `(pk/pick-key [["[:phone-booth :box]/7" "[:phone-booth :box]" "pm-mirrors"]])` → `[:phone-booth :box]`.

46 numbered cases. Existing tests stay green unchanged (`plan_test` t38–t40, `tidy_after_remove_test`, `calls_test`).

---

## 6. Docs (le-docteur)
- **TASKS.md**, a new row right after the T-17 row:
  `- [x] Nov 23 (crew round booth-model-v0) T-18 Props can be models; the red phone box goes in (Michael, Nov 23: "make the phone booth look more like a real london phone booth … keep with the theme"). The booth is now Hal's weathered red phone box (a Blender model, \`tools/blender/phone_booth.py\`), loaded from \`public/assets/props/phone-booth.glb\` through a prop's \`:model\`; its old boxes show until it loads, or if it can't. You can see the phone through its glass; at night its ceiling lamp glows and lights the box with one small light, and the TELEPHONE signs glow. Clicking it still jacks you back in.`
- **docs/GDD.md §5 Art Direction**, append one sentence to the paragraph:
  `Props can also be Blender models (a prop's \`:model\`, a GLB drawn with the kit's own three.js, its primitive \`:look\` as the fallback); the red phone box is the first, with its own see-through glass and a lamp and signs that glow at night.`

## 7. Builder note (run dir): what Hal should check headless
- The booth in the kit: the red box replaces the three boxes at the same spot and yaw, door
  facing as before, standing on the ground (no `:scale`); console shows no warning.
- The panes: the model's own semi-transparent glass; the phone visible inside; nothing behind
  the glass vanishes (depthWrite off).
- Jacked out (click the booth): the sign shows the reason above the roof, not inside it; click it
  again: jacked back in.
- Night: the ceiling lamp glows and lights the inside, a soft pool on the ground through the
  glass, reaching about 3 m; the four TELEPHONE panels glow but add no light (one new point
  light for the booth); by day neither shows. The lamp post bulb still works as before.
- A broken URL (try it in a scratch copy, not in the repo): the boxes stay, one warning.

## 8. Out of scope
Models for other props; swaying vines; editing the model or its script; animations; a kit
glass override (supported and tested, not used).
