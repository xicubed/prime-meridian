# Spec: avoidance v0 (walkers go around each other, Juniper and the rocks)

Quest: `.crew/quests/avoid-v0.md`. Round dir: `crew/runs/avoid-v0-20261107-1327/`.
This builds on `motion-bricks-v0`: `pm.motion`, `pm.bricks`, and the 59 tests in `test/pm/motion_test.cljs`.

**How it works.**
- Everything is a circle on the ground `(x, z)`.
- Every 0.25 s a **planner** looks along each walker's straight path to its goal, over a 2 s horizon. It checks the static circles, and the other walkers at their predicted positions (current velocity).
- **Blocked** means the path comes closer than the two radii + 0.15 m, at a time > 0.
- A blocked walker aims at a **tangent detour point** around the first blocker, at clearance + 0.1 m. It tries its own right first; after that, it tries the side it is already using first. If that point is inside a static, or the path to it is blocked, it tries the other side.
- If both sides are blocked it **waits** 2 s. If it is still blocked after that, it gives up on that waypoint: it counts it as arrived and goes on.
- A target inside a static becomes the nearest free point on the line to it.

`step` and `follow` move toward or measure that aim. In `?bricks` mode the aim is just another `moveTo`.

I checked every number below by hand, and ran the scenario cases through a JS model of exactly these rules. The real-estate run was 600 s: walkers never got closer than 0.754 m, the closest static gap was 0.153 m, and the ping-pong order held.

## 0. Who touches what

| Who | May edit | Must not touch |
|---|---|---|
| builder (`un-automate-monde`) | `src/pm/avoid.cljs` (new), `src/pm/motion.cljs`, `src/pm/bricks.cljs`, `src/pm/three.cljs` (add §5 fns only), `src/pm/scene.cljs` (add the 3 palette keys of §5 only), `resources/estate.edn` (exactly §1.1), then `touch src/pm/world.cljs` | `src/pm/core.cljs` (no change needed), every other `src/` file, `public/**`, `resources/content/*.edn`, `docs/GDD.md`, all tests |
| tester (`le-testeur`) | `test/pm/avoid_test.cljs` (new, ns `pm.avoid-test`) | every `src/`, `resources/`, `public/` file; `test/pm/motion_test.cljs` and other tests |

**Contract:** all existing tests, including the 59 in `motion_test.cljs`, keep passing unchanged. `m/config` is unchanged, and its test compares the whole map.

Nobody reads `assets/private/`, `public/kit/` or `public/state/`. There are no npm deps. The build must stay at 0 warnings. Nobody commits.

## 1. Data

### 1.1 `resources/estate.edn`

- **Juniper's `{:id :body …}` item** (no `:scale`, `:yaw 90`): add `:footprint [[0.75 0 0.5] [0 0 0.5] [-0.6 0 0.45]]`.
- **Flora's `{:id :jar …}` item** (`:scale 4`): add `:footprint [[0 0 0.0875]]`.
  - **This deviates from the quest's `[[0 0 0.35]]`.** The quest's rule scales footprints by `:scale`, so 0.35 would become a 1.4 m circle. 0.0875 × 4 = the intended 0.35 m.
  - Add the comment `; 0.35 m in the world (scale 4)`.
  - Flora's body gets no footprint.
- **`:motion`** gains `:obstacles [{:at [-4.8 -8.2] :r 0.7} {:at [-4.3 -12.0] :r 0.6}]`, `:radius 0.3` and `:horizon-s 2`. `:bricks`, `:ground` and `:speed` are unchanged.

### 1.2 Footprints → circles

A footprint entry is `[dx dz r]`, in the item's own frame: +x is its nose, metres before scale.

The world circle is computed with `s` = `:scale` (a positive number, else 1), `θ` = `:yaw` in degrees (a number, else 0), and `[x _ z]` = `:at`:

```
[rx rz] = [dx·cosθ + dz·sinθ, −dx·sinθ + dz·cosθ]   ; three's rotation.y, the same as pm.plan :yaw
circle  = {:at [(+ x (* s rx)) (+ z (* s rz))] :r (* s r)}
```

With yaw 90, `[1 0]` → `[0 −1]` (the nose points at world −z), which matches the estate comment.

### 1.3 Walker state additions (`pm.motion`)

These are in addition to motion-bricks §1.3. `m/walker` sets their initial values:

```clojure
:radius 0.3      ; entry's :radius when a positive number, else 0.3 (walkers: else the config :radius)
:vel    [0 0]    ; m/s on the ground, last step/follow
:aim    nil      ; [x z] detour point being steered to, or nil (straight to the goal)
:goal   nil      ; [x z] free point standing in for a target inside a static, or nil (= the target)
:side   nil      ; :right / :left while detouring, else nil
:wait   0        ; seconds left waiting (both sides blocked)
:waited false    ; a wait ran out; the next both-blocked plan gives up on the waypoint
```

