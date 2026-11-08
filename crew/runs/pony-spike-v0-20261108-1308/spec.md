# Spec: pony-spike-v0 (a foal that can't stand yet)

Quest: `.crew/quests/pony-spike-v0.md`. Plan of record: `docs/ideas-animal-motion.md` §"Nov 8".
Architect: un-architecte, 2026-11-08. Builder: un-automate-monde. Tester: le-testeur.

The builder and the tester work at the same time from this file alone. Every name, signature,
string and number below is binding. Where this spec shows an exact string (XML, HUD, error
message), match it character for character.

**Checked against real MuJoCo.** Before writing this, the architect built both bodies below as
XML (the exact template in §4.6) in `@mujoco/mujoco` 4.0.0 under Node 26. Real MuJoCo matched the
hand-computed masses to 6 decimals and the counts exactly. With zero controls both bodies come to
rest standing (horse torso 0.839 m above the plane, dog 0.439 m), with no NaN. Under random
position targets both fall and flop (torso about 0.15 m and 0.10 m, lying down), still with no
NaN. 1000 steps cost 15 to 67 ms in Node, so 8 steps a frame is well under 1 ms.

---

## 1. Shape of the work

```
resources/creatures/horse.edn ─┐                        ┌─ bb creature-mjcf <kind>  (scripts/creature_mjcf.clj)
resources/creatures/dog.edn  ──┼─ pm.species (inline) ──┤
                               │                        └─ pm.creature  (cljc, pure: species → MJCF, frames, parts)
resources/estate.edn :creature-spike ─┐                          │
                                      ├─ pm.creature-motion (cljs, pure: URL, controller, fixed step, HUD)
                                      │                          │
                                      └──────── pm.creature-spike (interop: MuJoCo WASM + three objects)
                                                                 │
                                         pm.core: start! / tick! / hud-segment (3 lines)
```

| Namespace | File | Kind | Owns |
|---|---|---|---|
| `pm.creature` | `src/pm/creature.cljc` | pure, **cljc** (bb + cljs) | species validation, MJCF XML, z-up↔y-up, masses, counts, body parts |
| `pm.creature-motion` | `src/pm/creature_motion.cljs` | pure cljs (js/Math, js/URLSearchParams only) | spike config/URL, the controller interface + twitch noise, fixed-step accumulator, stats, HUD text, pose from MuJoCo arrays |
| `pm.species` | `src/pm/species.cljs` | data index | `{kind edn-text}` for the shipped species files (via `shadow.resource/inline`) |
| `pm.creature-spike` | `src/pm/creature_spike.cljs` | interop (like `pm.bricks`) | `defonce !state`, MuJoCo load/step/delete, three objects through `pm.three` |

**No species names in code.** `horse`, `dog`, `pony` and `juniper` appear in no `src/` file,
with one exception: `pm.species`, which is a file index (a map of two `rc/inline` calls, nothing
else). `shadow.resource/inline` needs literal paths, so this one manifest is unavoidable. The URL
flag name (`"pony"`) and the default kind (`:horse`) are data in `resources/estate.edn`.

---

## 2. Who touches what

| Path | Builder (un-automate-monde) | Tester (le-testeur) |
|---|---|---|
| `src/pm/creature.cljc` | **new** | read only |
| `src/pm/creature_motion.cljs` | **new** | read only |
| `src/pm/species.cljs` | **new** | read only |
| `src/pm/creature_spike.cljs` | **new** | — |
| `src/pm/three.cljs` | add `set-quat!` (only) | — |
| `src/pm/core.cljs` | 3 wiring lines (§6.3), nothing else | — |
| `resources/creatures/horse.edn`, `dog.edn` | **new**, verbatim from §3 | read only |
| `resources/estate.edn` | add `:creature-spike` (§5.1), nothing else | read only |
| `bb.edn` | add task `creature-mjcf` | — |
| `scripts/creature_mjcf.clj` | **new** | — |
| `test/pm/creature_test.cljs` | — | **new** (cases C1–C27) |
| `test/pm/creature_motion_test.cljs` | — | **new** (cases M1–M15) |
| `test/pm/creature_mujoco_test.cljs` | — | **new** (cases R1–R5, real MuJoCo) |

Nobody edits `package.json`, `public/vendor/`, `public/index.html`, `resources/content/`,
`creatures/`, `docs/GDD.md`, `assets/private/` or `public/kit/`. The spec is the only thing the
architect writes. Nobody commits; Hal runs the gate and Michael approves.

---

## 3. The species files (verbatim)

MuJoCo frame throughout a species file: **x forward, y left, z up, metres. Angles in degrees.**
A body's `:at` is its origin in its parent's frame (rest pose, all joints at 0). `:capsule`
`:from`/`:to` are the capsule's segment ends in the body's own frame, and `:r` is its radius. The
first body is the root: it has no `:parent`, `:at` or `:joint`, and gets MuJoCo's free joint. Any
other body has at most one hinge `:joint` (no `:joint` means it is welded to its parent). Unknown
keys are allowed and ignored, so `:gait` and `:rewards` can come later.

### 3.1 `resources/creatures/horse.edn`

```clojure
;; A foal: ~1.0 m at the withers, ~0.9 m body. MuJoCo frame: x forward, y left, z up; metres.
;; Angles in degrees. Read by pm.creature (the game, and `bb creature-mjcf horse`).
{:kind    :horse
 :density 1000          ; kg/m³: each body's mass = its capsule's volume × density
 :kp      150           ; position-actuator stiffness, N·m/rad (one actuator per joint)
 :damping 5             ; joint damping, N·m·s/rad
 :colors  {:coat 0x9b6b43 :dark 0x4a3426 :hoof 0x2e2622}
 :bodies
 [{:name :torso :capsule {:from [-0.3 0 0] :to [0.3 0 0] :r 0.15} :color :coat}
  {:name :neck :parent :torso :at [0.3 0 0.08]
   :joint {:axis [0 1 0] :range [-40 30]}
   :capsule {:from [0 0 0] :to [0.18 0 0.24] :r 0.06} :color :dark}
  {:name :head :parent :neck :at [0.18 0 0.24]
   :capsule {:from [0 0 0] :to [0.24 0 -0.1] :r 0.07} :color :coat}
  {:name :fl-upper :parent :torso :at [0.28 0.1 -0.05]
   :joint {:axis [0 1 0] :range [-45 45]}
   :capsule {:from [0 0 0] :to [0 0 -0.38] :r 0.05} :color :coat}
  {:name :fl-lower :parent :fl-upper :at [0 0 -0.38]
   :joint {:axis [0 1 0] :range [-10 100]}
   :capsule {:from [0 0 0] :to [0 0 -0.37] :r 0.04} :color :hoof}
  {:name :fr-upper :parent :torso :at [0.28 -0.1 -0.05]
   :joint {:axis [0 1 0] :range [-45 45]}
   :capsule {:from [0 0 0] :to [0 0 -0.38] :r 0.05} :color :coat}
  {:name :fr-lower :parent :fr-upper :at [0 0 -0.38]
   :joint {:axis [0 1 0] :range [-10 100]}
   :capsule {:from [0 0 0] :to [0 0 -0.37] :r 0.04} :color :hoof}
  {:name :hl-upper :parent :torso :at [-0.28 0.1 -0.05]
   :joint {:axis [0 1 0] :range [-45 45]}
   :capsule {:from [0 0 0] :to [0 0 -0.38] :r 0.05} :color :coat}
  {:name :hl-lower :parent :hl-upper :at [0 0 -0.38]
   :joint {:axis [0 1 0] :range [-100 10]}
   :capsule {:from [0 0 0] :to [0 0 -0.37] :r 0.04} :color :hoof}
  {:name :hr-upper :parent :torso :at [-0.28 -0.1 -0.05]
   :joint {:axis [0 1 0] :range [-45 45]}
   :capsule {:from [0 0 0] :to [0 0 -0.38] :r 0.05} :color :coat}
  {:name :hr-lower :parent :hr-upper :at [0 0 -0.38]
   :joint {:axis [0 1 0] :range [-100 10]}
   :capsule {:from [0 0 0] :to [0 0 -0.37] :r 0.04} :color :hoof}]}
```

Hinge sign: a positive angle about `+y` (left) swings a downward leg's foot **backward** (−x).
Front lower legs fold back (`[-10 100]`), hind lower legs fold forward (`[-100 10]`).

### 3.2 `resources/creatures/dog.edn`

