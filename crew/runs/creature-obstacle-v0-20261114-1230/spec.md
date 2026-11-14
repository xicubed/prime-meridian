# Spec: creature-obstacle-v0 (walkers go around the foal)

Quest: `.crew/quests/creature-obstacle-v0.md` (TASKS M-13). Builds on senses-v0
(`crew/runs/senses-v0-20261107-1553/spec.md`) and avoid-v0. Start commit `e89f92e`.
Architect: un-architecte, 2026-11-14. Builder: un-automate-monde. Tester: le-testeur.

Every name, signature, key and number below is binding.

## 1. The idea

Each tick, the creature spike publishes **one circle on the ground**: a centre and radius that
cover every body's capsule (from MuJoCo's `xpos`/`xmat` and the species' capsule radii,
converted from z-up to the game's x/z), plus the torso's velocity. `pm.bricks` asks for it each
tick and passes it to `pm.motion` as an **extra**. Inside `pm.motion`, an extra becomes an
"other" (like another walker) marked `:obstacle true`. It goes through the same senses
(`perceive`: sight cone, hearing scaled by its speed, reaction delay, forgetting). Once noticed,
the planner treats it as another walker's circle. An extra is never planned (it isn't in `ws`)
and never pushed. With no extras (`nil` or `[]`), every function returns exactly what it returns
today.

Kinematic walkers (`?kinematic`, or MotionBricks failed) also use the planner (`tick-kinematic!`
→ `m/advance`), so both modes avoid the foal. The circle comes from MuJoCo's body state, so it
follows him lying, getting up or standing, under either controller (policy or `?brain=twitch`).

### 1.1 Two rules the model forced (read this before building)

I checked the whole design in a model: a Babashka port of today's `pm.motion`/`pm.avoid` plus
these rules, run on the real estate routes. It reproduces crew-walkers t20 exactly. Two changes
beyond the plain plumbing were needed. Both apply **only to obstacles** (`:obstacle true`), so
walkers among themselves behave bit for bit as today.

1. **Senses measure to the obstacle's edge, not its centre.** A walker's hearing range (1.2 m)
   is larger than the clearance it keeps from another walker (0.3 + 0.3 + 0.15 = 0.75 m), so a
   walker next to another walker always hears it. A lying foal has r ≈ 0.78 m, so the clearance
   is 1.23 m, which is more than the hearing range. With centre distances, Le Coureur skirted the
   lying foal outside his sight cone and beyond 1.2 m from the centre, forgot him after 1.5 s,
   then cut straight back through him (model: 0.53 m inside the circle). Measured to the edge,
   hearing covers the whole clearance zone and nobody forgets him while beside him.
2. **A noticed obstacle's circle is also a place a goal can't be** (`a/free-goal` gets it, as it
   gets statics). A walker eventually walks off a route point. The foal doesn't. Un Architecte's
   8 s pause point `[3.5 -7.5]` is 1.12 m from the foal's spot. With a 1.0 m circle there and no
   goal move, the model had him circling the foal (6 legs in 120 s instead of 12–13) and brushing
   past Le Coureur at 0.31 m. With the move, he stops at a respectful distance (0.37 m past the
   circle) and carries on.

## 2. Data

### 2.1 The published circle (`pm.creature-spike/obstacles`)

```clojure
[{:id :creature          ; cm/obstacle-id; never a species name
  :at [4.67 -8.225]      ; game [x z], metres
  :r  0.7774779          ; metres; covers every capsule in the ground plane
  :vel [0.6 -0.8]        ; game [vx vz] m/s, the torso's (free joint's) linear velocity
  :y  1.79}]             ; ground height (the MuJoCo plane), only for drawing ?paths
```

The vector is `[]` when the spike isn't running (no `?pony`, still loading, failed) or hasn't
ticked yet.

### 2.2 An extra, as `pm.motion` takes it

Any map with `:id` (a keyword), `:at` `[x z]` (two numbers), `:r` (a number > 0) and an optional
`:vel` `[vx vz]`. Other keys (`:y`) are ignored. Invalid ones are dropped. `m/extra-others` turns
it into an "other":

```clojure
{:id :creature :pos [4.67 -8.225] :radius 0.7774779 :vel [0.6 -0.8] :obstacle true}
```

Walkers never have `:obstacle`. An extra's `:id` must differ from every walker `:id`; the spike
always uses `:creature`.

### 2.3 Capsules (`cm/capsules`)

One entry per body, in `c/bodies` order (MuJoCo body id = index + 1), body frame, MuJoCo z-up:

```clojure
[{:id 1 :from [0.1 0 0] :to [0.3 0 0] :r 0.15}    ; horse :torso
 {:id 2 :from [0 0 0] :to [0.18 0 0.24] :r 0.06}  ; :neck
 ...]                                              ; 12 entries for horse and dog
```

### 2.4 Frames (existing)

- MuJoCo → game: `c/mj->three [x y z]` = `[x z (- y)]`. So the **ground-plane** point of a
  MuJoCo position `[x y z]` is game `[x (- y)]`, and its height `z` is dropped.
- The MuJoCo world is the game world (`c/spawn` places the body at `three->mj (:at place)`;
  `place-groups!` puts the parts in a root at the scene origin). No offset.
- `xmat` is row-major 3×3 per body: world = M·local, `M[i][j] = xmat[9·id + 3i + j]`, as in
  `cm/capsule-clearance`.
- The free joint's `qvel[0..2]` is the torso's linear velocity in the world frame.

