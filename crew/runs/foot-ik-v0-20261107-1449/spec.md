# Spec: foot IK v0 (feet on the real ground, and no hop)

Quest: `.crew/quests/foot-ik-v0.md` (task M-1). Round dir: `crew/runs/foot-ik-v0-20261107-1449/`.

This builds on `motion-bricks-v0`, `avoid-v0` and `characters-v0`, all in the working tree, uncommitted: `pm.motion`, `pm.avoid`, `pm.bricks`, and the motion/avoid/characters tests.

**The plan, each frame in `?bricks` mode, after `(.update c dt)`, for each walker whose body has leg bones:**
1. Read each ankle's world position and cast a ray down under it.
2. A **pure `pm.feet/foot-frame`** turns those readings into:
   - the walker's base height (the lowest planted foot's ground, which replaces the single root ray and removes the hop);
   - the pelvis drop;
   - each foot's target;
   - each foot's tilt.
3. `pm.bricks` applies the result:
   - it lowers the object;
   - it solves each leg with the pure two-bone IK and turns the hip and knee bones;
   - it restores the foot's own orientation and then tilts it.
4. The planner also rejects detour points on ground more than 0.4 m above or below the walker.

All the maths is pure and tested. The interop is in `pm.bricks` and `pm.three`.

## 0. Who touches what

| Who | May edit | Must not touch |
|---|---|---|
| builder (`un-automate-monde`) | `src/pm/feet.cljs` (new), `src/pm/motion.cljs` (§3 only), `src/pm/bricks.cljs`, `src/pm/three.cljs` (add §5 fns only), `src/pm/scene.cljs` (add the 2 palette keys of §5 only), `resources/estate.edn` (exactly §1), then `touch src/pm/world.cljs` | `public/**` (never `public/vendor/`), `src/pm/avoid.cljs`, `src/pm/core.cljs`, every other `src/` file, `resources/content/*.edn`, `docs/GDD.md`, all existing tests |
| tester (`le-testeur`) | `test/pm/feet_test.cljs` (new, ns `pm.feet-test`) | every `src/`, `resources/`, `public/` file; existing tests |

**Contract:** every existing test keeps passing unchanged. A `plan` cfg without `:ground-at` behaves exactly as today.

Nobody reads `assets/private/`, `public/kit/` or `public/state/`. There are no npm deps. The build must stay at 0 warnings. Nobody commits.

## 1. Data (`resources/estate.edn`, inside `:motion`)

```clojure
:legs {:soma   {:left  ["LeftLeg" "LeftShin" "LeftFoot" "LeftToeBase"]           ; hip knee ankle toe (toe optional)
                :right ["RightLeg" "RightShin" "RightFoot" "RightToeBase"]}
       :mixamo {:left  ["mixamorig:LeftUpLeg" "mixamorig:LeftLeg" "mixamorig:LeftFoot" "mixamorig:LeftToeBase"]
                :right ["mixamorig:RightUpLeg" "mixamorig:RightLeg" "mixamorig:RightFoot" "mixamorig:RightToeBase"]}}
:feet {:drop-cap 0.35   ; max pelvis drop, m
       :step-max 0.4}   ; a detour point's ground may differ from the walker's base by at most this, m
```

- The mixamo names are written the way the port writes them (`MIXAMO("mixamorig:")`). The lookup in §2.1 also finds `mixamorigLeftUpLeg`, which is GLTFLoader's sanitized form, and bare `LeftUpLeg`.
- A legs key is a rig: `:soma` is the mannequin, and any other key is a character's `:rig` from characters-v0.

## 2. `pm.feet` (new, pure; builder writes it, tester tests it)

`(ns pm.feet (:require [clojure.string :as str] [pm.motion :as m]))`. It has no three.js and no atoms. Vectors are `[x y z]` in world metres, with y up.

### 2.1 Names and config

