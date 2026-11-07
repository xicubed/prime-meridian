# Spec: MotionBricks v0, two crew walkers on the estate

Quest: `.crew/quests/motion-bricks-v0.md`. Round dir: `crew/runs/motion-bricks-v0-20261107-1211/`.

Two walkers pace the west bank, ping-ponging along their routes and pausing at each point.

- **Default:** kinematic figures (a capsule, a head and a visor) glide along at `:speed`, facing the way they're going.
- **With `?bricks`:** the same walkers become SOMA mannequins driven by one shared MotionBricks model.
- **On any failure:** everyone goes back to the figures, and the HUD gives the reason.

The rules live in a pure namespace, `pm.motion`, which is fully tested. The interop lives in `pm.bricks`, which is small and holds no rules. No code names a walker.

## 0. Who touches what

| Who | May edit | Must not touch |
|---|---|---|
| builder (`un-automate-monde`) | `src/pm/motion.cljs` (new), `src/pm/bricks.cljs` (new), `src/pm/three.cljs` (add the §4 functions only), `src/pm/scene.cljs` (add two palette keys only, §5), `src/pm/core.cljs` (wiring, §6), `resources/estate.edn` (add `:motion` and `:walkers` exactly as in §1.1), then `touch src/pm/world.cljs` | `public/vendor/**`, `public/index.html`, `resources/content/*.edn`, every other `src/` file, `docs/GDD.md`, all tests |
| tester (`le-testeur`) | `test/pm/motion_test.cljs` (new) | every `src/`, `resources/`, `public/` file; other tests |

Nobody reads `assets/private/`, `public/kit/` or `public/state/`. There are no npm deps. The build must stay at 0 warnings. Nobody commits.

## 1. Data

### 1.1 Estate (`resources/estate.edn`, top level, next to `:mirrors`)

```clojure
:motion  {:bricks {:lib   "/vendor/three-motionbricks-0.2.0/index.mjs"
                   :rig   :soma
                   :model "https://repalash.com/three-motionbricks/soma-model/"}
          :ground ["SM_Hiroshi_Terrain" "SM_Hiroshi_Gravel"]   ; meshes you can stand on (name prefixes)
          :speed  1.2}                                         ; kinematic m/s
:walkers [{:id :un-automate-monde :name "Un Automate"
           :route [[-6.5 -6.5] [-6.0 -10.0] [-5.0 -14.0]] :pause-s 3}
          {:id :le-testeur :name "Le Testeur"
           :route [[-5.0 -14.0] [-6.0 -10.0] [-6.5 -6.5]] :pause-s 5}]
```

`w/load-estate` already keeps unknown top-level keys, so `(:motion W)` and `(:walkers W)` come through as written. A route point is `[x z]` in the kit's world metres.

### 1.2 Config (`m/config`)

```clojure
{:bricks {:lib "/vendor/three-motionbricks-0.2.0/index.mjs" :rig :soma
          :model "https://repalash.com/three-motionbricks/soma-model/"}
 :ground ["SM_Hiroshi_Terrain" "SM_Hiroshi_Gravel"]
 :speed  1.2}
```

### 1.3 Walker state (one per valid `:walkers` entry, in data order)

```clojure
{:id :un-automate-monde :name "Un Automate"
 :route [[-6.5 -6.5] [-6.0 -10.0] [-5.0 -14.0]] :pause-s 3
 :pos     [-6.5 -6.5]   ; [x z] now
 :heading 1.4288993     ; yaw in radians about +y (§2.1 convention)
 :to      0             ; route index: the point being walked to, or paused at
 :dir     1             ; +1 or -1, the ping-pong direction that chose :to
 :pause   3             ; seconds of pause left; > 0 means standing at route[:to]
 :leg     0             ; how many targets have been picked so far
 :ground  nil           ; last raw ground height under :pos (nil = none yet)
 :y       nil}          ; smoothed height actually shown (nil = hide)
```

A new walker starts standing at its first point, paused for `:pause-s`. It faces its first leg.

### 1.4 Motion status (held by `pm.bricks`, rendered by `m/hud-text`)

