# Spec: senses v0: walkers only react to what they can see or hear

Quest: `.crew/quests/senses-v0.md`. Follows `avoid-v0` (planner in `pm.motion`, geometry in
`pm.avoid`). Start commit `fbfbdea`.

## 1. The idea in one paragraph

Each walker keeps a small **awareness map** of the other walkers: who it currently perceives
(seen in a forward cone, or heard nearby from any direction), for how long, and whom it has
**noticed**. Perception is updated **every frame** (time-based, inside `m/replan`, so kinematic
and MotionBricks modes both get it with no change to their tick code). The planner still runs
every 0.25 s, but `plan-all` now passes each walker **only the walkers it has noticed**.
Statics (footprints, `:obstacles`) are untouched and always known. `m/plan` itself is
unchanged except that it uses the walker's own `:speed` when it has one. Old tests that call
`plan` directly with `others` keep their meaning.

Why per-walker `:speed` (new, optional): with one shared kinematic speed, nobody can ever come
up behind a walking walker, so the quest's overtake can't happen in a test or live. A walker
entry may now say `:speed 0.6`. `advance` steps it at that speed, and `plan` predicts its path
at that speed. Walkers without it behave exactly as before. (Modelled: if the slow leader is
planned at the shared 1.2 m/s, it wrongly predicts a collision with the follower cutting back in,
and it sidesteps by 3 cm. Planned at its own speed, it never leaves its line.)

## 2. Data

### 2.1 `resources/estate.edn` (builder adds; then `touch src/pm/world.cljs`)

Add under `:motion`, after `:display-scale`:

```clojure
:senses {:sight-m 8 :sight-deg 110   ; sees others within 8 m inside ±55° of where it faces
         :hear-m 1.2                  ; hears others within 1.2 m (× their speed / walking pace, if faster)
         :react-s 0.4                 ; must perceive someone this long, continuously, to notice them
         :forget-s 1.5}               ; forgets them after this long unperceived
```

Walkers don't change. Optional per-walker keys (documented here, not added to the estate):

```clojure
{:id :x :route [...] :senses {:sight-deg 200} :speed 0.8}   ; partial override; kinematic m/s
```

Tip for Hal's live check (not part of the build): to see an overtake in kinematic mode, put
`:speed 0.8` on one walker temporarily.

### 2.2 Walker state (new fields from `m/walker`)

```clojure
{;; ... all existing fields unchanged ...
 :senses {:sight-m 8 :sight-deg 110 :hear-m 1.2 :react-s 0.4 :forget-s 1.5}
 :speed  nil          ; or a positive number from the entry
 :facing nil          ; radians, same convention as :heading; nil = use :heading
 :aware  {}           ; other-id -> entry (below)
 :glance false}
```

An awareness entry, keyed by the other walker's `:id`:

```clojure
{:on 0.25        ; seconds perceived continuously (0 while unperceived)
 :off 0          ; seconds unperceived since it was last perceived (0 while perceived)
 :noticed false  ; true once :on reached :react-s; stays true until forgotten
 :how :sight}    ; this frame: :sight (seen; maybe heard too), :hear (heard only), nil (not perceived)
```

Entries exist only while the other is perceived or still noticed. A not-noticed entry that
stops being perceived is dropped at once (continuity broken). A noticed one is dropped when
`:off` reaches `:forget-s`.

`:glance` is true when some entry is `:noticed` and `:how :hear`: someone the walker knows is
there, close, and out of sight. It's for debugging for now (the ?paths hearing circle changes
colour); the head turn comes later. The HUD text does not change.

### 2.3 Conventions (existing)

- Heading: `yaw = atan2(-dz, dx)`. The forward vector of heading `h` is `[cos h, -sin h]`, so
  `h = 0` faces +x and `h = π/2` faces −z.
- Distances are centre to centre in the xz plane (radii are not subtracted).
- Walking pace for hearing is the constant `m/walk-pace` = 1.2 m/s (not cfg `:speed`).
- Time tolerance: "reached" means `>= limit - 1e-9`, because 8 × 0.05 = 0.39999999999999997.

## 3. Public functions (`pm.motion`)

All pure. No three.js, atoms, clock or walker names.

### 3.1 Constants

```clojure
(def default-senses {:sight-m 8 :sight-deg 110 :hear-m 1.2 :react-s 0.4 :forget-s 1.5})
(def walk-pace 1.2)        ; m/s, the speed at which hearing range is :hear-m
(def face-min-speed 0.05)  ; m/s; slower than this, :facing is kept
```

### 3.2 `(senses base override)` → senses map

- `base` is a full senses map. `override` may be a map, nil or anything else.
- For each of the five keys: if `(get override k)` is a number, not NaN, and `>= 0`, use it.
  `:sight-deg` is capped at 360 (`(min 360 v)`). Otherwise use `(get base k)`.