| Signature | Rules |
|---|---|
| `(sanitize s)` | Port parity: each whitespace char → `"_"`, then every `[`, `]`, `.`, `:`, `/` is removed. |
| `(find-bone wanted names)` | It returns the first name in `names` (a seq of strings, in order) found by three passes, tried in order. **(1)** `(= n wanted)`. **(2)** `(= (sanitize n) (sanitize wanted))`. **(3)** `(= (sanitize n) (sanitize bare))`, where `bare = (str/replace wanted #"^[^:]*:" "")`. It returns nil when no pass finds one, or when `names` is nil or empty. |
| `(leg-bones chain names)` | `chain` is `[hip knee ankle toe?]`. Returns `{:hip h :knee k :ankle a :toe t}` of `find-bone` results. It is nil when hip, knee or ankle is nil; `:toe` may be nil. |
| `(legs world)` | Returns `{rig {:left chain :right chain}}` from `(get-in world [:motion :legs])`. A rig is kept only when its key is a keyword and both `:left` and `:right` are vectors of 3 or 4 non-blank strings. Otherwise `{}`. |
| `(feet-config world)` | Each key of `(get-in world [:motion :feet])` is used when it is a number ≥ 0, else its default: `{:drop-cap 0.35 :step-max 0.4 :plant-height 0.04 :plant-speed 0.3 :tilt-max-deg 25 :tilt-blend-s 0.1 :smooth-s 0.08 :ray-move 0.03}`. Exactly these 8 keys. |
| `(ground-hit hits prefixes)` | `hits` is `[[y name n] …]` (from `t/down-hits-n`). It keeps the hits whose name passes `m/ground-name?`, and returns `[y n]` of the **highest** one. `n` defaults to `[0 1 0]` when missing or nil. With no such hit → nil. |
| `(needs-ray? last-xz xz move)` | True when `last-xz` is nil, or when `|xz − last-xz| ≥ move` (2-D, `[x z]`). |

### 2.2 Maths

| Signature | Rules |
|---|---|
| `(smooth-by prev target dt tau)` | `prev` nil → `target`; `target` nil → `prev`; `tau ≤ 0` → `target`. Otherwise `prev + (target − prev)·(1 − e^(−max(dt,0)/tau))`. |
| `(pelvis-drop dl dr cap)` | A nil `d` counts as 0. `(min cap (max 0 (- (min dl dr))))`. |
| `(base-height grounds prev)` | `grounds` is the planted feet's ground heights, and may contain nils. Returns the min of the non-nils, or `prev` when there are none. |
| `(planted? height speed cfg ds)` | `height` is the ankle's height above its rest height over the generated floor; `speed` is horizontal m/s. Returns `(and (number? height) (<= height (* plant-height ds)) (<= speed (* plant-speed ds)))`. Negative heights count. |
| `(two-bone hip knee ankle target pole)` | Analytic hip–knee–ankle IK (below). Returns `{:knee K' :ankle A' :reach r :clamped? bool}`. |
| `(tilt n max-deg)` | Returns `{:axis a :angle θ}`, turning the foot's up `[0 1 0]` toward the ground normal `n`. `n` nil or zero → `{:axis nil :angle 0}`. Otherwise `n̂ = n/|n|` and `c = [0 1 0] × n̂`. When `|c| < 1e-6` → `{:axis nil :angle 0}`; this covers flat ground and an upside-down normal. Otherwise `axis = c/|c|`, and `angle = min(acos(clamp(n̂y, −1, 1)), max-deg·π/180)`. |
| `(blend-weight w planted? dt blend-s)` | When `blend-s ≤ 0` → 1 if planted, else 0. Otherwise it moves `w` toward 1 (planted) or 0 by `dt/blend-s`, clamped to [0, 1]. |