**Planner config** (`cfg`): `{:speed 1.2 :horizon-s 2}`.

## 2. `pm.avoid` (new, pure geometry; builder writes it, tester tests it)

`(ns pm.avoid)` has no requires. It uses only `js/Math`.

```clojure
(def margin 0.15)     ; clearance = r1 + r2 + margin
(def pad 0.1)         ; detour circle = clearance + pad
(def free-pad 0.05)   ; a moved goal sits at clearance + free-pad
```

A **circle** is `{:at [x z] :r R}`, with optional `:vel [vx vz]` (default `[0 0]`).

| Signature | Rules |
|---|---|
| `(rotate [dx dz] yaw-deg)` | §1.2's rotation. |
| `(footprint item)` | A vector of circles for one placed item. `:at` must be a 3-vector of numbers, else `[]`. An entry is kept only when it is a vector of exactly 3 numbers with `r > 0`; others are dropped. `:footprint` missing or not sequential → `[]`. |
| `(statics world)` | The footprint circles of every placed item. Mirrors go in `(sort (keys (:mirrors world)))` order, then `:placed` order, then footprint order. After those come the valid `(get-in world [:motion :obstacles])` entries (`:at` a 2-vector of numbers, `:r` a positive number) as `{:at :r}`. The result is a vector. |
| `(inside? pt r statics)` | True when some static has `|pt − c| < r + R + margin` (strict). |
| `(first-block p v t-max r circles)` | The walker is at `p` with velocity `v`, over `t ∈ [0, t-max]`. For each circle: `d0 = c − p`, `vr = vc − v`, `t* = 0` when `|vr|² = 0`, else `clamp(−(d0·vr)/|vr|², 0, t-max)`, and `dist = |d0 + vr·t*|`. It **blocks** when `t* > 0` and `dist < r + R + margin`. Returns the blocker with the smallest `t*` (the first in order on ties) as `{:t t* :at (c + vc·t*) :clear (r+R+margin) :dist dist}`, or nil. |
| `(path-block p q speed horizon-s r circles)` | With `d = |q − p|`, nil when `d = 0`. Otherwise `u = (q−p)/d`, `L = min(d, speed·horizon-s)`, and the result is `(first-block p (u·speed) (L/speed) r circles)`. |
| `(tangent-aim p c clear side)` | `clear` is the padded radius. With `dc = c − p` and `d = |dc|`: `û = dc/d`, or `[1 0]` when `d = 0`. **right(v) = [(− vz) vx]**; the walker's right when facing `v`, with y up. When `d > clear`: `α = asin(clear/d)` and `len = √(d² − clear²) + clear`. Otherwise `α = π/2` and `len = clear`. `dir = û·cosα + s·right(û)·sinα`, with `s = +1` for `:right` and −1 for `:left`. Returns `p + dir·len`. |
| `(free-goal p g r statics)` | Returns `g` when `g = p`. Otherwise `u = (g−p)/|g−p|`. Up to `(inc (count statics))` passes are made; each goes over the statics in order. When `|g − c| < C` (`C = r + R + margin`), it sets `w = g − c`, `k = w·u + √((w·u)² − |w|² + (C+free-pad)²)` and `g := g − u·k`. The loop stops after a pass that moves nothing. When the final `(g − p)·u < 0` → `p`. A free `g` comes back equal to the input. |
| `(circle-points [x z] r n)` | `n+1` points: point `k` is `[x + r·cos(2πk/n), z + r·sin(2πk/n)]`, and the last point is the first one, repeated exactly. |

## 3. `pm.motion` changes (builder)

`(:require [clojure.string :as str] [pm.avoid :as a])`. Four new constants: `(def plan-every-s 0.25)`, `(def wait-s 2)`, `(def resend-m 0.5)` and `(def default-radius 0.3)`.

### 3.1 Config and walkers

- **`(avoid-config world)`** → `{:radius r :horizon-s h}`. `r` is `(get-in world [:motion :radius])` when it is a positive number, else 0.3. `h` is `:horizon-s` when it is a positive number, else 2. **`config` itself is unchanged.**
- **`walker`** adds the §1.3 fields.
- **`walkers`**: an entry whose `:radius` isn't a positive number gets `(assoc entry :radius (:radius (avoid-config world)))` before `walker`.

### 3.2 Points

- **`(goal-point w)`** → `(or (:goal w) (target w))`.
- **`(steer-point w)`** → `(or (:aim w) (goal-point w))`.

### 3.3 `pick` / `arrive` (private)

