# Spec: mirrors v0 (Flora and Juniper in the world)

Round `mirrors-v0` · quest `.crew/quests/mirrors-v0.md` · architect: un-architecte · 2026-10-31

Sources: GDD §2.4 (the canal: "slow water, a dock, a worm jar"), §2.5 (mirrors are data, never
code; "a second example from the same code path"), §5 (flat-shaded, warm palette), AGENTS.md
(three.js only through `pm.three`, palette in `pm.scene`, `!` for mutators, 0 warnings).

## 0. Who touches what

| Crew member | May create/edit | Must not touch |
|---|---|---|
| builder (un-automate-monde) | `resources/estate.edn` (edit: add `:placed` only), `src/pm/world.cljs` (edit: make `fill` public, nothing else), `src/pm/plan.cljs` (new), `src/pm/three.cljs` (edit), `src/pm/scene.cljs` (new), `src/pm/core.cljs` (edit) | anything under `test/`, `shadow-cljs.edn`, `pm.kit`, `pm.brook`, `pm.audio`, GDD, `assets/`, `public/kit/` |
| tester (le-testeur) | `test/pm/plan_test.cljs` (new) | everything else, including `test/pm/world_test.cljs` |

- `shadow-cljs.edn` already picks up `pm.plan-test` (`:ns-regexp "-test$"`). Nobody changes it.
- The 32 existing tests in `world_test.cljs` must still pass unchanged (the estate edit only adds keys).
- **No code may name a mirror** (`:flora`, `:juniper`, axolotl, horse, worm, hay, jar, bale) outside
  `resources/estate.edn` and the tests. Colour names in the palette are colours (`:pink`, `:gold`), not things.
- **Everything added to the kit's scene is built with the kit's THREE**, via `pm.three` after
  `use-three!`. `pm.scene` never touches `js/THREE`, the npm module, or `window.hiroshi`.

### Decisions this spec makes (read once)

1. **`:home` stays the GDD place name** (`:canal`, `:barn`, §2.4/§2.5). Positions go on a new
   `:placed` vector of items; each has `:at` and `:look`. (Juniper's `:home :barn` is not where he
   stands this round, which is why a place name and a position are different keys.)