**`two-bone` in detail.**
- The bone lengths are `l1 = |knee − hip|` and `l2 = |ankle − knee|`. Let `D = target − hip` and `dist = |D|`.
- `u = D/dist`. When `dist < 1e-9`, `u = (ankle − hip)/|ankle − hip|` instead.
- `r = clamp(dist, max(|l1 − l2|, 1e-4), l1 + l2)`. `:clamped?` is true when `dist > l1 + l2` or `dist < max(|l1 − l2|, 1e-4)`.
- **Bend direction `b`.** Here `perp(v)` is the unit part of `v` perpendicular to `u`, or nil when that part's length is < 1e-6. `b` is the first non-nil of `perp(knee − hip)`, `perp(pole)` (when pole is non-nil), `perp([0 0 1])` and `perp([1 0 0])`. This keeps the knee bending the way it already bends.
- `cosα = clamp((l1² + r² − l2²)/(2·l1·r), −1, 1)`, `sinα = √(1 − cos²α)`.
- `K' = hip + u·(l1·cosα) + b·(l1·sinα)`, and `A' = hip + u·r`.

### 2.3 `foot-frame` (the per-frame rule)

```clojure
(feet-init) => {:d [0 0] :drop 0 :w [0 0] :prev [nil nil]}
(foot-frame state input cfg ds dt) =>
  {:state {:d [dl dr] :drop D :w [wl wr] :prev [[xl zl] [xr zr]]}
   :ground h        ; base-height of the planted feet's ground (nil = none planted: keep the old one)
   :obj-y  y        ; where to put the walker object (b − D)
   :targets [TL TR] ; ankle targets
   :tilts [{:axis :angle} {:axis :angle}]   ; angle already × the foot's blend weight
   :planted [pl pr]}
```

`input` is `{:obj-y oy :base b :feet [{:ankle [x y z] :g g :n n :a0 a0} {…}]}`, left first.
- `oy` is the object's y while `update` posed it.
- `b` is the walker's smoothed `:y`.
- `g`/`n` come from `ground-hit` (and may be nil).
- `a0` is the foot's rest ankle height above the floor (measured at swap-in).

For each foot `i`, with `A = ankle`:
- `loc = A.y − oy` (the generated ankle height above the generated floor) and `hgt = loc − a0`;
- `v = 0` when `prev_i` is nil or `dt ≤ 0`, else `|[A.x A.z] − prev_i| / dt`;
- `planted_i = (planted? hgt v cfg ds)`;
- `d_i = (smooth-by d_prev_i (if g (- g b) 0) dt smooth-s)`;
- `w_i = (blend-weight w_prev_i planted_i dt tilt-blend-s)`;
- `T_i = [A.x (+ b loc d_i) A.z]`: the foot rides its own ground, keeping its generated height above the floor;
- `tilt_i = (tilt n_i tilt-max-deg)`, with `:angle` × `w_i`.

Then:
- `:ground = (base-height [g of each planted foot] nil)`;
- `D = (smooth-by drop_prev (pelvis-drop dl dr drop-cap) dt smooth-s)`;
- `:obj-y = b − D`;
- `:prev` holds the two ankles' `[x z]`.

## 3. `pm.motion` additions (builder)

- **`(walkable? g base step-max)`** → `(and (number? g) (<= (js/Math.abs (- g base)) step-max))`.
- **`plan` §3.6.5 of avoid-v0 (the side check):** a side's `D` is also rejected when all three hold:
  - `(:ground-at cfg)` is a fn;
  - `(:y w)` is a number;
  - `(not (walkable? ((:ground-at cfg) Dx Dz) (:y w) (or (:step-max cfg) 0.4)))`.

  A rejected point is treated like a blocked one: the other side is tried, then the walker waits or gives up, as today. Without `:ground-at`, or with `:y` nil, nothing changes.

## 4. `pm.bricks` changes (builder; interop, no rules)

### 4.1 Start

- `start!` stores `:legs (f/legs world)` and `:fcfg (f/feet-config world)`.
- `pcfg` gains `:step-max (:step-max fcfg)` and `:ground-at`:

  ```clojure
  (fn [x z] (let [gm (:ground-meshes @!state)]
              (when (seq gm) (m/ground-height (t/down-hits gm x z 40) (:ground cfg)))))
  ```

  The rule therefore applies in **both** modes.

### 4.2 Swap-in, per walker, after the body is set