- Keys other than the five are ignored. When `override` is not a map, the result is `base`
  (as a 5-key map).
- `0` is valid: `:react-s 0` means notice on the first perceived frame, and `:hear-m 0` means
  hearing only at distance 0.

### 3.3 `(senses-config world)` → senses map

`(senses default-senses (get-in world [:motion :senses]))`. `{}` gives `default-senses`.

### 3.4 `walker` / `walkers` changes

- `(walker entry)` adds the fields of §2.2:
  - `:senses (senses default-senses (:senses entry))`;
  - `:speed`: the entry's `:speed` when it is a number `> 0`, else nil;
  - `:facing nil`, `:aware {}`, `:glance false`.

  Every existing field is unchanged.
- `(walkers world)`: before calling `walker`, it sets the entry's `:senses` to
  `(senses (senses-config world) (:senses entry))`, so world values apply and per-walker keys
  override them. Radius and display-scale handling is unchanged.

### 3.5 `(forward heading)` → `[fx fz]`

`[(js/Math.cos heading) (- (js/Math.sin heading))]`.

### 3.6 `(facing w)` → radians

`(or (:facing w) (:heading w))`.

### 3.7 `(sees? w o)` → boolean

- `s = (or (:senses w) default-senses)`.
- `d` = the xz distance from `(:pos w)` to `(:pos o)`.
- `d > :sight-m` → false. `d = 0` → true. Otherwise true when
  `dot(forward(facing w), (o - w)) / d >= cos(:sight-deg / 2 in radians) - 1e-9`.

### 3.8 `(hear-range w o)` → metres

`(:hear-m s) × max(1, |(:vel o)| / walk-pace)`. A missing `:vel` counts as `[0 0]`.

### 3.9 `(hears? w o)` → boolean

`d <= (hear-range w o)`, from any direction.

### 3.10 `(perceive w others dt)` → walker

- `dt' = (max 0 dt)`.
- **Facing first:** if `|(:vel w)| >= face-min-speed`, set `:facing (yaw vx vz)`. Otherwise keep
  `:facing` as it is (possibly nil). Sight below uses the updated facing.
- Others whose `:id` equals `(:id w)` are ignored.
- For each remaining other `o` (with `e` = old entry or nil):
  - **Perceived** (`(or (sees? w o) (hears? w o))`):
    - `on' = (+ (or (:on e) 0) dt')`
    - entry `{:on on' :off 0 :noticed (or (true? (:noticed e)) (>= on' (- react-s 1e-9))) :how (if seen :sight :hear)}`
  - **Not perceived, and `e` was noticed:**
    - `off' = (+ (:off e) dt')`
    - if `off' >= forget-s - 1e-9`, drop the entry; otherwise keep
      `{:on 0 :off off' :noticed true :how nil}`
  - **Not perceived, and not noticed:** drop the entry.
- Ids in the old `:aware` that aren't among `others` are handled as "not perceived" (the forget
  timer keeps running).
- `:glance` = `(boolean (some #(and (:noticed %) (= :hear (:how %))) (vals aware')))`.
- Returns `w` with the new `:facing`, `:aware` and `:glance`. Nothing else changes.

### 3.11 `(perceive-all ws dt)` → vector

For each `i`, `(perceive (ws i) <all other walkers> dt)`, reading the input positions and
velocities (not the results of earlier walkers in the same call). Keeps order and count.

### 3.12 `(noticed w others)` → vector

`(filterv #(true? (get-in (:aware w) [(:id %) :noticed])) others)`, in the order of `others`.

### 3.13 Changed planner functions

- **`plan`:** the only change is `speed (or (:speed w) (:speed cfg))` where it used
  `(:speed cfg)` (it is used for both `path-block` calls).
- **`plan-all`:** `(plan w (noticed w <others>) statics cfg)`.
- **`replan`** (same signature `[before after statics cfg dt acc]`):
  - First `after` becomes `(perceive-all after dt)`, on every call.
  - Then the existing logic runs: plan when `acc + dt >= 0.25 - 1e-9` or a leg changed (comparing
    `:leg` of before and after), returning `[(plan-all after' ...) 0]`. Otherwise it returns
    `[after' (+ acc dt)]`.
- **`advance`:** steps each walker with `(step w (or (:speed w) (:speed cfg)) dt)`, then calls
  `replan` as now.
- **`follow`:** unchanged. It still does not touch `:heading` (existing t32). In MotionBricks
  mode, `:facing` comes from `perceive` via the measured `:vel`.

### 3.14 `(cone-points pos heading sight-m sight-deg n)` → `[[x z] ...]`

- `n' = (max 1 n)`, `half = sight-deg / 2` in radians.
- Points: the apex `pos`; then, for `k = 0..n'`, the angle `a = heading - half + k × (2·half / n')`
  gives `[x + sight-m·cos a, z - sight-m·sin a]`; then `pos` again.