Both also clear the planning state: `:aim nil :goal nil :side nil :wait 0 :waited false`.

### 3.4 `step` (changed; behaviour without `:aim`, `:goal` or `:wait` is unchanged)

1. A route with fewer than 2 points → `w` (identical).
2. **Pausing:** as before, and also `:vel [0 0]`.
3. **Waiting** (`:wait > 0`, not pausing): `p = wait − dt'`.
   - `p > 0` → `:wait p`.
   - Otherwise → `:wait 0 :waited true`.
   - Either way it sets `:vel [0 0]`, and `:pos`/`:heading` are unchanged.
4. **Moving:** `gp = (goal-point w)`, `dg = |gp − pos|`.
   - When `dg ≤ arrive-m` → `(arrive w)` with `:vel [0 0]`.
   - Otherwise it moves toward `sp = (steer-point w)`. With `ds = |sp − pos|` and `s = min(speed·dt', ds)`, it sets the new `:pos` and `:heading = (yaw dx dz)` toward `sp`. `:vel` is `unit·s/dt'`, or `[0 0]` when `dt' = 0`.
   - **No aim:** arrive when `dg − s ≤ arrive-m`, as today.
   - **With an aim:** arrive when `|gp − pos'| ≤ arrive-m`. Otherwise, when `ds − s ≤ arrive-m`, set `:aim nil`.

### 3.5 `follow` (changed)

- When `pos` is non-nil and `dt' > 0`: `:vel = (pos − old-pos)/dt'`, and then `:pos pos`. A nil `pos` keeps both. When `dt' = 0`, it sets `:pos pos` and keeps `:vel`.
- Single point, and pausing: as before.
- **Waiting:** as in `step` §3.4.3, without moving. `idle?` is ignored.
- **Moving:**
  - `dg` is measured to `(goal-point w)`. Arrive when `dg ≤ arrive-m`, or when `idle?` is true and `:aim` is nil.
  - Otherwise, when `:aim` is non-nil and `|aim − pos| ≤ arrive-m`, set `:aim nil`.

### 3.6 `(plan w others statics cfg)` → walker

`others` holds the other walkers' states. They become circles `{:at (:pos o) :r (:radius o) :vel (:vel o)}`, so a paused or waiting walker is a still circle.

1. Single point, or `:pause > 0` → `(assoc w :aim nil)`.
2. `G = (target w)` and `P = (:pos w)`. `g = (a/free-goal P G (:radius w) statics)`. `w1 = (assoc w :goal (when (not= g G) g))`.
3. When `|g − P| ≤ arrive-m` → `(assoc w1 :aim nil)`; `step`/`follow` will arrive.
4. `circles = (into statics other-circles)`. `b = (a/path-block P g speed horizon-s r circles)`.
   - When `b` is nil → `(assoc w1 :aim nil :side nil :wait 0 :waited false)`.
5. Blocked: `clear = (:clear b) + a/pad`.
   - Sides are tried in this order: `[:left :right]` when `(:side w)` is `:left`, else `[:right :left]`.
   - For each side, `D = (a/tangent-aim P (:at b) clear side)`. `D` is taken when it is not `(a/inside? D r statics)` and `(a/path-block P D speed horizon-s r circles)` is nil.
   - The first side taken → `(assoc w1 :aim D :side side :wait 0 :waited false)`.
6. Both sides blocked:
   - `(:waited w)` → `(arrive w1)`: gives up the waypoint; pause or next target, with the state cleared.
   - else, `(:wait w) > 0` → `(assoc w1 :aim nil)`: keeps waiting.
   - otherwise → `(assoc w1 :aim nil :side nil :wait wait-s)`.

### 3.7 Batch and cadence

- **`(plan-all walkers statics cfg)`** → `(vec (map-indexed (fn [i w] (plan w <every other walker of the same input vector> statics cfg)) walkers))`. Each walker plans against the same snapshot.
- **`(replan before after statics cfg dt acc)`** → `[walkers' acc']`.
  - `after` is `before` already stepped or followed.
  - When `acc + dt ≥ plan-every-s − 1e-9`, or some walker's `:leg` differs between `before` and `after` → `[(plan-all after statics cfg) 0]`.
  - Otherwise → `[after (+ acc dt)]`.
- **`(advance walkers statics cfg dt acc)`** → `(replan walkers (mapv #(step % (:speed cfg) dt) walkers) statics cfg dt acc)`.
- **`(detours walkers)`** → the count with a non-nil `:aim`.

### 3.8 `(steer-command sent w)` (for `?bricks`)

`sent` is the last command given: nil, `[:stop]` or `[:move [x z]]`.