- `rig` = `(:rig (:character w))` when the character body succeeded, else `:soma` (the mannequin).
- `chain = (get legs rig)`, and `names = (t/object-names (.-object c))`.
- `:left`/`:right` = `(f/leg-bones (:left chain) names)` etc., with each name resolved through `(t/by-name obj name)`.
- After `set-scale!`/`set-pos!`/`set-yaw!` and `(t/update-world! obj)`, measure `a0_i = (second (t/world-pos ankle_i)) − obj.y`.
- The view gets `:legs {:left {:hip :knee :ankle :toe :a0} :right {…}}` (bones are JS objects), `:feet (f/feet-init)` and `:rays [nil nil]` (the last `{:xz :g :n}` per foot).
- When the chain is missing or either side is nil, set `:legs nil`, and log once (`console.info`, naming the walker): that walker keeps today's root-ray height.

### 4.3 `tick!`

The root-ray `sense-ground` is skipped for walkers whose view has `:legs` while the mode is `:bricks`. Their `:ground` comes from the feet.

### 4.4 `tick-bricks!`, per walker, after `(.update c dt)`, when `:legs`

1. `(t/update-world! obj)`, `oy = obj.y`, and `A_i = (t/world-pos ankle_i)`.
2. **Rays.** For each foot, when `(f/needs-ray? (:xz ray_i) [A.x A.z] ray-move)`: `[g n] = (f/ground-hit (t/down-hits-n gm A.x A.z 40) (:ground cfg))`, stored with `:xz`. Otherwise reuse the stored `g`/`n`.
3. `r = (f/foot-frame (:feet view) {:obj-y oy :base (:y w) :feet [...]} fcfg ds dt)`.
   - When `(:ground r)` is non-nil, it becomes the walker's `:ground`; `settle` smooths it into `:y` next frame.
4. `obj.y ← (:obj-y r)`, then `(t/update-world! obj)`.
5. **Each leg `i`:**
   - Save `q = (t/world-quat ankle)`.
   - With `H`, `K`, `A` = the world positions of hip, knee and ankle, `pole = (toe − ankle)` when there is a toe, else nil: `s = (f/two-bone H K A T_i pole)`.
   - `(t/aim-bone! hip (K − H) ((:knee s) − H))`.
   - Then read `K2` and `A2` again: `(t/aim-bone! knee (A2 − K2) ((:ankle s) − K2))`.
   - `(t/set-world-quat! ankle q)`, which keeps the foot's generated orientation.
   - When the tilt angle is > 0 and the axis is non-nil: `(t/tilt-bone! ankle axis angle)`.
6. Store `(:state r)`. The anchor and label use `:y` as today.
7. Without `:legs`: `obj.y ← :y`, as today.

The port's `setPose` rewrites the leg bones absolutely on each `update`, so the IK never accumulates. It is applied after `update` every frame.

### 4.5 `?feet` (debug)

When `(m/url-flag? search "feet")` is set, each foot gets two `t/stand-in` spheres (size `[0.06 0.06 0.06]`), placed each frame with `t/set-pos!`:
- the ground point `[A.x g A.z]`, colour `:feet-ground`;
- the target `T_i`, colour `:feet-target`.

`m/url-flag?` is a new one-line helper, `(.has (js/URLSearchParams. (or search "")) name)`. The existing flag fns can stay as they are.

## 5. `pm.three` and `pm.scene` additions (builder)

| Signature | Does |
|---|---|
| `(down-hits-n objs x z from)` | Like `down-hits`, but each hit is `[y name [nx ny nz]]`. The normal is `hit.face.normal` transformed by the object's `matrixWorld` (`transformDirection`), or nil when there is no face. Never throws. |
| `(object-names root)` | A vector of every non-blank `.name` under `root`, root included, in `traverse` order. |
| `(by-name root name)` | `root.getObjectByName(name)`, or nil. |
| `(update-world! obj)` | `obj.updateMatrixWorld(true)`; returns `obj`. |
| `(world-pos obj)` | `[x y z]` of `getWorldPosition`. |
| `(world-quat obj)` | A new world `Quaternion` (opaque to CLJS). |
| `(set-world-quat! bone q)` | Sets `bone.quaternion = parentWorld⁻¹ · q`, then `bone.updateMatrixWorld(true)`. |
| `(aim-bone! bone from to)` | `from`/`to` are `[x y z]`; it is a no-op when either is shorter than 1e-9. `r = setFromUnitVectors(f̂, t̂)`, the new world quat is `r · world(bone)`, then `set-world-quat!`. |
| `(tilt-bone! bone [ax ay az] angle)` | The same, with `r = setFromAxisAngle(axis, angle)`. |