## 3. Public functions

### 3.1 `pm.creature-motion` (pure; new, in the "Pose from MuJoCo arrays" section)

```clojure
(def obstacle-id :creature)
```

**`(capsules sp)`** → vector. For each body `b` at index `i` of `(c/bodies sp)`:
`{:id (inc i) :from (:from (:capsule b)) :to (:to (:capsule b)) :r (:r (:capsule b))}`.

**`(ground-circle caps xpos xmat qvel)`** → `{:at [x z] :r r :vel [vx vz]}`, or nil when `caps`
is empty.

- `xpos`, `xmat`, `qvel` are array-likes read with `aget` (MjData's typed arrays; `#js [...]` in
  tests). `qvel` may be nil.
- For each capsule `k` and each endpoint `p` in `[from to]`: the world point is
  `W = xpos[3id..3id+2] + M·p`, with `M` read as in §2.4. Its ground point is `E = [Wx (- Wy)]`
  with radius `r_k`.
- Box: `xmin = min(Ex − r_k)`, `xmax = max(Ex + r_k)`, and the same for z, over all endpoints.
- `:at` = `[(xmin + xmax)/2 (zmin + zmax)/2]`.
- `:r` = `max(|E − at| + r_k)` over all endpoints. This covers each capsule: a capsule is the set
  of points within `r_k` of its segment, and any point of the segment is no farther from `at`
  than its farther endpoint.
- `:vel` = `[(aget qvel 0) (- (aget qvel 1))]`, or `[0 0]` when `qvel` is nil.
- Height plays no part: neither the endpoints' `z` nor `qvel[2]` matters.

### 3.2 `pm.motion`

**`(extra-others extras)`** → vector of other-maps (§2.2), in order. It keeps each `c` where
`(keyword? (:id c))`, `:at` is a vector of 2 numbers and `:r` is a number `> 0`, and returns
`{:id (:id c) :pos (:at c) :radius (:r c) :vel v :obstacle true}`. Here `v` is `(:vel c)` when
it is a vector of 2 numbers, else `[0 0]`. `nil` or `[]` → `[]`.

**`(sense-dist w o)`** → metres. The xz distance `d` from `(:pos w)` to `(:pos o)`; when
`(:obstacle o)` it is `(max 0 (- d (:radius o)))`.