| Case | Result |
|---|---|
| The route has fewer than 2 points, or `:pause > 0` | nil |
| `:wait > 0` | nil when `sent` = `[:stop]`, else `[:stop]` |
| Otherwise, with `p = (steer-point w)` | nil when `sent` is `[:move q]` with `|q − p| ≤ resend-m`, else `[:move p]` |

### 3.9 `hud-text` (extended) and `paths-flag?`

- When `hud-text`'s existing text is non-nil and `(:detours status)` is a number ≥ 1, append `" · " n " detour"`, plus `"s"` unless `n` = 1. Everything else is unchanged.
- **`(paths-flag? search)`** works like `bricks-flag?`, for the `paths` param.

## 4. `pm.bricks` changes (builder; interop, no rules)

- **State.** `start!` also stores `:statics (a/statics world)`, `:pcfg {:speed (:speed cfg) :horizon-s (:horizon-s (m/avoid-config world))}`, `:plan-acc 0` and per view `:sent nil`.
- **Kinematic tick:** `[ws acc] = (m/advance walkers statics pcfg dt plan-acc)`, then `settle` each walker. Rendering is unchanged.
- **Bricks tick:**
  - `before` = the walkers; `after` = `follow` each (as today);
  - `[ws acc] = (m/replan before after statics pcfg dt plan-acc)`, then `settle`;
  - per walker, `cmd = (m/steer-command (:sent view) w)`. `[:move [x z]]` → `(.moveTo c (t/vec3 [x (:y w) z]) "walk")`, and `[:stop]` → `(.stop c)`. Store `:sent cmd` when `cmd` is non-nil;
  - then `update`, the y offset and the anchor, as today;
  - **remove the `new-target?`/`moveTo` call**, which `steer-command` replaces.
- **Swap-in:** replace the `(zero? (:pause w))` `moveTo` with `cmd = (m/steer-command nil w)`. Issue it as above and store it as `:sent`; this is still issued **at** swap-in, so `follow`'s `idle?` can't fake an arrival on the first frame. `fallback!` clears `:sent`.
- **HUD:** each tick sets `(:detours status)` to `(m/detours walkers)`. `hud-segment` is unchanged, and `pm.core` needs no change.
- **`?paths`** (when `(m/paths-flag? search)` at `start!`): build a `pm-paths` group under the root.
  - **Each static:** one `t/polyline` of `(a/circle-points at r 24)`, colour `:path-static`, at `y` = the ground height under its centre (`m/ground-height` of `t/down-hits`, else 0) + 0.05.
  - **Each walker:** a circle of its `:radius` (`:path-walker`), a 2-point line pos → `(m/goal-point w)` (`:path-walker`), and a 2-point line pos → `(:aim w)` (`:path-aim`; both points = pos when aim is nil). All of these sit at the walker's `y + 0.05`.
  - Every frame, call `t/set-polyline!` on the walker lines.
  - Points are `[x y z]`.

## 5. `pm.three` and `pm.scene` additions (builder)

| Signature | Does |
|---|---|
| `(polyline points color)` | A `THREE.Line` with a `BufferGeometry` whose position attribute holds `points` (`[[x y z] …]`, flattened into a `Float32Array`). It uses a `LineBasicMaterial` `{color, depthTest false, transparent true, opacity 0.9}` and `renderOrder 999`. Returns the line. |
| `(set-polyline! line points)` | When the point count is the same, it rewrites the position array in place and sets `needsUpdate`. Otherwise it swaps in a new geometry and disposes the old one. Returns `line`. |

`pm.scene/palette` gets exactly these: `:path-walker 0x4fd1c5`, `:path-static 0xf6ad55`, `:path-aim 0xf56565`.

## 6. Test cases (`test/pm/avoid_test.cljs`)

The test requires `[cljs.test :refer [deftest is]] [pm.avoid :as a] [pm.motion :as m] [pm.world :as w]`.

**Helpers.**
- `W = (w/load-estate w/estate-edn)`, `cfg = {:speed 1.2 :horizon-s 2}`.
- `near?`/`vnear?`: tolerance 1e-6.
- `(mv e)` = `(assoc (m/walker e) :pause 0 :to 1 :leg 1)`: a walker already on its first leg.
- `(sim ws statics n dt)` iterates `m/advance` n times from `acc` 0. It returns the final walkers, the minimum over frames of the distance between walkers 0 and 1, and the minimum over frames and walkers of `|pos − c| − R − radius` over the statics.

**Fixtures.**

