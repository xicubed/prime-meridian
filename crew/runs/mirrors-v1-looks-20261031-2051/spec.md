# Spec: mirrors v1 (looks you can read from the default cameras)

Round `mirrors-v1-looks` · quest `.crew/quests/mirrors-v1-looks.md` · architect: un-architecte · 2026-10-31

Sources: GDD §2.5 (mirrors are data, never code; a second example from the same code path),
§5 (flat-shaded, warm palette), AGENTS.md (three.js only through `pm.three`, palette in `pm.scene`,
`!` for mutators, 0 warnings), the v0 spec (`crew/runs/mirrors-v0-20261031-1656/spec.md`) and
the code as committed (`c07f095`).

## 0. Who touches what

| Crew member | May create/edit | Must not touch |
|---|---|---|
| builder (un-automate-monde) | `resources/estate.edn` (edit: the `:placed` vectors and one comment), `src/pm/plan.cljs`, `src/pm/three.cljs` (`group`, `stand-in` only), `src/pm/scene.cljs` | `test/`, `src/pm/core.cljs`, `src/pm/world.cljs`, `pm.kit`, `pm.brook`, `pm.audio`, `shadow-cljs.edn`, GDD, `assets/`, `public/kit/` |
| tester (le-testeur) | `test/pm/plan_test.cljs` (edit: §7 says which tests change) | everything else, including `test/pm/world_test.cljs` |

- The 32 tests in `world_test.cljs` must still pass unchanged (the estate edit touches only `:placed`).
- **No code may name a mirror** (`:flora`, `:juniper`, axolotl, horse, gill, worm, jar, mane...)
  outside `resources/estate.edn` and the tests. Palette keys are colours named for what they
  colour in general (`:gill`, `:eye`, `:mane`, `:tin`). That's allowed: the quest names them, and
  any creature may use them.
- Everything added to the kit's scene is still built through `pm.three` (`(three)`, after
  `use-three!`); `pm.scene` never touches `js/THREE`, the npm module or `window.hiroshi`.
- Meshes are still built **once** in `mount!`; `update!` only changes label text.

### Decisions this spec makes

1. **Every look becomes parts.** `scene-plan` drops `:look` and returns `:parts` (normalized,
   scaled) plus `:yaw`. A single-shape look is one part sitting on the base, `[0 h/2 0]`, so v0
   positions are unchanged for v0 data (bit-for-bit: `h/2 + h/2 = h` exactly in floats).
2. **Single-shape looks stay lenient** (bad shape → `:box`, bad size → `[0.5 0.5 0.5]`), as in v0.
   **Parts are strict**: a part with a bad shape, size, `:at` or `:rot` is dropped. If no part
   survives, the look is the default stand-in.