```clojure
;; A mid-size dog: ~0.55 m at the shoulder. MuJoCo frame: x forward, y left, z up; metres.
;; Angles in degrees. Read by pm.creature (the game, and `bb creature-mjcf dog`).
{:kind    :dog
 :density 1000          ; kg/m³: each body's mass = its capsule's volume × density
 :kp      40            ; position-actuator stiffness, N·m/rad (one actuator per joint)
 :damping 1.5           ; joint damping, N·m·s/rad
 :colors  {:coat 0xc49a6c :dark 0x6b4a32}
 :bodies
 [{:name :torso :capsule {:from [-0.22 0 0] :to [0.22 0 0] :r 0.1} :color :coat}
  {:name :neck :parent :torso :at [0.2 0 0.04]
   :joint {:axis [0 1 0] :range [-40 30]}
   :capsule {:from [0 0 0] :to [0.09 0 0.12] :r 0.045} :color :coat}
  {:name :head :parent :neck :at [0.09 0 0.12]
   :capsule {:from [0 0 0] :to [0.12 0 -0.05] :r 0.05} :color :dark}
  {:name :fl-upper :parent :torso :at [0.2 0.07 -0.03]
   :joint {:axis [0 1 0] :range [-45 45]}
   :capsule {:from [0 0 0] :to [0 0 -0.2] :r 0.035} :color :coat}
  {:name :fl-lower :parent :fl-upper :at [0 0 -0.2]
   :joint {:axis [0 1 0] :range [-10 100]}
   :capsule {:from [0 0 0] :to [0 0 -0.18] :r 0.03} :color :dark}
  {:name :fr-upper :parent :torso :at [0.2 -0.07 -0.03]
   :joint {:axis [0 1 0] :range [-45 45]}
   :capsule {:from [0 0 0] :to [0 0 -0.2] :r 0.035} :color :coat}
  {:name :fr-lower :parent :fr-upper :at [0 0 -0.2]
   :joint {:axis [0 1 0] :range [-10 100]}
   :capsule {:from [0 0 0] :to [0 0 -0.18] :r 0.03} :color :dark}
  {:name :hl-upper :parent :torso :at [-0.2 0.07 -0.03]
   :joint {:axis [0 1 0] :range [-45 45]}
   :capsule {:from [0 0 0] :to [0 0 -0.2] :r 0.035} :color :coat}
  {:name :hl-lower :parent :hl-upper :at [0 0 -0.2]
   :joint {:axis [0 1 0] :range [-100 10]}
   :capsule {:from [0 0 0] :to [0 0 -0.18] :r 0.03} :color :dark}
  {:name :hr-upper :parent :torso :at [-0.2 -0.07 -0.03]
   :joint {:axis [0 1 0] :range [-45 45]}
   :capsule {:from [0 0 0] :to [0 0 -0.2] :r 0.035} :color :coat}
  {:name :hr-lower :parent :hr-upper :at [0 0 -0.2]
   :joint {:axis [0 1 0] :range [-100 10]}
   :capsule {:from [0 0 0] :to [0 0 -0.18] :r 0.03} :color :dark}]}
```

Vector index → body, for both files (tests use these indices):
0 torso · 1 neck · 2 head · 3 fl-upper · 4 fl-lower · 5 fr-upper · 6 fr-lower · 7 hl-upper ·
8 hl-lower · 9 hr-upper · 10 hr-lower.

### 3.3 Hand-worked numbers for both species