```clojure
{:mode :kinematic}
{:mode :loading :progress 0.37}
{:mode :bricks :backend "webgpu" :walkers 2 :gen-ms 420.4}
{:mode :failed :reason "Failed to fetch"}
```

## 2. `pm.motion` (pure; builder writes it, tester tests it)

`(ns pm.motion (:require [clojure.string :as str]))`

It has no three.js, no atoms, no `js/Date` and no walker names. The only JS it uses is `js/Math` and `js/URLSearchParams`, which Node has.

### 2.1 Constants

```clojure
(def default-speed 1.2)   ; m/s, when :motion has no valid :speed
(def arrive-m 0.3)        ; arrival radius, metres
(def smooth-tau 0.15)     ; seconds, height smoothing time constant
(def figure                ; the kinematic figure, in pm.plan's look-part format (+x forward, degrees)
  {:parts [{:shape :capsule :size [1.1 0.42 0.42] :at [0 0.6 0]     :rot [0 0 90] :color :crew}       ; body, stood up
           {:shape :sphere  :size [0.3 0.32 0.3]  :at [0 1.38 0]                  :color :crew-head}  ; head
           {:shape :box     :size [0.06 0.08 0.18] :at [0.14 1.4 0]               :color :eye}]       ; visor: shows facing
   :label-y 1.95})          ; name label height above the walker's ground point
```

**Heading convention.** It is the same as `pm.plan`'s `:yaw`: the object's `rotation.y`. Its local +x then points along world `(cos h, 0, −sin h)`, so a walker moving by `(dx, dz)` faces `h = atan2(−dz, dx)`.

### 2.2 Functions

| Signature | Returns / rules |
|---|---|
| `(yaw dx dz)` | `(js/Math.atan2 (- dz) dx)`. |
| `(config world)` | `{:bricks b :ground g :speed s}`. `b` is `(get-in world [:motion :bricks])`, nil when it isn't a map. `g` is `:ground` filtered to strings, as a vector (`[]` when missing). `s` is `:speed` when it's a positive number, else `default-speed`. |
| `(walker entry)` | A §1.3 state, or nil when the entry is invalid: `:id` must be a keyword and the route must have ≥ 1 valid point. **Valid point**: a vector of exactly 2 numbers. Invalid points are dropped, keeping order. `:name` is used when it's a non-blank string, else `(name id)`. `:pause-s` is used when it's a number ≥ 0, else 0. Initial `:pos` is the first point, `:to 0 :dir 1 :pause pause-s :leg 0 :ground nil :y nil`. `:heading` is `(yaw dx dz)` from point 0 to point 1, or `0` with one point. `:route` and `:pause-s` are stored cleaned. |
| `(walkers world)` | `(into [] (keep walker) (:walkers world))`. `[]` when there are none. |
| `(next-index n i dir)` | Ping-pong, `[i' dir']`. With `n < 2` → `[0 1]`. Otherwise `j = i + dir`: when `0 ≤ j < n` → `[j dir]`, else `[(- i dir) (- dir)]`. |
| `(target w)` | `(get (:route w) (:to w))`, an `[x z]`. |
| `(new-target? before after)` | `(not= (:leg before) (:leg after))`. |
| `(step w speed dt)` | Kinematic advance (below). |
| `(follow w pos idle? dt)` | Advance driven by an outside position (MotionBricks root, below). |
| `(ground-name? s prefixes)` | True when string `s` starts with any string in `prefixes`. A non-string `s` → false. |
| `(ground-height hits prefixes)` | `hits` is `[[y "name…"] …]` (the text format of `t/drop-ray` / `t/down-hits`). Returns the **max** `y` over hits whose name passes `ground-name?`, or nil when none do. A nil/empty `hits` → nil. |
| `(sense-ground w hits prefixes)` | `(if-let [h (ground-height hits prefixes)] (assoc w :ground h) w)`: no hit keeps the last height. |
| `(smooth prev target dt)` | With `prev` nil → `target`. With `target` nil → `prev`. Otherwise `prev + (target − prev)·(1 − e^(−max(dt,0)/smooth-tau))`. |
| `(settle w dt)` | `(assoc w :y (smooth (:y w) (:ground w) dt))`. |
| `(bricks-flag? search)` | True when `search` (e.g. `"?probe=1,2&bricks"`) has a `bricks` param, via `(.has (js/URLSearchParams. (or search "")) "bricks")`. |
| `(load-fraction stage loaded total)` | Turns the library's `onProgress(stage, loaded, total)` into a fraction. `"runtime"` → 0. `"model"` → `loaded/total` when `total > 0`, else 0. `"warmup"` → 1. Any other stage → nil (the caller keeps its last value). |
| `(short-reason msg)` | The first line of `(str msg)`, trimmed. Blank or nil → `"unknown"`. Longer than 40 chars → its first 39 chars + `"…"` (40 in all). |
| `(hud-text status)` | See §2.5. |