**`sees?`** (changed): as today, except the range test and the "coincident" test use
`(sense-dist w o)`:
- `sd > :sight-m` → false;
- `sd = 0` → true (coincident, or inside the obstacle's circle);
- otherwise the cone test as today, with the direction to `(:pos o)` and divided by the **centre**
  distance `d`. (`d > 0` here, because `sd > 0`.)

For walkers `sd = d`, so the results are unchanged.

**`hears?`** (changed): `(<= (sense-dist w o) (hear-range w o))`. `hear-range` is unchanged; it
uses `(:vel o)`, which for the foal is the torso's velocity.

**`perceive`**: unchanged. It sees obstacles through `sees?`/`hears?`.

**`perceive-all`**: `([ws dt] (perceive-all ws dt nil))` and `([ws dt extras] ...)`. Each walker
`i` perceives the other walkers (as today, in order), followed by `(extra-others extras)`.

**`plan`** (same signature `[w others statics cfg]`; one change):

```clojure
goal-statics (into statics (keep (fn [o] (when (:obstacle o) {:at (:pos o) :r (:radius o)}))) others)
g (a/free-goal P G r goal-statics)
```

That replaces `(a/free-goal P G r statics)`. Everything else is unchanged: the obstacle is
already in `circles` through `other-circles` (`{:at (:pos o) :r (:radius o) :vel (:vel o)}`),
and `a/inside? D r statics` still takes `statics` only. With no obstacle in `others`,
`goal-statics` equals `statics`, so the result is identical.

**`plan-all`**: `([ws statics cfg] (plan-all ws statics cfg nil))` and
`([ws statics cfg extras] ...)`. For walker `i`: `others` = the other walkers ++
`(extra-others extras)`, then `(plan w (noticed w others) statics cfg)`.

**`replan`**: `([before after statics cfg dt acc] (replan before after statics cfg dt acc nil))`
and `([before after statics cfg dt acc extras] ...)`. This is today's body, with
`(perceive-all after dt extras)` and `(plan-all after statics cfg extras)`.

**`advance`**: `([ws statics cfg dt acc] (advance ws statics cfg dt acc nil))` and
`([ws statics cfg dt acc extras] ...)`. Today's step, then the 7-arity `replan`.

Unchanged: `step`, `follow`, `noticed`, `hear-range`, `detours`, `steer-command`, `hud-text`,
everything in `pm.avoid`.

### 3.3 `pm.creature-spike` (interop)

- `start-twitch!` and `start-policy!` also store `:caps (cm/capsules sp)` in `!state`.
- A private `(publish! ^js data)`: computes
  `(some-> (cm/ground-circle caps (.-xpos data) (.-xmat data) (.-qvel data)) (assoc :id cm/obstacle-id :y ground-z))`
  and stores it as `:obstacle`. `tick-twitch!` and `tick-policy!` call it right after
  `place-groups!`.
- `fail!` dissocs `:obstacle`.
- **`(obstacles)`** → `[(:obstacle s)]` when `(:mode (:status s))` is `:running` and `:obstacle`
  is non-nil, else `[]`. Public; no arguments.

### 3.4 `pm.bricks` (interop)

- Requires `[pm.creature-spike :as cs]`. There is no require cycle: `pm.creature-spike` doesn't
  require `pm.bricks`, and `pm.ui` requires only `pm.talk`.
- `tick!` reads `extras (cs/obstacles)` once per frame. It passes them to `tick-kinematic!`
  (`(m/advance walkers statics pcfg dt plan-acc extras)`), to `tick-bricks!`
  (`(m/replan before after statics pcfg dt plan-acc extras)`) and to `update-paths!`.
- `pm.core` ticks `bricks/tick!` before `creature-spike/tick!`, so the walkers use the circle
  from the previous frame (≤ 0.1 s old). That's accepted; don't reorder `pm.core`.
- **`?paths`**: `build-paths!` adds `:creature`, a `t/polyline` of
  `(a/circle-points [0 0] 1 24)` (25 points) lifted to y 0, colour `:path-creature`, hidden
  (`t/set-visible! false`). It goes in `paths-root` and in the returned map.
  `(update-paths! paths walkers extras)`: when `(first extras)` is `c`, it calls
  `t/set-polyline!` with `(a/circle-points (:at c) (:r c) 24)` at y `(+ (or (:y c) 0) 0.05)`,
  then `t/set-visible! true`; with no extras, `t/set-visible! false`. The walkers' lines don't
  change.

### 3.5 `pm.scene` palette

Add one key: `:path-creature 0xf687b3`.

## 4. Who touches what

| Crew member | Files |
|---|---|
| **builder** `un-automate-monde` | `src/pm/motion.cljs`, `src/pm/creature_motion.cljs`, `src/pm/creature_spike.cljs`, `src/pm/bricks.cljs`, `src/pm/scene.cljs` (one palette key) |
| **tester** `le-testeur` | `test/pm/creature_obstacle_test.cljs` (new, ns `pm.creature-obstacle-test`). Edits no existing test. |
| nobody | `resources/estate.edn` (the foal stays at `:creature-spike :at`), `resources/creatures/*.edn`, `src/pm/core.cljs`, `src/pm/creature.cljc`, `src/pm/avoid.cljs`, every existing test, `public/`, `assets/private/` |

The GDD needs no change: it doesn't describe sense distances. Hal marks M-13 in `TASKS.md` at
commit.

Existing tests must pass unchanged. None passes extras, and walkers have no `:obstacle`, so
avoid t60–t65, senses t39–t42, characters t17 and crew-walkers t20 give the same numbers.

## 5. Test fixtures (tester)

```clojure
(ns pm.creature-obstacle-test
  (:require [cljs.test :refer [deftest is]]
            [pm.motion :as m] [pm.avoid :as a] [pm.world :as w]
            [pm.creature :as c] [pm.creature-motion :as cm] [pm.species :as species]))

(def W (w/load-estate w/estate-edn))
(def cfg {:speed 1.2 :horizon-s 2})
(def horse (c/parse (get species/texts :horse)))
(def dog   (c/parse (get species/texts :dog)))
(defn near? [a b] (< (js/Math.abs (- a b)) 1e-6))
(defn vnear? [a b] (and (= (count a) (count b)) (every? true? (map near? a b))))
(defn dist [[x1 z1] [x2 z2]] (js/Math.hypot (- x1 x2) (- z1 z2)))

;; MuJoCo arrays from rows [[xpos3 xmat9] ...] for body ids 1..n (id 0, the world, is zeros)
(defn arrays [rows]
  [(clj->js (into [0 0 0] (mapcat first rows)))
   (clj->js (into (vec (repeat 9 0)) (mapcat second rows)))])
(def I    [1 0 0  0 1 0  0 0 1])
(def Rz90 [0 -1 0  1 0 0  0 0 1])     ; yaw +90° about MuJoCo z: body +x -> MuJoCo +y -> game -z
(def Rlie [1 0 0  0 0 -1  0 1 0])     ; roll +90° about body x: lying on the side, legs toward MuJoCo +y
(def Rfl  [0 0 1  1 0 0  0 1 0])      ; Rz(+90)·Rlie: a lying front leg folded back along the belly
(def Rhl  [0 0 -1  -1 0 0  0 1 0])    ; Rz(-90)·Rlie: a lying hind leg folded forward
(def qv (clj->js [0.6 0.8 0.1 0 0 0]))
(defn circle-of [sp rows] (let [[xp xm] (arrays rows)] (cm/ground-circle (cm/capsules sp) xp xm qv)))

(defn mv [e] (assoc (m/walker e) :pause 0 :to 1 :leg 1))
(def seen-1s {:on 1 :off 0 :noticed true :how :sight})
(defn frames ([ws statics n] (frames ws statics n (constantly nil)))
  ([ws statics n extras-at]           ; extras-at: frame index i (0-based) -> extras for that advance
   (loop [ws ws acc 0 i 0 out []]
     (if (>= i n) out
       (let [[ws' acc'] (m/advance ws statics cfg 0.05 acc (extras-at i))]
         (recur ws' acc' (inc i) (conj out ws')))))))
(defn first-index [pred xs] (first (keep-indexed (fn [i x] (when (pred x) i)) xs)))
(defn gap [w c] (- (dist (:pos w) (:at c)) (:r c) (:radius w)))   ; < 0 = inside r + walker r
```

`frames` index `i` is the state after frame `i + 1`. In `frames ... extras-at`, the `i`-th call
to `advance` gets `(extras-at i)`.

### 5.1 Body rows (all derived by forward kinematics with every joint at 0; hand-checked in §7)

**Horse standing** at the estate spot, torso origin MuJoCo `[4.5 8.0 0.9]` (game x 4.5, z −8.0),
every `xmat` = `I`:

| id | body | xpos |
|---|---|---|
| 1 | torso | [4.5 8.0 0.9] |
| 2 | neck | [4.8 8.0 0.98] |
| 3 | head | [4.98 8.0 1.22] |
| 4 | fl-upper | [4.78 8.1 0.85] |
| 5 | fl-lower | [4.78 8.1 0.47] |
| 6 | fr-upper | [4.78 7.9 0.85] |
| 7 | fr-lower | [4.78 7.9 0.47] |
| 8 | hind | [4.5 8.0 0.9] |
| 9 | hl-upper | [4.22 8.1 0.85] |
| 10 | hl-lower | [4.22 8.1 0.47] |
| 11 | hr-upper | [4.22 7.9 0.85] |
| 12 | hr-lower | [4.22 7.9 0.47] |

**Horse standing, yaw 90°**, every `xmat` = `Rz90`. xpos 1–12: `[4.5 8.0 0.9] [4.5 8.3 0.98]
[4.5 8.48 1.22] [4.4 8.28 0.85] [4.4 8.28 0.47] [4.6 8.28 0.85] [4.6 8.28 0.47] [4.5 8.0 0.9]
[4.4 7.72 0.85] [4.4 7.72 0.47] [4.6 7.72 0.85] [4.6 7.72 0.47]`.

**Horse lying** on its side, every `xmat` = `Rlie`. xpos 1–12: `[4.5 8.0 0.15] [4.8 7.92 0.15]
[4.98 7.68 0.15] [4.78 8.05 0.25] [4.78 8.43 0.25] [4.78 8.05 0.05] [4.78 8.43 0.05]
[4.5 8.0 0.15] [4.22 8.05 0.25] [4.22 8.43 0.25] [4.22 8.05 0.05] [4.22 8.43 0.05]`.

**Horse curled**: lying, with the legs folded along the belly. `xmat`: ids 1, 2, 3, 8 `Rlie`;
4–7 `Rfl`; 9–12 `Rhl`. xpos 1–12: `[4.5 8.0 0.15] [4.8 7.92 0.15] [4.98 7.68 0.15]
[4.78 8.05 0.25] [4.4 8.05 0.25] [4.78 8.05 0.05] [4.4 8.05 0.05] [4.5 8.0 0.15]
[4.22 8.05 0.25] [4.6 8.05 0.25] [4.22 8.05 0.05] [4.6 8.05 0.05]`.

**Dog standing** at the MuJoCo origin, every `xmat` = `I`. xpos 1–12: `[0 0 0.5] [0.2 0 0.54]
[0.29 0 0.66] [0.2 0.07 0.47] [0.2 0.07 0.27] [0.2 -0.07 0.47] [0.2 -0.07 0.27] [0 0 0.5]
[-0.2 0.07 0.47] [-0.2 0.07 0.27] [-0.2 -0.07 0.47] [-0.2 -0.07 0.27]`.

**Dog lying** at the estate spot, every `xmat` = `Rlie`. xpos 1–12: `[4.5 8.0 0.1]
[4.7 7.96 0.1] [4.79 7.84 0.1] [4.7 8.03 0.17] [4.7 8.23 0.17] [4.7 8.03 0.03] [4.7 8.23 0.03]
[4.5 8.0 0.1] [4.3 8.03 0.17] [4.3 8.23 0.17] [4.3 8.03 0.03] [4.3 8.23 0.03]`.

### 5.2 Lap circles (literals, from cases 5 and 7)

```clojure
(def STAND {:id :creature :at [4.67 -8.0] :r 0.62})
(def LIE   {:id :creature :at [4.67 -8.225] :r 0.7774779})
(def SPOT  (let [[x _ z] (:at (:creature-spike W))] {:id :creature :at [x z] :r 1.0}))  ; [4.5 -8.0]
(defn kick [i]   ; LIE until frame 600, rolls 0.6 m toward -x over 10 frames at 1.2 m/s, then still
  (let [[x z] (:at LIE)]
    (cond (< i 600) [LIE]
          (< i 610) [(assoc LIE :at [(- x (* 0.06 (- i 599))) z] :vel [-1.2 0])]
          :else     [(assoc LIE :at [(- x 0.6) z])])))
```

## 6. Test cases

All values were worked out by hand (§7). The scenario and lap values were also run in the model
of §1.1. "Model" numbers are what it printed; assert the thresholds, not the model numbers.

### The circle from body positions (`pm.creature-motion`)

1. **`capsules`.**
   - `(count (cm/capsules horse))` → 12; `(mapv :id (cm/capsules horse))` → `[1 2 … 12]`.
   - `(first (cm/capsules horse))` → `{:id 1 :from [0.1 0 0] :to [0.3 0 0] :r 0.15}`.
   - `(mapv :r (cm/capsules horse))` → `[0.15 0.06 0.07 0.05 0.04 0.05 0.04 0.15 0.05 0.04 0.05 0.04]`.
   - `(mapv :r (cm/capsules dog))` → `[0.1 0.045 0.05 0.035 0.03 0.035 0.03 0.1 0.035 0.03 0.035 0.03]`.
   - `(nth (cm/capsules horse) 7)` → `{:id 8 :from [-0.3 0 0] :to [-0.1 0 0] :r 0.15}` (the hind
     half, depth-first after the front legs).
   - `cm/obstacle-id` → `:creature`.
2. **One capsule along x, z-up → game x/z.**
   - Call `(cm/ground-circle [{:id 1 :from [0 0 0] :to [1 0 0] :r 0.5}] (clj->js [0 0 0 2 3 0.4]) (clj->js (into (vec (repeat 9 0)) I)) qv)`.
   - Result ≈ `{:at [2.5 -3] :r 1.0 :vel [0.6 -0.8]}`.
3. **Height is ignored.**
   - A vertical capsule: `[{:id 1 :from [0 0 0] :to [0 0 1] :r 0.2}]` with xpos `[0 0 0 1 2 5]`
     and xmat `I` → `:at` ≈ `[1 -2]`, `:r` ≈ 0.2.
   - The case 2 capsule with xpos z 7 instead of 0.4 → the same result as case 2.
4. **Rotation, and MuJoCo +y is game −z.** `[{:id 1 :from [0 0 0] :to [1 0 0] :r 0.1}]`, xpos
   `[0 0 0 0 0 0]`, xmat `Rz90` → `:at` ≈ `[0 -0.5]`, `:r` ≈ 0.6.
5. **Horse standing** (§5.1) → `:at` ≈ `[4.67 -8.0]`, `:r` ≈ 0.62, `:vel` ≈ `[0.6 -0.8]`.
6. **Horse standing, yaw 90°** → `:at` ≈ `[4.5 -8.17]`, `:r` ≈ 0.62 (the head points game −z).
7. **Horse lying** → `:at` ≈ `[4.67 -8.225]`, `:r` ≈ 0.7774779 (set by the head's tip).
8. **Horse curled** → `:at` ≈ `[4.64 -7.88]`, `:r` ≈ 0.6732456 (set by the folded front
   hoof's tip).
9. **Dog** (the second species: nothing is horse-only).
   - Standing → `:at` ≈ `[0.07 0]`, `:r` ≈ 0.39.
   - Lying → `:at` ≈ `[4.57 -8.115]`, `:r` ≈ 0.4577070.
10. **Covering, and edge cases.**
    - For the lying and curled horse, every capsule endpoint `E` (its ground point) with
      radius `r_k` satisfies `|E − at| + r_k <= r + 1e-9`. The tester computes the endpoints
      in the test from the rows and `capsules`.
    - `(cm/ground-circle [] xp xm qv)` → nil.
    - `qvel` nil → `:vel` `[0 0]` (case 2 with nil).

### Extras and senses (`pm.motion`)

Use `A = (m/walker {:id :a :route [[0 0] [10 0]]})` (at `[0 0]`, facing +x) and
`(defn ob [at r v] (first (m/extra-others [{:id :foal :at at :r r :vel v}])))`.

11. **`extra-others`.** Call
    `(m/extra-others [{:id :foal :at [1 2] :r 0.5} {:id :foal :at [1 2] :r 0.5 :vel [1 -1] :y 1.79} {:id "x" :at [1 2] :r 1} {:id :b :at [1 2 3] :r 1} {:id :c :at [1 2] :r 0} {:id :d :at [1 2] :r -1} {:id :e :at [1 2] :r 1 :vel [1]} nil])`.
    The result is:
    `[{:id :foal :pos [1 2] :radius 0.5 :vel [0 0] :obstacle true} {:id :foal :pos [1 2] :radius 0.5 :vel [1 -1] :obstacle true} {:id :e :pos [1 2] :radius 1 :vel [0 0] :obstacle true}]`.
    Also `(m/extra-others nil)` → `[]`.
12. **`sense-dist`.**
    - `(m/sense-dist A (assoc A :id :b :pos [3 4]))` → 5 (a walker: the centre distance).
    - `(m/sense-dist A (ob [3 4] 1 [0 0]))` → 4.
    - `(m/sense-dist A (ob [0.3 0.4] 1 [0 0]))` → 0 (inside).
13. **Sight to the edge.**
    - `(m/sees? A (ob [8.5 0] 1 [0 0]))` → true (edge 7.5). A walker at `[8.5 0]` is not seen
      (senses t9, unchanged).
    - `[9.5 0]` r 1 → false (edge 8.5).
    - `[-0.5 0]` r 1 → true (inside the circle, though behind).
    - `[-3 0]` r 1 → false (behind, edge 2).
    - `[3 5]` r 1 → false (the centre is at 59.04°, outside ±55°).
14. **Hearing to the edge, scaled by his speed.**
    - `(m/hears? A (ob [-2 0] 0.9 [0 0]))` → true (edge 1.1).
    - `[-2 0]` r 0.7, still → false (edge 1.3 > 1.2).
    - `[-3 0]` r 0.7 with `:vel [2.4 0]` → true (range 2.4, edge 2.3).
15. **`perceive-all` with an extra.**
    - `(m/perceive-all [A] 0.1 [{:id :foal :at [5 0] :r 0.5}])` → its only walker's `:aware` =
      `{:foal {:on 0.1 :off 0 :noticed false :how :sight}}`.
    - `(m/perceive-all [A] 0.1 [])` → `:aware {}`.
16. **A silent, still foal behind is not perceived; a kicking one is heard.**
    - `C = (assoc (mv {:id :c :route [[-5 0] [5 0]] :pause-s 1}) :pos [2.5 0])`.
    - `still = (m/extra-others [{:id :creature :at [0 0] :r 0.6}])`.
    - `kicking = (m/extra-others [{:id :creature :at [0 0] :r 0.6 :vel [2.4 0]}])`.
    - `(nth (iterate #(m/perceive % still 0.05) C) 8)` → `:aware {}`, `:glance false`.
    - `(nth (iterate #(m/perceive % kicking 0.05) C) 7)` → `:creature` not noticed.
    - `(nth (iterate #(m/perceive % kicking 0.05) C) 8)` → `:creature` `{:noticed true :how :hear}`,
      and `:glance` true.

### The planner (`pm.motion`)

`L = (mv {:id :l :route [[0 0] [8 0]] :pause-s 1})`.

17. **A noticed obstacle is a walker's circle.**
    - Call `(m/plan-all [(assoc L :pos [2 0] :aware {:foal seen-1s})] [] cfg [{:id :foal :at [4 0] :r 0.5}])`.
    - First walker: `:aim` ≈ `[4.3424073 1.4449073]`, `:side :right`, `:goal` nil. These are
      the same numbers as the static `{:at [4 0] :r 0.5}` gives in senses t33.
18. **Not noticed → ignored.** The same call without `:aware` → `:aim` nil, `:side` nil.
19. **The goal moves out of a noticed obstacle.**
    - `U = (assoc (mv {:id :u :route [[4 -6] [3.5 -7.5]] :pause-s 8}) :aware {:foal seen-1s})`.
    - `(first (m/plan-all [U] [] cfg [{:id :foal :at [4.5 -8.0] :r 1.0}]))` → `:goal` ≈
      `[3.7701562 -6.6895314]`, `:aim` nil.
    - The same without `:aware` → `:goal` nil.
20. **Arrives at the moved goal.**
    - Run `(frames [U] [] 60 (constantly [{:id :foal :at [4.5 -8.0] :r 1.0}]))`.
    - The first frame with `:pause` > 0 is index 7, with `:pause` 8.
    - Its `:pos` is within 0.3 of `[3.7701562 -6.6895314]` (model `[3.848 -6.455]`).
    - Its distance to `[4.5 -8.0]` is ≥ 1.3 (r + walker radius).
21. **Empty extras are exactly today.**
    - `ws = (m/walkers W)`, `S = (a/statics W)`.
    - `(= (m/plan-all ws S cfg) (m/plan-all ws S cfg nil) (m/plan-all ws S cfg []))`.
    - `(= (m/perceive-all ws 0.05) (m/perceive-all ws 0.05 []))`.
    - `(= (m/replan ws ws S cfg 0.05 0.2) (m/replan ws ws S cfg 0.05 0.2 []))`.
    - 400 frames of `m/advance` on `ws`: the 5-arity, the 6-arity with `nil` and the 6-arity
      with `[]` give `=` walker vectors in every frame.

### Scenarios (dt 0.05)

22. **Detour: a route through a perceived circle.**
    - Setup: `A1 = (mv {:id :a :route [[0 0] [10 0]] :pause-s 100})`,
      `F1 = {:id :creature :at [5 0] :r 0.8}`,
      `ws = (mapv first (frames [A1] [] 240 (constantly [F1])))`.
    - First noticed at index 7, `:how :sight`.
    - The first index with a non-nil `:aim` is 24, with `:side :right`.
    - `(gap w F1)` ≥ 0 in every frame (model 0.172).
    - At the first frame with x ≥ 5: z > 0.5 (model 1.313). It passes on its right (+z when
      heading +x).
    - The final frame has `:pause` > 0 and `:pos` within 0.3 of `[10 0]`.
23. **Mirrored, dog-sized: heading −x.**
    - Setup: `B1 = (mv {:id :b :route [[10 0] [0 0]] :pause-s 100})`,
      `F2 = {:id :creature :at [5 0] :r 0.39}` (case 9's dog radius),
      `ws = (mapv first (frames [B1] [] 240 (constantly [F2])))`.
    - First noticed at index 7, `:sight`. The first `:aim` is at index 29, `:side :right`.
    - Gap ≥ 0 in every frame (model 0.25).
    - At the first frame with x ≤ 5: z < −0.5 (model −0.958).
    - The final frame has `:pause` > 0 and `:pos` within 0.3 of `[0 0]`.
24. **Coming from behind, sight only (silent, still foal).**
    - Setup: `C` from case 16, `F3 = {:id :creature :at [0 0] :r 0.6}`,
      `ws = (mapv first (frames [C] [] 400 (constantly [F3])))`.
    - Before the turn: in every frame with `:leg` 1, `(:aware w)` has no `:creature` key and
      `:aim` is nil.
    - The first index with `:leg` 2 is 56.
    - The first index with a `:creature` entry is 57 (`:how :sight`). The first noticed index is
      64, at x ≈ 4.24.
    - In every frame before index 64, `:aim` is nil.
    - Gap ≥ 0 in every frame (model 0.213).
    - At the first frame with `:leg` 2 and x ≤ 0: z < −0.5 (model −1.138).
    - At the first index with `:leg` 3, `:pos` is within 0.3 of `[-5 0]`.

### Laps on the real estate routes (six walkers, 2400 frames = 120 s)

For each case below:
- `S = (a/statics W)`; run `(frames (m/walkers W) S 2400 extras-at)`.
- Track, over all frames:
  - the min of `(gap w c)` over all six walkers, with `c` = the frame's first extra;
  - the pair min distance;
  - the min static gap (crew-walkers t20's definition);
  - the picks (the `:to` after each `:leg` change).
- Every case must satisfy:
  - foal gap ≥ 0 (no walker within the foal's r + its own radius);
  - pair ≥ 0.6;
  - static gap ≥ −1e-9;
  - the first 8 picks are `[1 2 1 0 1 2 1 0]` for `:un-architecte` and `[1 2 3 0 1 2 3 0]` for
    `:le-coureur`;
  - final `:leg` ≥ 10 for `:un-architecte` and ≥ 40 for `:le-coureur`.

| # | extras-at | foal gap (model) | pair | static | UA leg | LC leg |
|---|---|---|---|---|---|---|
| 25 | `(constantly [STAND])` | 0.381 | 0.784 | 0.155 | 12 | 58 |
| 26 | `(constantly [LIE])` | 0.250 | 0.756 | 0.151 | 12 | 57 |
| 27 | `(constantly [SPOT])` | 0.152 | 0.765 | 0.155 | 13 | 48 |
| 28 | `kick` | 0.154 | 0.826 | 0.151 | 12 | 51 |

Also check:
- In 25–28, both `:un-architecte` and `:le-coureur` first notice `:creature` at index 7. The
  `:how` there is `:sight` for both, except in 27, where Le Coureur's is `:hear` (§7).
- In 27, some `:un-architecte` frame has a non-nil `:goal`, and every such `:goal` is ≥ 1.45 −
  1e-9 from `[4.5 -8.0]` (model: 32 frames, each 1.50 away).

29. **Without `?pony` nothing changes.**
    - `(m/extra-others [])` → `[]`. (Case 21 covers the equal runs; crew-walkers t20 is the
      lap without extras and stays unchanged.)
    - The estate keeps `(:creature-spike W)` = `{:flag "pony" :kind :horse :seed 1 :at [4.5 1.79 -8.0] :yaw 0 :drop-m 0.4}`
      (nobody moved the foal).

## 7. Hand arithmetic

- **Case 2.**
  - The endpoints are MuJoCo `(2,3)` and `(3,3)`, so game `(2,−3)` and `(3,−3)`, with r 0.5.
  - Box: x [1.5, 3.5], z [−3.5, −2.5], so the centre is (2.5, −3).
  - Each endpoint is 0.5 from the centre: 0.5 + 0.5 = 1.0.
  - The velocity is `[0.6, −0.8]`, from `qvel[0]` and `−qvel[1]`.
- **Case 3.** Both ends are at MuJoCo xy (1, 2), so the centre is (1, −2) and r = 0 + 0.2.
- **Case 4.**
  - `Rz90·[1 0 0]` = `[0 1 0]`, so the ends are MuJoCo (0,0) and (0,1), which is game (0,0) and
    (0,−1).
  - Box: x [−0.1, 0.1], z [−1.1, 0.1], so the centre is (0, −0.5) and r = 0.5 + 0.1.
- **Case 5, horse standing** (MuJoCo xy; game z = −y).
  - Capsule extents along x: hind −0.3 → 4.2 − 0.15 = **4.05**; the head's tip
    4.5 + 0.3 + 0.18 + 0.24 = 5.22, + 0.07 = **5.29**. Centre x 4.67.
  - Along y: the torso and legs both reach ±0.15 (8.0 ± 0.15; legs 8.0 ± 0.1 ± 0.05). Centre
    y 8.0, so game z −8.0.
  - Radius:
    - head tip: 5.22 − 4.67 = 0.55, + 0.07 = **0.62**;
    - hind end: 4.67 − 4.2 = 0.47, + 0.15 = **0.62**;
    - hind hoof (4.22, 8.1): √(0.45² + 0.1²) + 0.05 = 0.511;
    - so r = 0.62.
- **Case 6.**
  - `Rz90` turns the body's +x to MuJoCo +y. The offset (0.17, 0) becomes (0, 0.17), so the
    centre is MuJoCo (4.5, 8.17) → game (4.5, −8.17). r is unchanged.
- **Case 7, lying.**
  - `Rlie·[x y z]` = `[x −z y]`, so the horizontal y is −(local z).
  - Neck end / head origin (4.98, 7.68); head tip (4.98 + 0.24, 7.68 + 0.1) = (5.22, 7.78);
    hooves (4.78 | 4.22, 8.05 + 0.38 + 0.37 = 8.80).
  - Box:
    - x: 4.05 to 5.29 → 4.67;
    - y: 7.68 − 0.07 = 7.61 (head origin) to 8.80 + 0.04 = 8.84 → 8.225.
    - The centre is game (4.67, −8.225).
  - Radius:
    - head tip: √(0.55² + 0.445²) = √0.500525 = 0.707478, + 0.07 = **0.777478**;
    - hind hoof: √(0.45² + 0.575²) = √0.533125 = 0.730154, + 0.04 = 0.770154;
    - head origin: √(0.31² + 0.545²) + 0.07 = 0.697;
    - hind end: √(0.47² + 0.225²) + 0.15 = 0.671.
- **Case 8, curled.**
  - The front legs run from (4.78, 8.05) back by 0.38 then 0.37, to x 4.40 then **4.03**. The
    hind legs run from (4.22, 8.05) forward to 4.60 then 4.97.
  - Box:
    - x: 4.03 − 0.04 = 3.99 to 5.29 → 4.64;
    - y: 7.61 to 8.15 (torso) → 7.88.
    - The centre is game (4.64, −7.88).
  - Radius:
    - front hoof tip (4.03, 8.05): √(0.61² + 0.17²) = √0.401 = 0.633246, + 0.04 = **0.673246**;
    - head tip (5.22, 7.78): √(0.58² + 0.1²) + 0.07 = 0.6586;
    - hind end (4.2, 8.0): √(0.44² + 0.12²) + 0.15 = 0.6061.
- **Case 9, dog.**
  - Standing:
    - x: −0.22 − 0.1 = −0.32 to 0.2 + 0.09 + 0.12 + 0.05 = 0.46 → 0.07;
    - y is symmetric (legs ±0.07 ± 0.035 = ±0.105) → 0;
    - r = 0.46 − 0.07 = 0.39 (the head tip, and also the hind end: 0.29 + 0.1).
  - Lying:
    - x: 4.18 to 4.96 → 4.57;
    - y: 7.84 − 0.05 = 7.79 to 8.41 + 0.03 = 8.44 → 8.115;
    - r from the head tip (4.91, 7.89): √(0.34² + 0.225²) = √0.166225 = 0.407707, + 0.05 =
      0.457707.
- **Case 13, `[3 5]`:** cos = 3/√34 = 0.5145 < cos 55° = 0.5736.
- **Case 16.**
  - The edge distance is 2.5 − 0.6 = 1.9, behind (180°): not seen.
  - Still: hearing range 1.2 < 1.9. Kicking at 2.4 m/s: the range is 1.2 × 2.4/1.2 = 2.4 ≥ 1.9.
  - Eight frames: 8 × 0.05 = 0.39999999999999997 ≥ 0.4 − 1e-9.
- **Case 17.** clear = 0.3 + 0.5 + 0.15. The obstacle has vel 0, so `first-block` is the static
  case (avoid t24), and `[8 0]` is not near it, so the goal doesn't move.
- **Case 19.**
  - u = ([3.5 −7.5] − [4 −6]) / 1.5811 = (−0.31623, −0.94868).
  - C = 0.3 + 1.0 + 0.15 = 1.45; cp = 1.5.
  - w = G − c = (−1, 0.5); w·u = −0.15811; |w|² = 1.25.
  - k = −0.15811 + √(0.025 − 1.25 + 2.25) = −0.15811 + 1.01242 = 0.85431.
  - g = G − u·k = (3.5 + 0.27016, −7.5 + 0.81047) = **(3.77016, −6.68953)**.
  - g is not behind P. The path P→g passes no closer than 1.5 > 1.45, so `:aim` is nil.
- **Case 22.**
  - Noticed: the edge is 4.2 m ahead at 0° from frame 1, so 8 frames → index 7.
  - First aim:
    - the look-ahead is 2.4 m;
    - the circle blocks once 5 − x − 2.4 < 0.8 + 0.3 + 0.15 = 1.25, i.e. x > 1.35;
    - plans run at indices 4, 9, 14, 19, 24 (acc reaches 0.25);
    - x after index i is 0.06(i + 1): at 19 it is 1.2 (clear), at 24 it is 1.5 (blocked).
- **Case 23.**
  - clear = 0.3 + 0.39 + 0.15 = 0.84; blocked once (10 − 0.06(i+1)) − 5 − 2.4 < 0.84, i.e.
    i + 1 > 29.33.
  - The plan at index 29 (x 8.2) is the first.
- **Case 24.**
  - Arrival at [5 0]: 2.5 + 0.06k ≥ 4.7 at k = 37 (index 36), then a 1 s pause. The pick (leg 2)
    comes at index 56 with vel 0.
  - The first step toward −x is at index 57: facing π, edge 4.06 m at 0° → seen.
  - Noticed 8 frames later, at index 64, at x = 4.72 − 8 × 0.06 = 4.24.
  - While on leg 1 and paused, the facing stays +x (paused vel is 0, below `face-min-speed`), the
    foal is at 180° and the edge is ≥ 1.9 > 1.2, so there is no entry at all.
- **Case 27.** The goal ring is cp = 1.45 + `a/free-pad` 0.05 = 1.5 from the centre.
- **Laps, why index 7.**
  - Un Architecte starts at [6 −4], facing [4 −6]: heading (−0.707, −0.707). The foal's
    centres are 4.2–4.6 m away, inside the ±55° cone.
  - Le Coureur starts at [3 −8.5], facing [5 −10]: (0.8, −0.6).
    - STAND's centre is 1.743 m away at 53.5° (cos 0.594), so it is seen.
    - LIE's is at 46.2°, also seen.
    - SPOT's centre [4.5 −8] is at 55.3° (cos 0.569 < 0.5736), so it is not seen. Its edge is
      1.581 − 1.0 = 0.581 m ≤ 1.2, so it is heard (`:how :hear`).
  - Both are paused at the start (vel 0), so the facing is the heading.

## 8. Done when

- `bash .crew/gate.sh` passes: 0 warnings in `app` and `test`. All existing tests pass
  unchanged, plus `pm.creature-obstacle-test` (29 cases).
- Live (Hal), with `?pony&paths`:
  - a pink circle follows the foal as he drops, gets up and stands;
  - Un Architecte and Le Coureur go around it and never walk into it;
  - Un Architecte's pause by the bank ends outside the circle.

  Repeat with `?pony&paths&brain=twitch` and with `?pony&paths&kinematic`. Without `?pony` there
  is no circle and the walkers do exactly as before. Michael takes a screenshot from his own
  camera.