- That makes `n' + 3` points, a closed outline. `k = 0` is on the walker's right side (+z when
  facing +x).

Unchanged: `hud-text`, `steer-command`, `step`, everything in `pm.avoid`.

## 4. Interop

### `pm.bricks` (`?paths` only)

- `walker-path-lines!` adds two lines per walker:
  - `:cone`: `(m/cone-points (:pos w) (m/facing w) sight-m sight-deg 16)` (19 points), lifted to
    `[x (+ y 0.05) z]`, colour `:path-sight`;
  - `:hear`: `(a/circle-points (:pos w) hear-m 24)` at the same height, colour `:path-hear`.

  `sight-m`, `sight-deg` and `hear-m` come from `(:senses w)`.
- `build-paths!` adds both lines to `paths-root` and stores them in the walker's line map.
- `update-paths!` rewrites both with `t/set-polyline!` each frame (constant point counts, so they
  are updated in place). It also calls `(t/set-line-color! (:hear lines) (part-color (if (:glance w) :path-glance :path-hear)))`.
- No other change: perception already happens inside `m/replan`, which `tick-kinematic!` (via
  `m/advance`) and `tick-bricks!` both call. The MotionBricks model walks at its own pace (a
  walker's `:speed` only affects kinematic stepping and the planner's prediction).

### `pm.three`

`(set-line-color! line color)`: `(.setHex (.-color (.-material line)) color)`, with `^js` hints.
Returns nil.

### `pm.scene` palette

Three new keys: `:path-sight 0xfaf089`, `:path-hear 0x9f7aea`, `:path-glance 0x63b3ed`.

## 5. Who touches what

| Crew member | Files |
|---|---|
| **builder** `un-automate-monde` | `src/pm/motion.cljs`, `src/pm/bricks.cljs`, `src/pm/three.cljs` (`set-line-color!` only), `src/pm/scene.cljs` (3 palette keys), `resources/estate.edn` (`:motion :senses`), `touch src/pm/world.cljs` (no edit) |
| **tester** `le-testeur` | `test/pm/senses_test.cljs` (new; namespace `pm.senses-test`). Edits no existing test. |
| nobody | `public/vendor/`, `resources/content/`, `assets/private/`, `public/kit/`, `public/state/`, existing tests |

Existing tests must still pass unchanged. The JS model predicts:

| Test | Model | Threshold |
|---|---|---|
| avoid t60 head-on, min distance | 0.939 | ≥ 0.6 |
| avoid t61 crossing, min distance | 0.858 | ≥ 0.6 |
| avoid t65 estate, min distance | 0.631 | ≥ 0.6 |
| avoid t65 estate, min static gap | 0.161 | ≥ 0 |
| characters t17 (display scale 1.4), min distance | 0.962 | ≥ 0.84 |

Ping-pong orders are unchanged. avoid t31/t32 and feet t39–t44 call `m/plan` directly and are
unaffected.

## 6. Test fixtures (tester)

```clojure
(ns pm.senses-test
  (:require [cljs.test :refer [deftest is]]
            [pm.motion :as m] [pm.avoid :as a] [pm.world :as w]))

(def W (w/load-estate w/estate-edn))
(def cfg {:speed 1.2 :horizon-s 2})
(defn near? [a b] (< (js/Math.abs (- a b)) 1e-6))
(defn vnear? [a b] (and (= (count a) (count b)) (every? true? (map near? a b))))
(defn dist [[x1 z1] [x2 z2]] (js/Math.hypot (- x1 x2) (- z1 z2)))
(def PI js/Math.PI)

(def A (m/walker {:id :a :route [[0 0] [10 0]]}))       ; at [0 0], heading 0: faces +x, :pause 0
(defn o [id pos vel] (assoc (m/walker {:id id :route [[0 0] [1 0]]}) :pos pos :vel vel))
(def B5 (o :b [5 0] [0 0]))     ; ahead, 5 m
(def Bk (o :b [-1 0] [0 0]))    ; behind, 1 m (heard)
(def Bf (o :b [-5 0] [0 0]))    ; behind, 5 m (neither)
(defn run [w others dt n] (nth (iterate #(m/perceive % others dt) w) n))

(defn mv [e] (assoc (m/walker e) :pause 0 :to 1 :leg 1))
(def L (mv {:id :l :route [[0 0] [8 0]] :pause-s 1}))
(def S1 [{:at [4 0] :r 0.5}])
(def F (m/walker {:id :f :route [[0 0] [3 4] [6 0]] :pause-s 2}))
(def Fm (assoc F :pause 0 :to 1 :leg 1))
(def Ah (assoc (mv {:id :a :route [[0 0] [10 0]] :pause-s 100}) :vel [1.2 0]))
(def Hh (assoc (mv {:id :h :route [[10 0] [0 0]] :pause-s 100}) :pos [4 0] :vel [-1.2 0]))
(def seen-1s {:on 1 :off 0 :noticed true :how :sight})

(defn frames [ws statics n]          ; vector of the walker vectors after each frame, dt 0.05
  (loop [ws ws acc 0 i 0 out []]
    (if (>= i n) out
      (let [[ws' acc'] (m/advance ws statics cfg 0.05 acc)]
        (recur ws' acc' (inc i) (conj out ws'))))))
(defn first-index [pred frs] (first (keep-indexed (fn [i x] (when (pred x) i)) frs)))
```