Capsule mass is `density · π · r² · (L + 4r/3)`, where `L = |to − from|` (the cylinder length;
this is exactly MuJoCo's capsule volume). `π = 3.141592653589793`.

| body | horse r, L | horse kg | dog r, L | dog kg |
|---|---|---|---|---|
| torso | 0.15, 0.60 → π·0.0225·0.8 | **56.548668** | 0.10, 0.44 → π·0.01·0.573333 | **18.011798** |
| neck | 0.06, 0.30 (0.18/0.24 → 3-4-5) → π·0.0036·0.38 | **4.297699** | 0.045, 0.15 (0.09/0.12) → π·0.002025·0.21 | **1.335962** |
| head | 0.07, 0.26 (0.24/0.10 → 5-12-13) → π·0.0049·0.353333 | **5.439144** | 0.05, 0.13 (0.12/0.05) → π·0.0025·0.196667 | **1.544616** |
| each upper leg (×4) | 0.05, 0.38 → π·0.0025·0.446667 | **3.508112** | 0.035, 0.20 → π·0.001225·0.246667 | **0.949285** |
| each lower leg (×4) | 0.04, 0.37 → π·0.0016·0.423333 | **2.127905** | 0.03, 0.18 → π·0.0009·0.22 | **0.622035** |
| **total** | | **88.829579** | | **27.177656** |

Rest-pose bottom (lowest capsule surface, torso origin = 0, joints at 0):
horse `−0.05 − 0.38 − 0.37 − 0.04 = −0.84`; dog `−0.03 − 0.20 − 0.18 − 0.03 = −0.44`.
Withers check: horse 0.84 + 0.15 = 0.99 m; dog 0.44 + 0.10 = 0.54 m. ✓

Counts (both species): 11 bodies, 9 hinges, so `nbody 12` (with the world body), `njnt 10`
(with the free joint), `nu 9`, `nq 16` (7 + 9), `nv 15` (6 + 9), `ngeom 12` (with the plane).

Joint ranges in radians (6 decimals, as the XML prints them): −40° → −0.698132, 30° → 0.523599,
±45° → ±0.785398, −10° → −0.174533, 10° → 0.174533, 100° → 1.745329, −100° → −1.745329.

---

## 4. `pm.creature` (src/pm/creature.cljc), pure, runs in bb and cljs

Requires only `clojure.string` and the EDN reader: `#?(:clj clojure.edn :cljs cljs.reader)`.
Math goes through reader conditionals (`#?(:clj (Math/sqrt x) :cljs (js/Math.sqrt x))`, same for
`asin`, `atan2`, `cos`, `sin`, `round`, `floor`). No atoms, no I/O, no species names.

### 4.1 Constants

```clojure
(def timestep 0.002)                             ; s, MuJoCo <option timestep>
(def ground-half 2)                              ; m, plane half-size: a 4 m square
(def origin {:at [0 0 0] :yaw 0 :drop-m 0.4})    ; the trainer's placement (bb task)
```

### 4.2 Validation

**`(problems sp)`** → a vector of strings, `[]` when `sp` is valid. It never throws (any input,
including `nil`, gives a vector). It checks in this order and collects **all** problems in this
order, top-level checks first, then each body in vector order. Exact texts follow, where `<n>` is
`(pr-str name)` (for example `:fl-lower`) and `<i>` is the body's vector index:

| # | rule | problem text |
|---|---|---|
| P1 | `(:kind sp)` is a keyword | `":kind must be a keyword"` |
| P2 | `:density` is a number > 0 | `":density must be a positive number"` |
| P3 | `:kp` is a number > 0 | `":kp must be a positive number"` |
| P4 | `:damping` is a number ≥ 0 | `":damping must be a number >= 0"` |
| P5 | `:colors` is a map, keyword keys, integer values in `0..0xffffff` | `":colors must map keywords to 0xRRGGBB ints"` |
| P6 | `:bodies` is a non-empty vector of maps | `":bodies must be a non-empty vector"`; stop here: no body checks |
| P7 | the root (index 0) has no `:parent`, `:at`, `:joint` | `"root <n> must have no :parent, :at or :joint"` |
| P8 | `:name` is a keyword | `"body <i>: :name must be a keyword"`; skip this body's other checks |
| P9 | `:name` not used by an earlier body | `"body <n>: duplicate name"` |
| P10 | a non-root's `:parent` names an **earlier** body | `"body <n>: unknown parent <p>"`, where `<p>` is `(pr-str parent)` |
| P11 | a non-root's `:at` is a vector of 3 numbers | `"body <n>: :at must be [x y z]"` |
| P12 | `:capsule` has `:from`, `:to` (3 numbers each, not equal) and `:r` > 0 | `"body <n>: :capsule needs :from, :to ([x y z], different) and :r > 0"` |
| P13 | `:color` is a key of `:colors` | `"body <n>: :color <c> is not in :colors"`, where `<c>` is `(pr-str color)` |
| P14 | if present, `:joint` has an `:axis` of 3 numbers, not all 0, and a `:range` of `[lo hi]` numbers with `-180 <= lo < hi <= 180` | `"body <n>: :joint needs a non-zero :axis [x y z] and :range [lo hi] with -180 <= lo < hi <= 180"` |
| P15 | at least one body has a `:joint` (checked last; only when P6 passed) | `"needs at least one :joint"` |

For the root, P7 replaces P10, P11 and P14. P12 and P13 still apply to the root. P13 is skipped
when P5 failed.

**`(parse text)`** → the species map (as read) when valid. Otherwise it throws `ex-info` with
`{:problems v}` and this message:
- unreadable EDN: `"bad species: not EDN (" <reader message> ")"` (the tester checks only the prefix `"bad species: not EDN"`)
- not a map: `"bad species: not a map"`
- problems: `(str "bad species" (when (keyword? kind) (str " " kind)) ": " (first problems))`,
  for example `"bad species :horse: body :fl-lower: unknown parent :fl-uper"`.

**`(check! sp)`** → `sp` if `(problems sp)` is empty, else it throws the same `ex-info` as
`parse` (the "problems" form). `parse` = read + `check!`.

### 4.3 Structure

- **`(bodies sp)`** → the bodies in **depth-first order**: the root, then each child subtree in
  the order the children appear in `:bodies`. This is MuJoCo's body order, so the body at
  position `i` of this vector has MuJoCo body id `i + 1` (id 0 is the world). For both shipped
  files, DFS order equals file order.
- **`(joints sp)`** → `[{:name kw :axis [x y z] :range [lo-rad hi-rad]} ...]` for the bodies with
  a `:joint`, in `bodies` order. This is MuJoCo's hinge order and actuator order. Radians =
  `deg × π / 180`.
- **`(ctrl-ranges sp)`** → `(mapv :range (joints sp))`.
- **`(counts sp)`** → `{:nbody (inc nb) :njnt (inc nj) :nu nj :nq (+ 7 nj) :nv (+ 6 nj) :ngeom (inc nb)}`,
  where `nb` is the body count and `nj` the joint count.
- **`(capsule-mass sp body)`** → kg, by the formula in §3.3.
- **`(body-masses sp)`** → `[[name kg] ...]` in `bodies` order.
- **`(rest-bottom sp)`** → the lowest z (m, torso origin = 0, rest pose) over all bodies:
  `min(origin-z + min(from-z, to-z) − r)`, where origin-z is the sum of `:at` z along the
  parent chain. Rest pose has no rotations, so offsets simply add.

### 4.4 Frames: z-up (MuJoCo) ↔ y-up (three.js), the one place

three.js is y-up, MuJoCo is z-up, and both are right-handed. Use the same world metres, rotated
by −90° about x: `three = (mj.x, mj.z, −mj.y)`. MuJoCo +x (forward) stays three +x, MuJoCo +z
(up) becomes three +y, and MuJoCo +y (left) becomes three −z. A three.js yaw of +ψ (rotation.y)
is a MuJoCo rotation of +ψ about z, so the sign is the same.

- **`(mj->three [x y z])`** → `[x z (- y)]`
- **`(three->mj [x y z])`** → `[x (- z) y]`
- **`(mj-quat->three [w x y z])`** → `[x z (- y) w]` (three.js component order x y z w). This is
  conjugation by the frame rotation: the vector part rotates like a position, and w stays.

Nothing else in the codebase converts axes. `pm.creature-motion/body-pose` and `body-part` call
these.

### 4.5 Placement

A placement is `{:at [x y z] :yaw deg :drop-m m}` with `:at` in **three.js** world coordinates
(the estate's), `:at` y being the terrain height. Extra keys are ignored (the spike config is
passed as is).

**`(spawn sp place)`** → `{:ground [gx gy gz] :torso [tx ty tz] :quat [w x y z]}`, all in the
MuJoCo frame:
- `[gx gy gz] = (three->mj (:at place))`, so the plane sits at the terrain height.
- `[tx ty tz] = [gx gy (+ gz (:drop-m place) (- (rest-bottom sp)))]`, so the lowest point starts
  `:drop-m` above the plane.
- `:quat = [(cos (/ ψ 2)) 0 0 (sin (/ ψ 2))]`, where `ψ = yaw × π / 180`.

### 4.6 MJCF

**`(fmt x)`** → a number as XML text: round to 6 decimals (`#?(:clj (String/format
java.util.Locale/ROOT "%.6f" (object-array [(double x)])) :cljs (.toFixed x 6))`), strip trailing
zeros, then a trailing `.`, then turn `"-0"` into `"0"`. (bb and the browser may differ in the
last digit on exact ties. Nothing compares the two.)

**`(mjcf sp place)`** → the XML string. It calls `check!` first, so a bad species throws the
`ex-info` from §4.2. A vector of numbers prints as `fmt` values joined by single spaces. The
layout is exactly the template below: two-space indent per nesting level, one element per line,
and a final newline. Bodies nest by parent in `bodies` (DFS) order. A body with a `:joint` gets
the joint line; a body without one gets none. Actuators go one per joint, in `joints` order.

The exact output for `(mjcf horse {:at [4.5 1.79 -8.0] :yaw 0 :drop-m 0.4})` (two of the four
legs elided; `fr-` and `hr-` follow the same pattern with `y = -0.1`):

```xml
<mujoco model="horse">
  <compiler angle="radian" autolimits="true"/>
  <option timestep="0.002" integrator="implicitfast"/>
  <worldbody>
    <geom name="ground" type="plane" pos="4.5 8 1.79" size="2 2 0.1" contype="1" conaffinity="0"/>
    <body name="torso" pos="4.5 8 3.03" quat="1 0 0 0">
      <freejoint name="root"/>
      <geom name="torso" type="capsule" fromto="-0.3 0 0 0.3 0 0" size="0.15" density="1000" contype="0" conaffinity="1"/>
      <body name="neck" pos="0.3 0 0.08">
        <joint name="neck" type="hinge" axis="0 1 0" limited="true" range="-0.698132 0.523599" damping="5"/>
        <geom name="neck" type="capsule" fromto="0 0 0 0.18 0 0.24" size="0.06" density="1000" contype="0" conaffinity="1"/>
        <body name="head" pos="0.18 0 0.24">
          <geom name="head" type="capsule" fromto="0 0 0 0.24 0 -0.1" size="0.07" density="1000" contype="0" conaffinity="1"/>
        </body>
      </body>
      <body name="fl-upper" pos="0.28 0.1 -0.05">
        <joint name="fl-upper" type="hinge" axis="0 1 0" limited="true" range="-0.785398 0.785398" damping="5"/>
        <geom name="fl-upper" type="capsule" fromto="0 0 0 0 0 -0.38" size="0.05" density="1000" contype="0" conaffinity="1"/>
        <body name="fl-lower" pos="0 0 -0.38">
          <joint name="fl-lower" type="hinge" axis="0 1 0" limited="true" range="-0.174533 1.745329" damping="5"/>
          <geom name="fl-lower" type="capsule" fromto="0 0 0 0 0 -0.37" size="0.04" density="1000" contype="0" conaffinity="1"/>
        </body>
      </body>
      … fr-upper / fr-lower …
      <body name="hl-upper" pos="-0.28 0.1 -0.05">
        <joint name="hl-upper" type="hinge" axis="0 1 0" limited="true" range="-0.785398 0.785398" damping="5"/>
        <geom name="hl-upper" type="capsule" fromto="0 0 0 0 0 -0.38" size="0.05" density="1000" contype="0" conaffinity="1"/>
        <body name="hl-lower" pos="0 0 -0.38">
          <joint name="hl-lower" type="hinge" axis="0 1 0" limited="true" range="-1.745329 0.174533" damping="5"/>
          <geom name="hl-lower" type="capsule" fromto="0 0 0 0 0 -0.37" size="0.04" density="1000" contype="0" conaffinity="1"/>
        </body>
      </body>
      … hr-upper / hr-lower …
    </body>
  </worldbody>
  <actuator>
    <position name="neck" joint="neck" kp="150" ctrllimited="true" ctrlrange="-0.698132 0.523599"/>
    <position name="fl-upper" joint="fl-upper" kp="150" ctrllimited="true" ctrlrange="-0.785398 0.785398"/>
    <position name="fl-lower" joint="fl-lower" kp="150" ctrllimited="true" ctrlrange="-0.174533 1.745329"/>
    <position name="fr-upper" joint="fr-upper" kp="150" ctrllimited="true" ctrlrange="-0.785398 0.785398"/>
    <position name="fr-lower" joint="fr-lower" kp="150" ctrllimited="true" ctrlrange="-0.174533 1.745329"/>
    <position name="hl-upper" joint="hl-upper" kp="150" ctrllimited="true" ctrlrange="-0.785398 0.785398"/>
    <position name="hl-lower" joint="hl-lower" kp="150" ctrllimited="true" ctrlrange="-1.745329 0.174533"/>
    <position name="hr-upper" joint="hr-upper" kp="150" ctrllimited="true" ctrlrange="-0.785398 0.785398"/>
    <position name="hr-lower" joint="hr-lower" kp="150" ctrllimited="true" ctrlrange="-1.745329 0.174533"/>
  </actuator>
</mujoco>
```

Template rules behind it:
- `model` = `(name kind)`. Names of body, joint, geom and actuator = `(name body-name)`.
- Plane: `pos` = spawn `:ground`, `size` = `"<g> <g> 0.1"` with g = `ground-half`.
- Root: `pos` = spawn `:torso`, `quat` = spawn `:quat`, then `<freejoint name="root"/>`.
- Non-root `pos` = `:at`. Joint `axis` = `:axis`, `range` = radians, `damping` = `:damping`.
- Geom: `fromto` = from ++ to, `size` = r, `density` = `:density`. Body geoms are always
  `contype="0" conaffinity="1"` and the plane is `contype="1" conaffinity="0"`, so the body
  collides with the ground only and never with itself.
- Actuator `kp` = `:kp`, `ctrlrange` = the joint's radians.

### 4.7 Drawing parts (pm.plan look-part style, three.js frame)

**`(body-part sp body)`** → one part, in the **body's own frame converted to three.js**, in
`pm.plan`'s part shape but with `:color` resolved to the species' hex int:
`{:shape :capsule :size [(+ L (* 2 r)) (* 2 r) (* 2 r)] :at (mj->three midpoint) :rot [0 b c] :color hex}`.
- `L` = |to − from|, midpoint = (from + to) / 2, and d = `(mj->three (to − from)) / L`, a unit
  vector `[dx dy dz]`.
- `pm.three/stand-in` builds a capsule along its local +x with total length `sx` and diameter
  `sy`. Euler XYZ `[0 b c]` maps +x to `(cos b · cos c, sin c, −sin b · cos c)`, so
  `c = asin(clamp(dy, −1, 1))` and `b = atan2(−dz, dx)`, or `b = 0` when `|dx| < 1e-9` and
  `|dz| < 1e-9` (a vertical capsule; this avoids `atan2(0, −0) = π`). `b` and `c` are returned in
  **degrees** (the plan convention; the interop converts with `pm.plan/radians`).

**`(body-parts sp)`** → `[{:name kw :id (inc i) :part (body-part sp body)} ...]` in `bodies` order.

---

## 5. `pm.creature-motion` (src/pm/creature_motion.cljs), pure

Requires `pm.creature` and `pm.motion` (for `short-reason`). It uses `js/Math` and
`js/URLSearchParams` only. No atoms, no three.js, no species names.

### 5.1 Spike config and URL

Estate entry (builder adds it to `resources/estate.edn`, top level, with a comment):

```clojure
 :creature-spike {:flag "pony" :kind :horse :seed 1     ; ?pony[=<kind>][&seed=n]: MuJoCo creature spike (pony-spike-v0)
                  :at [4.5 1.79 -8.0] :yaw 0 :drop-m 0.4} ; east bank, terrain 1.79 m (Hal probed); yaw in degrees
```

**`(spike-config world)`** → `{:flag s :kind kw :seed int :at [x y z] :yaw deg :drop-m m}` or
`nil`. It returns `nil` unless `(:creature-spike world)` is a map with a non-blank string
`:flag`, a keyword `:kind` and an `:at` of 3 numbers. Defaults: `:seed` 1 (when not an integer),
`:yaw` 0 (when not a number), `:drop-m` 0.4 (when not a number ≥ 0). Other keys are dropped.

**`(spike-request search cfg)`** → `nil` when `cfg` is nil or the search string lacks the
`(:flag cfg)` param (`js/URLSearchParams`; `search` may be nil or ""). Otherwise
`(assoc cfg :kind k :seed s)`:
- `k` = `(keyword v)` for a non-blank flag value `v`, else `(:kind cfg)`.
- `s` = `(js/parseInt seed-param 10)` when the `seed` param is present and parses to a finite
  number, else `(:seed cfg)`.

### 5.2 The controller interface (how this fits pm.motion's pattern)

`pm.motion` is pure rules over a plain walker map. `pm.bricks` is the one interop namespace with
`defonce !state`, `start! [h world]`, `tick!`, `hud-segment` and `status`. The thing that moves a
walker is a swappable **driver**: kinematic `m/step` or MotionBricks `m/follow`, chosen by
`(:mode status)`. `fallback!` swaps one for the other without anything else knowing. The driver's
output is a small command (`m/steer-command` → `[:move p]` / `[:stop]`), and only the interop
applies it to the engine.

The spike mirrors that one-to-one:

| pm.motion / pm.bricks | pm.creature-motion / pm.creature-spike |
|---|---|
| walker map (plain data) | controller map `{:kind kw :n nu ...}` (plain data) |
| driver `m/step` / `m/follow` | **`act`**, which dispatches on `(:kind ctrl)` |
| `m/steer-command` → `[:move p]` | `act` → `actions`: `nu` numbers in [−1, 1], one per actuator in `joints` order |
| `apply-command!` (interop) | `actions->ctrl` (pure) + writing `data.ctrl` (interop) |
| `m/hud-text status` | `hud-text status` |
| `start!` / `tick!` / `hud-segment` | the same three, called from `pm.core` |

**`(act ctrl obs dt)`** → `[ctrl' actions]`. It is called **once per physics step** with
`dt = timestep` (never with frame dt), so a flop replays exactly from its seed whatever the frame
rate. `obs` is the seam for a trained policy ("only what the body can feel": joint angles, tilt,
foot contact, built by the interop from MjData). The twitch controller ignores it, and the spike
passes `nil`.
- `:twitch` → `(let [c (twitch-step ctrl dt)] [c (twitch-actions c)])`
- any other kind → `[ctrl (vec (repeat (:n ctrl) 0))]`

A trained policy later is one new `:kind :policy` branch plus its constructor. The interop, the
stepping, the actuator mapping, the drawing and the HUD stay untouched. If its inference is
async (onnxruntime-web), the controller map holds the last action and the interop refreshes it
out of band, the way `pm.bricks` gates MotionBricks generations. Later still, a physics creature
can join the walkers: its torso xz feeds `m/follow` exactly as a MotionBricks character's
position does, so routes, avoidance and senses apply unchanged. That is not in this round.

**`(controller kind n seed)`** → `:twitch` → `(twitch-init n seed)`; anything else →
`{:kind kind :n n}`.

**`(actions->ctrl actions ranges)`** → `(mapv (fn [a [lo hi]] (+ (/ (+ lo hi) 2) (* (clamp a -1 1) (/ (- hi lo) 2)))) actions ranges)`,
which maps −1 → lo, 0 → mid-range and 1 → hi, after clamping a to [−1, 1].

### 5.3 The twitch noise

Seeded Park–Miller ("minimal standard") PRNG. It is exact in doubles: 16807 · (2³¹ − 1) < 2⁵³.

- **`(rng-seed s)`** → `(inc (mod (js/Math.floor (js/Math.abs s)) 2147483646))`, an integer in
  [1, 2147483646].
- **`(rng-next x)`** → `[x' u]`, where `x' = (mod (* 16807 x) 2147483647)` and `u = (/ x' 2147483647)`.

Each actuator is a **channel with its own stream**, so channels never steal each other's draws
and the result does not depend on how time is sliced. Channel state:
`{:v value :from a :to b :t elapsed :dur d :rng x}`.

**`(twitch-init n seed)`** → `{:kind :twitch :n n :chans [ch0 ... ch(n-1)]}`. Channel `i`:
`x = (rng-seed (+ (* seed 100) i))`, then discard 2 draws (warm-up). Then draw `u1` →
`:to (- (* 2 u1) 1)` and draw `u2` → `:dur (+ 0.3 (* 0.5 u2))`. Start with `:v 0 :from 0 :t 0`
and `:rng` = the state after those 4 draws.

**`(twitch-step ctrl dt)`** → if `dt` is not a finite number > 0, return `ctrl` unchanged (`=`).
Otherwise, for each channel independently:
1. `t ← t + dt`
2. while `t ≥ dur`: `from ← to`, `t ← t − dur`, draw u1 → `to ← 2u1 − 1`, draw u2 → `dur ← 0.3 + 0.5u2`
3. `v ← from + (to − from) · s(t / dur)` with smoothstep `s(u) = u²(3 − 2u)`

`v` is a convex mix of two targets in [−1, 1], so it is always in [−1, 1], and `dur` is always in
[0.3, 0.8].

**`(twitch-actions ctrl)`** → `(mapv :v (:chans ctrl))`.

### 5.4 Fixed physics step

```clojure
(def step-s pm.creature/timestep)   ; 0.002
(def max-steps 25)
```

**`(fixed-steps acc dt)`** = `(fixed-steps acc dt step-s max-steps)`.
**`(fixed-steps acc dt step max)`** → `{:steps s :dropped d :acc a}`:
- `acc` and `dt` count as 0 unless they are finite numbers > 0. `total = acc + dt`.
- `n = floor(total / step + 1e-9)`, `s = min(n, max)`, `d = n − s`.
- `a = max(0, total − n · step)`. The time of the dropped steps is thrown away; only the
  fractional remainder carries.

### 5.5 Stats and HUD

```clojure
(def empty-stats {:since nil :sum 0 :n 0 :ms nil :steps 0 :dropped 0})
```

**`(stats-tick stats now-ms frame-ms steps dropped)`** → stats':
- `since ← (or since now-ms)`, `sum ← sum + frame-ms`, `n ← n + 1`, `steps ← steps` (this
  frame's), `dropped ← dropped + dropped` (cumulative).
- Then, if `now-ms − since ≥ 1000`: `ms ← sum / n`, `sum ← 0`, `n ← 0`, `since ← now-ms`.
  Otherwise `ms` keeps its previous value.

**`(hud-text status)`** → a string, or `nil` when `status` is not a map. `status` is
`{:mode m :flag s :kind kw ...}`, and `(name kind)` is printed:
- `:loading` → `"<flag>: <kind> loading"`
- `:running` → `"<flag>: <kind> · <ms> ms/frame · <steps> steps · <dropped> dropped · torso <h> m"`,
  where `<ms>` = `(.toFixed ms 1)` or `-` when `(:ms (:stats status))` is nil, `<steps>` and
  `<dropped>` come from `(:stats status)`, and `<h>` = `(.toFixed torso 2)` from `(:torso status)`
  or `-` when nil.
- `:failed` → `"<flag>: <kind> failed (<reason>)"`, with `<reason>` = `(pm.motion/short-reason (:reason status))`.
- any other mode → `nil`.

### 5.6 Pose from MuJoCo arrays

**`(body-pose xpos xquat id)`** → `{:pos (mj->three [xpos[3id] xpos[3id+1] xpos[3id+2]]) :quat (mj-quat->three [xquat[4id] … xquat[4id+3]])}`.
It reads with `aget`, so it works on JS typed arrays and plain JS arrays. `xpos` is the body
frame's origin (not its centre of mass), which is what the parts are placed relative to.

---

## 6. Interop (builder only; not unit-tested)

### 6.1 `pm.three/set-quat!`

```clojure
(defn set-quat! [^js obj [x y z w]] (.set (.-quaternion obj) x y z w) obj)
```

### 6.2 `pm.creature-spike` (src/pm/creature_spike.cljs)

```clojure
(defonce !state (atom {:status nil :started? false}))
;; when running, also: :mj :model :data :sp :ranges :ground-z :ctrl :acc :stats :root :groups
```

- **`(start! h world)`** is a no-op when already started or when
  `(cm/spike-request (.. js/window -location -search) (cm/spike-config world))` is nil. **Without
  the flag nothing is imported, built or shown, and the game is unchanged.** Otherwise:
  set `:started? true` and status `{:mode :loading :flag :kind}`, look up
  `(get pm.species/texts kind)`, and if it is missing, fail with reason
  `(str "no species " kind)`. Parse it (`c/parse`), build `(c/mjcf sp req)`, then
  `((js/Function. "u" "return import(u)") "mujoco")` → `(.default mod)` → `mj`:
  `(.from_xml_string (.-MjModel mj) xml)` → `(new (.-MjData mj) model)`. Build the view: a
  `t/group` named `"pm-creature"` added to `(.-scene h)`, holding one `t/group` per
  `c/body-parts` entry (named `"pm-creature/<body>"`), each holding one `t/stand-in` from its
  part (`:rot` through `pm.plan/radians`). Then `:ctrl (cm/controller :twitch (count ranges) seed)`,
  `:acc 0`, `:stats cm/empty-stats`, `:ground-z` = spawn ground z, and status `:running`. Every
  promise step `.catch`es into `fail!`.
- **`(tick! dt now-ms)`** runs only when `:running`, inside `try`/`catch` → `fail!`.
  `{:keys [steps dropped acc]} (cm/fixed-steps acc dt)`. Time the loop with
  `js/performance.now`. Each step: `[ctrl actions] (cm/act ctrl nil c/timestep)`, write
  `(cm/actions->ctrl actions ranges)` into `(.-ctrl data)` with `aset`, then
  `(.mj_step mj model data)`. After the loop, re-read `(.-xpos data)` and `(.-xquat data)` (do
  not cache typed-array views across frames; the WASM heap can grow), then `t/set-pos!` /
  `t/set-quat!` each body group from `cm/body-pose` with id 1..n. Torso height =
  `xpos[5] − ground-z`. Update `:stats` with `cm/stats-tick` and `:status`
  `{:mode :running :flag :kind :stats :torso}`. **Reuse the groups every frame; never rebuild.**
- **`(fail! e)`** → `(.delete data)` then `(.delete model)` if they exist (each in its own
  `try`), remove the root group, drop `:model`/`:data` from the state, and set status
  `{:mode :failed :flag :kind :reason (or (.-message e) (str e))}`. `js/console.warn` once.
- **`(hud-segment)`** → `(cm/hud-text (:status @!state))`, which is `nil` before start.
- Every MuJoCo object that is created is deleted when it is replaced or on failure. In v0 nothing
  is replaced, because hot reload keeps the running sim (`defonce`, and `start!` is a no-op).

### 6.3 `pm.core` wiring (exactly three additions)

1. `start-kit!`: after `(bricks/start! h @!world)` → `(creature-spike/start! h @!world)`.
2. `frame`: after the `bricks/tick!` line → `(when (kit/handle) (creature-spike/tick! (min dt 0.1) now))`.
3. `hud-mirrors-suffix`: after the bricks segment →
   `(when-let [s (creature-spike/hud-segment)] (str " · " s))`.

Scaffold mode (no kit) never starts the spike.

### 6.4 `bb creature-mjcf <kind>`

`bb.edn` task:
`creature-mjcf {:doc "Print a species' MuJoCo body (MJCF XML): bb creature-mjcf <kind>" :task (apply shell "bb" "scripts/creature_mjcf.clj" *command-line-args*)}`.
`scripts/creature_mjcf.clj` reads `resources/creatures/<kind>.edn`, then
`(print (c/mjcf (c/parse text) c/origin))`. When the file is missing or `<kind>` is not given, it
prints `usage: bb creature-mjcf <kind>  (no resources/creatures/<kind>.edn)` to stderr and exits
1. A bad species prints the `ex-message` to stderr and exits 1. The builder runs
`bb creature-mjcf horse | head -6` and `bb creature-mjcf dog | head -6` once. Expected: the plane
at `pos="0 0 0"` and the torso at `pos="0 0 1.24"` (horse) or `pos="0 0 0.84"` (dog).

---

## 7. Test cases

All in cljs under the `:test` build (`node out/test.js`). Load the species with
`(c/parse (get pm.species/texts :horse))` and `(… :dog)`, called `horse` and `dog` below.
"≈" means `|a − b| ≤ 1e-6` unless stated. For vectors, ≈ applies element-wise. `-0` equals `0`
under `=` in cljs.

### 7.1 `test/pm/creature_test.cljs` (pm.creature)

- **C1** `(c/problems horse)` = `[]` and `(c/problems dog)` = `[]`. `(:kind horse)` = `:horse`,
  `(:kind dog)` = `:dog`, and `(set (keys pm.species/texts))` = `#{:horse :dog}`.
- **C2** `(c/problems (assoc horse :density 0))` = `[":density must be a positive number"]`.
- **C3** `(c/problems (assoc-in horse [:bodies 4 :parent] :fl-uper))` =
  `["body :fl-lower: unknown parent :fl-uper"]`. `(c/parse (pr-str that))` throws, and
  `ex-message` = `"bad species :horse: body :fl-lower: unknown parent :fl-uper"`, with
  `(:problems (ex-data e))` = that same vector.
- **C4** `(c/problems (assoc-in dog [:bodies 3 :joint :range] [45 -45]))` =
  `["body :fl-upper: :joint needs a non-zero :axis [x y z] and :range [lo hi] with -180 <= lo < hi <= 180"]`.
  The same text comes back for `[:bodies 3 :joint :axis]` set to `[0 0 0]`.
- **C5** `(c/problems (assoc-in horse [:bodies 2 :color] :pink))` = `["body :head: :color :pink is not in :colors"]`.
- **C6** `(c/problems (update horse :bodies conj (get-in horse [:bodies 1])))` = `["body :neck: duplicate name"]`.
- **C7** `(c/problems (assoc-in horse [:bodies 0 :joint] {:axis [0 1 0] :range [-10 10]}))` =
  `["root :torso must have no :parent, :at or :joint"]`.
- **C8** `(c/parse "{:kind")` throws with an `ex-message` that starts with `"bad species: not EDN"`.
  `(c/parse "[1 2]")` throws `"bad species: not a map"`. `(c/problems nil)` is a non-empty vector
  whose first item is `":kind must be a keyword"`.
- **C9** `(c/problems (assoc horse :bodies []))` = `[":bodies must be a non-empty vector"]`.
  `(c/problems (assoc horse :bodies [(get-in horse [:bodies 0])]))` = `["needs at least one :joint"]`.
- **C10** `(c/problems (assoc-in horse [:bodies 3 :capsule :to] [0 0 0]))` =
  `["body :fl-upper: :capsule needs :from, :to ([x y z], different) and :r > 0"]`. The same text
  comes back for `:r` = 0.
- **C11** `(c/problems (-> dog (assoc :density -1) (assoc :kp 0)))` =
  `[":density must be a positive number" ":kp must be a positive number"]` (all problems, in order).
- **C12** `fmt`: `8.0` → `"8"`, `3.0300000000000002` → `"3.03"`, `-0.6981317007977318` →
  `"-0.698132"`, `-0.0` → `"0"`, `-1e-7` → `"0"`, `1.5` → `"1.5"`, `0.7071067811865476` → `"0.707107"`.
- **C13** `(mapv :name (c/bodies horse))` = `[:torso :neck :head :fl-upper :fl-lower :fr-upper :fr-lower :hl-upper :hl-lower :hr-upper :hr-lower]`, and the same for dog. **DFS, not file order:** for the fixture `f` below,
  `(mapv :name (c/bodies f))` = `[:root :a :a2 :b]`:
  ```clojure
  (def f {:kind :fixture :density 1000 :kp 10 :damping 1 :colors {:c 0x808080}
          :bodies [{:name :root :capsule {:from [0 0 0] :to [0.1 0 0] :r 0.05} :color :c}
                   {:name :a :parent :root :at [0 0 0] :joint {:axis [0 1 0] :range [-10 10]}
                    :capsule {:from [0 0 0] :to [0 0 -0.1] :r 0.02} :color :c}
                   {:name :b :parent :root :at [0 0 0]
                    :capsule {:from [0 0 0] :to [0 0.2 0] :r 0.05} :color :c}
                   {:name :a2 :parent :a :at [0 0 -0.1] :joint {:axis [0 1 0] :range [0 90]}
                    :capsule {:from [0 0 0] :to [0 0 -0.1] :r 0.02} :color :c}]})
  ```
  `(c/problems f)` = `[]`. `(mapv :name (c/joints f))` = `[:a :a2]`.
- **C14** `(mapv :name (c/joints horse))` = `[:neck :fl-upper :fl-lower :fr-upper :fr-lower :hl-upper :hl-lower :hr-upper :hr-lower]`.
  `(c/ctrl-ranges horse)` ≈ `[[-0.698132 0.523599] [-0.785398 0.785398] [-0.174533 1.745329] [-0.785398 0.785398] [-0.174533 1.745329] [-0.785398 0.785398] [-1.745329 0.174533] [-0.785398 0.785398] [-1.745329 0.174533]]`.
  The dog's ranges are identical. `(:axis (first (c/joints dog)))` = `[0 1 0]`.
- **C15** `(c/counts horse)` = `(c/counts dog)` = `{:nbody 12 :njnt 10 :nu 9 :nq 16 :nv 15 :ngeom 12}`.
  `(c/counts f)` = `{:nbody 5 :njnt 3 :nu 2 :nq 9 :nv 8 :ngeom 5}`.
- **C16** `(c/body-masses horse)` ≈ `[[:torso 56.548668] [:neck 4.297699] [:head 5.439144] [:fl-upper 3.508112] [:fl-lower 2.127905] [:fr-upper 3.508112] [:fr-lower 2.127905] [:hl-upper 3.508112] [:hl-lower 2.127905] [:hr-upper 3.508112] [:hr-lower 2.127905]]`
  (names `=`, kg ≈). The sum ≈ 88.829579.
- **C17** `(c/body-masses dog)` ≈ `[[:torso 18.011798] [:neck 1.335962] [:head 1.544616] [:fl-upper 0.949285] [:fl-lower 0.622035] [:fr-upper 0.949285] [:fr-lower 0.622035] [:hl-upper 0.949285] [:hl-lower 0.622035] [:hr-upper 0.949285] [:hr-lower 0.622035]]`.
  The sum ≈ 27.177656. Every mass > 0 for both. With `(assoc dog :density 2000)`, the torso ≈
  36.023596 (density is used).
- **C18** `(c/rest-bottom horse)` ≈ −0.84, `(c/rest-bottom dog)` ≈ −0.44, `(c/rest-bottom f)` ≈ −0.22
  (a2: −0.1 − 0.1 − 0.02).
- **C19** `(c/spawn horse {:at [4.5 1.79 -8.0] :yaw 0 :drop-m 0.4})` ≈
  `{:ground [4.5 8 1.79] :torso [4.5 8 3.03] :quat [1 0 0 0]}`. For the dog, `:torso` ≈
  `[4.5 8 2.63]`. `(c/spawn dog {:at [0 0 0] :yaw 90 :drop-m 0.4})` ≈
  `{:ground [0 0 0] :torso [0 0 0.84] :quat [0.707107 0 0 0.707107]}`. Extra keys in the
  placement (`:flag`, `:seed`) change nothing.
- **C20** `x = (c/mjcf horse {:at [4.5 1.79 -8.0] :yaw 0 :drop-m 0.4})`. Counts of substrings in
  `x`: `"<body "` 11, `"<joint "` 9, `"<freejoint "` 1, `"<position "` 9, `"<geom "` 12,
  `"type=\"plane\""` 1. `x` starts with `"<mujoco model=\"horse\">\n"` and ends with
  `"</mujoco>\n"`. It contains each of these lines (trimmed):
  `<option timestep="0.002" integrator="implicitfast"/>`,
  `<geom name="ground" type="plane" pos="4.5 8 1.79" size="2 2 0.1" contype="1" conaffinity="0"/>`,
  `<body name="torso" pos="4.5 8 3.03" quat="1 0 0 0">`,
  `<joint name="hl-lower" type="hinge" axis="0 1 0" limited="true" range="-1.745329 0.174533" damping="5"/>`,
  `<geom name="head" type="capsule" fromto="0 0 0 0.24 0 -0.1" size="0.07" density="1000" contype="0" conaffinity="1"/>`,
  `<position name="neck" joint="neck" kp="150" ctrllimited="true" ctrlrange="-0.698132 0.523599"/>`.
  The trimmed line after `<body name="head" pos="0.18 0 0.24">` starts with `<geom ` (no joint
  for a welded body). Every line of the §4.6 template that is shown in full appears in `x`.
- **C21** `y = (c/mjcf dog {:at [4.5 1.79 -8.0] :yaw 0 :drop-m 0.4})`, with the same tag counts as
  C20. It contains `<mujoco model="dog">`, `<body name="torso" pos="4.5 8 2.63" quat="1 0 0 0">`,
  `<geom name="fl-upper" type="capsule" fromto="0 0 0 0 0 -0.2" size="0.035" density="1000" contype="0" conaffinity="1"/>`,
  `<joint name="neck" type="hinge" axis="0 1 0" limited="true" range="-0.698132 0.523599" damping="1.5"/>`
  and `<position name="hr-lower" joint="hr-lower" kp="40" ctrllimited="true" ctrlrange="-1.745329 0.174533"/>`.
- **C22** `(c/mjcf dog {:at [0 0 0] :yaw 90 :drop-m 0.4})` contains
  `<geom name="ground" type="plane" pos="0 0 0" size="2 2 0.1" contype="1" conaffinity="0"/>` and
  `<body name="torso" pos="0 0 0.84" quat="0.707107 0 0 0.707107">`. `(c/mjcf horse c/origin)`
  contains `<body name="torso" pos="0 0 1.24" quat="1 0 0 0">`.
- **C23** `(c/mjcf (assoc horse :kp -1) c/origin)` throws `ex-info` with message
  `"bad species :horse: :kp must be a positive number"`.
- **C24** `(c/mj->three [1 2 3])` = `[1 3 -2]`, `(c/three->mj [1 3 -2])` = `[1 2 3]`,
  `(c/mj->three [0 0 1])` = `[0 1 0]` (z-up → y-up), `(c/mj->three [0 1 0])` = `[0 0 -1]`
  (MuJoCo left → three −z), and `(c/three->mj [4.5 1.79 -8.0])` = `[4.5 8.0 1.79]`.
  Round trip: `(c/three->mj (c/mj->three [0.3 -0.7 2.5]))` = `[0.3 -0.7 2.5]`.
- **C25** `(c/mj-quat->three [1 0 0 0])` = `[0 0 0 1]`. `(c/mj-quat->three [0.7 0.1 0.2 0.3])` =
  `[0.1 0.3 -0.2 0.7]`. A MuJoCo yaw of 90° `[0.707107 0 0 0.707107]` →
  `[0 0.707107 0 0.707107]` (about three +y). A MuJoCo pitch about +y `[0.8 0 0.6 0]` →
  `[0 0 -0.6 0.8]` (about three −z).
- **C26** `body-part` (sizes and `:at` ≈, `:rot` ≈ to 1e-6 degrees):
  - horse torso (`(get-in horse [:bodies 0])`) → `{:shape :capsule :size [0.9 0.3 0.3] :at [0 0 0] :rot [0 0 0] :color 0x9b6b43}`
  - horse fl-upper → `:size [0.48 0.1 0.1] :at [0 -0.19 0] :rot [0 0 -90] :color 0x9b6b43`
  - horse fl-lower → `:size [0.45 0.08 0.08] :at [0 -0.185 0] :rot [0 0 -90] :color 0x2e2622`
  - horse neck → `:size [0.42 0.12 0.12] :at [0.09 0.12 0] :rot [0 0 53.130102] :color 0x4a3426`
  - horse head → `:size [0.4 0.14 0.14] :at [0.12 -0.05 0] :rot [0 0 -22.619865] :color 0x9b6b43`
  - fixture `f` body `:b` (a capsule pointing MuJoCo +y, left) → `:size [0.3 0.1 0.1] :at [0 0 -0.1] :rot [0 90 0] :color 0x808080`
- **C27** `(c/body-parts dog)` has count 11, `(mapv :id …)` = `[1 2 3 4 5 6 7 8 9 10 11]`, and
  `(mapv :name …)` = the C13 order. The first entry's `:part` is
  `{:shape :capsule :size [0.64 0.2 0.2] :at [0 0 0] :rot [0 0 0] :color 0xc49a6c}` (≈), the
  `:fl-upper` part is `:size [0.27 0.07 0.07] :at [0 -0.1 0] :rot [0 0 -90]`, and the `:head`
  part's `:color` = `0x6b4a32`.

### 7.2 `test/pm/creature_motion_test.cljs` (pm.creature-motion, alias `cm`)

`cfg` = `{:flag "pony" :kind :horse :seed 1 :at [4.5 1.79 -8.0] :yaw 0 :drop-m 0.4}`.

- **M1** `(cm/spike-config (world/load-estate world/estate-edn))` = `cfg` (the estate entry
  exists as specified). `(cm/spike-config {})` = nil. `(cm/spike-config {:creature-spike {:flag "x" :kind :k :at [1 2]}})` = nil.
  `(cm/spike-config {:creature-spike {:flag "x" :kind :k :at [1 2 3] :seed "a" :yaw nil :drop-m -1 :junk 1}})` =
  `{:flag "x" :kind :k :seed 1 :at [1 2 3] :yaw 0 :drop-m 0.4}`.
- **M2** `spike-request`:
  `"?pony"` → `cfg`; `"?pony=dog"` → `(assoc cfg :kind :dog)`; `"?pony=horse&seed=3"` →
  `(assoc cfg :seed 3)`; `"?seed=3"` → nil; `""` → nil; `nil` → nil;
  `"?pony=dog&seed=abc"` → `(assoc cfg :kind :dog)`; `"?pony=&seed=-4"` → `(assoc cfg :seed -4)`;
  `"?pony=dog&seed=2.9"` → `(assoc cfg :kind :dog :seed 2)`; `(cm/spike-request "?pony" nil)` → nil.
- **M3** `fixed-steps` (`:acc` ≈ to 1e-9):
  | acc | dt | steps | dropped | acc' |
  |---|---|---|---|---|
  | 0 | 0.016 | 8 | 0 | 0 |
  | 0 | 0.0167 | 8 | 0 | 0.0007 |
  | 0.0007 | 0.0167 | 8 | 0 | 0.0014 |
  | 0.0014 | 0.0167 | 9 | 0 | 0.0001 |
  | 0 | 0.1 | 25 | 25 | 0 |
  | 0.0015 | 0.1 | 25 | 25 | 0.0015 |
  | 0 | 0.05 | 25 | 0 | 0 |
  | 0 | 0.052 | 25 | 1 | 0 |
  | 0 | 0.001 | 0 | 0 | 0.001 |
  | 0.001 | 0.001 | 1 | 0 | 0 |
  | 0.0005 | 0 | 0 | 0 | 0.0005 |
  | 0.0005 | −0.01 | 0 | 0 | 0.0005 |
  | 0.0005 | `##NaN` | 0 | 0 | 0.0005 |
  | nil | 0.004 | 2 | 0 | 0 |

  `(cm/fixed-steps 0 0.01 0.005 1)` → `{:steps 1 :dropped 1 :acc 0}`. `cm/step-s` = 0.002 and
  `cm/max-steps` = 25.
- **M4** `(cm/rng-seed 1)` = 2, `(cm/rng-seed 0)` = 1, `(cm/rng-seed -3)` = 4, `(cm/rng-seed 3.7)` = 4,
  `(cm/rng-seed 2147483647)` = 2. `(cm/rng-next 2)` → `[33614 u]` with u ≈ 0.0000156528 (1e-9).
  `(first (cm/rng-next 33614))` = 564950498 and its u ≈ 0.263075576. `(first (cm/rng-next 564950498))` = 1097816499.
- **M5** `(cm/twitch-init 2 1)`: `:kind :twitch`, `:n 2`, and channels (≈ 1e-9):
  ch0 `{:v 0 :from 0 :t 0 :to -0.3677249166 :dur 0.4618316621 :rng 695061696}`,
  ch1 `{:v 0 :from 0 :t 0 :to -0.8565142722 :dur 0.6911567281 :rng 1680005354}` (`:rng` exact).
  `(cm/twitch-init 2 3)` gives `:to` ≈ −0.1255960386 and −0.6143853942, and `:dur` ≈
  0.3268448545 and 0.5561699204. `(cm/twitch-init 1 0)` ch0: `:to` ≈ 0.5112106444, `:dur` ≈
  0.5293250660, `:rng` 984943658.
- **M6** Easing on a hand-built state `h = {:kind :twitch :n 1 :chans [{:v 0 :from 0 :to 0.8 :t 0 :dur 0.5 :rng 2}]}`.
  `(first (:chans (cm/twitch-step h dt)))`:
  | dt | :v | :from | :to | :t | :dur | :rng |
  |---|---|---|---|---|---|---|
  | 0.125 | 0.125 (s(¼)=0.15625) | 0 | 0.8 | 0.125 | 0.5 | 2 |
  | 0.25 | 0.4 (s(½)=½) | 0 | 0.8 | 0.25 | 0.5 | 2 |
  | 0.375 | 0.675 (s(¾)=0.84375) | 0 | 0.8 | 0.375 | 0.5 | 2 |
  | 0.5 | 0.8 (rolled, t=0) | 0.8 | −0.9999686945 | 0 | 0.4315377881 | 564950498 |
  | 0.6 | 0.5548289402 | 0.8 | −0.9999686945 | 0.1 | 0.4315377881 | 564950498 |
  | 1.0 | −0.9764934631 (rolled twice) | −0.9999686945 | 0.0224212888 | 0.0684622119 | 0.7586501319 | 1969887316 |

  Values are ≈ 1e-9 and `:rng` is exact. The 0.5 row is hand-checkable: from x=2, the draws are
  33614 (u = 1.5653e-5, so to = −0.99997) and 564950498 (u = 0.26308, so dur = 0.43154).
- **M7** `(cm/twitch-step c 0)`, `(… c -0.1)` and `(… c ##NaN)` all return `c` (`=`), for
  `c = (cm/twitch-init 3 1)`.
- **M8** Repeatable: two separate `(cm/twitch-init 9 3)`, each stepped 1000 times by 0.002, are `=`.
  `(cm/twitch-init 9 1)` and `(cm/twitch-init 9 3)` differ in ch0's `:to`.
- **M9** Range: `(cm/twitch-init 9 7)` stepped 5000 × 0.002 (10 s). After every step, every `:v` is
  in [−1, 1] and every `:dur` is in [0.3, 0.8]. At the end, every channel's `:to` differs from
  its initial `:to` (each channel has re-targeted). Observed: min v −0.9948, max v 0.9992.
- **M10** Step-size independence: `(cm/twitch-init 3 5)` stepped 1000 × 0.002 versus one step of
  2.0 gives the same `:rng` per channel (exact: 603745413, 463499475, 572165128) and `:v` ≈ 1e-9
  (−0.2333254991, −0.0405640359, 0.4035366848).
- **M11** `act`: `[c' a] = (cm/act (cm/twitch-init 2 1) nil 0.3)` → `a` = `(cm/twitch-actions c')` ≈
  `[-0.2639112316 -0.3440240828]`, and `c'` = `(cm/twitch-step (cm/twitch-init 2 1) 0.3)`.
  `(cm/controller :twitch 2 1)` = `(cm/twitch-init 2 1)`.
  `(cm/act (cm/controller :still 3 1) nil 0.002)` = `[{:kind :still :n 3} [0 0 0]]`.
- **M12** `(cm/actions->ctrl a [[-0.5 1.5] [0 2]])`: `a = [-1 1]` → `[-0.5 2]`; `[0 0]` → `[0.5 1]`;
  `[2 -3]` → `[1.5 0]` (clamped); `[0.5 -0.5]` → `[1 0.5]`. With the horse's real ranges,
  `(cm/actions->ctrl (vec (repeat 9 0)) (c/ctrl-ranges horse))` first ≈ −0.087266 (the neck's
  mid-range, −5°).
- **M13** `stats-tick` chain from `cm/empty-stats`:
  1. `(… s0 0 0.8 8 0)` → `{:since 0 :sum 0.8 :n 1 :ms nil :steps 8 :dropped 0}`
  2. `(… s1 500 1.0 9 0)` → `{:since 0 :sum 1.8 :n 2 :ms nil :steps 9 :dropped 0}`
  3. `(… s2 1000 0.9 8 2)` → `{:since 1000 :sum 0 :n 0 :ms 0.9 :steps 8 :dropped 2}` (`:ms` ≈)
  4. `(… s3 1200 2.0 25 25)` → `{:since 1000 :sum 2.0 :n 1 :ms 0.9 :steps 25 :dropped 27}`
- **M14** `hud-text`:
  - `{:mode :running :flag "pony" :kind :horse :stats {:ms 0.94 :steps 8 :dropped 0} :torso 0.3149}` → `"pony: horse · 0.9 ms/frame · 8 steps · 0 dropped · torso 0.31 m"`
  - `{:mode :running :flag "pony" :kind :dog :stats {:ms 1.26 :steps 9 :dropped 3} :torso 0.4392}` → `"pony: dog · 1.3 ms/frame · 9 steps · 3 dropped · torso 0.44 m"`
  - `:stats {:ms nil :steps 0 :dropped 0}`, `:torso nil` (horse) → `"pony: horse · - ms/frame · 0 steps · 0 dropped · torso - m"`
  - `{:mode :loading :flag "pony" :kind :dog}` → `"pony: dog loading"`
  - `{:mode :failed :flag "pony" :kind :lobster :reason "no species :lobster"}` → `"pony: lobster failed (no species :lobster)"`
  - `nil` → nil; `{:mode :other :flag "pony" :kind :horse}` → nil
- **M15** `body-pose`: `xpos = (js/Float64Array.from #js [0 0 0 1 2 3])`,
  `xquat = (js/Float64Array.from #js [1 0 0 0 0.7 0.1 0.2 0.3])`.
  `(cm/body-pose xpos xquat 1)` = `{:pos [1 3 -2] :quat [0.1 0.3 -0.2 0.7]}`.
  `(cm/body-pose xpos xquat 0)` = `{:pos [0 0 0] :quat [0 0 0 1]}`.

### 7.3 `test/pm/creature_mujoco_test.cljs`: the bodies compile in real MuJoCo

One or more `deftest`s using `cljs.test/async`. Load the module the way the game does, but with
an absolute path, because the import map is browser-only and the gate runs `node out/test.js`
from the repo root:
`((js/Function. "u" "return import(u)") (str (.cwd js/process) "/node_modules/@mujoco/mujoco/mujoco.js"))`
→ `(.then #((.-default ^js %)))` → `mj`. Any rejection → `(is false (str e))` and `(done)`. The
architect checked this exact import from CommonJS under Node 26. Call `.delete` on data, then on
model, after each case. Place `P` = `{:at [4.5 1.79 -8.0] :yaw 0 :drop-m 0.4}`.

- **R1** horse: `model = (.from_xml_string (.-MjModel mj) (c/mjcf horse P))`, `data = (new (.-MjData mj) model)`.
  `nbody`, `njnt`, `nu`, `nq`, `nv` and `ngeom` equal `(c/counts horse)` (12 10 9 16 15 12).
  `body_mass[0]` = 0 and `body_mass[i]` ≈ the i-th value of `(c/body-masses horse)` for i = 1..11.
  `jnt_range[2j+2]`, `jnt_range[2j+3]` ≈ `(nth (c/ctrl-ranges horse) j)` for j = 0..8 (joint 0
  is the free joint). `qpos[0..2]` ≈ `[4.5 8 3.03]` and `qpos[3..6]` ≈ `[1 0 0 0]`.
- **R2** dog: the same checks against `(c/counts dog)`, `(c/body-masses dog)` and
  `(c/ctrl-ranges dog)`. `qpos[0..2]` ≈ `[4.5 8 2.63]`.
- **R3** Rest with zero controls (ctrl left at 0): step 1000 times (2 s) with `(.mj_step mj model data)`.
  After every step the torso z `xpos[5]` is finite and `xpos[5] − 1.79` ≥ the torso r (0.15
  horse, 0.10 dog), so it never sinks into the plane. At the end every `qpos` value is finite (no
  NaN), and the torso height `xpos[5] − 1.79` is in **[0.80, 0.86] for the horse** and **[0.40, 0.46]
  for the dog**. Observed in the architect's run: 0.8391 and 0.4392 (both stand stiffly), with
  minima 0.818 and 0.418.
- **R4** Yaw: `(c/mjcf dog {:at [0 0 0] :yaw 90 :drop-m 0.4})` compiles, and `qpos[0..6]` ≈
  `[0 0 0.84 0.707107 0 0 0.707107]`.
- **R5** The twitch drives real MuJoCo (horse **and** dog, place `P`): `ctrl = (cm/controller :twitch 9 1)`,
  `ranges = (c/ctrl-ranges sp)`. Run 120 frames of dt 1/60 through `cm/fixed-steps` (carrying
  `:acc`). For each step: `[ctrl a] (cm/act ctrl nil c/timestep)`, write
  `(cm/actions->ctrl a ranges)` into `(.-ctrl data)` with `aset`, then `mj_step`. Afterwards:
  the total steps over the 120 frames are 1000 (±1), every `qpos` is finite, the torso height
  `xpos[5] − 1.79` is > 0.05, and at least one hinge `qpos[7..15]` has |value| > 0.05 (the
  controls really move the joints). Observed in a random-target run: the body falls and lies at
  about 0.15 m (horse) and 0.10 m (dog).

**Count: 47 numbered cases** (C1–C27, M1–M15, R1–R5).

---

## 8. Done when (gate + live)

- `bash .crew/gate.sh` → `GATE: PASS`: both builds with 0 warnings (`^js` hints on interop locals,
  including `js/process`, `mod`, `mj`, `model` and `data`), and all tests, including C1–C27,
  M1–M15 and R1–R5, pass.
- `grep -rniE 'horse|dog|pony|juniper' src/` matches only `src/pm/species.cljs`.
- Without `?pony`, the game is byte-for-byte the same at runtime: no import, no objects, no HUD
  segment.
- Live (Hal): `?pony` shows a foal dropping onto the east bank at (4.5, 1.79, −8.0) and flopping.
  `?pony=dog` shows a dog. `?pony=horse&seed=3` replays the same flop on reload. The HUD reads
  like `pony: horse · 0.9 ms/frame · 8 steps · 0 dropped · torso 0.31 m`. Hal takes a screenshot.
