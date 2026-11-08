# Spec: crew-walkers v0 — the whole crew on the estate

Quest: `.crew/quests/crew-walkers-v0.md` (TASKS W3-0). Follows `crew-talk-v0`.
Architect: un-architecte. Builder: un-automate-monde. Tester: le-testeur.

## 0. What changes

- **Data:** `resources/estate.edn` `:walkers` grows from 2 to 6. Code names no walker.
- **`pm.motion`** (pure):
  - walkers gain `:tint`, `:loop` and `:face`;
  - `next-index` can loop;
  - a paused walker can face a point;
  - colour multiply helpers;
  - a **generation gate**, a pure FIFO that lets at most one MotionBricks generation start per frame, with at most one in flight;
  - the HUD shows `· queue n`.
- **`pm.crew`:** `plate-name`, so the roster title from `crew.edn` `:titles` names the plate, falling back to `:name`.
- **`pm.tint`** (new, small, node-testable): clones a character's materials and multiplies their colour by the walker's tint. It touches three.js objects only through `.traverse`, `.isMesh`, `.material`, `.clone` and `.color.getHex/setHex`, and imports nothing.
- **`pm.bricks`** (interop):
  - tints mannequins and kinematic figures;
  - plates use `plate-name`;
  - the generation gate is wrapped around the shared MotionBricks engine;
  - `:queue` goes into the status.