Frame index `i` in `frs` is the state after frame `i + 1` (time `0.05 × (i + 1)`).

## 7. Test cases

All expected values were worked out by hand (§8 shows the arithmetic). The scenario values were
also checked in a JS model of `pm.motion`/`pm.avoid` plus these rules.

### Config and walker data

1. **Defaults.**
   - `(= {:sight-m 8 :sight-deg 110 :hear-m 1.2 :react-s 0.4 :forget-s 1.5} m/default-senses)`
   - `(= m/default-senses (m/senses-config W))`
   - `(= m/default-senses (m/senses-config {}))`
2. **Merging and validation.**
   - `(m/senses m/default-senses {:sight-m 5 :hear-m -1 :react-s "x" :sight-deg 400 :extra 1})`
     → `{:sight-m 5 :sight-deg 360 :hear-m 1.2 :react-s 0.4 :forget-s 1.5}`
   - `(m/senses m/default-senses nil)` → `m/default-senses`
   - `(m/senses m/default-senses "x")` → `m/default-senses`
   - `(:react-s (m/senses m/default-senses {:react-s 0}))` → `0`
   - `(:hear-m (m/senses m/default-senses {:hear-m js/NaN}))` → `1.2`
3. **Walker fields.**
   - `A`: `(:senses A)` = `m/default-senses`, `(:aware A)` = `{}`, `(:glance A)` is `false`,
     `(:facing A)` is nil, `(:speed A)` is nil.
   - `(:speed (m/walker {:id :s :route [[0 0]] :speed 0.6}))` → `0.6`
   - `:speed` of `0`, `-1` or `"x"` → nil.
   - `(:senses (m/walker {:id :s :route [[0 0]] :senses {:hear-m 2}}))` →
     `{:sight-m 8 :sight-deg 110 :hear-m 2 :react-s 0.4 :forget-s 1.5}`
4. **World and per-walker overrides.**
   - Call `(mapv :senses (m/walkers {:motion {:senses {:sight-m 6}} :walkers [{:id :a :route [[0 0]] :senses {:hear-m 2}} {:id :b :route [[0 0]]}]}))`.
   - Result: `[{:sight-m 6 :sight-deg 110 :hear-m 2 :react-s 0.4 :forget-s 1.5} {:sight-m 6 :sight-deg 110 :hear-m 1.2 :react-s 0.4 :forget-s 1.5}]`
5. **Estate, both walkers.**
   - For each of `(m/walkers W)`: `:senses` = `m/default-senses`, `:aware {}`, `:glance false`,
     `:speed nil`, `:facing nil`.
   - `(mapv :id (m/walkers W))` → `[:un-automate-monde :le-testeur]` (unchanged).

### Geometry

6. **`forward`.**
   - `(m/forward 0)` → `[1 0]`
   - `(m/forward (/ PI 2))` ≈ `[0 -1]`
   - `(m/forward (m/yaw 3 4))` ≈ `[0.6 0.8]`
7. **`facing`.**
   - `(m/facing A)` → `0`
   - `(m/facing (assoc A :facing 2))` → `2`
   - `(m/facing (assoc A :heading 1))` → `1`
8. **Seen in front, inside the cone.** `(m/sees? A (o :b P [0 0]))` is true for P =
   - `[5 0]` (0°)
   - `[5 3]` (30.96°)
   - `[3 -4]` (53.13°, the other side)
   - `[7.9 0]` (inside the range)
9. **Not seen.** `(m/sees? A (o :b P [0 0]))` is false for P =
   - `[-3 0]` (behind)
   - `[3 5]` (59.04°, outside ±55°)
   - `[8.5 0]` (beyond 8 m)
   - `[-5 0]`
10. **Sight follows the facing, not the route.**
    - Facing −z: `(def Az (assoc A :heading (/ PI 2)))` gives `(m/sees? Az (o :b [0 -5] [0 0]))`
      true, and false for `[0 5]` and `[5 0]`.
    - Facing −x: `(def Ax (assoc A :facing PI))` gives `(m/sees? Ax (o :b [-5 0] [0 0]))` true, and
      false for `[5 0]`.
11. **Edge cases.**
    - `(m/sees? A (o :b [0 0] [0 0]))` → true (coincident).
    - With `:sight-deg 360` (`(assoc A :senses (assoc m/default-senses :sight-deg 360))`), `[-3 0]`
      is seen.
    - With `:sight-m 2`, `[3 0]` is not seen.