### 2.3 `step` (kinematic)

With `dt' = (max 0 dt)`:

1. **Single point.** When the route has fewer than 2 points, return `w` unchanged (identical).
2. **Pausing** (`(:pause w) > 0`). Let `p = pause − dt'`.
   - When `p > 0` → `(assoc w :pause p)`.
   - Otherwise → `(pick w)`.
   - Either way `:pos` and `:heading` are unchanged. A leftover `dt` is **not** carried into movement.
3. **Moving** (`:pause` 0). Let `[tx tz] = (target w)`, `[x z] = :pos`, `dx = tx − x`, `dz = tz − z`, `d = hypot(dx, dz)`.
   - When `d ≤ arrive-m` → `(arrive w)`, with pos and heading unchanged.
   - Otherwise `s = min(speed·dt', d)`, `:pos = [x + dx/d·s, z + dz/d·s]` and `:heading = (yaw dx dz)`. When `d − s ≤ arrive-m`, also `(arrive …)`.

The two helpers are private:
- `(pick w)`: `[i' dir'] = (next-index (count route) (:to w) (:dir w))` → `(assoc w :to i' :dir dir' :pause 0 :leg (inc leg))`.
- `(arrive w)`: when `:pause-s > 0` → `(assoc w :pause pause-s)`, otherwise `(pick w)`, so a 0-second pause goes straight on.

`dt` 0 leaves the walker unchanged: a paused walker gets `:pause` set to the same value, and a moving one moves 0 m. When it's more than 0.3 m from the target, it isn't arriving either.

### 2.4 `follow` (MotionBricks)