2. **The jar is Flora's second placed item**, labelled `:sign`, so its text comes from Flora's rules.
3. **`:idle` text**: an item may say what its label shows when no marker of its kind holds, with
   `{field}` filled from state. The jar uses `"Worms: {stock}"`, so the count is visible at 15 and
   pressing F visibly changes it (the quest's "jar's count drops"). At stock ≤ 2 Flora's `:sign` rule
   takes over (`"Worms left: 2"`). Items without `:idle` show `""` when no marker holds.
4. **The plan is its own pure namespace, `pm.plan`** (needs `pm.world`, not three.js), so it runs
   in the Node test build. `pm.scene` is the only three.js consumer of the plan.
5. `:at` is the **bottom centre** of the item (it stands on the ground / floats on the water).

## 1. Data: `resources/estate.edn`

Add a `:placed` key to each mirror; change nothing else (keys, values, comments, `:sounds` stay).
Tests depend on these exact values.

```clojure
;; in Flora's mirror map, after :chores
:placed [{:id :body :at [-0.5 2.4 -27.0] :label :say       ; in the creek by the stone canal, at the water surface
          :look {:shape :capsule :size [0.5 0.16 0.16] :color :pink}}  ; 2x real size so you can see her
         {:id :jar  :at [1.8 2.6 -27.0]  :label :sign :idle "Worms: {stock}"   ; on the bank
          :look {:shape :cylinder :size [0.22 0.3 0.22] :color :glass}}]

;; in Juniper's mirror map, after :chores
:placed [{:id :body :at [7.0 1.45 11.0] :label :say        ; in the meadow by the flowers
          :look {:shape :box :size [2.0 1.6 0.6] :color :brown}}]
```

Item shape (all keys but `:at` optional):

| key | meaning |
|---|---|
| `:id` | keyword, unique within the mirror (not checked). Missing → the item's 0-based index in `:placed`. |
| `:at` | `[x y z]` kit world metres, y up, **bottom centre** of the item. Must be a vector of exactly 3 numbers, else the item is skipped. |
| `:look` | `{:shape :size :color}`. `:shape` ∈ `#{:box :cylinder :capsule}` else `:box`. `:size` `[sx sy sz]` bounding box in metres, exactly 3 positive numbers, else `[0.5 0.5 0.5]`. `:color` a palette keyword, else `:stand-in`. Other keys dropped. |
| `:label` | `:say` (bubble) or `:sign`; anything else (or missing) = no label. |
| `:idle` | string shown when no marker of the label's kind holds; `{field}` filled from `:state` like rule text. Non-string → ignored. |

Shapes, all within the `:size` bounding box: `:box` = `sx × sy × sz`; `:cylinder` = upright,
diameter `sx`, height `sy` (`sz` ignored); `:capsule` = lying along x, length `sx`, diameter `sy`
(`sz` ignored).

## 2. `src/pm/world.cljs` (builder)

Only change: `(defn- fill [s state] ...)` becomes public `(defn fill [s state] ...)` with a docstring.
Behaviour unchanged: `nil` s → `nil`; `{field}` → `(str v)`; nil/missing field → placeholder kept.

## 3. `src/pm/plan.cljs`: namespace `pm.plan` (builder)

Requires `[clojure.string :as str]` and `[pm.world :as w]`. Pure: no three.js, no `js/Date`, no atoms.

### `label-lift`
`(def label-lift 0.3)`: metres from the top of an item to its label's centre.

### `(label-text markers kind idle state)` → string
- `markers`: the vector from `w/markers`. `kind`: `:say` or `:sign`.
- `texts` = `(keep kind markers)`. If `(seq texts)` → `(str/join " · " texts)` (space, U+00B7, space).
- Else if `(string? idle)` → `(w/fill idle state)`. Else `""`.

### `(scene-plan world now)` → vector of item maps
- Mirror ids in `(sort (keys (:mirrors world)))` order; within a mirror, `:placed` order.
  Missing `:mirrors`/`:placed` → nothing. Items whose `:at` isn't 3 numbers are skipped.
- `markers` computed once per mirror: `(w/markers world mirror-id now)`.
- Each item map, with `[x y z]` = `:at`, `[sx sy sz]` = normalised size, `h` = `sy`:

```clojure
{:key      [mirror-id item-id]           ; item-id = (:id item), or its index in :placed
 :mirror   mirror-id
 :item     item-id
 :look     {:shape kw :size [sx sy sz] :color kw}   ; normalised (§1)
 :at       [x y z]                        ; as given
 :center   [x (+ y (/ h 2)) z]            ; mesh centre
 ;; only when :label is :say or :sign:
 :label    :say | :sign
 :label-at [x (+ y h label-lift) z]       ; label centre (add left to right)
 :text     (label-text markers label idle (:state mirror))}
```
Unlabelled items have **exactly** the keys `#{:key :mirror :item :look :at :center}`.

Real estate, `W` freshly loaded, at T0 (never fed, never hayed):

```clojure
[{:key [:flora :body] :mirror :flora :item :body
  :look {:shape :capsule :size [0.5 0.16 0.16] :color :pink}
  :at [-0.5 2.4 -27.0] :center [-0.5 2.48 -27.0]
  :label :say :label-at [-0.5 2.86 -27.0] :text "I'm hungry"}
 {:key [:flora :jar] :mirror :flora :item :jar
  :look {:shape :cylinder :size [0.22 0.3 0.22] :color :glass}
  :at [1.8 2.6 -27.0] :center [1.8 2.75 -27.0]
  :label :sign :label-at [1.8 3.2 -27.0] :text "Worms: 15"}
 {:key [:juniper :body] :mirror :juniper :item :body
  :look {:shape :box :size [2.0 1.6 0.6] :color :brown}
  :at [7.0 1.45 11.0] :center [7.0 2.25 11.0]
  :label :say :label-at [7.0 3.35 11.0] :text "Hay, please?"}]
```

### `(label-texts plan)` → map
`{key text}` for every item in `plan` that has `:label`. Unlabelled items absent.

### `(label-changes prev plan)` → map
`prev`: map `{key text}` (or `nil`). Returns `{key text}` for each labelled item in `plan` whose
`:text` differs from `(get prev key)`, including `""` when the key is absent from `prev`. Keys in
`prev` not in `plan` are ignored (no removals this round). Nothing changed → `{}`.

### `(plan-summary plan)` → string
`"scene: mirrors <m> · items <i> · labels <s>/<l>"`, where m = distinct `:mirror` values in `plan`,
i = `(count plan)`, l = items with `:label`, s = labelled items whose `:text` is not `""`.
Empty plan → `"scene: mirrors 0 · items 0 · labels 0/0"`.

## 4. `src/pm/three.cljs` (builder)

### Switching THREE
- `(defonce ^:private !three (atom THREE))`: `THREE` is the npm `"three/webgpu"` module alias (keep
  the require). `defonce` so hot reload keeps the kit's THREE.
- `(three)` → the current THREE object.
- `(use-three! T)` → sets the current THREE to `T`, or back to the npm module when `T` is `nil`;
  returns the now-current THREE.
- **Every** constructor in the file (including the existing `renderer`, `scene`, `camera`, `lights`,
  `box`, `ground`) looks its class up on `(three)` at call time, e.g.
  `(let [^js T (three) Mesh (.-Mesh T)] (Mesh. g m))`. No `THREE/...` call remains outside the
  `defonce`. Existing signatures and behaviour stay the same, so the scaffold is unchanged.

### New helpers (all build with `(three)`)
- `(group opts)` → `Group`. Opts `{:name str :pos [x y z]}`, both optional (pos default `[0 0 0]`).
- `(stand-in {:keys [shape size color pos]})` → flat-shaded `Mesh` (MeshStandardMaterial
  `{:color color :flatShading true}`, cast+receive shadow), centred at `pos`. `color` is an int.
  - `:box` → `BoxGeometry sx sy sz`
  - `:cylinder` → `CylinderGeometry (/ sx 2) (/ sx 2) sy 16`
  - `:capsule` → `CapsuleGeometry (/ sy 2) (max 0 (- sx sy)) 4 8`, mesh `rotation.z` = π/2 so it lies along x
  - anything else → as `:box`
- `(label {:keys [text height paper ink pos]})` → `Sprite` (always faces the camera). Height in
  metres (default 0.3), width `(* 4 height)`; `paper`/`ink` ints (converted privately to `"#rrggbb"`).
  Canvas 512×128 (`js/document.createElement "canvas"`), `CanvasTexture` with `colorSpace`
  `SRGBColorSpace`, `SpriteMaterial #js {:map tex :transparent true :depthWrite false}`. Keeps
  `#js {:canvas :texture :paper :ink}` in `.-userData`. Calls `set-label-text!` with `text` (default `""`).
- `(set-label-text! sprite text)` → sprite. `""` or nil → `visible` false. Otherwise clears the
  canvas, draws a rounded paper rectangle (`roundRect`, radius 24) and the text centred in ink,
  bold 56px sans-serif, shrinking the font so the text fits 472 px wide; `visible` true;
  `texture.needsUpdate` true. Never makes a new texture or canvas.

`^js` hints on every interop local; 0 warnings.

## 5. `src/pm/scene.cljs`: namespace `pm.scene` (builder)

Requires `[pm.three :as t]`, `[pm.plan :as p]`. Never requires `pm.kit` or three.js directly.

- `(def palette {...})`: the only hex literals. At least:
  `:pink 0xf2a7c3 :brown 0x8a5a3b :glass 0xbfe3dc :gold 0xe3c46b :moss 0x7a9a5a :stand-in 0xb8b0a6
   :bubble-paper 0xfffaf0 :bubble-ink 0x3a2d22 :sign-paper 0xe9d3a8 :sign-ink 0x4a3420`.
  A look colour not in the palette → `:stand-in`.
- `(def label-height 0.3)`.
- `(defonce ^:private !mounted (atom nil))`: `{:root group :labels {key sprite} :texts {key text} :plan plan}`.
- `(mount! scene world now)` → nil. If already mounted, does nothing. Otherwise: `plan` =
  `(p/scene-plan world now)`; `root` = `(t/group {:name "pm-mirrors"})`; per item one
  `t/stand-in` at `:center` (name = `(pr-str key)`), and when it has `:label` one `t/label` at
  `:label-at` (`:say` → bubble paper/ink, `:sign` → sign paper/ink; `label-height`); `t/add!` root to
  `scene`; store; then `(update! world now)`.
- `(update! world now)` → nil. Not mounted → nothing (scaffold). Else `plan` = `(p/scene-plan world now)`,
  `changes` = `(p/label-changes texts plan)`, `t/set-label-text!` on each changed sprite only, merge
  `changes` into `:texts`, store `plan`. **Never creates or removes meshes/labels.**
- `(summary)` → `(p/plan-summary plan)` of the stored plan, or `nil` when not mounted.

## 6. `src/pm/core.cljs` (builder)

- Require `[pm.scene :as scene]`.
- `start-kit!`: first `(t/use-three! (.-THREE h))`, then (before `requestAnimationFrame`)
  `(scene/mount! (.-scene h) @!world (js/Date.now))`.
- `frame`: after `tick!`, `(scene/update! @!world (js/Date.now))` every frame.
- `hud-mirrors-suffix`: after the mirror status lines and before the brook,
  `(when-let [s (scene/summary)] (str " · " s))`.
- `start-scaffold!`, `build-scene!`, `tick!`, `reload!`, `on-key!` unchanged. Scaffold: nothing mounted,
  `summary` nil, HUD identical to today.

Expected with the kit: HUD contains `Flora · stock 15 · I'm hungry · scene: mirrors 2 · items 3 · labels 3/3`;
after F: `Flora · stock 14 · scene: mirrors 2 · items 3 · labels 2/3`, Flora's bubble gone, jar reads
`Worms: 14`.

## 7. Test cases: `test/pm/plan_test.cljs`, namespace `pm.plan-test`

Require `[cljs.test :refer [deftest is]]`, `[pm.world :as w]`, `[pm.plan :as p]`. Name each
`deftest` `tNN-<slug>` as listed. Shared values:

```clojure
(def T0 1800000000000)
(def W  (w/load-estate w/estate-edn))
(defn ev [m c at via] {:event :chore-done :mirror m :chore c :at at :via via :who :me})
(def fed (ev :flora :feed T0 :key))
(def hay (ev :juniper :hay T0 :key))
(def W1 (w/chore-done W fed))
(defn stock [n] (assoc-in W [:mirrors :flora :state :stock] n))
(defn by-key [plan] (into {} (map (juxt :key identity)) plan))
(defn texts [world now] (p/label-texts (p/scene-plan world now)))
(defn near? [a b] (and (= (count a) (count b))
                       (every? #(< (js/Math.abs %) 1e-9) (map - a b))))
(def bales-item {:id :bales :at [9.0 1.45 11.0] :label :sign :idle "Bales: {bales}"
                 :look {:shape :box :size [1.0 0.5 0.5] :color :gold}})
(def WJ (update-in W [:mirrors :juniper :placed] conj bales-item))
(def odd-edn
  (pr-str {:mirrors [{:id :zed :state {:n 1}
                      :rules [{:when [:>= :n 1] :say "hi"}
                              {:when [:>= :n 1] :say "there"}
                              {:when [:>= :n 1] :sign "n {n}"}]
                      :placed [{:at [0 0 0]}
                               {:id :a :at [1 2] :label :say}
                               {:id :b :at [1 2 3] :label :shout
                                :look {:shape :cone :size [1 -1 1] :color "red"}}
                               {:id :c :at [0 1 0] :label :say
                                :look {:shape :cylinder :size [2 4 2] :color :moss :extra 1}}
                               {:id :d :at [0 0 0] :label :sign}]}
                     {:id :alpha :state {:k 2} :rules []
                      :placed [{:id :x :at [0 0 0] :label :say :idle "k={k}"}
                               {:id :y :at [0 0 0] :label :sign :idle 7}]}
                     {:id :empty}]}))
(def O (w/load-estate odd-edn))
(def P0 (p/scene-plan W T0))
```

Float positions are checked with `near?`; everything else with `=`.

### Data
1. `t01-estate-placed`: `(count (get-in W [:mirrors :flora :placed]))` = `2`;
   `(count (get-in W [:mirrors :juniper :placed]))` = `1`; `(get-in W [:mirrors :flora :home])` = `:canal`;
   `(get-in W [:mirrors :juniper :home])` = `:barn`; `p/label-lift` = `0.3`;
   `(w/fill "Worms: {stock}" {:stock 15})` = `"Worms: 15"`; `(w/fill "x {nope}" {})` = `"x {nope}"`.

### label-text
2. `t02-label-text`: with `ms = [{:say "a"} {:sign "b"} {:say "c" :sign "d"}]`:
   `(p/label-text ms :say nil {})` = `"a · c"`; `(p/label-text ms :sign nil {})` = `"b · d"`;
   `(p/label-text [{:sign "x"}] :sign "idle" {})` = `"x"`;
   `(p/label-text [] :sign "Worms: {stock}" {:stock 15})` = `"Worms: 15"`;
   `(p/label-text [{:say "hi"}] :sign "Worms: {stock}" {:stock 15})` = `"Worms: 15"`;
   `(p/label-text [] :say nil {})` = `""`; `(p/label-text [] :sign 7 {})` = `""`;
   `(p/label-text [] :sign "Worms: {stock}" {})` = `"Worms: {stock}"`.

### scene-plan: the real estate
3. `t03-plan-order-and-shape`: `(mapv :key P0)` = `[[:flora :body] [:flora :jar] [:juniper :body]]`;
   `(dissoc (nth P0 0) :center :label-at)` = `{:key [:flora :body] :mirror :flora :item :body :look {:shape :capsule :size [0.5 0.16 0.16] :color :pink} :at [-0.5 2.4 -27.0] :label :say :text "I'm hungry"}`;
   `(dissoc (nth P0 1) :center :label-at)` = `{:key [:flora :jar] :mirror :flora :item :jar :look {:shape :cylinder :size [0.22 0.3 0.22] :color :glass} :at [1.8 2.6 -27.0] :label :sign :text "Worms: 15"}`;
   `(dissoc (nth P0 2) :center :label-at)` = `{:key [:juniper :body] :mirror :juniper :item :body :look {:shape :box :size [2.0 1.6 0.6] :color :brown} :at [7.0 1.45 11.0] :label :say :text "Hay, please?"}`.
4. `t04-plan-positions`: `near?` for `:center` / `:label-at` of P0 items 0, 1, 2:
   `[-0.5 2.48 -27.0]` / `[-0.5 2.86 -27.0]`; `[1.8 2.75 -27.0]` / `[1.8 3.2 -27.0]`;
   `[7.0 2.25 11.0]` / `[7.0 3.35 11.0]`.
5. `t05-positions-dont-follow-state`: `(map (juxt :key :at :center :label-at) P0)` =
   `(map (juxt :key :at :center :label-at) (p/scene-plan W1 (+ T0 1000)))` (exact `=`, same computation).

### Flora: hungry/fed, full/low
6. `t06-flora-hungry-full`: `(texts W T0)` = `{[:flora :body] "I'm hungry" [:flora :jar] "Worms: 15" [:juniper :body] "Hay, please?"}`.
7. `t07-flora-fed`: `(texts W1 (+ T0 1000))` = `{[:flora :body] "" [:flora :jar] "Worms: 14" [:juniper :body] "Hay, please?"}`.
8. `t08-flora-hungry-again`: `(get (texts W1 (+ T0 172800000)) [:flora :body])` = `"I'm hungry"`;
   `(get (texts W1 (+ T0 172799999)) [:flora :body])` = `""`; jar at both = `"Worms: 14"`.
9. `t09-flora-low`: `(get (texts (stock 2) T0) [:flora :jar])` = `"Worms left: 2"` (body `"I'm hungry"`);
   `(stock 3)` → jar `"Worms: 3"`; `(stock 0)` → jar `"Worms left: 0"`.
10. `t10-flora-snap`: `S3 = (stock 3)`: `(texts S3 T0)` has body `"I'm hungry"`, jar `"Worms: 3"`;
    `(texts (w/chore-done S3 fed) (+ T0 1))` = `{[:flora :body] "" [:flora :jar] "Worms left: 2" [:juniper :body] "Hay, please?"}`;
    `(get (texts (w/chore-done (stock 1) fed) (+ T0 1)) [:flora :jar])` = `"Worms left: 0"`.
11. `t11-flora-no-stock-field`: `N = (update-in W [:mirrors :flora :state] dissoc :stock)`:
    `(get (texts N T0) [:flora :jar])` = `"Worms: {stock}"`.
12. `t12-key-f-end-to-end`: `K = (w/chore-done W (w/key-event W "F" T0))`;
    `(texts K (+ T0 5000))` = `{[:flora :body] "" [:flora :jar] "Worms: 14" [:juniper :body] "Hay, please?"}`;
    `K2 = (w/chore-done K (w/key-event K "f" (+ T0 5000)))` (ignored, not due): `(texts K2 (+ T0 6000))` = the same map.

### Juniper: the second mirror, same code
13. `t13-juniper-hungry-fed`: `(get (texts W T0) [:juniper :body])` = `"Hay, please?"`;
    `(texts (w/chore-done W hay) (+ T0 1000))` = `{[:flora :body] "I'm hungry" [:flora :jar] "Worms: 15" [:juniper :body] ""}`.
14. `t14-juniper-sign-not-on-bubble`: `B3 = (assoc-in W [:mirrors :juniper :state :bales] 3)`:
    `(get (texts B3 T0) [:juniper :body])` = `"Hay, please?"` (the `:sign` marker "Bales left: 3" holds but
    Juniper has no `:sign` item, so it shows nowhere); `(count (p/scene-plan B3 T0))` = `3`.
15. `t15-juniper-store-item`: on WJ: `(mapv :key (p/scene-plan WJ T0))` =
    `[[:flora :body] [:flora :jar] [:juniper :body] [:juniper :bales]]`;
    `(get (texts WJ T0) [:juniper :bales])` = `"Bales: 6"`;
    with bales 3 (`(assoc-in WJ [:mirrors :juniper :state :bales] 3)`) → `"Bales left: 3"`;
    `item = (get (by-key (p/scene-plan WJ T0)) [:juniper :bales])`: `near?` `:center` `[9.0 1.7 11.0]`,
    `:label-at` `[9.0 2.25 11.0]`; `(:look item)` = `{:shape :box :size [1.0 0.5 0.5] :color :gold}`.
16. `t16-juniper-hay-cycle`: `WJ4 = (reduce w/chore-done WJ (for [k (range 4)] (ev :juniper :hay (+ T0 (* k 43200000)) :key)))`
    (bales 2, last-hayed `1800129600000`): at `(+ T0 129600001)`: body `""`, bales item `"Bales left: 2"`;
    at `(+ T0 172800000)`: body `"Hay, please?"`, bales item `"Bales left: 2"`.
    `(texts (w/chore-done WJ hay) (+ T0 1000))` → bales item `"Bales: 5"`, body `""`.
17. `t17-renamed-copy`: `WP = (assoc-in W [:mirrors :pet] (assoc (get-in W [:mirrors :flora]) :id :pet))`:
    `(mapv :key (p/scene-plan WP T0))` = `[[:flora :body] [:flora :jar] [:juniper :body] [:pet :body] [:pet :jar]]`;
    `(get (texts WP T0) [:pet :body])` = `"I'm hungry"`; `[:pet :jar]` = `"Worms: 15"`;
    `(p/plan-summary (p/scene-plan WP T0))` = `"scene: mirrors 3 · items 5 · labels 5/5"`.

### scene-plan: edges
18. `t18-odd-items`: `PO = (p/scene-plan O 0)`, `BO = (by-key PO)`:
    `(mapv :key PO)` = `[[:alpha :x] [:alpha :y] [:zed 0] [:zed :b] [:zed :c] [:zed :d]]` (`:a` skipped, `:empty` contributes nothing);
    `(set (keys (BO [:zed 0])))` = `#{:key :mirror :item :look :at :center}`; `(:item (BO [:zed 0]))` = `0`;
    `(:look (BO [:zed 0]))` = `{:shape :box :size [0.5 0.5 0.5] :color :stand-in}`; `near?` center `[0 0.25 0]`;
    `(:look (BO [:zed :b]))` = `{:shape :box :size [0.5 0.5 0.5] :color :stand-in}`; `(contains? (BO [:zed :b]) :label)` = `false`; center `[1 2.25 3]`;
    `(:look (BO [:zed :c]))` = `{:shape :cylinder :size [2 4 2] :color :moss}`; center `[0 3 0]`; label-at `[0 5.3 0]`; `:text` `"hi · there"`;
    `(BO [:zed :d])`: `:label` `:sign`, `:text` `"n 1"`, label-at `[0 0.8 0]`;
    `(:text (BO [:alpha :x]))` = `"k=2"`; `(:text (BO [:alpha :y]))` = `""`.
19. `t19-empty-worlds`: `(p/scene-plan {} T0)` = `[]`; `(p/scene-plan {:mirrors {}} T0)` = `[]`;
    `(p/scene-plan (w/load-estate "{:mirrors [{:id :rock}]}") T0)` = `[]`.

### label-texts / label-changes
20. `t20-label-texts`: `(p/label-texts P0)` = the map of case 6;
    `(set (keys (p/label-texts (p/scene-plan O 0))))` = `#{[:alpha :x] [:alpha :y] [:zed :c] [:zed :d]}`;
    `(p/label-texts [])` = `{}`.
21. `t21-label-changes`: `T = (p/label-texts P0)`: `(p/label-changes {} P0)` = `T`; `(p/label-changes nil P0)` = `T`;
    `(p/label-changes T P0)` = `{}`; `(p/label-changes (assoc T [:gone 0] "x") P0)` = `{}`;
    with `PK = (p/scene-plan K (+ T0 5000))` (K from case 12): `(p/label-changes T PK)` = `{[:flora :body] "" [:flora :jar] "Worms: 14"}`;
    snap: `(p/label-changes (texts (stock 3) T0) (p/scene-plan (w/chore-done (stock 3) fed) (+ T0 1)))` =
    `{[:flora :body] "" [:flora :jar] "Worms left: 2"}`;
    Juniper: `(p/label-changes T (p/scene-plan (w/chore-done W hay) (+ T0 1000)))` = `{[:juniper :body] ""}`.

### plan-summary
22. `t22-summary`: `(p/plan-summary P0)` = `"scene: mirrors 2 · items 3 · labels 3/3"`;
    `(p/plan-summary (p/scene-plan W1 (+ T0 1000)))` = `"scene: mirrors 2 · items 3 · labels 2/3"`;
    `(p/plan-summary (p/scene-plan (w/chore-done W hay) (+ T0 1000)))` = `"scene: mirrors 2 · items 3 · labels 2/3"`;
    `(p/plan-summary (p/scene-plan WJ T0))` = `"scene: mirrors 2 · items 4 · labels 4/4"`;
    `(p/plan-summary (p/scene-plan O 0))` = `"scene: mirrors 2 · items 6 · labels 3/4"`;
    `(p/plan-summary [])` = `"scene: mirrors 0 · items 0 · labels 0/0"`.

`pm.three` and `pm.scene` are not unit-tested (they need a browser); the gate compiles them with 0
warnings and Hal checks them live (HUD readout, F changes the bubble and the jar, §6).

## 8. Out of scope / follow-ups for Hal

- Out of scope: real models, animation, swimming, mouse picking, the barn, camera moves, removing
  or moving placed items at runtime (editing `:placed` needs a page reload; `!mounted` is `defonce`).
- GDD §2.5 should gain one line on `:placed` items (`:at`, `:look`, `:label`, `:idle`) and the
  `:home` place-name vs `:at` position split. The crew doesn't edit the GDD this round, so that's a
  manager follow-up.