12. **Hearing range.** `(m/hear-range A (o :b [0 0] V))` is:
    - `1.2` for `V` = `[0 0]` or `[0.6 0]`
    - `2.4` for `[2.4 0]`
    - `3` for `[1.8 2.4]` (speed 3 → 1.2 × 2.5)
    - `1.2` when `:vel` is dissoc'ed

    With A's `:hear-m 2` and `V [2.4 0]` it is `4`.
13. **Heard behind when close, louder when fast.**
    - `(m/hears? A Bk)` → true, while `(m/sees? A Bk)` → false.
    - `(m/hears? A (o :b [-1.5 0] [1.2 0]))` → false.
    - `(m/hears? A (o :b [-1.5 0] [2.4 0]))` → true.
    - `(m/hears? A (o :b [0.8 -0.8] [0 0]))` → true (d 1.1314, beside).
    - `(m/hears? A (o :b [0 1.3] [0 0]))` → false.

### Perception over time

14. **One frame seen.** `(m/perceive A [B5] 0.1)`:
    - `:aware` = `{:b {:on 0.1 :off 0 :noticed false :how :sight}}`
    - `:glance false`
    - `:pos`, `:heading` and `:route` are unchanged.
15. **Reaction delay (dt 0.1).**
    - `(get-in (run A [B5] 0.1 3) [:aware :b :noticed])` → false (`:on` ≈ 0.3).
    - `(run A [B5] 0.1 4)` → noticed true (`:on` ≈ 0.4).
    - `(m/noticed (run A [B5] 0.1 3) [B5])` → `[]`
    - `(m/noticed (run A [B5] 0.1 4) [B5])` → `[B5]`
16. **Reaction delay, other steps.**
    - dt 0.05: noticed false after 7 frames, true after 8 (`:on` = 0.39999999999999997, which
      passes the 1e-9 tolerance).
    - dt 0.25: false after 1 frame, true after 2.
17. **Continuity.**
    - `w3 = (run A [B5] 0.1 3)`.
    - `w4 = (m/perceive w3 [Bf] 0.1)` → `:aware {}` (perceived for 0.3 s, then lost before being noticed).
    - `(m/perceive w4 [B5] 0.1)` → `{:b {:on 0.1 :off 0 :noticed false :how :sight}}` (the count restarts).
18. **Forgetting.**
    - `wn = (run A [B5] 0.1 4)` (noticed).
    - `g1 = (m/perceive wn [Bf] 0.5)` → `:aware {:b {:on 0 :off 0.5 :noticed true :how nil}}`.
    - `g2 = (m/perceive g1 [Bf] 0.5)` → `:off 1.0`, noticed true; `(m/noticed g2 [Bf])` → `[Bf]`.
    - `g3 = (m/perceive g2 [Bf] 0.5)` → `:aware {}`; `(m/noticed g3 [Bf])` → `[]`.
19. **Seen again inside the forget window.** `(m/perceive g2 [B5] 0.1)` → `:aware {:b {:on 0.1 :off 0 :noticed true :how :sight}}` (still noticed, no new delay).
20. **An other missing from the list is "not perceived".** `(m/perceive wn [] 0.5)` →
    `:aware {:b {:on 0 :off 0.5 :noticed true :how nil}}`.
21. **`react-s 0`, dt 0 and negative dt.**
    - `(m/perceive (assoc A :senses (assoc m/default-senses :react-s 0)) [B5] 0.1)` → `:b` noticed true.
    - `(m/perceive A [B5] 0)` → `{:b {:on 0 :off 0 :noticed false :how :sight}}`.
    - `(m/perceive A [B5] -1)` → the same.
22. **Glance: noticed by hearing only.**
    - `(run A [Bk] 0.1 3)` → `:b` has `:how :hear`, noticed false, and `:glance` is false.
    - `(run A [Bk] 0.1 4)` → noticed true, `:how :hear`, `:glance` true.
23. **Louder when fast.**
    - `(run A [(o :b [-2 0] [2.4 0])] 0.1 4)` → `:b` noticed true, `:how :hear`, `:glance` true.
    - `(run A [(o :b [-2 0] [0 0])] 0.1 4)` → `:aware {}`, `:glance` false.
24. **Glance clears.** With `wg = (run A [Bk] 0.1 4)`:
    - `(m/perceive wg [(o :b [1 0] [0 0])] 0.1)` → `:how :sight`, noticed true, `:glance` false.
    - `(m/perceive wg [Bf] 0.1)` → `:how nil`, noticed true, `:glance` false.