```clojure
L   = (mv {:id :l :route [[0 0] [8 0]] :pause-s 1})
F   = (m/walker {:id :f :route [[0 0] [3 4] [6 0]] :pause-s 2})
Fm  = (assoc F :pause 0 :to 1 :leg 1)
S1  = [{:at [4 0] :r 0.5}]
S2  = [{:at [4 0] :r 0.5} {:at [4.3 1.5] :r 0.4}]
B   = (m/walker {:id :b :route [[4.3 -1.5] [9 -9]] :pause-s 50})   ; standing at [4.3 -1.5], paused
```

### Footprints and statics

1. With `I = {:at [1 0 2] :yaw 90 :scale 2 :footprint [[0.5 0 0.25] [0 1 0.1]]}`, `(a/footprint I)` ≈ `[{:at [1 1] :r 0.5} {:at [3 2] :r 0.2}]`.
2. `(a/footprint {:at [1 0 2] :footprint [[0.5 0 0.25]]})` ≈ `[{:at [1.5 2] :r 0.25}]` (no yaw, no scale). `(a/footprint (assoc I :yaw 180 :scale 1))` gives a first circle ≈ `{:at [0.5 2] :r 0.25}`.
3. `(a/rotate [0.5 0] 90)` ≈ `[0 -0.5]`; `(a/rotate [0 1] 90)` ≈ `[1 0]`; `(a/rotate [1 0] -90)` ≈ `[0 1]`.
4. `(a/footprint {:at [0 0 0] :footprint [[1 2] [0 0 -1] ["a" 0 1] [0 0 1]]})` ≈ `[{:at [0 0] :r 1}]`. `(a/footprint {:footprint [[0 0 1]]})` = `[]`. `(a/footprint {:at [0 0 0]})` = `[]`.
5. `(a/statics W)` has count 6, ≈ in order:
   ```clojure
   {:at [1.4 -15.7] :r 0.35}   ; jar
   {:at [-5 -10.75] :r 0.5}  {:at [-5 -10] :r 0.5}  {:at [-5 -9.4] :r 0.45}   ; Juniper
   {:at [-4.8 -8.2] :r 0.7}  {:at [-4.3 -12] :r 0.6}                           ; rocks
   ```
6. `(a/statics {:motion {:obstacles [{:at [1 2] :r 0.5} {:at [1] :r 1} {:at [0 0] :r 0}]}})` = `[{:at [1 2] :r 0.5}]`. `(a/statics {})` = `[]`.
7. `(m/avoid-config W)` = `{:radius 0.3 :horizon-s 2}`; `(m/avoid-config {})` = `{:radius 0.3 :horizon-s 2}`.
8. `(mapv :radius (m/walkers {:motion {:radius 0.5} :walkers [{:id :a :route [[0 0]]} {:id :b :route [[0 0]] :radius 0.4}]}))` = `[0.5 0.4]`. `(:radius F)` = 0.3. A new walker has `:vel [0 0] :aim nil :goal nil :side nil :wait 0 :waited false`.

### Geometry

9. `(a/first-block [2 0] [1.2 0] 2 0.3 S1)` → `:t` ≈ 1.6666667, `:at` ≈ `[4 0]`, `:clear` ≈ 0.95, `:dist` ≈ 0.
10. Head-on mover: `(a/first-block [0 0] [1.2 0] 2 0.3 [{:at [4 0] :r 0.3 :vel [-1.2 0]}])` → `:t` ≈ 1.6666667, `:at` ≈ `[2 0]`, `:clear` ≈ 0.75.
11. All of these give nil, with `(a/first-block [0 0] [1.2 0] 2 0.3 [c])`:
    - behind: `{:at [-1 0] :r 0.3}`;
    - inside but moving away: `{:at [-0.5 0] :r 0.3}`;
    - beyond the horizon: `{:at [4 0] :r 0.5}` (closest at 2.4 m, 1.6 m away);
    - the same velocity: `{:at [1 0] :r 0.3 :vel [1.2 0]}`;
    - a side gap of 0.96: `{:at [2 0.96] :r 0.5}`.

    With a side gap of 0.94, `{:at [2 0.94] :r 0.5}` blocks.