`pm.scene/palette` gets `:feet-ground 0x48bb78` and `:feet-target 0xed64a6`.

## 6. Test cases (`test/pm/feet_test.cljs`)

The test requires `[cljs.test :refer [deftest is]] [pm.feet :as f] [pm.motion :as m] [pm.avoid :as a] [pm.world :as w]`.

**Setup.**
- `W = (w/load-estate w/estate-edn)`. Tolerance 1e-6 (`near?`/`vnear?`).
- `C = (f/feet-config W)` and `C0 = (assoc C :smooth-s 0 :tilt-blend-s 0)` (exact, unsmoothed).
- `H = [0 1 0]`, `K = [0.3 0.6 0]`, `A = [0 0.2 0]`. Both lengths are 0.5.
- `lvl` is the level-ground input:
  ```clojure
  lvl = {:obj-y 2.0 :base 2.0
         :feet [{:ankle [0 2.1 0]     :g 2.0 :n [0 1 0] :a0 0.1}     ; planted (hgt 0)
                {:ankle [0.3 2.25 0.2] :g 2.3 :n [0 1 0] :a0 0.1}]}  ; swinging (hgt 0.15), over ground 0.3 higher
  ```

### Names and config

1. `(f/sanitize "mixamorig:Left Leg")` = `"mixamorigLeft_Leg"`; `(f/sanitize "a[b].c/d")` = `"abcd"`.
2. Exact: `(f/find-bone "LeftLeg" ["Hips" "LeftLeg" "LeftShin"])` = `"LeftLeg"` (SOMA).
3. Sanitized with the prefix: `(f/find-bone "mixamorig:LeftUpLeg" ["mixamorigHips" "mixamorigLeftUpLeg"])` = `"mixamorigLeftUpLeg"`.
4. Without the prefix: `(f/find-bone "mixamorig:LeftUpLeg" ["Hips" "LeftUpLeg"])` = `"LeftUpLeg"`.
5. Pass order: `(f/find-bone "mixamorig:LeftUpLeg" ["LeftUpLeg" "mixamorigLeftUpLeg" "mixamorig:LeftUpLeg"])` = `"mixamorig:LeftUpLeg"`. `(f/find-bone "mixamorig:LeftUpLeg" ["LeftUpLeg" "mixamorigLeftUpLeg"])` = `"mixamorigLeftUpLeg"`.
6. `(f/find-bone "mixamorig:LeftLeg" ["mixamorigLeftUpLeg" "mixamorigLeftLeg"])` = `"mixamorigLeftLeg"` (not UpLeg). `(f/find-bone "LeftLeg" ["LeftUpLeg"])`, `(f/find-bone "LeftLeg" [])` and `(f/find-bone "LeftLeg" nil)` are all nil.
7. `(f/leg-bones ["mixamorig:LeftUpLeg" "mixamorig:LeftLeg" "mixamorig:LeftFoot" "mixamorig:LeftToeBase"] ["mixamorigLeftUpLeg" "mixamorigLeftLeg" "mixamorigLeftFoot"])` = `{:hip "mixamorigLeftUpLeg" :knee "mixamorigLeftLeg" :ankle "mixamorigLeftFoot" :toe nil}`. The same with no knee in `names` → nil.
8. `(set (keys (f/legs W)))` = `#{:soma :mixamo}`. `(get-in (f/legs W) [:soma :left])` = `["LeftLeg" "LeftShin" "LeftFoot" "LeftToeBase"]`. `(get-in (f/legs W) [:mixamo :right 0])` = `"mixamorig:RightUpLeg"`.
9. `(f/legs {:motion {:legs {:a {:left ["A" "B" "C"] :right ["D" "E" "F"]} :b {:left ["A" "B"] :right ["D" "E" "F"]} :c {:left ["A" "" "C"] :right ["D" "E" "F"]} :d "x"}}})` = `{:a {:left ["A" "B" "C"] :right ["D" "E" "F"]}}`. `(f/legs {})` = `{}`.
10. `(f/feet-config W)` = `{:drop-cap 0.35 :step-max 0.4 :plant-height 0.04 :plant-speed 0.3 :tilt-max-deg 25 :tilt-blend-s 0.1 :smooth-s 0.08 :ray-move 0.03}`, and `(f/feet-config {})` is the same. `(f/feet-config {:motion {:feet {:drop-cap -1 :smooth-s 0 :step-max "x"}}})` has `:drop-cap 0.35`, `:smooth-s 0` and `:step-max 0.4`.
11. `(f/ground-hit [[2.0 "SM_Hiroshi_Terrain" [0 1 0]] [2.4 "SM_Hiroshi_Gravel" [0.1 0.99 0]] [3.0 "Rock" [0 1 0]]] (:ground (m/config W)))` = `[2.4 [0.1 0.99 0]]`. `(f/ground-hit [[2.0 "SM_Hiroshi_Terrain"]] …)` = `[2.0 [0 1 0]]`. `(f/ground-hit [[3.0 "Rock" [0 1 0]]] …)` is nil.
12. `(f/needs-ray? nil [0 0] 0.03)` is true; `(f/needs-ray? [0 0] [0.02 0] 0.03)` is false; `(f/needs-ray? [0 0] [0.03 0.01] 0.03)` is true (0.0316).