25. **Two others.** `Cc = (o :c [-1 0] [0 0])`; `w2 = (run A [B5 Cc] 0.1 4)`:
    - `:aware` keys are `#{:b :c}`.
    - `:b` has `:how :sight`, `:c` has `:how :hear`; both are noticed.
    - `:glance` is true.
    - `(m/noticed w2 [Cc B5])` → `[Cc B5]` (the order of the argument).
26. **Self ignored.** `(:aware (m/perceive A [A] 0.1))` → `{}`.
27. **Facing comes from motion.**
    - `(m/perceive (assoc A :vel [0 1.2]) [] 0.1)` → `:facing` ≈ −1.5707963. That walker sees
      `(o :b [0 5] [0 0])` and does not see `(o :b [5 0] [0 0])`.
    - `(:facing (m/perceive (assoc A :vel [0.03 0]) [] 0.1))` → nil (too slow).
    - Facing is updated before sensing:
      `(get-in (m/perceive (assoc A :vel [0 1.2]) [(o :b [0 5] [0 0])] 0.1) [:aware :b :how])` →
      `:sight`.
28. **`follow` + `perceive` (MotionBricks path).**
    - `f1 = (m/follow Fm [1 1] false 0.1)`: `:heading` ≈ −0.9272952 (unchanged), `:facing` nil,
      `:vel` ≈ `[10 10]`.
    - `(:facing (m/perceive f1 [] 0.1))` ≈ −0.7853982.
29. **`perceive-all` is one-sided when one has its back turned.**
    - `Bb = (m/walker {:id :b :route [[3 0] [10 0]]})` (at `[3 0]`, faces +x, with A 3 m
      behind it).
    - `(m/perceive-all [A Bb] 0.1)` → the first walker's `:aware` is
      `{:b {:on 0.1 :off 0 :noticed false :how :sight}}`; the second's is `{}`.
    - The result is still 2 walkers, ids `[:a :b]`.

### Planner

30. **`plan-all` ignores walkers nobody noticed.** `(m/plan-all [Ah Hh] [] cfg)` → both `:aim` nil,
    `:side` nil, `:wait` 0.
31. **Only the one who noticed detours.**
    - Call `(m/plan-all [(assoc Ah :aware {:h seen-1s}) Hh] [] cfg)`.
    - First: `:aim` ≈ `[2.4081643 1.1306643]`, `:side :right`.
    - Second: `:aim` nil.
32. **Both noticed → head-on keeps right (the t31 values).**
    - Call `(m/plan-all [(assoc Ah :aware {:h seen-1s}) (assoc Hh :aware {:a seen-1s})] [] cfg)`.
    - First: aim ≈ `[2.4081643 1.1306643]`, side `:right`.
    - Second: aim ≈ `[1.5918357 -1.1306643]`, side `:right`.
33. **Statics always known.**
    - `(m/plan-all [(assoc L :pos [2 0])] S1 cfg)` → `:aim` ≈ `[4.3424073 1.4449073]`,
      `:side :right` (with `:aware {}`).
    - The same with an unnoticed walker present: `(m/plan-all [(assoc L :pos [2 0]) (o :z [2 3] [0 0])] S1 cfg)` → the first walker's aim ≈ `[4.3424073 1.4449073]`.
34. **`plan` uses the walker's own speed.**
    - `(m/plan (assoc L :pos [2 0] :speed 0.5) [] S1 cfg)` → `:aim` nil, `:side` nil. It looks
      ahead 1 m; the static's centre is 2 m ahead; at the end of the look-ahead the distance is
      1 > 0.95.
    - `(m/plan (assoc L :pos [2 0]) [] S1 cfg)` → aim ≈ `[4.3424073 1.4449073]` (unchanged).
35. **`advance` uses the walker's own speed.**
    - `(m/advance [(assoc L :speed 0.6)] [] cfg 0.1 0)` → the walker's `:pos` ≈ `[0.06 0]`,
      `:vel` ≈ `[0.6 0]`, acc ≈ 0.1.
    - `(m/advance [L] [] cfg 0.1 0)` → `:pos` ≈ `[0.12 0]`.
36. **`replan` perceives on every call.**
    - `[ws acc] = (m/replan [A B5] [A B5] [] cfg 0.1 0)`: acc ≈ 0.1;
      `(:aware (first ws))` = `{:b {:on 0.1 :off 0 :noticed false :how :sight}}`;
      `(:aware (second ws))` = `{}` (B5 faces +x, and A is 5 m behind it).
    - `[ws2 acc2] = (m/replan ws ws [] cfg 0.1 0.2)`: acc2 = 0 (it planned);
      `(get-in (first ws2) [:aware :b :on])` ≈ 0.2.

### Cone outline