12. The earliest of two: `(a/first-block [2 0] [1.2 0] 2 0.3 [{:at [4 0] :r 0.5} {:at [3 0] :r 0.5}])` → `:at` ≈ `[3 0]`, `:t` ≈ 0.8333333.
13. `(a/path-block [2 0] [4.3424073 -1.4449073] 1.2 2 0.3 [{:at [4.3 -1.5] :r 0.3}])` → `:t` ≈ 2, `:dist` ≈ 0.3518969. `(a/path-block [1 1] [1 1] 1.2 2 0.3 S1)` is nil.
14. `(a/tangent-aim [0 0] [5 0] 3 :right)` ≈ `[5.6 4.2]`; `:left` ≈ `[5.6 -4.2]` (3-4-5: sinα 0.6, len 4 + 3).
15. Inside the padded circle: `(a/tangent-aim [0 0] [0.5 0] 1.05 :right)` ≈ `[0 1.05]`; `:left` ≈ `[0 -1.05]`. `(a/tangent-aim [1 1] [1 1] 1 :right)` ≈ `[1 2]`.
16. `(a/tangent-aim [2 0] [4 0] 1.05 :right)` ≈ `[4.3424073 1.4449073]`, from sinα = 0.525, cosα = 0.8511022, len = 1.7022044 + 1.05.
17. `(a/free-goal [0 0] [5 0] 0.3 [{:at [5.2 0] :r 0.5}])` ≈ `[4.2 0]` (k = −0.2 + √1.0 = 0.8).
18. `(a/free-goal [0 0] [5 0] 0.3 [{:at [5.2 0] :r 0.5} {:at [4.0 0.3] :r 0.2}])` ≈ `[3.3675445 0]` (second k = 0.2 + √0.4).
19. `(a/free-goal [0 0] [1 0] 0.3 [{:at [1 0] :r 2}])` = `[0 0]` (it went behind p). `(a/free-goal [0 0] [5 0] 0.3 [{:at [5 3] :r 0.5}])` = `[5 0]`.
20. `(a/inside? [4.3424073 1.4449073] 0.3 [{:at [4.3 1.5] :r 0.4}])` is true (0.0695 < 0.85). `(a/inside? [0 0] 0.3 S1)` is false.
21. `(a/circle-points [1 2] 0.5 4)` ≈ `[[1.5 2] [1 2.5] [0.5 2] [1 1.5] [1.5 2]]`, and the last point `=` the first.

### Planner: clear, static, sides

22. A clear path makes no detour: `(m/plan (assoc L :pos [2 0]) [] [{:at [4 2] :r 0.5}] cfg)` → `:aim nil`, `:side nil`, `:wait 0`.
23. Out of the horizon yet: `(:aim (m/plan L [] S1 cfg))` is nil.
24. A static on the line: `(m/plan (assoc L :pos [2 0]) [] S1 cfg)` → `:aim` ≈ `[4.3424073 1.4449073]`, `:side :right`, `:goal nil`.
25. Right blocked → left: `(m/plan (assoc L :pos [2 0]) [] S2 cfg)` → `:aim` ≈ `[4.3424073 -1.4449073]`, `:side :left`.
26. Sticky side: `(m/plan (assoc L :pos [2 0] :side :left) [] S1 cfg)` → `:aim` ≈ `[4.3424073 -1.4449073]`, `:side :left`.

### Planner: both blocked, wait, go, give up

27. Both blocked: `P5 = (m/plan (assoc L :pos [2 0]) [B] S2 cfg)` → `:aim nil`, `:wait 2`, `:waited false`. The right side is inside S2's second circle; the left path hits B (case 13).
28. `Wt = (m/step P5 1.2 0.5)` → `:wait` 1.5, `:pos [2 0]`, `:vel [0 0]`. `(m/plan Wt [B] S2 cfg)` → `:wait` 1.5, `:aim nil` (still waiting).
29. It goes once B has gone: `(m/plan Wt [(assoc B :pos [20 20])] S2 cfg)` → `:aim` ≈ `[4.3424073 -1.4449073]`, `:side :left`, `:wait 0`.
30. `Wx = (m/step Wt 1.2 2)` → `:wait 0`, `:waited true`. `(m/plan Wx [B] S2 cfg)` gives up: `:pause 1`, `:to 1`, `:leg 1`, `:aim nil`, `:waited false`.

### Planner: walkers and targets

31. Head-on:
    ```clojure
    A = (assoc (mv {:id :a :route [[0 0] [10 0]] :pause-s 100}) :vel [1.2 0])
    H = (assoc (mv {:id :h :route [[10 0] [0 0]] :pause-s 100}) :pos [4 0] :vel [-1.2 0])
    ```
    - `(m/plan A [H] [] cfg)` → `:aim` ≈ `[2.4081643 1.1306643]`, `:side :right`.
    - `(m/plan H [A] [] cfg)` → `:aim` ≈ `[1.5918357 -1.1306643]`, `:side :right`.

    The blocker is H predicted at `[2 0]`, with clear 0.85.
32. Crossing at a right angle:
    ```clojure
    X = (assoc (mv {:id :x :route [[0 0] [8 0]] :pause-s 100}) :vel [1.2 0])
    Y = (assoc (mv {:id :y :route [[4 -4] [4 4]] :pause-s 100}) :pos [2 -2] :vel [0 1.2])
    ```
    - `(m/plan Y [X] [] cfg)` → `:aim` ≈ `[3.5734476 0.1958377]`, `:side :left`.
    - `(m/plan X [Y] [] cfg)` → `:wait 2`, `:aim nil` (it gives way).