**Routes: none nudged.** Every point and segment clears the statics, the creek, the plateau rock and its patch (§4, tests 9–11).
- **Le Coureur's loop** closes with the `:loop true` flag (last point → first).
- **Le Coureur's speed:** `:speed 1.9` (1.6 × 1.2 = 1.92, rounded) works in kinematic mode. MotionBricks has fixed gaits (walk 1.13 m/s, run 3.43 m/s), so with `?bricks` he walks at gait pace, and `:pause-s 1` is what makes him quicker there.
- **Le Critique faces `[-6 -10]`** (the west bank walkers' middle) while paused. That turns his figure (kinematic) and his sight cone (both modes).
- **The plateau rock at `[-5 -17]`** is not added as a static: Le Docteur's route never comes within 1.118 m of it, and nobody else walks there.

Six-walker model check: the scratchpad JS model of `pm.motion`/`pm.avoid`/senses, with these routes, loop, speed and face, over 2400 frames of 0.05 s. The same model reproduces the senses spec's pair-only figures, 0.631 / 0.962.

| display scale | closest pair | closest static gap |
|---|---|---|
| 1.0 | Un Automate–Le Testeur 0.784 | 0.155 |
| 1.4 | 0.933 | 0.152 |

- The west bank pair keeps picks `1 2 1 0 1 2 1 0` with legs ≥ 15.
- Le Coureur picks `1 2 3 0 …`.
- So the existing scenario tests t65, t17 and t42 still hold with six walkers.

## 1. Files and owners

| Who | May touch |
|---|---|
| **Builder** (un-automate-monde) | `resources/estate.edn` (`:walkers` only), `src/pm/motion.cljs`, `src/pm/crew.cljs`, new `src/pm/tint.cljs`, `src/pm/bricks.cljs` |
| **Tester** (le-testeur) | new `test/pm/crew_walkers_test.cljs` (ns `pm.crew-walkers-test`), plus **exactly** these existing assertions, which assume two walkers (new values in §5): `motion_test` t03; `characters_test` t10 and t15; `crew_test` t26 and t27; `senses_test` t05 |
| Nobody | `public/vendor/` (the gate wraps the engine object at runtime; the vendored file is not edited), `resources/content/`, other tests, `pm.three`, `pm.core` |

Never send keys or prompts to crew members.

## 2. Data: `resources/estate.edn` `:walkers`

The first two stay first and unchanged (tests index 0 and 1). Each new entry gets a one-line comment naming its patch.

```clojure
:walkers [{:id :un-automate-monde :name "Un Automate" :agent :un-automate-monde
            :route [[-6.5 -6.5] [-6.0 -10.0] [-5.0 -14.0]] :pause-s 3
            :character {:file "/assets/characters/polybot.glb" :rig :mixamo}}
           {:id :le-testeur :name "Le Testeur" :agent :le-testeur
            :route [[-5.0 -14.0] [-6.0 -10.0] [-6.5 -6.5]] :pause-s 5}            ; NVIDIA's mannequin, untinted (white)
           {:id :un-architecte :name "Un Architecte" :agent :un-architecte
            :route [[6.0 -4.0] [4.0 -6.0] [3.5 -7.5]] :pause-s 8 :tint 0x4a7bd0}   ; east bank, north side: walks slowly, stops to think
           {:id :le-coureur :name "Le Coureur" :agent :le-coureur
            :route [[3.0 -8.5] [5.0 -10.0] [3.0 -12.0] [2.2 -10.0]] :loop true
            :speed 1.9 :pause-s 1 :tint 0xe8833a}                                 ; east bank, south side: a longer, quicker loop
           {:id :le-critique :name "Le Critique" :agent :le-critique
            :route [[-8.0 -4.0] [-7.2 -5.2]] :pause-s 6 :face [-6.0 -10.0]
            :tint 0x8e5cc4}                                                       ; woods' edge, north end of the west bank, watching the pair
           {:id :le-docteur :name "Le Docteur" :agent :le-docteur
            :route [[-6.0 -17.5] [-6.0 -20.0]] :pause-s 6 :tint 0x2f9e8f}]       ; south plateau by Flora; the rock at [-5 -17] is not walkable
```

`:motion` is unchanged.

## 3. Public functions

### 3.1 `pm.motion` (changed)

**`walker`** gains three keys. Everything else is as now.
- `:tint`: `(:tint entry)` when it is an integer in `0..0xffffff`, else nil.
- `:loop`: `(true? (:loop entry))`.
- `:face`: `(:face entry)` when it is a valid point `[x z]` (the same `valid-point?` as routes), else nil.

**`(next-index n i dir)`** is unchanged. It becomes `(next-index n i dir false)`.

**`(next-index n i dir loop?)`**:
- `n` < 2 → `[0 1]`;
- `loop?` true → `[(mod (inc i) n) 1]`;
- else the 3-arity rule.

**`pick`** (private) passes `(:loop w)` as `loop?`.

**`step`**, the pause branch: while the remaining pause `p` is > 0 and `(:face w)` is non-nil, also set `:heading` and `:facing` to
`(yaw (- fx x) (- fz z))`, the direction from `(:pos w)` to the face point. When the face point is the walker's own position
(distance 0), leave both unchanged. When `p` ≤ 0, it picks as now and doesn't face. `:vel` is `[0 0]` as now.

**`follow`**, the pause branch: the same face rule while `p` > 0. It sets `:heading` and `:facing`; the position and `:vel` come from
the character as now.

**`(tint-hex base tint)`** → an integer.
- When both are integers in `0..0xffffff`, each 8-bit channel is `(js/Math.round (/ (* cb ct) 255))`. This is an sRGB per-channel multiply.
- Otherwise → `base`, unchanged; a nil tint means untinted.

**`(figure-hexes palette tint)`** → a vector, one hex per part of `figure`, in order. Each is `(tint-hex (get palette (:color part)) tint)`,
except parts whose `:color` is `:eye`, which keep the palette colour. `pm.bricks` uses it to colour the kinematic figure.

**The generation gate** (pure data; `frame` is `pm.bricks`'s frame counter):

```clojure
(def empty-gate {:queue [] :in-flight nil :last-start nil :next-id 0})
```

- **`(gate-enqueue gate)`** → `[gate' id]`.
  - `id` = `(:next-id gate)`; it is appended to `:queue`, and `:next-id` is incremented.
- **`(gate-start gate frame)`** → `[gate' id-or-nil]`.
  - It starts the queue head when `:in-flight` is nil, `:queue` is non-empty and `:last-start` ≠ `frame`. Then:
    - the head is popped;
    - `:in-flight` = id;
    - `:last-start` = frame.
  - Otherwise → `[gate nil]`, unchanged.
- **`(gate-done gate id)`** → `gate` with `:in-flight` nil when `id` = `:in-flight`; otherwise `gate`, unchanged.
- **`(gate-queued gate)`** → `(count (:queue gate))`. This counts waiting jobs only, not the one in flight.

**`hud-text`**, the `:bricks` mode: when `(:queue status)` is a number > 0, append `(str " · queue " q)` right after the gen part
and before the char-errors part. Detours stay last. Other modes ignore `:queue`.

### 3.2 `pm.crew` (changed)

**`(plate-name crew w)`** → `(get-in crew [:titles (:agent w)])` when it's a non-blank string; else `(:name w)`. `crew` may be nil.
`parse` already validates `:titles`.

### 3.3 `pm.tint` (new, `src/pm/tint.cljs`)

```clojure
(ns pm.tint
  "Per-walker tints: clone a character's materials and multiply their colour.
   Touches only the three.js objects handed to it (.traverse, .isMesh, .material,
   .clone, .color.getHex/.setHex) and imports nothing, so Node can test it."
  (:require [pm.motion :as m]))
```

**`(tint-object! obj tint)`** → the number of materials cloned.
- Unless `(and (integer? tint) (<= 0 tint 0xffffff))`, it does nothing and returns 0. That covers nil, `-1`, `"blue"` and so on.
- Otherwise it calls `(.traverse obj f)`, and for each `o` with `(.-isMesh o)` true:
  - **The material.** `.-material` is a single material or a JS array. Each material `mt` that has a `.-color` is replaced by its tinted
    clone:
    - `c = (.clone mt)`;
    - `(.setHex (.-color c) (m/tint-hex (.getHex (.-color mt)) tint))`.
  - **Memoized by identity within the call.** The same source material on several meshes maps to **one** clone.
  - An array is replaced by a **new** JS array, so the original array is untouched.
  - **No colour.** Materials without `.-color` are left in place and not counted.
- **Originals are never mutated**: other walkers sharing them keep their colour.

### 3.4 `pm.bricks` (interop; not unit-tested, Hal checks live)

- **`build-figure!`** takes the walker's `:tint`. Part `k` gets `(nth (m/figure-hexes scene/palette tint) k)` instead of `part-color`.
- **Mannequin walkers** (`(nil? ch)` → `.setMannequin`), and the mannequin fallbacks: right after `.setMannequin c`, call
  `(tint/tint-object! (.-object c) (:tint w))`. Walkers without `:tint` (Le Testeur) are untouched. Un Automate's Polybot has no
  tint and is untouched.
- **`set-dots!`** uses `(crew/plate-name crew-state w)` for the name run instead of `(:name w)`. When it changes, the runs change and the plate is
  redrawn. `build-view!` still starts with `(:name w)`.
- **Generation gate**:
  - **State.** `!state` gains `:gate m/empty-gate`, `:jobs {}` (id → job) and `:raw nil`.
  - **Install.** After the model and bodies are loaded and before `swap-in!`:
    - let `engine = (.-engine first-c)`, the SharedEngine every character shares through `loadFrom`;
    - if `(fn? (.-generate engine))`:
      - keep `raw = (.bind (.-generate engine) engine)`;
      - replace `.generate` with a fn of `[ctx tgt opt]` that returns `(js/Promise. (fn [res rej] …))`. That fn does
        `gate-enqueue`, stores `{:ctx :tgt :opt :res :rej :enq (js/performance.now)}` under the id, and returns.
    - Otherwise, log once with `console.info` and run ungated.
  - **Each `tick!`**, while in `:bricks` mode, once per frame: `(m/gate-start gate frame)`. With an id:
    - call `(raw ctx tgt opt)` for that job;
    - on resolve: set the result's `inferenceMs` to `(- (js/performance.now) enq)` (so each walker's latency estimate includes its wait
      and it plans earlier), do `gate-done`, then `res`;
    - on reject: do `gate-done`, then `rej`.
    - Dissoc the job either way.
  - **Status.** Set `:queue (m/gate-queued gate)` in `:status`.
  - **A walker that waits keeps its current clip.** MotionBricks keeps playing its segment while its request is pending; nothing to add.
  - **`fallback!`** resets `:gate` to `m/empty-gate` and `:jobs` to `{}`. Their promises never settle; the characters are disposed.
- **Kinematic mode** has no gate. Six walkers run through the same `m/advance` as two.

## 4. Test cases (tester: `test/pm/crew_walkers_test.cljs`)

```clojure
(ns pm.crew-walkers-test
  (:require [cljs.test :refer [deftest is]]
            [pm.motion :as m] [pm.avoid :as a] [pm.crew :as c] [pm.tint :as tint] [pm.world :as w]))

(def W (w/load-estate w/estate-edn))
(def WS (m/walkers W))
(defn by-id [id] (first (filter #(= id (:id %)) WS)))
(defn near? [x y] (< (js/Math.abs (- x y)) 1e-6))
(defn dist [[x1 z1] [x2 z2]] (js/Math.hypot (- x1 x2) (- z1 z2)))
(defn seg-dist [p a b]   ; distance from point p to segment ab
  (let [[px pz] p [ax az] a [bx bz] b dx (- bx ax) dz (- bz az) L (+ (* dx dx) (* dz dz))
        t (if (zero? L) 0 (max 0 (min 1 (/ (+ (* (- px ax) dx) (* (- pz az) dz)) L))))]
    (dist p [(+ ax (* t dx)) (+ az (* t dz))])))
(defn segs [wk] (let [r (:route wk) s (mapv vector r (rest r))]
                  (if (:loop wk) (conj s [(peek r) (first r)]) s)))
(def CREEK (mapv (fn [[x _ z _]] [x z]) (:points (first (:sounds W)))))
(defn creek-dist [p] (reduce min (map (fn [[a b]] (seg-dist p a b)) (map vector CREEK (rest CREEK)))))
(def NEW [:un-architecte :le-coureur :le-critique :le-docteur])
```

### Six walkers from the data

1. **Ids, agents, names.**
   - `(mapv :id WS)` = `[:un-automate-monde :le-testeur :un-architecte :le-coureur :le-critique :le-docteur]`.
   - `(mapv :agent WS)` is the same vector.
   - `(mapv :name WS)` = `["Un Automate" "Le Testeur" "Un Architecte" "Le Coureur" "Le Critique" "Le Docteur"]`.
2. **Routes and pauses.**
   - `(mapv :route WS)` = `[[[-6.5 -6.5] [-6 -10] [-5 -14]] [[-5 -14] [-6 -10] [-6.5 -6.5]] [[6 -4] [4 -6] [3.5 -7.5]] [[3 -8.5] [5 -10] [3 -12] [2.2 -10]] [[-8 -4] [-7.2 -5.2]] [[-6 -17.5] [-6 -20]]]`.
   - `(mapv :pause-s WS)` = `[3 5 8 1 6 6]`. `(mapv :pause WS)` is the same vector.
   - `(mapv :pos WS)` = the first point of each route.
3. **Tints, loop, face, speed, character.**
   - `(mapv :tint WS)` = `[nil nil 0x4a7bd0 0xe8833a 0x8e5cc4 0x2f9e8f]`, i.e. `[nil nil 4881360 15237946 9329860 3120783]`.
   - `(mapv :loop WS)` = `[false false false true false false]`.
   - `(mapv :face WS)` = `[nil nil nil nil [-6 -10] nil]`.
   - `(mapv :speed WS)` = `[nil nil nil 1.9 nil nil]`.
   - `(mapv :character WS)` = `[{:file "/assets/characters/polybot.glb" :rig :mixamo} nil nil nil nil nil]`.
   - `(mapv :radius WS)` = six `0.3`.
4. **Starting headings** (`yaw` toward the second point).
   - Un Automate `1.4288993`, Le Testeur `-1.8157750`.
   - Un Architecte `2.3561945` (3π/4).
   - Le Coureur `0.6435011` (`atan2 1.5 2`).
   - Le Critique `0.9827937` (`atan2 1.2 0.8`).
   - Le Docteur `1.5707963` (π/2).
   - Use `near?`.
5. **Walker validation of the new keys.**
   - `(:tint (m/walker {:id :a :route [[0 0]] :tint t}))` → nil for `t` = `0x1000000`, `-1`, `"blue"`, `1.5` and nil; `0` → `0`; `0xffffff` → `16777215`.
   - `(:loop (m/walker {:id :a :route [[0 0]] :loop "yes"}))` → false; `:loop true` → true.
   - `(:face (m/walker {:id :a :route [[0 0]] :face f}))` → nil for `f` = `[1]`, `"x"`, `[1 "2"]`; `[1 2]` → `[1 2]`.

### Loop and face

6. **`next-index` with a loop.**
   - `(m/next-index 4 0 1 true)` = `[1 1]`, `(m/next-index 4 3 1 true)` = `[0 1]`.
   - `(m/next-index 4 2 -1 true)` = `[3 1]`, `(m/next-index 1 0 1 true)` = `[0 1]`, `(m/next-index 2 1 1 true)` = `[0 1]`.
   - `(m/next-index 4 3 1 false)` = `[2 -1]`, which is `(m/next-index 4 3 1)`.
   - The existing t09 and t10 still hold.
7. **A looping walker goes round and keeps its speed.**
   - Let `SQ = (m/walker {:id :sq :route [[0 0] [1 0] [1 1] [0 1]] :loop true :pause-s 0})`. Step it 200 times with `(m/step w 1 0.1)` and
     collect `:to` at each `:leg` change. The first 8 are `[1 2 3 0 1 2 3 0]`.
   - The same walker without `:loop` gives `[1 2 3 2 1 0 1 2]` (ping-pong).
   - Le Coureur in motion: `(first (first (m/advance [(assoc (by-id :le-coureur) :pause 0 :to 1 :leg 1)] [] {:speed 1.2 :horizon-s 2} 0.1 0)))`
     has `:pos` ≈ `[3.152 -8.614]` and `:vel` ≈ `[1.52 -1.14]`.
     - Check: 1.9 × 0.1 = 0.19 m toward `[5 -10]`, whose unit vector is `[0.8 -0.6]`.
8. **Le Critique faces the pair while paused.** Let `K = (by-id :le-critique)` (at `[-8 -4]`, pause 6).
   - `(m/step K 1.2 0.5)`: `:pause` 5.5, `:heading` ≈ `1.2490458`, `:facing` ≈ `1.2490458` (`atan2 6 2`), `:vel` `[0 0]`, `:pos` `[-8 -4]`.
   - At the other end: `(m/step (assoc K :pos [-7.2 -5.2] :pause 3) 1.2 0.5)` → `:heading` ≈ `1.3258177` (`atan2 4.8 1.2`).
   - The pause runs out: `(m/step (assoc K :pause 0.2) 1.2 0.5)` gives `:to` 1, `:pause` 0, and `:heading` ≈ `0.9827937` (no face).
   - The bricks path: `(m/follow K [-8 -4] true 0.5)` → `:pause` 5.5, `:heading` and `:facing` ≈ `1.2490458`, `:vel` `[0 0]`.
   - No face: `(:heading (m/step (by-id :le-testeur) 1.2 0.5))` ≈ `-1.8157750`, unchanged.
   - A face at its own position: with `Z = (m/walker {:id :z :route [[0 0] [0 1]] :pause-s 2 :face [0 0]})`, `(m/step Z 1.2 0.5)` keeps
     `:heading` ≈ `-1.5707963` and `:facing` nil.

### Walkable routes (data in the repo plus a probe stub)

9. **Clear of the statics.** Let `S = (a/statics W)`.
   - For every walker `wk` and every route point `p`, `(- (dist p (:at s)) (:r s) (:radius wk))` ≥ 0 for every static. The minimum is
     0.2, at Un Automate and Le Testeur.
   - For the four `NEW` walkers and every segment of `(segs wk)` (Le Coureur's 4 segments include the closing one),
     `(- (seg-dist (:at s) a b) (:r s) (:radius wk))` ≥ 0. The minima are 7.329, 3.381, 2.842 and 4.857.
10. **Out of the creek and in their patches.**
    - Every route point of every walker is ≥ 3.0 m from the creek centerline: `CREEK`, from `:sounds`; the minimum is 3.328, at Le Coureur.
    - **Patches:**
      - the west bank (Un Automate, Le Testeur, Le Critique): every x < -4;
      - the east bank (Un Architecte, Le Coureur): every x > 2;
      - Un Architecte is north of Le Coureur: his min z -7.5 > Le Coureur's max z -8.5;
      - Le Critique is at the north end: his min z -5.2 > the max z of Un Automate and Le Testeur, -6.5;
      - Le Docteur is on the plateau: every z ≤ -17.5, and each point is ≥ 1.0 m from the rock `[-5 -17]` (the minimum is 1.1180340).
11. **Probe stub, walkable at every sample.** `PROBE` (in the test) returns:
    - nil (not walkable) inside any static circle (`dist < r`), within 1.5 m of `CREEK`, or within 0.6 m of `[-5 -17]`;
    - otherwise the patch height Hal measured: 3.5 when z < -16; 2.0 when x < -7 and z > -6 (the woods' edge); 2.65 when x < -2 (west bank, the
      feet tests' stub); 1.65 when z > -8 (east north); else 1.8 (east south).

    For every walker, every segment of `(segs wk)` sampled every 0.25 m (both ends included) satisfies
    `(m/walkable? (PROBE x z) (PROBE x0 z0) 0.4)`, where `[x0 z0]` is the segment's first point. (The west bank segments pass 0.288 m
    from the nearest static's edge.) Heights aren't in the repo, so the real step check is Hal's live `?probe`.

### Colours

12. **`tint-hex`.**
    - `(m/tint-hex 0xf4f2ee 0x4a7bd0)` = `0x4775c2` (71 117 194: 244·74/255 = 70.81 → 71, 242·123/255 = 116.73 → 117, 238·208/255 = 194.13 → 194).
    - The other mannequin bodies (base `0xf4f2ee`): `0xe8833a` → `0xde7c36`, `0x8e5cc4` → `0x8857b7`, `0x2f9e8f` → `0x2d9685`.
    - `(m/tint-hex 0x16171a 0x4a7bd0)` = `0x060b15`.
    - `(m/tint-hex 0x808080 0x808080)` = `0x404040`; `(m/tint-hex 0xffffff 0x4a7bd0)` = `0x4a7bd0`; `(m/tint-hex 0x123456 0xffffff)` = `0x123456`.
    - Unchanged: `(m/tint-hex 0x123456 nil)` = `0x123456` and `(m/tint-hex 0x123456 -1)` = `0x123456`; `(m/tint-hex nil 0x4a7bd0)` = nil.
13. **Kinematic figure colours.** Let `PAL = {:crew 0x5b7fa3 :crew-head 0xf0e6d6 :eye 0x222222}`.
    - `(m/figure-hexes PAL 0x4a7bd0)` = `[0x1a3d85 0x466faf 0x222222]`.
    - `(m/figure-hexes PAL 0xe8833a)` = `[0x534125 0xda7631 0x222222]`.
    - `(m/figure-hexes PAL nil)` = `[0x5b7fa3 0xf0e6d6 0x222222]`.
14. **`tint-object!` clones, shares one clone per source, and never mutates the source.** Stubs, in the test:
    ```clojure
    (defn stub-mat [hex]
      (let [col #js {:v hex}]
        (set! (.-getHex col) (fn [] (.-v col)))
        (set! (.-setHex col) (fn [h] (set! (.-v col) h) col))
        (let [mt #js {:color col}] (set! (.-clone mt) (fn [] (stub-mat (.-v col)))) mt)))
    (defn stub-obj [nodes] (let [o #js {}] (set! (.-traverse o) (fn [f] (f o) (doseq [n nodes] (f n)))) o))
    ```
    - Setup:
      - `BODY = (stub-mat 0xf4f2ee)`, `INK = (stub-mat 0x16171a)`, `PLAIN = #js {:clone (fn [] #js {})}` (no `.color`);
      - the meshes `MA = #js {:isMesh true :material BODY}`, `MB = #js {:isMesh true :material BODY}`, `MC = #js {:isMesh true :material #js [INK PLAIN]}`;
      - a non-mesh `G = #js {:isMesh false :material BODY}`;
      - `O = (stub-obj [MA MB MC G])`.
    - `(tint/tint-object! O 0x4a7bd0)` = 2.
    - **One clone per source:** `(identical? (.-material MA) (.-material MB))`, and not `(identical? (.-material MA) BODY)`.
    - **Colours:** `(.getHex (.-color (.-material MA)))` = `0x4775c2`; `(.getHex (.-color (aget (.-material MC) 0)))` = `0x060b15`.
    - **Untouched:**
      - `(identical? (aget (.-material MC) 1) PLAIN)`;
      - `(identical? (.-material G) BODY)`;
      - `(.getHex (.-color BODY))` = `0xf4f2ee` (the source was not mutated).
    - **A second walker sharing BODY gets its own clone:** with `MD = #js {:isMesh true :material BODY}`, `(tint/tint-object! (stub-obj [MD]) 0xe8833a)` = 1.
      - `(.getHex (.-color (.-material MD)))` = `0xde7c36`;
      - `(.getHex (.-color (.-material MA)))` is still `0x4775c2`.
    - **No tint:** `(tint/tint-object! (stub-obj [ME]) nil)` = 0 with `ME = #js {:isMesh true :material BODY}`, and `(identical? (.-material ME) BODY)`.
      The same with tint `-1` and `"blue"`.

### Names

15. **The plate name: the roster title, else `:name`.**
    - `(c/plate-name {:titles {:le-docteur "Le Docteur (docs)"}} {:agent :le-docteur :name "Le Docteur"})` = `"Le Docteur (docs)"`.
    - `(c/plate-name {:titles {}} {:agent :le-docteur :name "Le Docteur"})` = `"Le Docteur"`.
    - `(c/plate-name nil {:agent :x :name "X"})` = `"X"`.
    - `(c/plate-name {:titles {:x ""}} {:agent :x :name "X"})` = `"X"`.
16. **End to end through `crew.edn`.** Let `CE = (c/parse (pr-str {:as-of 1000 :agents {:un-automate-monde {:status :working} :le-docteur {:status :blocked}} :titles {:un-automate-monde "Un Automate (monde)" :le-testeur "Le Testeur" :un-architecte "Un Architecte" :le-coureur "Le Coureur" :le-critique "Le Critique" :le-docteur "Le Docteur"}}))`.
    - `(mapv #(c/plate-name CE %) WS)` = `["Un Automate (monde)" "Le Testeur" "Un Architecte" "Le Coureur" "Le Critique" "Le Docteur"]`.
    - `(mapv #(c/label-text (c/dot CE (:agent %) 3000 (c/colors W)) (c/plate-name CE %)) WS)` = `["● Un Automate (monde)" "○ Le Testeur" "○ Un Architecte" "○ Le Coureur" "○ Le Critique" "● Le Docteur"]`.
    - `(mapv #(:pulse? (c/dot CE (:agent %) 3000 (c/colors W))) WS)` = `[false false false false false true]`. Le Docteur is blocked: his dot pulses.

### Generation gate

17. **Enqueue, start, done.** Let `G0 = m/empty-gate`, `[G1 a] = (m/gate-enqueue G0)`, `[G2 b] = (m/gate-enqueue G1)`.
    - `a` = 0 and `b` = 1, and `G2` = `{:queue [0 1] :in-flight nil :last-start nil :next-id 2}`.
    - `[G3 s] = (m/gate-start G2 10)`: `s` = 0, `G3` = `{:queue [1] :in-flight 0 :last-start 10 :next-id 2}`.
    - One in flight: `(m/gate-start G3 11)` = `[G3 nil]`.
    - `G4 = (m/gate-done G3 0)` = `{:queue [1] :in-flight nil :last-start 10 :next-id 2}`.
    - Not twice in a frame: `(m/gate-start G4 10)` = `[G4 nil]`.
    - `(m/gate-start G4 11)` = `[{:queue [] :in-flight 1 :last-start 11 :next-id 2} 1]`.
    - `(m/gate-done G3 7)` = `G3` (wrong id), and `(m/gate-start G0 5)` = `[G0 nil]`.
    - `(mapv m/gate-queued [G0 G2 G3])` = `[0 2 1]`.
18. **Stagger across frames.** The helper, in the test:
    ```clojure
    (defn run-gate [n gen-frames last-frame]
      (loop [g (reduce (fn [g _] (first (m/gate-enqueue g))) m/empty-gate (range n)) f 1 started [] fly nil]
        (if (> f last-frame)
          started
          (let [fin?   (and fly (>= f (+ (:at fly) gen-frames)))
                g      (if fin? (m/gate-done g (:id fly)) g)
                fly    (if fin? nil fly)
                [g id] (m/gate-start g f)
                fly    (if id {:id id :at f} fly)
                now?   (and id (zero? gen-frames))
                g      (if now? (m/gate-done g id) g)
                fly    (if now? nil fly)]
            (recur g (inc f) (if id (conj started [f id]) started) fly)))))
    ```
    - Six walkers asking at once, each generation done within its frame: `(run-gate 6 0 10)` = `[[1 0] [2 1] [3 2] [4 3] [5 4] [6 5]]`. No frame starts two.
    - Generations taking 3 frames: `(run-gate 3 3 10)` = `[[1 0] [4 1] [7 2]]`.
    - `(run-gate 0 0 5)` = `[]`.
    - Every `run-gate` result has distinct frames: `(apply distinct? (map first r))` when `(count r)` ≥ 2.
19. **HUD queue.**
    - `(m/hud-text {:mode :bricks :backend "webgpu" :walkers 6 :gen-ms 123.4 :queue 2})` = `"motion: bricks webgpu · 6 walkers · gen 123 ms · queue 2"`.
    - With `:queue 0` → `"motion: bricks webgpu · 6 walkers · gen 123 ms"`. The same with `:queue nil` or no key.
    - With `:queue 2 :detours 1` → `"motion: bricks webgpu · 6 walkers · gen 123 ms · queue 2 · 1 detour"`.
    - With `:queue 1 :char-errors [{:name "Un Automate" :reason "no rig x"}]` → `"motion: bricks webgpu · 6 walkers · gen 123 ms · queue 1 · mannequin for Un Automate (no rig x)"`.
    - `(m/hud-text {:mode :kinematic :queue 3})` = `"motion: kinematic"`.

### Six walkers together (kinematic)

20. **The estate scenario with all six.** Run `(m/advance ws (a/statics W) {:speed 1.2 :horizon-s 2} 0.05 acc)` 2400 times from `WS`.
    - **Spacing:**
      - the closest pair distance over **all** pairs, every frame, is ≥ 0.6 (model: 0.784);
      - every walker's static gap is ≥ 0 (model: 0.155).
    - **Picks:**
      - Le Coureur's first 8 picks are `[1 2 3 0 1 2 3 0]`;
      - Le Critique's and Le Docteur's are `[1 0 1 0 1 0 1 0]`;
      - Un Architecte's are `[1 2 1 0 1 2 1 0]`.
    - **Legs:** Un Automate's and Le Testeur's final `:leg` are ≥ 12; Le Coureur's is ≥ 40 (model: 58).
    - **Bounds:** Le Critique stays within x ∈ [-8.01, -7.19], z ∈ [-5.21, -3.99], and Le Docteur within x = -6 ± 0.01, z ∈ [-20.01, -17.49].

**20 test cases**, plus the updated existing assertions in §5.

## 5. Existing assertions to update (tester)

| Test | New expectation |
|---|---|
| `motion_test` t03 | ids `[:un-automate-monde :le-testeur :un-architecte :le-coureur :le-critique :le-docteur]`; names `["Un Automate" "Le Testeur" "Un Architecte" "Le Coureur" "Le Critique" "Le Docteur"]` |
| `characters_test` t10 | `[{:file "/assets/characters/polybot.glb" :rig :mixamo} nil nil nil nil nil]` |
| `characters_test` t15 | six `0.42` (scaled) and `(vec (repeat 6 0.3))` |
| `crew_test` t26 | the six agents, in the t03 id order |
| `crew_test` t27 | at 3000: `["● Un Automate" "● Le Testeur" "○ Un Architecte" "○ Le Coureur" "○ Le Critique" "○ Le Docteur"]`; at 16001: all six `"○ …"`; colours `[15905076 10133670 10133670 10133670 10133670 10133670]` (agents absent from `S` are hollow, idle grey) |
| `senses_test` t05 | keep the per-walker checks except `(nil? (:speed wk))`, which becomes `(= [nil nil nil 1.9 nil nil] (mapv :speed (m/walkers W)))`; ids as t03 |

The scenario tests (`avoid_test` t65, `characters_test` t17, `senses_test` t42) are left alone. They now simulate six walkers, and the model
says they still pass: the pair at 0.784 / 0.933 m, picks unchanged. If one fails, report it; don't loosen it.

## 6. Notes for the builder

- `tint-hex`: pack the channels with `bit-shift-left`/`bit-or` and unpack with `bit-shift-right`/`bit-and 255`.
- Keep `pm.tint` free of requires beyond `pm.motion`, so `node out/test.js` loads it.
- The gate relies on `(.-engine c)` being the one SharedEngine shared through `loadFrom` (vendored three-motionbricks 0.2.0,
  `index.mjs` ~l.2809/2825). Check `fn?` before wrapping; if it's missing, run ungated and leave `:queue` out.
- Live (Hal), with `?bricks`:
  - six walkers in their patches, with distinct colours;
  - dots right for each;
  - 60 fps;
  - the HUD shows `gen … ms` and `· queue n` while generations wait.