37. `(m/cone-points [0 0] 0 8 90 2)` ≈ `[[0 0] [5.6568542 5.6568542] [8 0] [5.6568542 -5.6568542] [0 0]]`.
38. `(m/cone-points [1 2] (/ PI 2) 2 180 2)` ≈ `[[1 2] [3 2] [1 0] [-1 2] [1 2]]`.
    - `(count (m/cone-points [0 0] 0 8 110 16))` → 19.
    - `(m/cone-points [0 0] 0 8 90 0)` ≈ `[[0 0] [5.6568542 5.6568542] [5.6568542 -5.6568542] [0 0]]` (n treated as 1).

### Scenarios (dt 0.05)

39. **Overtake: the one behind detours, the one ahead keeps its line.**
    - Setup:
      - `lead = (assoc (mv {:id :lead :route [[0 0] [20 0]] :pause-s 100}) :pos [2 0] :speed 0.6)`
      - `fol = (mv {:id :fol :route [[0 0] [20 0]] :pause-s 100})`
      - `frs = (frames [lead fol] [] 400)`
    - The leader (index 0):
      - `:aim` is nil in every frame;
      - `(js/Math.abs z)` < 1e-9 in every frame;
      - its final `:pos` ≈ `[14 0]`.
    - The follower (index 1):
      - some frame has a non-nil `:aim`;
      - the max `z` over all frames is ≥ 0.5 (model 0.794);
      - at the first frame where its x ≥ the leader's x, its z > 0.5 (model 0.779): it passes
        on its right (+z when heading +x);
      - its final `:pause` > 0, `:to` 1, and it is within 0.3 of `[20 0]`.
    - The pair's min distance over all frames is ≥ 0.6 (model 0.776).
    - The follower first notices the leader at frame index 7, with
      `(get-in fol [:aware :lead :how])` = `:sight`.
    - The leader first notices the follower at index `i` (model 36), where:
      - the pair distance is ≤ 1.2 (model 1.035);
      - `(get-in lead [:aware :fol :how])` = `:hear`;
      - the leader's `:glance` is true;
      - before index `i`, the leader never had a noticed entry.
40. **Mirrored overtake: follower listed first, heading −x.**
    - Setup:
      - `fol = (mv {:id :fol :route [[0 0] [-20 0]] :pause-s 100})`
      - `lead = (assoc (mv {:id :lead :route [[0 0] [-20 0]] :pause-s 100}) :pos [-2 0] :speed 0.6)`
      - `frs = (frames [fol lead] [] 400)`
    - The leader (now index 1): no `:aim` in any frame; |z| < 1e-9 throughout; final `:pos` ≈
      `[-14 0]`.
    - The follower (index 0):
      - the min z over all frames is ≤ −0.5;
      - at the first frame where its x ≤ the leader's x, its z < −0.5 (right side when heading
        −x);
      - its final position is within 0.3 of `[-20 0]`, with `:pause` > 0.
    - The pair's min distance is ≥ 0.6.
    - The follower first notices at index 7 (`:sight`). The leader first notices with
      distance ≤ 1.2, `:how :hear` and `:glance` true.
41. **Head-on: both see, both keep right.**
    - Setup: `ws0 = [(mv {:id :a :route [[0 0] [10 0]] :pause-s 100}) (mv {:id :h :route [[10 0] [0 0]] :pause-s 100})]`,
      `frs = (frames ws0 [] 240)`.
    - Both walkers' first noticed index is 23, with `:how :sight`, and the pair distance there is
      ≈ 7.12. At index 22, neither has noticed the other.
    - At the first frame where `x_a - x_h` changes sign: `z_a` > 0 > `z_h` (model ±0.487).
    - The pair's min distance is ≥ 0.6 (model 0.939).
    - At the end, both have `:pause` > 0 and `:to` 1. Walker a is within 0.3 of `[10 0]`, h within
      0.3 of `[0 0]`.
42. **Estate, both walkers, 2400 frames (120 s).**
    - Setup: `ws0 = (m/walkers W)`, `statics = (a/statics W)`, `frs = (frames ws0 statics 2400)`.
    - Noticing at the start:
      - each walker's first frame with the other noticed is index 7 (they start 7.65 m apart,
        facing each other, both paused);
      - `(get-in (nth (nth frs 7) 0) [:aware :le-testeur :how])` → `:sight`;
      - `(get-in (nth (nth frs 7) 1) [:aware :un-automate-monde :how])` → `:sight`.
    - Ping-pong: the picks (the `:to` after each `:leg` change) are `[1 2 1 0 1 2 1 0]` for both
      walkers (first 8).
    - Final `:leg` ≥ 12 for both (model 16 and 14).
    - Pair min distance ≥ 0.6 (model 0.631).
    - Min static gap (centre distance − static r − walker radius) ≥ 0 (model 0.161).
    - At least one frame has some walker with `:glance` true (model: 405 and 363 frames).
    - The test may use the two ids from the data; nothing in `src/` names them.