It is the same as `step`, except:
- First, when `pos` is non-nil, `w` becomes `(assoc w :pos pos)`. A nil `pos` keeps `:pos`.
- With fewer than 2 route points, return that `w`. Don't pause.
- Pausing: the same as `step` §2.3.2. `idle?` is ignored.
- Moving: never moves and never changes `:heading`. `d` is measured from the (new) `:pos`. **Arrival when `d ≤ arrive-m` or `idle?` is true** (the character came to rest short of the point: don't wait forever).

`pm.bricks` calls `moveTo` exactly when `(new-target? before after)`.

### 2.5 `hud-text`

All separators are `" · "` (U+00B7), the same as the rest of the HUD.

| status | text |
|---|---|
| `{:mode :kinematic}` | `"motion: kinematic"` |
| `{:mode :loading :progress f}` | `"motion: loading model N%"`, with `N = round(100·clamp(f or 0, 0, 1))` (`js/Math.round`) |
| `{:mode :bricks :backend b :walkers n :gen-ms g}` | `"motion: bricks"`, then `" " b` when `b` is a non-blank string, then `" · " n " walker"` (plus `"s"` unless `n` = 1), then `" · gen " round(g) " ms"` when `g` is a number |
| `{:mode :failed :reason r}` | `"motion: kinematic (bricks failed: " r ")"` (the caller passes `r` through `short-reason`) |
| nil, or any other `:mode` | nil (no segment) |

## 3. `pm.bricks` (interop; builder only, no tests)

`(ns pm.bricks (:require [pm.three :as t] [pm.motion :as m] [pm.plan :as p] [pm.scene :as scene]))`

It holds one `defonce` atom with `{:status <§1.4> :walkers [<§1.3>…] :views [{:anchor :figure :char}…] :ground-meshes [...] :frame n :cfg <config>}`.

### Public functions

**`(start! h world)`** → nil. `h` is the kit handle (`window.hiroshi`). It is a no-op when already started (hot reload) or when `(m/walkers world)` is empty.
1. Make the root `(t/group {:name "pm-walkers"})` and add it to `(.-scene h)`.
2. For each walker, make an **anchor** group: `:pos [x (or y 0) z]`, named `(str "pm-walkers/" (name id))`.
   - It holds a **figure** group: `rotation.y` = `:heading`, with `m/figure`'s parts built by `t/stand-in`. Colours come from `scene/palette`, and `:rot` goes through `p/radians`.
   - It also holds a `t/label` at `[0 (:label-y m/figure) 0]`. The label's text is `:name`, its `:height` is `scene/label-height`, and it uses the sign colours (`:sign-paper`/`:sign-ink`).
   - The anchor is hidden (`t/set-visible! false`) while `:y` is nil.
3. Collect the ground meshes with `(t/find-meshes (.-scene h) #(m/ground-name? % (:ground cfg)))`. Sample each walker's ground once, then `settle` it with dt 0 (so `:y = :ground`).
4. Set status `{:mode :kinematic}`. When `(m/bricks-flag? (.. js/window -location -search))` and `(:bricks cfg)` is non-nil, set `{:mode :loading :progress 0}` and start the load (below).

**`(tick! h dt)`** → nil. Call it every frame with `dt` capped at 0.1. It does nothing before `start!`.
- **Ground.** Raycast walker `i` only on frames where `(mod frame 4) = (mod i 4)`: `(m/sense-ground w (t/down-hits meshes x z 40) prefixes)`. While `:ground-meshes` is empty, search again every 60th frame.
- **Kinematic** (any mode but `:bricks`, so loading keeps the figures walking):
  - `w' = (m/settle (m/step w speed dt) dt)`;
  - the anchor goes to `[x y z]`, the figure's yaw to `:heading`, and the anchor is visible when `:y` is non-nil.
- **Bricks**, per walker and its character `c`:
  - `pos` = `[(.-x v) (.-z v)]` of `(.getPosition c)`;
  - `w' = (m/settle (m/follow w pos (.-idle c) dt) dt)`;
  - when `(m/new-target? w w')`: `(.moveTo c (t/vec3 [tx (:y w') tz]) "walk")`;
  - then `(.update c dt)`. Set `c.object.position.y` to `:y` (x and z stay where they were placed at swap-in), and the anchor to `[x y z]`.
  - Any exception in this branch → `fallback!`.
- Refresh the HUD's `:gen-ms` from the first character's `(.. c -stats -averageInferenceMs)`.

**`(hud-segment)`** → `(m/hud-text (:status @!state))`, or nil before `start!`.

**`(status)`** → the status map, or nil.

### Load (private)

1. `((js/Function. "u" "return import(u)") (:lib b))` → module. `C = (.-MotionBricksCharacter mod)`.
2. Make one `(C. #js {:rig (name (:rig b)) :model (:model b)})` per walker, then `(.setMannequin c)` on each. Each character's `.-onError` is `fallback!`.
3. Call `(.load first on-progress)`. `on-progress` is `(fn [stage l t] …)`: when `(m/load-fraction stage l t)` is non-nil, it sets `:progress`. Then call `(.loadFrom c first)` for each of the rest (`js/Promise.all`). Then **swap in**.
4. Any rejection anywhere → `fallback!`. Never load the primitive clips: don't call `sit`/`setObstacles`/`perform`.

**Swap in**, for each walker and character:
- `c.object` goes to `[x (or y 0) z]` with yaw `:heading`, added under the root. The controller starts at the object's local origin, so the character starts where the figure stood.
- Hide the figure; keep the label.
- When the walker's `:pause` is 0, call `moveTo` its current `(m/target w)` **now**. Otherwise `follow`'s `idle?` would count as an instant arrival.
- Status: `{:mode :bricks :backend (.-backend first) :walkers n :gen-ms …}`.

**`fallback! e`** runs once (it is ignored when the status is already `:failed` or `:kinematic`):
- `console.warn` the full error;
- `(.dispose c)` each character (in `try`), and remove its object;
- show the figures; walkers continue kinematically from their current `:pos`;
- status `{:mode :failed :reason (m/short-reason (or (.-message e) e))}`.

## 4. `pm.three` additions (builder)

All existing functions stay unchanged. The new ones are below, and each type-hints `^js`.

| Signature | Does |
|---|---|
| `(vec3 [x y z])` | Returns `new T.Vector3(x, y, z)` with the current THREE. |
| `(set-pos! obj [x y z])` | Sets `obj.position`; returns `obj`. |
| `(set-yaw! obj rad)` | Sets `obj.rotation.y`; returns `obj`. |
| `(set-visible! obj on?)` | Sets `obj.visible` to `(boolean on?)`; returns `obj`. |
| `(remove! parent obj)` | Calls `parent.remove(obj)`; returns `parent`. |
| `(find-meshes root pred)` | Traverses `root` and returns a vector of every object with `isMesh` whose `.name` satisfies `(pred name)`. A nil root → `[]`. |
| `(down-hits objs x z from)` | Casts a ray down from `(x, from, z)` through the seq `objs` (`intersectObjects`, recursive). Returns all hits nearest first as `[[y name] …]`. `y` is **not** rounded. `name` is formatted as in `drop-ray` (object name or type, plus `"/"` and the material name when there is one). Never throws; on an error → `[]`. |

## 5. `pm.scene` palette (builder)

Add exactly two keys to `palette`: `:crew 0x5b7fa3` (muted blue) and `:crew-head 0xf0e6d6` (mannequin off-white). Nothing else in the file changes.

## 6. `pm.core` wiring (builder)

- `(:require … [pm.bricks :as bricks])`.
- In `start-kit!`, after `(scene/mount! …)`: `(bricks/start! h @!world)`.
- In `frame`, after `(tick! (min dt 0.1))`: `(when-let [h (kit/handle)] (bricks/tick! h (min dt 0.1)))`. The scaffold has no kit handle, so it gets no walkers.
- In `hud-mirrors-suffix`, right after the `scene/summary` segment: `(when-let [s (bricks/hud-segment)] (str " · " s))`.

## 7. Test cases (`test/pm/motion_test.cljs`, ns `pm.motion-test`)

The test requires `[cljs.test :refer [deftest is]] [pm.motion :as m] [pm.world :as w]` and sets `W = (w/load-estate w/estate-edn)`.

**Tolerance.** Compare reals with `|a − b| < 1e-6` and vectors element by element (`near?`).

**Fixture.**

```clojure
F = (m/walker {:id :f :name "F" :route [[0 0] [3 4] [6 0]] :pause-s 2})
```

`Fm` is F after its first pick: `(assoc F :pause 0 :to 1 :leg 1)`.

**Run helper.**

```clojure
(run w f n)  ; applies (f w) n times, collecting (:to w') each time (:leg w') changes
```

### Config and walkers

1. `(:speed (m/config W))` = 1.2; `(:ground (m/config W))` = `["SM_Hiroshi_Terrain" "SM_Hiroshi_Gravel"]`; `(:rig (:bricks (m/config W)))` = `:soma`; `(:lib (:bricks (m/config W)))` = `"/vendor/three-motionbricks-0.2.0/index.mjs"`.
2. `(m/config {})` = `{:bricks nil :ground [] :speed 1.2}`. `(m/config {:motion {:speed -1 :ground ["A" 3]}})` = `{:bricks nil :ground ["A"] :speed 1.2}`.
3. `(mapv :id (m/walkers W))` = `[:un-automate-monde :le-testeur]`; `(mapv :name (m/walkers W))` = `["Un Automate" "Le Testeur"]`.
4. First walker of W: `:pos [-6.5 -6.5]`, `:to 0`, `:dir 1`, `:pause 3`, `:leg 0`, `:ground nil`, `:y nil`, `:heading` ≈ 1.4288993 (= atan(7)).
5. Second walker of W: `:pos [-5.0 -14.0]`, `:pause 5`, `:to 0`, `:heading` ≈ −1.8157750 (= −(π − atan 4)).
6. F: `:pos [0 0]`, `:heading` ≈ −0.9272952, `:pause 2`, `:pause-s 2`, `:name "F"`.
7. ```clojure
   (m/walkers {:walkers [{:id "x" :route [[0 0]]}
                         {:id :a :route []}
                         {:id :b :route [[1] [2 3] ["a" 1] [4 5]] :pause-s -2}
                         {:id :c :route [[0 0]] :name ""}]})
   ```
   This gives two walkers:
   - `:b`: `:route [[2 3] [4 5]]`, `:pause-s 0`, `:pause 0`, `:name "b"`, `:pos [2 3]`, `:heading` ≈ −0.7853982;
   - `:c`: `:route [[0 0]]`, `:name "c"`, `:heading 0`.
8. `(m/walkers {})` = `[]`.

### Ping-pong

9. `(m/next-index 3 0 1)` = `[1 1]`; `(3 1 1)` = `[2 1]`; `(3 2 1)` = `[1 -1]`; `(3 1 -1)` = `[0 -1]`; `(3 0 -1)` = `[1 1]`.
10. `(m/next-index 2 0 1)` = `[1 1]`; `(2 1 1)` = `[0 -1]`; `(2 0 -1)` = `[1 1]`; `(m/next-index 1 0 1)` = `[0 1]`; `(m/next-index 0 0 -1)` = `[0 1]`.
11. `(m/target F)` = `[0 0]`; `(m/target Fm)` = `[3 4]`.
12. `(m/new-target? F Fm)` is true; `(m/new-target? F (assoc F :pause 1))` is false.

### Pauses (`step`)

13. `(m/step F 1 0.5)` → `:pause` 1.5, with `:pos [0 0]`, `:to 0`, `:leg 0` unchanged.
14. `(m/step F 1 2)` → `:pause 0`, `:to 1`, `:dir 1`, `:leg 1`, `:pos [0 0]`.
15. `(m/step F 1 3)` → the same as 14 (leftover time isn't moved): `:pos [0 0]`, `:leg 1`.
16. End of route: `(m/step (assoc F :to 2 :dir 1 :pause 0.5 :pos [6 0] :leg 4) 1 1)` → `:to 1`, `:dir -1`, `:leg 5`, `:pause 0`.
17. `(m/step F 1 0)` → `:pause 2` (equal to F). `(m/step F 1 -1)` → `:pause 2`.

### Kinematic movement, arrival and heading

18. `(m/step Fm 1 1)` → `:pos` ≈ `[0.6 0.8]`, `:heading` ≈ −0.9272952, `:pause 0`, `:leg 1`.
19. From `M = (assoc Fm :pos [0.6 0.8])`: `(m/step M 1 3.6)` → `:pos` ≈ `[2.76 3.68]`, `:pause 0` (0.4 m left).
20. `(m/step M 1 3.8)` → `:pos` ≈ `[2.88 3.84]`, `:pause 2` (0.2 m left ≤ 0.3: arrived), `:to 1`.
21. Overshoot capped: `(m/step M 10 1)` → `:pos` ≈ `[3 4]`, `:pause 2`.
22. Already inside: `(m/step (assoc Fm :pos [2.9 4.0] :heading 0.5) 1 0.1)` → `:pause 2`, `:pos [2.9 4.0]`, `:heading 0.5`.
23. Zero pause goes straight on: with `B` the `:b` walker from case 7, `(m/step B 1 0.1)` → `:to 1`, `:dir 1`, `:leg 1`, `:pause 0`, `:pos [2 3]`.
24. Single point: with `C` the `:c` walker from case 7, `(m/step C 1 5)` is `identical?` to `C`.
25. `(m/yaw 1 0)` ≈ 0; `(m/yaw 0 -1)` ≈ 1.5707963; `(m/yaw 0 1)` ≈ −1.5707963; `(m/yaw 3 4)` ≈ −0.9272952; `|(m/yaw -1 0)|` ≈ 3.1415927.
26. Facing −z: `(m/step (m/walker {:id :g :route [[0 0] [0 -2]] :pause-s 0}) 1 0.1)`, stepped again with speed 1 and dt 0.5, → `:pos` ≈ `[0 -0.5]`, `:heading` ≈ 1.5707963.

### Real data, both walkers (second-walker coverage)

27. First walker: `(m/step (m/step W1 1.2 3) 1.2 1)` → after the first step `:leg 1 :to 1 :pause 0`; after the second, `:pos` ≈ `[-6.3302944 -7.6879394]`, `:heading` ≈ 1.4288993.
28. Second walker: `(m/step (m/step W2 1.2 5) 1.2 1)` → `:pos` ≈ `[-5.2910428 -12.8358290]`, `:heading` ≈ −1.8157750, `:to 1`.
29. For **every** walker `x` in `(m/walkers W)`: `(m/step x 1.2 (:pause-s x))` has `:leg 1`, `:to 1`, `:pause 0`.
30. Order: `(run F #(m/step % 1 0.1) 400)` = `[1 2 1 0 1 2]`, and the final `:leg` is 6.
31. Order, second walker: `(run W2 #(m/step % 1.2 0.1) 320)` = `[1 2 1 0]` (C→B→A→B→C), and the final `:leg` is 4.

Timing margins for 30 and 31 (hand-checked):
- **Case 30.** A leg takes 64–72 steps (44–51 walking plus 20–21 paused). The 6th pick lands at step ≤ 381, and the 7th at ≥ 404.
- **Case 31.** The 4th pick lands at steps 283–297, and the 5th at ≥ 360.

### `follow`

32. `(m/follow Fm [1 1] false 0.1)` → `:pos [1 1]`, `:pause 0`, `:to 1`, `:heading` ≈ −0.9272952 (unchanged).
33. `(m/follow Fm [2.9 3.9] false 0.1)` → `:pause 2` (0.1414 m ≤ 0.3).
34. `(m/follow Fm [1 1] true 0.1)` → `:pause 2`, `:pos [1 1]` (it came to rest: idle arrival).
35. `(m/follow F [0.1 0] false 0.5)` → `:pause 1.5`, `:pos [0.1 0]`. `(m/follow F [0 0] true 0.5)` → `:pause 1.5` (`idle?` is ignored while paused).
36. `(m/follow F [0.1 0] false 2)` → `:pause 0`, `:to 1`, `:leg 1`, `:pos [0.1 0]`, and `(m/new-target? F that)` is true.
37. `(m/follow Fm nil false 0.1)` → `:pos [0 0]`, `:pause 0` (5 m away, not idle).
38. Second walker: `(m/follow (assoc W2 :pause 0 :to 1 :leg 1) [-6.1 -10.1] false 0.1)` → `:pause 5` (0.1414 m).

### Ground

Let `P = ["SM_Hiroshi_Terrain" "SM_Hiroshi_Gravel"]`.

39. `(m/ground-height [[3.1 "SM_Hiroshi_Terrain/M_Terrain"] [2.4 "SM_Hiroshi_Gravel"]] P)` = 3.1 (the highest qualifying hit).
40. `(m/ground-height [[5.0 "pm-walkers/le-testeur"] [4.2 "SM_Tree_01/Bark"] [2.65 "SM_Hiroshi_Terrain"]] P)` = 2.65.
41. `(m/ground-height [[2.0 "Mesh"]] P)` is nil; `(m/ground-height [] P)` is nil; `(m/ground-height nil P)` is nil; `(m/ground-height [[2.0 "X_SM_Hiroshi_Terrain"]] P)` is nil (a prefix, not a substring); `(m/ground-height [[2.0 "SM_Hiroshi_Terrain"]] [])` is nil.
42. `(m/ground-name? "SM_Hiroshi_Gravel_02" P)` is true; `(m/ground-name? nil P)` is false.
43. `(:ground (m/sense-ground F [[2.1 "SM_Hiroshi_Terrain"]] P))` = 2.1. `(:ground (m/sense-ground (assoc F :ground 2.1) [[9 "Rock"]] P))` = 2.1 (keeps the last height).

### Smoothing

44. `(m/smooth nil 2.1 0.016)` = 2.1; `(m/smooth 2.0 nil 0.1)` = 2.0; `(m/smooth nil nil 0.1)` is nil.
45. `(m/smooth 2.0 3.0 0.15)` ≈ 2.6321206; `(m/smooth 2.0 3.0 0)` = 2.0; `(m/smooth 2.0 3.0 10)` ≈ 3.0.
46. `(:y (m/settle (assoc F :ground 2.65) 0))` = 2.65. `(:y (m/settle (assoc F :y 2.0 :ground 3.0) 0.15))` ≈ 2.6321206. `(:y (m/settle (assoc F :y 2.0) 0.1))` = 2.0.

### Flag, progress, reasons

47. `(m/bricks-flag? "?bricks")` is true; `"?probe=1,2&bricks"` is true; `"?bricks=0"` is true; `""` is false; `nil` is false; `"?bricksx"` is false; `"?fresh"` is false.
48. `(m/load-fraction "model" 37 100)` ≈ 0.37; `("model" 5 0)` = 0; `("runtime" 0 1)` = 0; `("warmup" 0 1)` = 1; `("other" 1 2)` is nil.
49. `(m/short-reason "Failed to fetch")` = `"Failed to fetch"`; `(m/short-reason nil)` = `"unknown"`; `(m/short-reason "   ")` = `"unknown"`; `(m/short-reason "line one\nline two")` = `"line one"`; `(m/short-reason "  spaced  ")` = `"spaced"`.
50. `(m/short-reason (apply str (repeat 50 "a")))` = 39 `a`s followed by `"…"` (count 40). `(m/short-reason (apply str (repeat 40 "a")))` = the 40 `a`s, unchanged.

### HUD

51. `(m/hud-text {:mode :kinematic})` = `"motion: kinematic"`.
52. `(m/hud-text {:mode :loading :progress 0.37})` = `"motion: loading model 37%"`; `0.999` → `"… 100%"`; `nil` → `"motion: loading model 0%"`; `1.5` → `"… 100%"`; `-1` → `"… 0%"`.
53. `(m/hud-text {:mode :bricks :backend "webgpu" :walkers 2 :gen-ms 420.4})` = `"motion: bricks webgpu · 2 walkers · gen 420 ms"`.
54. `(m/hud-text {:mode :bricks :backend "wasm" :walkers 1 :gen-ms 419.5})` = `"motion: bricks wasm · 1 walker · gen 420 ms"`.
55. `(m/hud-text {:mode :bricks :backend "" :walkers 2 :gen-ms nil})` = `"motion: bricks · 2 walkers"`.
56. `(m/hud-text {:mode :failed :reason "no network"})` = `"motion: kinematic (bricks failed: no network)"`.
57. `(m/hud-text nil)` is nil; `(m/hud-text {:mode :off})` is nil.

### Figure data

58. `(count (:parts m/figure))` = 3. Every part's `:shape` is in `#{:box :sphere :cylinder :cone :capsule}`. The `:color` set is `#{:crew :crew-head :eye}`. `(:label-y m/figure)` = 1.95.

## 8. Live check (Hal; not unit-tested)

- **Default load:** two figures pace the bank and the HUD shows `motion: kinematic`. The figures stand on the grass, appearing only once a ground height is known.
- **`?bricks`:** the HUD shows `loading model N%` and then `bricks <backend> · 2 walkers · gen N ms`. Two mannequins walk on the grass. A reload is served from the browser cache (the library's Cache Storage).
- **Breaking the model URL** shows `motion: kinematic (bricks failed: …)`, and the figures walk on.
- The kit's camera and the 💭/🔊 buttons still work.

**A note for Hal on the data (it is not changed here).** Both routes share the middle point `[-6 -10]` and are out of phase. By hand, with kinematic speed 1.2:
- Un Automate pauses there from about 5.7 s to 8.7 s, and Le Testeur arrives at about 8.2 s, so for about half a second both stand on the same spot.
- Around 21–23 s they cross head-on between the first and second points.

Offsetting one route by about 1 m in x, or changing a `:pause-s`, would avoid this.