### Two-bone IK

13. **Reachable:** `(f/two-bone H K A [0 0.4 0] nil)` → `:knee` ≈ `[0.4 0.7 0]`, `:ankle` ≈ `[0 0.4 0]`, `:reach` ≈ 0.6, `:clamped? false`. (r 0.6, cosα 0.6, sinα 0.8, b = +x.)
14. **The knee side is kept:** `(f/two-bone H [-0.3 0.6 0] A [0 0.4 0] nil)` → `:knee` ≈ `[-0.4 0.7 0]`.
15. **Unreachable, clamped:** `(f/two-bone H K A [0 -0.5 0] nil)` → `:ankle` ≈ `[0 0 0]`, `:knee` ≈ `[0 0.5 0]`, `:reach` ≈ 1, `:clamped? true`.
16. **A straight leg uses the pole:** `(f/two-bone H [0 0.5 0] [0 0 0] [0 0.2 0] [0 0 1])` → `:knee` ≈ `[0 0.6 0.3]`, `:ankle` ≈ `[0 0.2 0]`. With pole nil, the `[0 0 1]` fallback gives the same `[0 0.6 0.3]`.
17. **Oblique target:** `(f/two-bone H K A [0.2 0.5 0.1] nil)` → `:ankle` ≈ `[0.2 0.5 0.1]`, `|knee − H|` ≈ 0.5, `|ankle − knee|` ≈ 0.5, `:knee` ≈ `[0.4373853 0.8387856 -0.1808426]` (x > 0: same side).
18. **Target at the hip:** `(f/two-bone H K A [0 1 0] nil)` → `:reach` ≈ 1e-4, `:clamped? true`, `:ankle` ≈ `[0 0.9999 0]`, `:knee` ≈ `[0.5 0.99995 0]`.

### Pelvis, base height, planted