3. **The item's scale applies to the fallback stand-in too** (it's a part like any other).
4. **The label height ignores part rotation**: a part's top is `dy + sy/2` in its own unrotated
   frame. Rotated parts (gills, a horse's neck) are small enough that this is close; it keeps the
   plan pure and the expected values hand-checkable.
5. **Degrees in data and in the plan; radians only at the wrapper.** `pm.plan/radians` converts,
   and `pm.scene` calls it. Euler order is three's default, `XYZ`.
6. **The HUD readout gains a parts count**, so Hal can confirm the looks loaded without
   looking at the scene: `scene: mirrors 2 · items 3 · parts 30 · labels 3/3`.

## 1. Data: `resources/estate.edn`

Replace the two `:placed` vectors with exactly this (comments optional, values exact; tests
depend on them). Add the display-scale comment above `:mirrors`. Nothing else changes.

```clojure
 ;; Display scale (standing rule): creatures and crew are drawn big enough to
 ;; see from the kit's camera views without zooming in, about the size of a
 ;; mid-size rock (~1 m). Real size doesn't matter; proportions do. Author a
 ;; look at real proportions and set the item's :scale to reach display size.
 ;; Look parts: {:shape :size [sx sy sz] :at [dx dy dz] :rot [rx ry rz] :color};
 ;; :at is the part's centre from the item's base (+x forward, y up), :rot in
 ;; degrees. :yaw (degrees about y) turns the item; +90 points +x at world -z.

;; Flora's :placed
:placed [{:id :body :at [-0.27 1.42 -15.7] :scale 4 :yaw 90 :label :say   ; real axolotl, 0.26 m nose to tail, drawn ~1 m; facing upstream (-z); back at the surface (~1.55)
          :look {:parts
                 [{:shape :capsule :size [0.12 0.032 0.04]  :at [0.02 0.016 0]   :color :pink}   ; body
                  {:shape :sphere  :size [0.05 0.03 0.056]  :at [0.1 0.017 0]    :color :pink}   ; head, wider than the body
                  {:shape :box     :size [0.09 0.024 0.006] :at [-0.085 0.016 0] :color :pink}   ; tail, flattened sideways
                  ;; gills: three stalks a side, behind the head, fanning up and back
                  {:shape :cone :size [0.006 0.024 0.006] :at [0.082 0.036  0.022] :rot [ 30 0 35] :color :gill}
                  {:shape :cone :size [0.006 0.024 0.006] :at [0.086 0.031  0.027] :rot [ 55 0 25] :color :gill}
                  {:shape :cone :size [0.006 0.024 0.006] :at [0.09  0.026  0.029] :rot [ 80 0 15] :color :gill}
                  {:shape :cone :size [0.006 0.024 0.006] :at [0.082 0.036 -0.022] :rot [-30 0 35] :color :gill}
                  {:shape :cone :size [0.006 0.024 0.006] :at [0.086 0.031 -0.027] :rot [-55 0 25] :color :gill}
                  {:shape :cone :size [0.006 0.024 0.006] :at [0.09  0.026 -0.029] :rot [-80 0 15] :color :gill}
                  ;; legs: four stubs, splayed out and down
                  {:shape :cylinder :size [0.008 0.016 0.008] :at [ 0.06 0.006  0.022] :rot [-60 0 0] :color :pink}
                  {:shape :cylinder :size [0.008 0.016 0.008] :at [ 0.06 0.006 -0.022] :rot [ 60 0 0] :color :pink}
                  {:shape :cylinder :size [0.008 0.016 0.008] :at [-0.03 0.006  0.022] :rot [-60 0 0] :color :pink}
                  {:shape :cylinder :size [0.008 0.016 0.008] :at [-0.03 0.006 -0.022] :rot [ 60 0 0] :color :pink}
                  ;; eyes
                  {:shape :sphere :size [0.008 0.008 0.008] :at [0.112 0.026  0.021] :color :eye}
                  {:shape :sphere :size [0.008 0.008 0.008] :at [0.112 0.026 -0.021] :color :eye}]}}
         {:id :jar :at [1.4 1.81 -15.7] :scale 4 :label :sign :idle "Worms: {stock}"   ; on the bank rocks (top 1.81); real jar 0.124 m, drawn ~0.5 m
          :look {:parts
                 [{:shape :cylinder :size [0.08 0.11 0.08]   :at [0 0.055 0] :color :glass}  ; jar
                  {:shape :cylinder :size [0.088 0.014 0.088] :at [0 0.117 0] :color :tin}]}}] ; lid

;; Juniper's :placed
:placed [{:id :body :at [-5.0 2.5 -10.0] :yaw 90 :label :say   ; west bank; long axis along world z, side-on to the creek camera
          :look {:parts
                 [{:shape :capsule  :size [1.3 0.62 0.6]   :at [0 1.2 0]                     :color :brown}  ; barrel
                  {:shape :box      :size [0.3 0.8 0.32]   :at [0.72 1.62 0]  :rot [0 0 -35] :color :brown}  ; neck, leaning forward
                  {:shape :box      :size [0.55 0.26 0.24] :at [1.1 1.9 0]    :rot [0 0 -35] :color :brown}  ; head, nose down
                  {:shape :cone     :size [0.06 0.14 0.05] :at [0.95 2.12  0.07]             :color :brown}  ; ears
                  {:shape :cone     :size [0.06 0.14 0.05] :at [0.95 2.12 -0.07]             :color :brown}
                  {:shape :sphere   :size [0.05 0.05 0.05] :at [1.15 1.98  0.12]             :color :eye}
                  {:shape :sphere   :size [0.05 0.05 0.05] :at [1.15 1.98 -0.12]             :color :eye}
                  {:shape :box      :size [0.08 0.75 0.06] :at [0.62 1.68 0]  :rot [0 0 -35] :color :mane}   ; mane, along the neck
                  {:shape :cylinder :size [0.1 0.7 0.1]    :at [-0.82 1.1 0]  :rot [0 0 -20] :color :mane}   ; tail
                  {:shape :cylinder :size [0.14 0.95 0.14] :at [ 0.48 0.475  0.18]           :color :brown}  ; legs
                  {:shape :cylinder :size [0.14 0.95 0.14] :at [ 0.48 0.475 -0.18]           :color :brown}
                  {:shape :cylinder :size [0.14 0.95 0.14] :at [-0.48 0.475  0.18]           :color :brown}
                  {:shape :cylinder :size [0.14 0.95 0.14] :at [-0.48 0.475 -0.18]           :color :brown}]}}]
```

Geometry notes (hand-checked; Hal confirms live):
- Flora at scale 4: 15 parts, 1.02 m from tail tip (x −0.52) to snout (x +0.50). Body and head tops
  are at 1.42 + 0.128 = **1.548** (the surface is ~1.55). Gill tops are at 1.572–1.612, above the water.
  With `:yaw 90` her snout points at world −z (upstream).
- Jar at scale 4: 0.496 m tall, lid included.
- Juniper at scale 1: 13 parts, ~2.25 m tail to nose, ears to 2.19 m, back 1.51 m, withers ~1.6 m
  where the neck rises. With `:yaw 90` his long axis lies along world z (head toward −z).

### Item and part shapes

| item key | meaning |
|---|---|
| `:at` | `[x y z]` world metres, the item's base (unchanged; 3 numbers or the item is skipped) |
| `:scale` | number > 0, default 1. Anything else (0, negative, non-number) → 1 |
| `:yaw` | degrees about y, default 0. Non-number → 0. Passed through as given |
| `:look` | a single-shape look `{:shape :size :color}`, or a compound look `{:parts [...]}` |
| `:label` `:idle` `:id` | unchanged from v0 |

A look is **compound** iff `(contains? look :parts)`. Otherwise it is single-shape (including
`nil`/missing).

Part keys: `:shape` ∈ `p/shapes` = `#{:box :cylinder :capsule :sphere :cone}`; `:size` 3 positive
numbers; `:at` 3 numbers, default `[0 (/ sy 2) 0]`; `:rot` 3 numbers in degrees, default `[0 0 0]`;
`:color` a keyword, else `:stand-in`. Other keys are dropped.

Shapes fill their `[sx sy sz]` box in the part's own frame, before `:rot`:
`:box` is a box; `:sphere` an ellipsoid; `:cylinder` an upright elliptic cylinder; `:cone` an
upright elliptic cone with its apex at +y; `:capsule` lies along x, length `sx`, diameter `sy`
(`sz` ignored).

## 2. `src/pm/plan.cljs` (builder)

Unchanged: `label-lift`, `label-text`, `label-texts`, `label-changes`. Changed: `scene-plan`,
`plan-summary`. New public: `shapes`, `default-part`, `radians`, `look-parts`, `parts-top`.
Still pure; still never names a mirror.

### `shapes`
`(def shapes #{:box :cylinder :capsule :sphere :cone})`

### `default-part`
`(def default-part {:shape :box :size [0.5 0.5 0.5] :at [0 0.25 0] :rot [0 0 0] :color :stand-in})`

### `(radians deg)` → number
`(* deg (/ js/Math.PI 180))`. 0 → 0; 90 → π/2; 180 → π.

### `(look-parts look scale)` → non-empty vector of parts
Every part is exactly `{:shape :size :at :rot :color}`.
1. `s` = `scale` when it is a number > 0, else 1.
2. Single-shape look (no `:parts` key): one part. `:shape` from the look if it is in `shapes`, else
   `:box`. `:size` from the look if it is 3 positive numbers, else `[0.5 0.5 0.5]`. `:color` as
   for parts. `:at [0 (/ sy 2) 0]`, `:rot [0 0 0]`. Any `:at`/`:rot` on a single-shape look are
   ignored.
3. Compound look: if `(:parts look)` is `sequential?`, keep each element that is a map with a
   valid `:shape` and `:size`, and an `:at`/`:rot` that are either absent or 3 numbers. Fill the
   defaults and keep the order. Anything else, or nothing valid, gives `[default-part]`.
4. Scale each part: `:size` → `(mapv #(* s %) size)`, `:at` → `(mapv #(* s %) at)`. `:rot`
   is not scaled.

### `(parts-top parts)` → number
`(apply max (map (fn [{[_ dy _] :at [_ sy _] :size}] (+ dy (/ sy 2))) parts))`. Empty → `0`.

### `(scene-plan world now)` → vector of item maps
Order, skipping and labels are the same as v0. Each item, with `[x y z]` = `:at`,
`parts` = `(look-parts (:look item) (:scale item))` and `top` = `(parts-top parts)`:

```clojure
{:key [mirror-id item-id] :mirror mirror-id :item item-id
 :at [x y z]
 :yaw    (if (number? (:yaw item)) (:yaw item) 0)
 :parts  parts
 :center [x (+ y (/ top 2)) z]
 ;; only when :label is :say or :sign (unchanged):
 :label kind :label-at [x (+ y top label-lift) z] :text "..."}
```
`:look` is **no longer** in the item. An unlabelled item has exactly
`#{:key :mirror :item :at :yaw :parts :center}`.

### `(plan-summary plan)` → string
`"scene: mirrors <m> · items <i> · parts <n> · labels <s>/<l>"`, where n = the total
`(count (:parts item))` over the plan. The other counts are as in v0. Empty plan →
`"scene: mirrors 0 · items 0 · parts 0 · labels 0/0"`.

## 3. `src/pm/three.cljs` (builder; only `group` and `stand-in` change)

- `(group {:keys [name pos rot]})`: as before, plus `:rot [rx ry rz]` in **radians**
  (default `[0 0 0]`), set with `(.set (.-rotation g) rx ry rz)`.
- `(stand-in {:keys [shape size color pos rot]})`: `pos` is the mesh centre in its parent's frame;
  `rot` is `[rx ry rz]` in radians (default `[0 0 0]`) on the mesh, Euler `XYZ`. Geometry, built
  with `(three)`:
  - `:box`: `BoxGeometry sx sy sz`
  - `:sphere`: `SphereGeometry 0.5 12 8`, mesh `scale` `(sx sy sz)`
  - `:cylinder`: `CylinderGeometry 0.5 0.5 1 12`, mesh `scale` `(sx sy sz)` (same look as v0 when sx = sz)
  - `:cone`: `ConeGeometry 0.5 1 12`, mesh `scale` `(sx sy sz)`
  - `:capsule`: `CapsuleGeometry (/ sy 2) (max 0 (- sx sy)) 4 8`, then **`(.rotateZ geom (/ js/Math.PI 2))`
    on the geometry** so it lies along x and the mesh rotation is free for `rot`
  - anything else: as `:box`

  The material, flat shading and cast/receive shadows are unchanged. `^js` hints throughout;
  0 warnings.

## 4. `src/pm/scene.cljs` (builder)

- `palette` gains (hex only here): `:gill 0xe0678f :eye 0x1e1a1c :mane 0x3b2a1e :tin 0x9a9184`.
  Unknown colours still map to `:stand-in`.
- `mount-item!` builds one **item group** per plan item:
  `(t/group {:name (pr-str key) :pos at :rot [0 (p/radians yaw) 0]})`. Each part becomes
  `(t/stand-in {:shape :size :color (color c) :pos (:at part) :rot (mapv p/radians (:rot part))})`,
  named `(str (pr-str key) "/" i)` and added to the item group. The item group goes into `root`.
  The label sprite is still added to `root` (world frame) at `:label-at`, so yaw never moves it.
- `mount!`, `update!` and `summary` are unchanged in behaviour. Nothing reads `:look` or `:center`
  any more (`:center` stays in the plan for tests and tools).
- `core.cljs` is unchanged: the HUD picks up the new summary through `scene/summary`.

Expected with the kit: the HUD contains `scene: mirrors 2 · items 3 · parts 30 · labels 3/3`,
and `labels 2/3` after F.

## 5. What's not unit-tested

`pm.three` and `pm.scene` need a browser. The gate compiles them with 0 warnings, and Hal checks
the look live from "The creek" and "Above it all": Flora reads as an axolotl facing upstream
with her gills above the water, and Juniper as a side-on horse.

## 6. Out of scope / follow-ups for Hal

- Out of scope: animation, swimming, rigged models, mouse picking, camera changes.
- GDD §2.5 should gain a line on looks (`:parts`, `:scale`, `:yaw`) and the display-scale rule.
  That's a manager follow-up; the crew doesn't edit the GDD this round.

## 7. Test cases: `test/pm/plan_test.cljs`

Keep the namespace and every shared value already in the file (`T0 W ev fed hay W1 stock by-key
texts near? bales-item WJ odd-edn O P0`), and add:

```clojure
(defn part-near? [p q]
  (and (= (dissoc p :size :at) (dissoc q :size :at))
       (near? (:size p) (:size q)) (near? (:at p) (:at q))))
(defn span [parts]   ; x extent in the item frame, ignoring :rot
  (- (apply max (map (fn [{[dx] :at [sx] :size}] (+ dx (/ sx 2))) parts))
     (apply min (map (fn [{[dx] :at [sx] :size}] (- dx (/ sx 2))) parts))))
(defn part-of [plan k i] (get-in (by-key plan) [k :parts i]))
(def toy-edn
  (pr-str {:mirrors [{:id :toy :state {}
                      :placed [{:id :a :at [1 0 2] :scale 2 :yaw 45 :label :say :idle "hello"
                                :look {:parts [{:shape :box :size [1 1 1] :at [0 0.5 0] :color :pink}
                                               {:shape :sphere :size [0.5 0.5 0.5] :at [0.5 1.25 0]
                                                :rot [0 90 0] :color :eye}]}}
                               {:id :b :at [0 0 0] :scale 0 :yaw "north"
                                :look {:parts [{:shape :star :size [1 1 1]}
                                               {:shape :box :size [1 0 1]}
                                               {:shape :box :size [1 1]}
                                               {:shape :box :size [1 1 1] :at [0 1]}
                                               {:shape :box :size [1 1 1] :rot "up"}
                                               "box"
                                               {:shape :cone :size [0.2 0.6 0.2]}]}}
                               {:id :c :at [0 0 0] :scale 3 :look {:parts []}}
                               {:id :d :at [0 0 0] :scale -1 :look {:parts :none :shape :sphere}}
                               {:id :e :at [0 0 0]
                                :look {:shape :sphere :size [1 2 3] :color :gill :rot [1 2 3] :at [9 9 9]}}]}]}))
(def Q  (w/load-estate toy-edn))
(def PQ (p/scene-plan Q 0))
```

### Kept unchanged
t01, t02, t05–t14, t16, t19, t20, t21: no edits (they don't read `:look`, and the counts, texts and
keys are unchanged).

### Rewritten (same names, new expected values)
3. `t03-plan-order-and-shape`: `(mapv :key P0)` = `[[:flora :body] [:flora :jar] [:juniper :body]]`;
   `(dissoc (nth P0 i) :center :label-at :parts)` =
   - i=0: `{:key [:flora :body] :mirror :flora :item :body :at [-0.27 1.42 -15.7] :yaw 90 :label :say :text "I'm hungry"}`
   - i=1: `{:key [:flora :jar] :mirror :flora :item :jar :at [1.4 1.81 -15.7] :yaw 0 :label :sign :text "Worms: 15"}`
   - i=2: `{:key [:juniper :body] :mirror :juniper :item :body :at [-5.0 2.5 -10.0] :yaw 90 :label :say :text "Hay, please?"}`

   `(contains? (nth P0 0) :look)` = `false`.
4. `t04-plan-positions` (`near?`): `:center` / `:label-at`:
   P0 item 0 `[-0.27 1.516 -15.7]` / `[-0.27 1.912 -15.7]`;
   item 1 `[1.4 2.058 -15.7]` / `[1.4 2.606 -15.7]`;
   item 2 `[-5.0 3.595 -10.0]` / `[-5.0 4.99 -10.0]`.
15. `t15-juniper-store-item`: as now, except replace the `(:look item)` assertion with
    `(:parts item)` = `[{:shape :box :size [1.0 0.5 0.5] :at [0 0.25 0] :rot [0 0 0] :color :gold}]` and
    `(:yaw item)` = `0`. Center `[9.0 1.7 11.0]` and label-at `[9.0 2.25 11.0]` are unchanged (back-compat).
17. `t17-renamed-copy`: as now, but the summary is `"scene: mirrors 3 · items 5 · parts 47 · labels 5/5"`
    (15 + 2 + 13 + 15 + 2).
18. `t18-odd-items`: `PO = (p/scene-plan O 0)`, `BO = (by-key PO)`. Keys as now,
    `[[:alpha :x] [:alpha :y] [:zed 0] [:zed :b] [:zed :c] [:zed :d]]`;
    `(set (keys (BO [:zed 0])))` = `#{:key :mirror :item :at :yaw :parts :center}`; `(:item (BO [:zed 0]))` = `0`;
    `(:parts (BO [:zed 0]))` = `[p/default-part]`; center `[0 0.25 0]`;
    `(:parts (BO [:zed :b]))` = `[{:shape :cone :size [0.5 0.5 0.5] :at [0 0.25 0] :rot [0 0 0] :color :stand-in}]`
    (`:cone` is a shape now; the size and colour fall back); `(contains? (BO [:zed :b]) :label)` = `false`; center `[1 2.25 3]`;
    `(:parts (BO [:zed :c]))` = `[{:shape :cylinder :size [2 4 2] :at [0 2 0] :rot [0 0 0] :color :moss}]`;
    center `[0 3 0]`; label-at `[0 5.3 0]`; text `"hi · there"`;
    `(BO [:zed :d])`: `:label :sign`, text `"n 1"`, label-at `[0 0.8 0]`;
    `(:text (BO [:alpha :x]))` = `"k=2"`; `(:text (BO [:alpha :y]))` = `""`; every item's `:yaw` = `0`.
22. `t22-summary`:
    `(p/plan-summary P0)` = `"scene: mirrors 2 · items 3 · parts 30 · labels 3/3"`;
    W1 at `(+ T0 1000)` → `"scene: mirrors 2 · items 3 · parts 30 · labels 2/3"`;
    `(w/chore-done W hay)` at `(+ T0 1000)` → `"scene: mirrors 2 · items 3 · parts 30 · labels 2/3"`;
    WJ at T0 → `"scene: mirrors 2 · items 4 · parts 31 · labels 4/4"`;
    O at 0 → `"scene: mirrors 2 · items 6 · parts 6 · labels 3/4"`;
    `[]` → `"scene: mirrors 0 · items 0 · parts 0 · labels 0/0"`.

### New
23. `t23-constants`: `p/shapes` = `#{:box :cylinder :capsule :sphere :cone}`;
    `p/default-part` = `{:shape :box :size [0.5 0.5 0.5] :at [0 0.25 0] :rot [0 0 0] :color :stand-in}`;
    `(p/radians 0)` = `0`; `(near? [(p/radians 90) (p/radians 180) (p/radians -90)] [1.5707963267948966 js/Math.PI -1.5707963267948966])`.
24. `t24-single-shape-back-compat`:
    `(p/look-parts nil 1)` = `[p/default-part]`; `(p/look-parts {} 1)` = `[p/default-part]`;
    `(p/look-parts {:shape :box :size [1 2 3] :color :pink} 1)` = `[{:shape :box :size [1 2 3] :at [0 1 0] :rot [0 0 0] :color :pink}]`;
    `(p/look-parts {:shape :star :size [1 2] :color "red"} 1)` = `[p/default-part]`;
    `(p/look-parts {:shape :sphere :size [1 2 3] :at [9 9 9] :rot [1 2 3]} 1)` = `[{:shape :sphere :size [1 2 3] :at [0 1 0] :rot [0 0 0] :color :stand-in}]`;
    v0 equivalence: for `{:shape :cylinder :size [0.22 0.3 0.22] :color :glass}` at scale 1,
    `(p/parts-top (p/look-parts look 1))` = `0.3` (exact `=`).
25. `t25-parts-normalized`:
    `(p/look-parts {:parts [{:shape :cone :size [1 2 1]} {:shape :box :size [1 1 1] :at [1 2 3] :rot [10 20 30] :color :eye :name "x"}]} 1)` =
    `[{:shape :cone :size [1 2 1] :at [0 1 0] :rot [0 0 0] :color :stand-in}
      {:shape :box :size [1 1 1] :at [1 2 3] :rot [10 20 30] :color :eye}]` (defaults filled, extra keys dropped, order kept);
    `(p/look-parts {:parts (list {:shape :sphere :size [1 1 1]})} 1)` = `[{:shape :sphere :size [1 1 1] :at [0 0.5 0] :rot [0 0 0] :color :stand-in}]` (any sequential).
26. `t26-invalid-parts-dropped`: on PQ, `(:parts (get (by-key PQ) [:toy :b]))` =
    `[{:shape :cone :size [0.2 0.6 0.2] :at [0 0.3 0] :rot [0 0 0] :color :stand-in}]`. Of the 7 parts only the cone
    survives: unknown shape, a zero size, a 2-element size, a 2-element `:at`, a string `:rot` and a non-map are each dropped.
    `(:parts (get (by-key PQ) [:toy :c]))` = `[{:shape :box :size [1.5 1.5 1.5] :at [0 0.75 0] :rot [0 0 0] :color :stand-in}]`
    (empty parts → scaled fallback); `(:parts (get (by-key PQ) [:toy :d]))` = `[p/default-part]` (non-sequential `:parts`; its
    `:shape` is ignored; scale −1 → 1); `(p/look-parts {:parts [{:shape :box}]} 2)` = `[{:shape :box :size [1 1 1] :at [0 0.5 0] :rot [0 0 0] :color :stand-in}]`.
27. `t27-scale`: `(p/look-parts {:shape :box :size [1 2 3] :color :pink} 2)` = `[{:shape :box :size [2 4 6] :at [0 2 0] :rot [0 0 0] :color :pink}]`;
    `(p/look-parts {:parts [{:shape :cone :size [1 1 1] :at [1 2 3] :rot [10 20 30]}]} 0.5)` =
    `[{:shape :cone :size [0.5 0.5 0.5] :at [0.5 1 1.5] :rot [10 20 30] :color :stand-in}]` (`:rot` not scaled);
    for each of scale `0`, `-1`, `"2"` and `nil`, `(p/look-parts {:shape :box :size [1 1 1]} s)` =
    `[{:shape :box :size [1 1 1] :at [0 0.5 0] :rot [0 0 0] :color :stand-in}]`.
28. `t28-parts-top-and-label`: `(p/parts-top [{:size [1 2 1] :at [0 1 0]} {:size [1 1 1] :at [0 3 0]}])` = `3.5`;
    `(p/parts-top [])` = `0`; `(p/parts-top [{:size [1 1 1] :at [0 -2 0]}])` = `-1.5`;
    `A = (get (by-key PQ) [:toy :a])`: `(:parts A)` =
    `[{:shape :box :size [2 2 2] :at [0 1 0] :rot [0 0 0] :color :pink} {:shape :sphere :size [1 1 1] :at [1 2.5 0] :rot [0 90 0] :color :eye}]`;
    `(p/parts-top (:parts A))` = `3`; `(:center A)` = `[1 1.5 2]`; `near?` `(:label-at A)` `[1 3.3 2]`; `(:text A)` = `"hello"`.
29. `t29-yaw-passed-through`: `(:yaw A)` = `45`; `(:yaw (get (by-key PQ) [:toy :b]))` = `0` (string);
    `(:yaw (get (by-key PQ) [:toy :e]))` = `0` (absent); `(mapv :yaw P0)` = `[90 0 90]`;
    yaw doesn't move positions: `(:center (get (by-key (p/scene-plan (assoc-in Q [:mirrors :toy :placed 0 :yaw] -30) 0)) [:toy :a]))` = `[1 1.5 2]`.
30. `t30-toy-plan`: `(mapv :key PQ)` = `[[:toy :a] [:toy :b] [:toy :c] [:toy :d] [:toy :e]]`;
    `(:parts (get (by-key PQ) [:toy :e]))` = `[{:shape :sphere :size [1 2 3] :at [0 1 0] :rot [0 0 0] :color :gill}]`;
    the `:center`s of :b, :c, :d, :e are `[0 0.3 0]`, `[0 0.75 0]`, `[0 0.25 0]`, `[0 1 0]` (`near?`);
    `(set (keys (get (by-key PQ) [:toy :b])))` = `#{:key :mirror :item :at :yaw :parts :center}`;
    `(p/plan-summary PQ)` = `"scene: mirrors 1 · items 5 · parts 6 · labels 1/1"`.
31. `t31-flora-look`: `FP = (:parts (nth P0 0))`: `(count FP)` = `15`;
    `(frequencies (map :color FP))` = `{:pink 7 :gill 6 :eye 2}`;
    `(frequencies (map :shape FP))` = `{:capsule 1 :sphere 3 :box 1 :cone 6 :cylinder 4}`;
    `(part-near? (nth FP 0) {:shape :capsule :size [0.48 0.128 0.16] :at [0.08 0.064 0] :rot [0 0 0] :color :pink})`;
    `(part-near? (nth FP 1) {:shape :sphere :size [0.2 0.12 0.224] :at [0.4 0.068 0] :rot [0 0 0] :color :pink})`;
    `(part-near? (nth FP 2) {:shape :box :size [0.36 0.096 0.024] :at [-0.34 0.064 0] :rot [0 0 0] :color :pink})`;
    `(part-near? (nth FP 3) {:shape :cone :size [0.024 0.096 0.024] :at [0.328 0.144 0.088] :rot [30 0 35] :color :gill})`;
    `(part-near? (nth FP 8) {:shape :cone :size [0.024 0.096 0.024] :at [0.36 0.104 -0.116] :rot [-80 0 15] :color :gill})`;
    `(near? [(span FP)] [1.02])` (~1 m long); `(near? [(p/parts-top FP)] [0.192])`;
    back at the surface: `(near? [(+ 1.42 (p/parts-top [(nth FP 0)]))] [1.548])`;
    gills above it: every `:gill` part has `(+ 1.42 dy (/ sy 2))` > `1.55` (the values are 1.612, 1.592, 1.572).
32. `t32-jar-look`: `JP = (:parts (nth P0 1))`: `(count JP)` = `2`; `(mapv :color JP)` = `[:glass :tin]`;
    `(part-near? (nth JP 0) {:shape :cylinder :size [0.32 0.44 0.32] :at [0 0.22 0] :rot [0 0 0] :color :glass})`;
    `(part-near? (nth JP 1) {:shape :cylinder :size [0.352 0.056 0.352] :at [0 0.468 0] :rot [0 0 0] :color :tin})`;
    `(near? [(p/parts-top JP)] [0.496])` (~0.5 m tall).
33. `t33-juniper-look`: `HP = (:parts (nth P0 2))`: `(count HP)` = `13`;
    `(frequencies (map :color HP))` = `{:brown 9 :eye 2 :mane 2}`;
    `(frequencies (map :shape HP))` = `{:capsule 1 :box 3 :cone 2 :sphere 2 :cylinder 5}`;
    exact (scale 1): `(nth HP 0)` = `{:shape :capsule :size [1.3 0.62 0.6] :at [0 1.2 0] :rot [0 0 0] :color :brown}`;
    `(nth HP 2)` = `{:shape :box :size [0.55 0.26 0.24] :at [1.1 1.9 0] :rot [0 0 -35] :color :brown}`;
    `(nth HP 8)` = `{:shape :cylinder :size [0.1 0.7 0.1] :at [-0.82 1.1 0] :rot [0 0 -20] :color :mane}`;
    `(near? [(p/parts-top HP)] [2.19])` (the ears); `(near? [(span HP)] [2.245])` (~2 m long);
    the four legs (indices 9–12) each have `:size [0.14 0.95 0.14]` and `(second (:at %))` = `0.475`.
34. `t34-same-code-both-looks`: `WS = (assoc-in W [:mirrors :flora :placed 0 :look] (get-in W [:mirrors :juniper :placed 0 :look]))`:
    `FS = (:parts (nth (p/scene-plan WS T0) 0))`: `(count FS)` = `13`;
    `(part-near? (nth FS 0) {:shape :capsule :size [5.2 2.48 2.4] :at [0 4.8 0] :rot [0 0 0] :color :brown})` (Flora's scale 4 on Juniper's look);
    `(near? [(p/parts-top FS)] [8.76])`; the Juniper item is unchanged: `(= (nth (p/scene-plan WS T0) 2) (nth P0 2))`.

There are 34 cases in all: the 22 existing ones (t03, t04, t15, t17, t18 and t22 rewritten,
16 unchanged) plus 12 new ones (t23–t34).