33. A target inside a footprint: `T = (mv {:id :t :route [[0 0] [5 0]] :pause-s 100})`. `(m/plan T [] [{:at [5.2 0] :r 0.5}] cfg)` → `:goal` ≈ `[4.2 0]`, `:aim nil`.
34. Real data, first walker on B→C: `(m/plan (assoc (first (m/walkers W)) :pos [-6 -10] :to 2 :dir 1 :pause 0 :leg 2) [] (a/statics W) cfg)` → `:aim` ≈ `[-6.1208569 -11.724002]`, `:side :left`.
    - The blocker is Juniper's nose circle `[-5 -10.75]`, with perpendicular distance 0.788 < 0.95.
    - The right point ≈ `[-4.378798 -9.4012569]` is inside his middle circle.
35. **The second walker, on the same leg** (`:to 0 :dir -1`): `(m/plan (assoc (second (m/walkers W)) :pos [-6 -10] :to 0 :dir -1 :pause 0 :leg 2) [] (a/statics W) cfg)` → `:aim` ≈ `[-6.1208569 -11.724002]`, `:side :left`.
36. Real data, A→B is clear: `(:aim (m/plan (assoc (first (m/walkers W)) :pause 0 :to 1 :leg 1) [] (a/statics W) cfg))` is nil.
37. `(m/plan F [] S1 cfg)` (paused) → `:aim nil` and `:pause 2`. `(m/plan (m/walker {:id :c :route [[0 0]]}) [] S1 cfg)` → `:aim nil`.

### `step` / `follow` with aims, goals and waits

38. `(m/step (assoc Fm :aim [0 2]) 1 1)` → `:pos` ≈ `[0 1]`, `:heading` ≈ −1.5707963, `:vel` ≈ `[0 1]`, `:aim [0 2]`, `:pause 0`.
39. `(m/step (assoc Fm :aim [0 1.2]) 1 1)` → `:pos` ≈ `[0 1]`, `:aim nil` (0.2 left).
40. `(m/step (assoc Fm :goal [0.6 0.8]) 1 0.8)` → `:pos` ≈ `[0.48 0.64]`, `:pause 2`, `:goal nil` (it arrived at the goal, not the target).
41. `(m/step (assoc Fm :pos [2.5 3.5] :aim [5 5]) 1 0.5)` → `:pos` ≈ `[2.9287465 3.7572479]`, `:pause 2`, `:aim nil` (it came within 0.253 of the goal while detouring).
42. `(m/step (assoc Fm :goal [0.1 0]) 1 0.1)` → `:pause 2`, `:pos [0 0]`.
43. `(:vel (m/step Fm 1 1))` ≈ `[0.6 0.8]`. `(:vel (m/step F 1 0.5))` = `[0 0]`.
44. `(m/step (assoc Fm :wait 2 :vel [1 0]) 1 0.5)` → `:wait` 1.5, `:pos [0 0]`, `:vel [0 0]`, `:waited false`. Stepping that with dt 2 → `:wait 0`, `:waited true`.
45. Pick clears the state: `(m/step (assoc F :aim [9 9] :goal [1 1] :side :left :waited true) 1 2)` → `:to 1`, `:aim nil`, `:goal nil`, `:side nil`, `:waited false`.
46. `(m/follow (assoc Fm :aim [0 2]) [0 0.5] true 0.5)` → `:pause 0` (an aim blocks the idle arrival), `:vel` ≈ `[0 1]`, `:pos [0 0.5]`.
47. `(m/follow (assoc Fm :aim [0 2]) [0 1.8] false 0.1)` → `:aim nil`, `:pause 0`.
48. `(m/follow (assoc Fm :wait 1) [0 0] true 0.5)` → `:wait` 0.5, `:pause 0`. `(m/follow (assoc Fm :wait 0.4) [0 0] true 0.5)` → `:wait 0`, `:waited true`, `:pause 0`.
49. `(m/follow (assoc Fm :goal [1 1]) [0.9 0.9] false 0.1)` → `:pause 2`. `(:vel (m/follow (assoc Fm :vel [5 5]) [1 1] false 0))` = `[5 5]`.

### Cadence and commands