19. `(f/pelvis-drop 0.1 -0.2 0.35)` ≈ 0.2; `(f/pelvis-drop 0.1 0.2 0.35)` = 0; `(f/pelvis-drop -0.5 0 0.35)` ≈ 0.35 (capped); `(f/pelvis-drop nil -0.1 0.35)` ≈ 0.1; `(f/pelvis-drop nil nil 0.35)` = 0.
20. `(f/base-height [2.1 2.3] nil)` = 2.1; `(f/base-height [nil 2.3] 2.0)` = 2.3; `(f/base-height [] 2.0)` = 2.0; `(f/base-height [] nil)` is nil.
21. `(f/planted? 0.03 0.1 C 1)` is true; `(0.05 0.1 C 1)` is false; `(0.03 0.5 C 1)` is false; `(0.05 0.1 C 1.4)` is true (≤ 0.056); `(-0.02 0 C 1)` is true; `(nil 0 C 1)` is false.

### Tilt, blend, smoothing

22. `(f/tilt [0 1 0] 25)` = `{:axis nil :angle 0}`; `(f/tilt [0 2 0] 25)` = the same; `(f/tilt [0 -1 0] 25)` = the same; `(f/tilt nil 25)` = the same.
23. A 10° slope: `(f/tilt [0.1736482 0.9848078 0] 25)` → `:angle` ≈ 0.1745329, `:axis` ≈ `[0 0 -1]`.
24. A 40° slope is capped: `(f/tilt [0.6427876 0.7660444 0] 25)` → `:angle` ≈ 0.4363323 (25°), `:axis` ≈ `[0 0 -1]`.
25. A slope along z: `(f/tilt [0 0.8 0.6] 25)` → `:axis` ≈ `[1 0 0]`, `:angle` ≈ 0.4363323 (acos 0.8 = 36.87°, capped).
26. `(f/blend-weight 0 true 0.05 0.1)` ≈ 0.5; `(f/blend-weight 0.5 true 0.1 0.1)` = 1; `(f/blend-weight 1 false 0.03 0.1)` ≈ 0.7; `(f/blend-weight 0 false 0.1 0.1)` = 0; `(f/blend-weight 0.3 true 0.01 0)` = 1.
27. `(f/smooth-by 0 0.3 0.08 0.08)` ≈ 0.1896362; `(f/smooth-by 0 1 0.04 0.08)` ≈ 0.3934693; `(f/smooth-by nil 0.3 0.1 0.08)` = 0.3; `(f/smooth-by 0.2 nil 0.1 0.08)` = 0.2; `(f/smooth-by 0 0.3 0.1 0)` = 0.3.

### `foot-frame`

28. `(f/feet-init)` = `{:d [0 0] :drop 0 :w [0 0] :prev [nil nil]}`.
29. **Level, one foot over higher ground:** `(f/foot-frame (f/feet-init) lvl C0 1 0.1)` →
    - `:planted [true false]`, `:ground 2.0`;
    - `:state :d` ≈ `[0 0.3]`, `:drop 0`, `:obj-y 2.0`, `:state :w [1 0]`;
    - `:targets` ≈ `[[0 2.1 0] [0.3 2.55 0.2]]`;
    - `:state :prev` ≈ `[[0 0] [0.3 0.2]]`.