43. **Unchanged surfaces.**
    - `(m/hud-text {:mode :kinematic})` → `"motion: kinematic"`.
    - `(m/hud-text {:mode :kinematic :detours 1})` → `"motion: kinematic · 1 detour"`.
    - `(:speed (m/config W))` → `1.2`.
    - `(:ground (m/config W))` → `["SM_Hiroshi_Terrain" "SM_Hiroshi_Gravel"]`.
    - `(m/avoid-config W)` → `{:radius 0.3 :horizon-s 2}`.

## 8. Hand arithmetic

- **Cone half-angle** is 55°, so its cosine is 0.573576.
  - `[5 3]`: cos = 5/√34 = 0.857493 (30.96°), seen.
  - `[3 -4]`: cos = 3/5 = 0.6 (53.13°), seen.
  - `[3 5]`: cos = 3/√34 = 0.514496 (59.04°), not seen.
  - `[-3 0]`: cos = −1, not seen.
- **Facing.**
  - `forward(π/2)` = `[cos, −sin]` = `[0, −1]`.
  - `yaw(0, 1.2)` = atan2(−1.2, 0) = −π/2 → forward `[0, 1]`: `[0 5]` is dead ahead and `[5 0]`
    is at 90°.
  - `yaw(10, 10)` = atan2(−10, 10) = −π/4 = −0.7853982.
- **Hearing.**
  - `[1.8 2.4]` has speed 3, so the range is 1.2 × 3/1.2 = 3.
  - `[-1.5 0]` at 1.2 m/s: range 1.2 < 1.5, not heard. At 2.4 m/s: range 2.4 ≥ 1.5, heard.
  - `[0.8 -0.8]`: d = 1.1314 ≤ 1.2, heard.
  - `[-2 0]` at 2.4 m/s: range 2.4 ≥ 2, heard; standing still: 1.2 < 2, not heard.
- **Timers.**
  - 0.1 + 0.1 + 0.1 = 0.30000000000000004 < 0.4, then + 0.1 = 0.4 ≥ 0.4 − 1e-9.
  - 8 × 0.05 = 0.39999999999999997 ≥ 0.4 − 1e-9 (7 × 0.05 = 0.35).
  - 0.25 + 0.25 = 0.5.
  - Forgetting: 0.5, then 1.0 (< 1.5, kept), then 1.5 (≥ 1.5 − 1e-9, dropped).
- **Own-speed plan (34).**
  - Look-ahead = min(6, 0.5 × 2) = 1 m, so t-max = 2 s.
  - Relative velocity of the static: (−0.5, 0); d0 = (2, 0).
  - Raw t* = 4, clamped to 2. The remaining distance is 2 − 1 = 1 ≥ 0.75 + 0.2 = 0.95 → no block.
  - At cfg speed 1.2 the look-ahead is 2.4 m, so the static blocks (avoid t24).
- **`advance` own speed (35).** 0.6 × 0.1 = 0.06.
- **Head-on noticing (41).**
  - The gap after frame k is 10 − 2 × 1.2 × 0.05k = 10 − 0.12k.
  - Frame 16: 8.08 (out of range). Frame 17: 7.96 (in range).
  - Perceived for 8 frames by frame 24 (index 23), where the gap is 10 − 2.88 = 7.12.
  - Nobody detours before noticing (no statics), so the line stays straight until then.
- **Estate start (42).**
  - W1 sits at [−6.5 −6.5] facing [−6 −10]; W2 at [−5 −14] facing [−6 −10]; d = √(1.5² + 7.5²)
    = 7.649 < 8.
  - W1's forward is (0.1414, −0.9899); the direction to W2 is (0.1961, −0.9806); the angle is
    3.1°.
  - W2's forward is (−0.2425, 0.9701); the direction to W1 is (−0.1961, 0.9806); the angle is
    2.9°.
  - Both are paused (vel 0), so facing = heading, and both are noticed at frame 8 (index 7).
- **Overtake (39).**
  - The follower sees the leader from frame 1 (2 m ahead, 0°) → noticed at index 7, gap
    2 − 0.6 × 0.4 = 1.76.
  - The leader faces +x with the follower behind it, so it can't see it. It hears the follower
    once the gap is ≤ 1.2 (the follower is at walking pace, so the range is 1.2), and it notices
    0.4 s later, at gap ≈ 1.035 (model). By then the follower is beside it and moving away
    faster, so the leader's own-speed prediction finds no block and it keeps z = 0.

## 9. Done when

- `bash .crew/gate.sh` passes: 0 warnings in `app` and `test`; all existing tests plus
  `pm.senses-test` (43 cases) pass.
- Live, Hal checks with `?paths`:
  - each walker draws a yellow sight cone and a purple hearing circle, which turns blue during a
    glance;
  - when one walker overtakes another from behind (kinematic, with `:speed 0.8` on one walker
    temporarily), only the follower steps aside;
  - head-on, both keep right;
  - the HUD text is unchanged, and the frame rate stays at 60 fps.