50. `(m/advance [L] [] cfg 0.1 0)` → walker `:pos` ≈ `[0.12 0]`, acc ≈ 0.1. `(second (m/advance [L] [] cfg 0.1 0.2))` = 0 (it planned).
51. A leg change plans at once: `(m/advance [(assoc (m/walker {:id :l :route [[0 0] [8 0]] :pause-s 1}) :pause 0.05)] [] cfg 0.1 0)` → acc 0, `:leg 1`.
52. `(m/advance [(assoc L :pos [1.9 0])] S1 cfg 0.25 0)` → `:pos` ≈ `[2.2 0]`, `:aim` ≈ `[4.2403445 1.4653445]`, `:side :right`, acc 0.
53. `(m/detours [(assoc F :aim [1 1]) F (assoc F :aim [2 2])])` = 2. `(m/detours [])` = 0.
54. `(m/steer-command nil Fm)` = `[:move [3 4]]`. `(m/steer-command [:move [3 4]] Fm)` = nil. `(m/steer-command [:move [3.3 4.3]] Fm)` = nil (0.424 ≤ 0.5). `(m/steer-command [:move [3.4 4.4]] Fm)` = `[:move [3 4]]` (0.566 > 0.5).
55. `(m/steer-command [:move [3 4]] (assoc Fm :aim [1 2]))` = `[:move [1 2]]`. `(m/steer-command nil (assoc Fm :goal [2.5 3.5]))` = `[:move [2.5 3.5]]`.
56. `(m/steer-command [:move [3 4]] (assoc Fm :wait 1))` = `[:stop]`. `(m/steer-command [:stop] (assoc Fm :wait 1))` = nil. `(m/steer-command nil F)` = nil (paused).

### HUD and flag

57. `(m/hud-text {:mode :kinematic :detours 1})` = `"motion: kinematic · 1 detour"`; `:detours 2` → `"motion: kinematic · 2 detours"`; `:detours 0` → `"motion: kinematic"`.
58. `(m/hud-text {:mode :bricks :backend "webgpu" :walkers 2 :gen-ms 420.4 :detours 1})` = `"motion: bricks webgpu · 2 walkers · gen 420 ms · 1 detour"`. `(m/hud-text {:mode :failed :reason "x" :detours 1})` = `"motion: kinematic (bricks failed: x) · 1 detour"`. `(m/hud-text {:mode :off :detours 1})` is nil.
59. `(m/paths-flag? "?paths")` is true; `"?bricks&paths"` is true; `"?bricks"` is false; `nil` is false.

### Scenarios (`sim`, dt 0.05, plan every 0.25 s)

60. **Head-on.** `[(mv {:id :a :route [[0 0] [10 0]] :pause-s 100}) (mv {:id :h :route [[10 0] [0 0]] :pause-s 100})]`, no statics, 240 frames.
    - The minimum distance is ≥ 0.6 (the model gives 0.939).
    - On the first frame where `(- xa xh)` changes sign (or hits 0), `za > zh`: both passed on their right (model: 0.487 vs −0.487).
    - At the end both have `:pause > 0`, `:to 1`, and are ≤ 0.3 from their targets.
61. **Crossing.** `[(mv {:id :x :route [[0 0] [8 0]] :pause-s 100}) (mv {:id :y :route [[4 -4] [4 4]] :pause-s 100})]`, 240 frames. The minimum distance is ≥ 0.6 (model 0.858), and both arrive.
62. **Static on the line.** `[(mv {:id :l :route [[0 0] [8 0]] :pause-s 100})]` with `S1`, 200 frames. The minimum static gap is ≥ 0 (model 0.240), and it arrives (≤ 0.3 from `[8 0]`, paused).
63. **Right blocked.** `[(assoc (mv {:id :l :route [[0 0] [8 0]] :pause-s 100}) :pos [2 0])]` with `S2`, 200 frames. The minimum static gap is ≥ 0 (model 0.240), and it arrives.
64. **Target inside.** `[T]` (case 33) with `[{:at [5.2 0] :r 0.5}]`, 120 frames. It ends paused with `:to 1`, x between 3.84 and 3.96, z ≈ 0, and a static gap ≥ 0. It never reaches the real target.
65. **Real estate, several laps, both walkers.** `(m/walkers W)` with `(a/statics W)`, 2400 frames (120 s).
    - The minimum walker distance is ≥ 0.6 (model 0.754), and the minimum static gap is ≥ 0 (model 0.153).
    - Each walker's first 8 picked `:to`s (the `:to` whenever `:leg` changes) are `[1 2 1 0 1 2 1 0]`.
    - Each final `:leg` is ≥ 12 (model 16 and 14).

## 7. Live check (Hal, both modes)

- Over several laps the walkers never overlap each other, Juniper or the rocks, and they keep their routes.
- When they meet head-on, each steps to its own right.
- The HUD shows `· 1 detour` / `· 2 detours` while they go around.
- `?paths` shows the walker circles, the goal and aim lines, and the footprint circles.
- With `?bricks`, detours become fresh `moveTo`s, re-sent only when the point moves by more than 0.5 m. A wait sends `stop`.