30. **Downhill swing foot drops the pelvis:** `lvl` with the right `:g 1.8` → `:state :d` ≈ `[0 -0.2]`, `:state :drop` ≈ 0.2, `:obj-y` ≈ 1.8, `:targets` ≈ `[[0 2.1 0] [0.3 2.05 0.2]]`.
31. **Cap:** right `:g 1.5` → `:drop` ≈ 0.35, `:obj-y` ≈ 1.65, the right target y ≈ 1.75 (the leg will clamp).
32. **A moving foot isn't planted:** from the state `(assoc (f/feet-init) :prev [[0 0] [0.3 0.2]])`, with `lvl`'s left ankle at `[0.05 2.1 0]` and dt 0.1 → speed 0.5 > 0.3 → `:planted [false false]`, `:ground` nil, `:obj-y 2.0`.
33. **Tilt only on a planted foot:** `lvl` with both `:n [0.1736482 0.9848078 0]` → the left tilt `:angle` ≈ 0.1745329 and `:axis` ≈ `[0 0 -1]`; the right tilt `:angle` = 0 (`w` 0).
34. **No ground under a foot:** the right `:g nil` → the right `d` ≈ 0, `:drop 0`, and the right target y ≈ 2.25.
35. **Display scale on planting:** the left ankle at `[0 2.15 0]` (hgt 0.05) → with ds 1.4, `:planted` first is true and `:ground 2.0`. With ds 1, `:planted` first is false and `:ground` nil.
36. **Smoothed** (with `C`, dt 0.08): `(f/foot-frame (f/feet-init) lvl C 1 0.08)` → the right `d` ≈ 0.1896362, the right target y ≈ 2.4396362, the left `w` ≈ 0.8, `:drop 0`.
37. **The base comes from the lower planted foot (the hop):**
    ```clojure
    {:obj-y 2.0 :base 2.0
     :feet [{:ankle [0 2.1 0] :g 2.0 :n [0 1 0] :a0 0.1}
            {:ankle [0.3 2.1 0.2] :g 2.3 :n [0 1 0] :a0 0.1}]}
    ```
    Both feet are planted → `:ground` ≈ 2.0, not the root's ground; `:targets` ≈ `[[0 2.1 0] [0.3 2.4 0.2]]`.

### Detours stay on walkable ground

The fixtures are as in avoid-v0:

```clojure
L  = (assoc (m/walker {:id :l :route [[0 0] [8 0]] :pause-s 1}) :pause 0 :to 1 :leg 1)
S1 = [{:at [4 0] :r 0.5}]
P  = (assoc L :pos [2 0] :y 2.0)
cfg {:speed 1.2 :horizon-s 2}
```

38. `(m/walkable? 2.3 2.0 0.4)` is true; `(m/walkable? 2.5 2.0 0.4)` is false; `(m/walkable? 1.5 2.0 0.4)` is false; `(m/walkable? nil 2.0 0.4)` is false.
39. **Right side low → left:** `(m/plan P [] S1 (assoc cfg :ground-at (fn [x z] (if (pos? z) 0.5 2.0)) :step-max 0.4))` → `:aim` ≈ `[4.3424073 -1.4449073]`, `:side :left`.
40. **Both missing → wait:** `:ground-at (fn [_ _] nil)` → `:aim nil`, `:wait 2`.
41. **Ground fine → right, as today:** `:ground-at (fn [_ _] 2.3)` → `:aim` ≈ `[4.3424073 1.4449073]`, `:side :right`.
42. **No base height → rule skipped:** `(m/plan (assoc P :y nil) [] S1 (assoc cfg :ground-at (fn [_ _] nil)))` → `:aim` ≈ `[4.3424073 1.4449073]`. The default `:step-max` is used when it's absent.
43. **No `:ground-at` → unchanged:** `(m/plan P [] S1 cfg)` → `:aim` ≈ `[4.3424073 1.4449073]`, `:side :right`.
44. **Real data, the second walker on B→C** (`(second (m/walkers W))` with `:pos [-6 -10] :to 0 :dir -1 :pause 0 :leg 2 :y 2.65`, statics `(a/statics W)`):
    - with `:ground-at (fn [_ _] 2.65)` → `:aim` ≈ `[-6.1208569 -11.724002]`, `:side :left` (the same as avoid case 35);
    - with `:ground-at (fn [x _] (if (< x -6) 0.5 2.65))` → the left side is too low and the right side is inside Juniper → `:aim nil`, `:wait 2`.

## 7. Live check (Hal, `?bricks&feet`)

- Walking across the bank, each foot sits on the grass: the uphill foot higher, the downhill foot lower, the knees bending, and the hips dropping toward the lower foot. The `?feet` markers sit on each other while a foot is planted.
- Planted feet tilt with the slope, by up to 25°.
- The dodge near the creek edge has no hop, and detours don't step down onto the gravel (more than 0.4 m below).
- 60 fps. There are about 2 foot rays per walker per frame, only after a foot has moved 3 cm since its last ray, plus the planner's detour probes.
- A walker whose legs aren't found logs once and walks as before.
