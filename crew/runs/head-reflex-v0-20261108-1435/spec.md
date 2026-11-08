# Spec: head-reflex-v0 (the foal lifts its head and looks at the world)

Quest: `.crew/quests/head-reflex-v0.md`. Builds on pony-spike-v0 (commit `44a586d`; its spec
`crew/runs/pony-spike-v0-20261108-1308/spec.md` still holds for everything not changed here).
Architect: un-architecte, 2026-11-08. Builder: un-automate-monde. Tester: le-testeur.

Every name, signature, string and number below is binding. Where an exact string is shown
(problem text, XML line, HUD), match it character for character.

**Checked in real MuJoCo before writing.** The architect ran both new bodies in
`@mujoco/mujoco` 4.0.0 under Node 26, with the exact algorithm of §5, in a scratch script:
- **Counts and masses:** 12 / 13 / 12 / 19 / 18 / 12, and the masses are unchanged to 6 decimals.
- **Zero controls:** both still stand (torso 0.8391 m / 0.4392 m).
- **Jacobians:** every hand-worked column in §7.3 R8 matches.
- **Dropped on either side with the legs at zero:**

| | reflex on: clearance | reflex on: tilt | reflex off: clearance | reflex off: tilt |
|---|---|---|---|---|
| horse | 0.244 m (need ≥ 0.175) | 1.2° | 0.022 m | 89.1° |
| dog | 0.141 m (need ≥ 0.105) | 0.6° | 0.021 m | 96.7° |

  There was no NaN in any run. With the legs kicking at random, the horse head held within 2°
  and the dog within 10°.

Two findings that shaped the design:
1. **Joint order matters.** In MuJoCo the first joint listed in a body is nearest the parent.
   Lying on its side, the neck's side-bend is the joint that lifts the head. With it listed
   after the pitch, it can only swing the neck's short sideways component, and the head stalled
   at 0.09 m. With side-bend listed first, the pitch can aim the neck forward and the bend then
   lifts its full length.
2. **`mj_jacBody` silently ignores a `Float64Array`.** See §5.6. A plain JS array throws.

---

## 1. What changes

```
resources/creatures/{horse,dog}.edn   neck + head get :joints (2 hinges each); a :reflexes vector
        │
pm.creature (cljc)        :joints form, :reflexes validation, (reflexes sp) compiled, :roll placement
        │
pm.creature-motion (cljs) reflex math (pure, plain arrays), the :reflex controller layer, HUD head
        │
pm.creature-spike         mj_forward once, mj_jacBody per step into DoubleBuffers, builds obs
```

No new namespaces. `pm.core`, `pm.three`, `pm.species`, `resources/estate.edn`, `bb.edn` and
`scripts/creature_mjcf.clj` stay unchanged. The game's placement has no `:roll`, so its drop is
as before. Nothing in code names a species, body or joint: the reflex reaches `:head`, `:neck-*`
and `:head-*` only through the species data.

## 2. Who touches what

| Path | Builder (un-automate-monde) | Tester (le-testeur) |
|---|---|---|
| `src/pm/creature.cljc` | edit (§4) | read only |
| `src/pm/creature_motion.cljs` | edit (§5) | read only |
| `src/pm/creature_spike.cljs` | edit (§6) | — |
| `resources/creatures/horse.edn`, `dog.edn` | edit, verbatim from §3 | read only |
| `test/pm/creature_test.cljs` | — | update C6, C9, C14, C15, C20, C21 (§7.1); add C28–C40 |
| `test/pm/creature_motion_test.cljs` | — | update M12 (§7.2) |
| `test/pm/creature_reflex_test.cljs` | — | **new**: X1–X14 (§7.2) |
| `test/pm/creature_mujoco_test.cljs` | — | update R1, R5; add R6–R8 (§7.3) |

Nobody edits `package.json`, `public/vendor/`, `creatures/` (Hal regenerates
`creatures/bodies/*.xml`), `docs/GDD.md`, `assets/private/`, `public/kit/`, `pm.core`,
`pm.three`, `pm.species`, the estate or `bb.edn`. The architect writes only this file. Nobody
commits.

---

## 3. The species files

Only the `:neck` and `:head` bodies change, and a `:reflexes` key is added at the top level.
Geometry is unchanged, so the masses are unchanged (§3.3). Both files keep every other line
as is.

### 3.1 horse.edn: replace the neck and head bodies, add `:reflexes`

```clojure
  {:name :neck :parent :torso :at [0.3 0 0.08]
   ;; several hinges: listed order = MuJoCo order, first = nearest the torso. Side-bend first:
   ;; lying on its side, the bend is what lifts the head, so the pitch must act after it.
   :joints [{:name :neck-bend  :axis [0 0 1] :range [-70 70]}   ; lateral flexion
            {:name :neck-pitch :axis [0 1 0] :range [-40 45]}]  ; + = head forward/down
   :capsule {:from [0 0 0] :to [0.18 0 0.24] :r 0.06} :color :dark}
  {:name :head :parent :neck :at [0.18 0 0.24]
   :joints [{:name :head-pitch :axis [0 1 0] :range [-60 60]}   ; poll flexion
            {:name :head-roll  :axis [1 0 0] :range [-70 70]}]  ; atlas/axis rotation
   :capsule {:from [0 0 0] :to [0.24 0 -0.1] :r 0.07} :color :coat}
```

Add this top-level key after `:bodies`:

```clojure
 ;; Reflexes: hand-written controllers layered over the twitch, per species (pm.creature-motion).
 ;; :head-level: lift :body's lowest point :lift-m above the ground and turn its :up axis to world up,
 ;; by Jacobian transpose on :joints only; strength ramps 0 -> 1 over :onset-s after the drop.
 :reflexes [{:kind :head-level :body :head
             :joints [:neck-bend :neck-pitch :head-pitch :head-roll]
             :up [0 0 1] :lift-m 0.25 :gain 2 :w-lift 40 :onset-s 1.5}]
```

### 3.2 dog.edn: the same change with the dog's own geometry

```clojure
  {:name :neck :parent :torso :at [0.2 0 0.04]
   ;; several hinges: listed order = MuJoCo order, first = nearest the torso. Side-bend first:
   ;; lying on its side, the bend is what lifts the head, so the pitch must act after it.
   :joints [{:name :neck-bend  :axis [0 0 1] :range [-70 70]}   ; lateral flexion
            {:name :neck-pitch :axis [0 1 0] :range [-40 45]}]  ; + = head forward/down
   :capsule {:from [0 0 0] :to [0.09 0 0.12] :r 0.045} :color :coat}
  {:name :head :parent :neck :at [0.09 0 0.12]
   :joints [{:name :head-pitch :axis [0 1 0] :range [-60 60]}   ; poll flexion
            {:name :head-roll  :axis [1 0 0] :range [-70 70]}]  ; atlas/axis rotation
   :capsule {:from [0 0 0] :to [0.12 0 -0.05] :r 0.05} :color :dark}
```

```clojure
 ;; (same comment as horse.edn)
 :reflexes [{:kind :head-level :body :head
             :joints [:neck-bend :neck-pitch :head-pitch :head-roll]
             :up [0 0 1] :lift-m 0.15 :gain 2 :w-lift 40 :onset-s 1.5}]
```

### 3.3 Hand-worked numbers (both species)

- **Hinge order** (= actuator order `k`, qpos `7 + k`, dof `6 + k`, because the root is always
  a free joint with 7 qpos and 6 dofs):
  0 neck-bend · 1 neck-pitch · 2 head-pitch · 3 head-roll · 4 fl-upper · 5 fl-lower ·
  6 fr-upper · 7 fr-lower · 8 hl-upper · 9 hl-lower · 10 hr-upper · 11 hr-lower.
  Real MuJoCo: `jnt_qposadr` = 0,7,8,…,18 and `jnt_dofadr` = 0,6,7,…,17.
- **Counts:** 11 bodies and 12 hinges, so `nbody 12 · njnt 13 · nu 12 · nq 19 · nv 18 · ngeom 12`.
- **Masses** are as in pony-spike-v0: horse 56.548668, 4.297699, 5.439144, then 3.508112 /
  2.127905 ×4, total 88.829579; dog 18.011798, 1.335962, 1.544616, then 0.949285 / 0.622035 ×4,
  total 27.177656.
- **Ranges in radians** (6 decimals): ±70° → ±1.22173, −40° → −0.698132, 45° → 0.785398,
  ±60° → ±1.047198. The legs are unchanged.
- **Head body id:** DFS index 2, so MuJoCo body id 3.

---

## 4. `pm.creature` (cljc) changes

### 4.1 Joints: two forms

A non-root body has **either** `:joint {:axis :range}` (one hinge, named after the body, as
today) **or** `:joints [{:name kw :axis :range} ...]` (one or more hinges, in listed order), or
neither (welded).

- **`(body-joints body)`** → `[{:name kw :axis v :range [lo-deg hi-deg]} ...]`. A `:joints`
  vector (if present) comes back as listed. A `:joint` gives `[{:name (:name body) ...}]`. With
  neither, the result is `[]`.
- **`(joints sp)`** → `[{:name :body :axis :range [lo-rad hi-rad]} ...]`: the bodies in DFS
  order, each body's `body-joints` in order. This adds the `:body` key to the existing map.
  `ctrl-ranges` and `counts` are unchanged in definition.
- **`mjcf`** writes one `<joint .../>` line per `body-joints` entry, in order, before the body's
  geom, using the entry's `:name`. It writes one `<position>` per `joints` entry, named by the
  joint name. The line format is unchanged.

### 4.2 Validation additions (`problems`)

The order of checks is: top level (P1–P5), P6, then **for each body in vector order**: P7, P8,
P9, P10, P11, P12, P13, P14, **P16, P17, P18**. After all bodies come **P19**, then P15, then the
**reflex checks Q1–Q7**. Existing texts are unchanged. P7 now also fires when the root has
`:joints`. P15 counts a body with `:joint` or a non-empty `:joints`.

| # | rule | text |
|---|---|---|
| P16 | not both `:joint` and `:joints` | `"body <n>: use :joint or :joints, not both"` (the body's joints are then its `:joints`) |
| P17 | `:joints`, if present, is a non-empty vector | `"body <n>: :joints must be a non-empty vector"` |
| P18 | each `:joints` entry `j` (vector index) is a map with a keyword `:name`, a non-zero `:axis` of 3 numbers and a valid `:range` (as P14) | `"body <n>: joint <j>: needs a keyword :name, a non-zero :axis [x y z] and :range [lo hi] with -180 <= lo < hi <= 180"` |
| P19 | joint names are unique across the species: every non-root body's joint names (entries with a keyword `:name` only), in `:bodies` vector order then listed order. Each repeat is reported where it occurs. | `"joint <jn>: duplicate name"` |
| Q1 | `:reflexes`, if present, is a vector of maps (else no other Q check runs) | `":reflexes must be a vector of maps"` |
| Q2 | `:kind` is `:head-level` | `"reflex <i>: unknown :kind <k>"` |
| Q3 | `:body` names a body | `"reflex <i>: unknown body <b>"` |
| Q4 | `:joints` is a non-empty vector (else Q5 is skipped) | `"reflex <i>: :joints must be a non-empty vector of joint names"` |
| Q5 | each entry names a joint (the P19 name set) | `"reflex <i>: unknown joint <j>"` (one per unknown entry, in order) |
| Q6 | `:up`, if present, is 3 numbers, not all 0 | `"reflex <i>: :up must be a non-zero [x y z]"` |
| Q7 | in this order: `:lift-m` number ≥ 0, `:gain` number > 0, `:w-lift` number ≥ 0, `:onset-s` number ≥ 0 (all required) | `"reflex <i>: :lift-m must be a number >= 0"`, `"reflex <i>: :gain must be a positive number"`, `"reflex <i>: :w-lift must be a number >= 0"`, `"reflex <i>: :onset-s must be a number >= 0"` |

`<i>` is the reflex's vector index. `<n>`, `<jn>`, `<k>`, `<b>` and `<j>` are `pr-str`. No
`:reflexes` key at all is valid. `parse` and `check!` throw with the first problem as before,
for example `"bad species :dog: reflex 0: unknown body :tail"`.

### 4.3 Compiled reflexes

**`(reflexes sp)`** → a vector (`[]` when there is no `:reflexes` key), one map per reflex,
with indices resolved from `joints`. It assumes `sp` is valid.

```clojure
{:kind :head-level :body :head :body-id 3            ; DFS index + 1
 :capsule {:from [0 0 0] :to [0.24 0 -0.1] :r 0.07}  ; the body's capsule (MuJoCo body frame)
 :up [0 0 1]                                          ; default [0 0 1]
 :joints [:neck-bend :neck-pitch :head-pitch :head-roll]
 :acts [0 1 2 3]                                      ; k = index in (joints sp), in :joints order
 :qpos [7 8 9 10]                                     ; 7 + k
 :dofs [6 7 8 9]                                      ; 6 + k
 :ranges [[-1.22173 1.22173] [-0.698132 0.785398] [-1.047198 1.047198] [-1.22173 1.22173]]
 :lift-m 0.25 :gain 2 :w-lift 40 :onset-s 1.5}
```

### 4.4 Rolled placement (for the tests; the game never passes `:roll`)

A placement may carry `:roll` (degrees, default 0): a rotation about the body's own x
(forward), applied before `:yaw`.
- **`(rest-bottom sp)`** = `(rest-bottom sp 0)`. **`(rest-bottom sp roll-deg)`** → the minimum
  over bodies, over capsule ends `p`, of `(oy + py)·sin φ + (oz + pz)·cos φ − r`, where
  `[ox oy oz]` is the body origin (the sum of `:at` along the chain) and `φ = roll × π/180`.
- **`spawn`** uses `(rest-bottom sp (or (:roll place) 0))`. Its `:quat` = q_yaw ⊗ q_roll =
  `[cy·cr, cy·sr, sy·sr, cr·sy]`, where `cy = cos(ψ/2)`, `sy = sin(ψ/2)`, `cr = cos(φ/2)` and
  `sr = sin(φ/2)`. With roll 0 this is today's `[cy 0 0 sy]`.
- Roll +90° puts the body's +y (left) up, so it lies on its right side.

---

## 5. `pm.creature-motion` (cljs) changes: the reflex

All array arguments named `xpos`, `xmat`, `jacp`, `jacr` and `qpos` are **JS arrays or typed
arrays read with `aget`** (MuJoCo views, or `#js [...]` in tests), never CLJS vectors. Small
vectors (`up`, `q`, ranges) are CLJS vectors. Matrices are row-major: `xmat` is 9 per body, and
a Jacobian is 3 × nv, so element (row r, dof d) is at `r·nv + d`.

### 5.1 Sensing (pure)

- **`(head-up xmat id up)`** → `[w0 w1 w2]`, where `wi = Σj xmat[9·id + 3i + j] · up[j]`: the
  body's local `up` axis in world frame.
- **`(tilt-deg u)`** → `acos(clamp(u[2], −1, 1)) × 180/π`, the angle from world up.
- **`(capsule-clearance xpos xmat id capsule ground-z)`** → the minimum over `p ∈ {from, to}` of
  `xpos[3id+2] + Σj xmat[9id + 6 + j]·p[j]`, minus `r`, minus `ground-z`: the lowest point of
  the body's capsule above the plane (a capsule's lowest point is at an end).
- **`(sense r xpos xmat jacp jacr ground-z)`** →
  `{:up (head-up xmat (:body-id r) (:up r)) :clearance (capsule-clearance xpos xmat (:body-id r) (:capsule r) ground-z) :jacp jacp :jacr jacr}`.

### 5.2 The math (pure)

**`(reflex-strength t onset-s)`** → `1` when `onset-s ≤ 0`, else `clamp(t / onset-s, 0, 1)`.

**`(reflex-targets r sense q strength nv)`** → a vector of joint targets (radians), one per
`(:joints r)`. `q` is the vector of those joints' current angles.
1. `[ux uy uz] = (:up sense)`; `e_rot = up × world_up = [uy, −ux, 0]`.
2. `e_lift = max(0, (:lift-m r) − (:clearance sense))`, along world up (row 2 of `jacp` only).
3. For each `i`, with `d = (:dofs r)[i]`:
   `g = Σ_{row=0..2} jacr[row·nv + d] · e_rot[row] + (:w-lift r) · jacp[2·nv + d] · e_lift`,
   then `target_i = clamp(q_i + strength · (:gain r) · g, lo_i, hi_i)`.

A level head gives `e_rot = 0`, and a head high enough gives `e_lift = 0`. Strength 0 gives
`target = q`: the joint is held where it is and goes limp under the servo. Because the position
servo's torque is `kp·(target − q) = kp · strength · gain · Jᵀe`, this is classic
Jacobian-transpose control and needs no integrator.

**`(target->action target [lo hi])`** → `(target − mid) / half`, the inverse of
`actions->ctrl`, where `mid = (lo+hi)/2` and `half = (hi−lo)/2`.

### 5.3 The controller layer (how it fits pm.motion's pattern)

pony-spike-v0 set up the pattern. The interop calls one pure `act` per physics step and gets
back `nu` actions in [−1, 1], the way `pm.bricks` asks `pm.motion` for a command each frame.
`act` dispatches on `(:kind ctrl)`, as the walker driver switches between `m/step` and
`m/follow`. The reflex is **a controller that wraps another controller**:

```clojure
(reflex-controller inner reflexes)
;; => {:kind :reflex :n (:n inner) :inner inner :reflexes reflexes :t 0}
```

**`act` gains a `:reflex` branch:**
1. `[inner' a] = (act inner obs dt)`. The inner controller (twitch) runs for **all** `n`
   actuators, so its random streams stay as they were (a seed still replays the legs).
2. `t' = t + dt`, with dt counting only when it is a finite number > 0.
3. If `obs` is nil, return `[ctrl' a]`: the layer is transparent.
4. Otherwise, for each reflex `r` at index `i`:
   - `sense = (nth (:senses obs) i)`
   - `q = (mapv #(aget (:qpos obs) %) (:qpos r))`
   - `s = (reflex-strength t' (:onset-s r))`
   - `targets = (reflex-targets r sense q s (:nv obs))`
   - Overwrite `a[(:acts r)[j]]` with `(target->action (targets j) ((:ranges r) j))`.

   These are the reflex's joints leaving the twitch: their noise is computed and discarded.
5. Return `[(assoc ctrl :inner inner' :t t') a']`.

`obs` = `{:nv nv :qpos qpos-array :senses [sense ...]}`, aligned with `(:reflexes ctrl)`.

A trained policy can later replace the inner controller (legs) or the whole layer, as one more
`:kind` branch. The interop, the stepping, `actions->ctrl`, the drawing and the HUD don't change.
The policy's observation ("only what the body can feel") goes in the same `obs` map.

### 5.4 HUD

`hud-text` `:running`: when `(:head status)` is a map `{:clearance c :tilt deg}`, append
`" · head " (.toFixed c 2) " m " (js/Math.round deg) "°"`. Without `:head`, the text is
unchanged.

### 5.5 Unchanged

Twitch, `fixed-steps`, stats, config/URL and `body-pose` are as before.

### 5.6 How these bindings want `mj_jacBody`'s arrays (pinned in Node, 4.0.0)

`mj_jacBody(model, data, jacp, jacr, bodyId)` takes **`mj.DoubleBuffer`** out-parameters:

```clojure
(let [^js jp (new (.-DoubleBuffer mj) (* 3 nv))   ; constructor takes a COUNT (zero-filled)
      ^js jr (new (.-DoubleBuffer mj) (* 3 nv))]
  (.mj_jacBody mj model data jp jr body-id)
  (.GetView jp)                                    ; Float64Array view of 3·nv, row-major
  (.delete jp) (.delete jr))                       ; when the model goes
```

What the architect observed:
- A **`Float64Array` is accepted without error but stays all zeros**: the binding copies it in
  and never back. This is the trap.
- A plain JS array throws `"[mj_jacBody] Invalid argument. Expected TypedArray or WasmBuffer"`.
- `(.-FromArray (.-DoubleBuffer mj))` on a zero `Float64Array` also works.
- The package README's `new DoubleBuffer([0,0,0])` throws `Cannot convert "0,0,0" to int`, and
  its `.getView()` does not exist. It is **`.GetView`**, capital G, as `mujoco.d.ts` says.
- Hand check: a hinge about y at the body origin, with a tip body 1 m along x, gives the tip
  `jacp [0 0 -1]` and `jacr [0 1 0]`. At q = 0.5 the tip `jacp` is
  `[-0.479426 0 -0.877583]`.

Re-read `(.GetView buf)` after each call. Never hold a view across frames, because the WASM
heap can grow.

---

## 6. `pm.creature-spike` changes (builder; not unit-tested)

- **`start!`**: right after `MjData`, call `(.mj_forward mj model data)` once. This fills
  `xpos`/`xmat` and the Jacobian inputs before the first step. Then `rx = (c/reflexes sp)`,
  `nv = (.-nv model)`, `tw = (cm/controller :twitch (count ranges) seed)`, and
  `:ctrl (if (seq rx) (cm/reflex-controller tw rx) tw)`. Create one pair of `DoubleBuffer`s of
  `3·nv` per reflex and store them as `:jacs [[jp jr] ...]`, with `:nv nv`.
- **`tick!`**: per physics step, **before** `act`, when `(:jacs @!state)` is non-empty: for each
  reflex `r` and its `[jp jr]`, call `(.mj_jacBody mj model data jp jr (:body-id r))`. Then
  build `obs {:nv nv :qpos (.-qpos data) :senses (mapv (fn [r [jp jr]] (cm/sense r (.-xpos data) (.-xmat data) (.GetView jp) (.GetView jr) ground-z)) rx jacs)}`
  (fresh views every step). Otherwise `obs` is nil. Then `act` → `actions->ctrl` → `ctrl` →
  `mj_step`, as now. After the loop, status gains
  `:head {:clearance (:clearance s0) :tilt (cm/tilt-deg (:up s0))}` from the last step's first
  sense, when there is one.
- **`fail!`**: `.delete` every `DoubleBuffer` (each in its own `try`) before data and model, and
  drop `:jacs`.
- Cost: 4 extra dofs and 1 `mj_jacBody` per step. Measured in Node, it stays well under 1 ms per
  frame.

---

## 7. Test cases

`horse` and `dog` are `(c/parse (get pm.species/texts …))`. "≈" means `|a − b| ≤ 1e-6` unless
stated. `f` is the existing fixture in `creature_test.cljs` (it has no `:reflexes`, so it stays
valid).

### 7.1 `test/pm/creature_test.cljs`

**Updated existing cases** (the new joints change them):
- **C6** `(c/problems (update horse :bodies conj (get-in horse [:bodies 1])))` =
  `["body :neck: duplicate name" "joint :neck-bend: duplicate name" "joint :neck-pitch: duplicate name"]`.
- **C9** `(c/problems (assoc horse :bodies []))` = `[":bodies must be a non-empty vector"]`.
  `(c/problems (-> horse (assoc :bodies [(get-in horse [:bodies 0])]) (dissoc :reflexes)))` =
  `["needs at least one :joint"]`.
- **C14** `(mapv :name (c/joints horse))` = `[:neck-bend :neck-pitch :head-pitch :head-roll :fl-upper :fl-lower :fr-upper :fr-lower :hl-upper :hl-lower :hr-upper :hr-lower]`.
  `(mapv :body (c/joints horse))` = `[:neck :neck :head :head :fl-upper :fl-lower :fr-upper :fr-lower :hl-upper :hl-lower :hr-upper :hr-lower]`.
  `(c/ctrl-ranges horse)` ≈ `[[-1.22173 1.22173] [-0.698132 0.785398] [-1.047198 1.047198] [-1.22173 1.22173] [-0.785398 0.785398] [-0.174533 1.745329] [-0.785398 0.785398] [-0.174533 1.745329] [-0.785398 0.785398] [-1.745329 0.174533] [-0.785398 0.785398] [-1.745329 0.174533]]`,
  and the dog's are equal. `(:axis (first (c/joints dog)))` = `[0 0 1]`, and
  `(:axis (nth (c/joints dog) 3))` = `[1 0 0]`.
- **C15** `(c/counts horse)` = `(c/counts dog)` = `{:nbody 12 :njnt 13 :nu 12 :nq 19 :nv 18 :ngeom 12}`.
  The fixture `f` is unchanged (`{:nbody 5 :njnt 3 :nu 2 :nq 9 :nv 8 :ngeom 5}`).
- **C20** (horse, place `{:at [4.5 1.79 -8.0] :yaw 0 :drop-m 0.4}`): `"<joint "` 12,
  `"<position "` 12, and `"<body "`, `"<freejoint "`, `"<geom "` and plane counts unchanged (11, 1,
  12, 1). Trimmed lines contain:
  `<joint name="neck-bend" type="hinge" axis="0 0 1" limited="true" range="-1.22173 1.22173" damping="5"/>`,
  `<joint name="neck-pitch" type="hinge" axis="0 1 0" limited="true" range="-0.698132 0.785398" damping="5"/>`,
  `<joint name="head-roll" type="hinge" axis="1 0 0" limited="true" range="-1.22173 1.22173" damping="5"/>`,
  `<position name="head-pitch" joint="head-pitch" kp="150" ctrllimited="true" ctrlrange="-1.047198 1.047198"/>`,
  and still the plane, torso (`pos="4.5 8 3.03" quat="1 0 0 0"`), hl-lower joint, option and head
  geom lines. The three lines after `<body name="neck" pos="0.3 0 0.08">` start, trimmed, with
  `<joint name="neck-bend"`, `<joint name="neck-pitch"`, `<geom name="neck"`. The line after
  `<body name="head" pos="0.18 0 0.24">` starts with `<joint name="head-pitch"`. **The old line
  `<position name="neck" ...>` is absent.**
- **C21** (dog): the same counts as C20. It contains
  `<joint name="neck-pitch" type="hinge" axis="0 1 0" limited="true" range="-0.698132 0.785398" damping="1.5"/>`
  and `<position name="neck-bend" joint="neck-bend" kp="40" ctrllimited="true" ctrlrange="-1.22173 1.22173"/>`.
  The torso, fl-upper geom and hr-lower position lines are as before.

All other existing C cases (C1–C5, C7, C8, C10–C13, C16–C19, C22–C27) must still pass unchanged.

**New cases:**
- **C28** Both forms are valid: `(c/problems horse)` = `(c/problems dog)` = `[]`. Horse uses
  `:joints` on neck and head and `:joint` on the legs. `(c/body-joints (get-in horse [:bodies 3]))` =
  `[{:name :fl-upper :axis [0 1 0] :range [-45 45]}]`, and
  `(mapv :name (c/body-joints (get-in horse [:bodies 1])))` = `[:neck-bend :neck-pitch]`.
  `(c/body-joints (get-in horse [:bodies 0]))` = `[]`.
- **C29** A bad `:joints` entry: `(c/problems (assoc-in horse [:bodies 1 :joints 1 :range] [45 -40]))` =
  `["body :neck: joint 1: needs a keyword :name, a non-zero :axis [x y z] and :range [lo hi] with -180 <= lo < hi <= 180"]`.
  `(c/problems (update-in horse [:bodies 2 :joints 0] dissoc :name))` =
  `["body :head: joint 0: needs a keyword :name, a non-zero :axis [x y z] and :range [lo hi] with -180 <= lo < hi <= 180" "reflex 0: unknown joint :head-pitch"]`.
  The same first text comes back for `[:bodies 2 :joints 1 :axis]` set to `[0 0 0]` (one problem).
- **C30** `(c/problems (assoc-in horse [:bodies 1 :joint] {:axis [0 1 0] :range [-10 10]}))` = `["body :neck: use :joint or :joints, not both"]`.
- **C31** `(c/problems (-> dog (dissoc :reflexes) (assoc-in [:bodies 1 :joints] [])))` = `["body :neck: :joints must be a non-empty vector"]`.
- **C32** `(c/problems (assoc-in horse [:bodies 1 :joints 0 :name] :fl-upper))` = `["joint :fl-upper: duplicate name" "reflex 0: unknown joint :neck-bend"]`.
  `(c/problems (assoc-in horse [:bodies 0 :joints] [{:name :x :axis [1 0 0] :range [-1 1]}]))` = `["root :torso must have no :parent, :at or :joint"]`.
- **C33** Reflex validation, each against `horse` (one problem each):
  - `[:reflexes 0 :body] :tail` → `["reflex 0: unknown body :tail"]`
  - `[:reflexes 0 :joints 2] :head-yaw` → `["reflex 0: unknown joint :head-yaw"]`
  - `[:reflexes 0 :kind] :tail-wag` → `["reflex 0: unknown :kind :tail-wag"]`
  - `[:reflexes 0 :joints] []` → `["reflex 0: :joints must be a non-empty vector of joint names"]`
  - `[:reflexes 0 :up] [0 0 0]` → `["reflex 0: :up must be a non-zero [x y z]"]`
  - `[:reflexes 0 :lift-m] -0.1` → `["reflex 0: :lift-m must be a number >= 0"]`
  - `[:reflexes 0 :gain] 0` → `["reflex 0: :gain must be a positive number"]`
  - `(update-in horse [:reflexes 0] dissoc :w-lift)` → `["reflex 0: :w-lift must be a number >= 0"]`
  - `[:reflexes 0 :onset-s] "1.5"` → `["reflex 0: :onset-s must be a number >= 0"]`
- **C34** `(c/problems (assoc horse :reflexes {:kind :head-level}))` = `[":reflexes must be a vector of maps"]`.
  `(c/problems (-> horse (assoc-in [:reflexes 0 :body] :tail) (assoc-in [:reflexes 0 :gain] -1)))` =
  `["reflex 0: unknown body :tail" "reflex 0: :gain must be a positive number"]`.
  `(c/problems (dissoc horse :reflexes))` = `[]` (no reflexes is valid).
- **C35** A second reflex: `r2 = {:kind :head-level :body :head :joints [:head-roll] :lift-m 0 :gain 1 :w-lift 0 :onset-s 0}`.
  `(c/problems (update horse :reflexes conj r2))` = `[]`. With `:body :nose`, it gives
  `["reflex 1: unknown body :nose"]`.
- **C36** `(c/parse (pr-str (assoc-in dog [:reflexes 0 :body] :tail)))` throws `"bad species :dog: reflex 0: unknown body :tail"`.
- **C37** `(c/reflexes horse)` = the map in §4.3 (`:ranges` ≈, everything else `=`).
  `(c/reflexes dog)` is the same except `:capsule {:from [0 0 0] :to [0.12 0 -0.05] :r 0.05}`
  and `:lift-m 0.15`. `(c/reflexes f)` = `[]`.
- **C38** Order follows the reflex's own `:joints`: with
  `(assoc-in horse [:reflexes 0 :joints] [:head-roll :neck-bend])`, `(c/reflexes …)` gives
  `:acts [3 0] :qpos [10 7] :dofs [9 6]` and `:ranges` ≈ `[[-1.22173 1.22173] [-1.22173 1.22173]]`.
  A reflex without `:up` gets `:up [0 0 1]`.
- **C39** Roll: `(c/rest-bottom horse 90)` ≈ −0.15, `(c/rest-bottom horse -90)` ≈ −0.15,
  `(c/rest-bottom dog 90)` ≈ −0.105, and `(c/rest-bottom horse 0)` ≈ `(c/rest-bottom horse)` ≈ −0.84.
  `(c/spawn horse {:at [4.5 1.79 -8.0] :yaw 0 :drop-m 0.4 :roll 90})` ≈
  `{:ground [4.5 8 1.79] :torso [4.5 8 2.34] :quat [0.707107 0.707107 0 0]}`. For the dog,
  `:torso` ≈ `[4.5 8 2.295]`. `(:quat (c/spawn dog {:at [0 0 0] :yaw 90 :drop-m 0.4 :roll 90}))` ≈ `[0.5 0.5 0.5 0.5]`.
- **C40** `(c/mjcf horse {:at [4.5 1.79 -8.0] :yaw 0 :drop-m 0.4 :roll 90})` contains the trimmed
  line `<body name="torso" pos="4.5 8 2.34" quat="0.707107 0.707107 0 0">`. With roll 0 or no
  `:roll`, it contains `<body name="torso" pos="4.5 8 3.03" quat="1 0 0 0">` (the game's drop is
  unchanged).

### 7.2 Pure reflex math: `test/pm/creature_reflex_test.cljs` (new), plus M12

**Update M12** (`creature_motion_test.cljs`):
`(cm/actions->ctrl (vec (repeat 12 0)) (c/ctrl-ranges horse))`, with first ≈ 0 (the neck-bend's
mid-range) and second ≈ 0.043633 (the neck-pitch's mid-range, 2.5°). The rest of M12 is
unchanged.

Hand-built reflexes use only the keys the math reads. `deg30 = 0.5235987755982988`. All values
≈ 1e-6.

- **X1 Tilted head untilts.** A single roll hinge, so `jacr = #js [1 0 0]` (nv 1: rows x,y,z),
  `jacp = #js [0 0 0]`, `r = {:dofs [0] :ranges [[-1.5 1.5]] :lift-m 0.25 :gain 0.5 :w-lift 0}`.
  The head is rolled 30° about x: `up = [0 -0.5 0.8660254]`, so `e_rot = [-0.5 0 0]` and
  `Jrᵀe = -0.5`. `(cm/reflex-targets r {:up up :clearance 1 :jacp jacp :jacr jacr} [deg30] 1 1)`
  ≈ `[0.2735988]` (= 0.5235988 − 0.5·0.5), moved toward 0.
- **X2 Level head gives no change.** As X1 with `up [0 0 1]` and `q [0.2]` → `[0.2]`.
- **X3 Clamping.** As X1 with `:gain 4 :ranges [[-0.3 1.0]]`: the unclamped value is
  0.5236 − 2 = −1.4764, so the target is `[-0.3]`. With `up [0 0.5 0.8660254]` (tilted the other
  way, `e_rot [0.5 0 0]`): 0.5236 + 2 → `[1.0]`.
- **X4 Strength.** As X1 with strength 0 → `[0.5235988]` (= q). With strength 0.5 → `[0.3985988]`.
- **X5 The lift term only when clearance < lift-m.** This uses the Jacobian checked in Node: a
  hinge about y, tip body 1 m along x, `jacp = #js [0 0 -1]`, `jacr = #js [0 1 0]`,
  `up [0 0 1]`, `r = {:dofs [0] :ranges [[-1 1]] :lift-m 0.3 :gain 0.5 :w-lift 2}`, `q [0]`,
  strength 1, nv 1.
  - Clearance 0.1: `e_lift = 0.2`, `Jpᵀ = -1·0.2`, times `w-lift` 2 → −0.4, times gain 0.5 →
    `[-0.2]`. A negative rotation about y lifts the tip.
  - Clearance 0.35 → `[0]`. Clearance 0.3 → `[0]`.
- **X6 Only the reflex's own dofs, row-major.** nv 3, with dof j's rotation axis as column j:
  dof0 z, dof1 y, dof2 x, so `jacr = #js [0 0 1 0 1 0 1 0 0]` and `jacp` = 9 zeros.
  `up [0.6 0 0.8]` (pitched about +y), so `e_rot = [0 -0.6 0]`.
  `r = {:dofs [1 2] :ranges [[-1 1] [-1 1]] :lift-m 0 :gain 1 :w-lift 0}`, `q [0.1 0.2]`,
  strength 0.5 → `[-0.2 0.2]`. For dof1, g = `jacr[4]·(-0.6)` = −0.6. For dof2, g =
  `jacr[5]·(-0.6)` = 0.
- **X7 `head-up` and `tilt-deg`.** `xmat = #js [1 0 0 0 1 0 0 0 1, 0.877583 0 0.479426 0 1 0 -0.479426 0 0.877583]`
  (body 1 = the Node hinge at q = 0.5). `(cm/head-up xmat 1 [0 0 1])` ≈ `[0.479426 0 0.877583]`
  and `(cm/head-up xmat 1 [1 0 0])` ≈ `[0.877583 0 -0.479426]`. `(cm/tilt-deg [0.479426 0 0.877583])` ≈
  28.647890 (1e-4), and `(cm/tilt-deg [0 0 1])` = 0, `[0 0 -1]` → 180, `[1 0 0]` → 90.
  `(cm/head-up xmat 0 [0 0 1])` = `[0 0 1]`.
- **X8 `capsule-clearance`.** `xpos = #js [0 0 0 0 0 1]`, `cap = {:from [0 0 0] :to [0.24 0 -0.1] :r 0.07}`,
  ground 0.5.
  - Identity `xmat` (body 1) → min(1, 0.9) − 0.07 − 0.5 = **0.33**.
  - Body 1 `xmat` = Rx(90°) `[1 0 0 0 0 -1 0 1 0]`: row 2 = [0 1 0], both ends at z 1 → **0.43**.
  - Ry(90°) `[0 0 1 0 1 0 -1 0 0]`: z = 1 − px, so the `to` end is at 0.76 → **0.19**.
- **X9 `reflex-strength`.** `(t, onset)`: (0, 1.5) → 0, (0.75, 1.5) → 0.5, (1.5, 1.5) → 1,
  (3, 1.5) → 1, (−1, 1.5) → 0, (0.3, 0) → 1.
- **X10 `target->action`.** With `[-0.5 1.5]`: 0.5 → 0, 1.5 → 1, −0.5 → −1, 1.0 → 0.5.
  `(cm/actions->ctrl [(cm/target->action 0.3 [-0.5 1.5])] [[-0.5 1.5]])` ≈ `[0.3]`.
- **X11 `act` with a `:reflex` layer over `:still`.** `inner = (cm/controller :still 3 1)`,
  `r = {:acts [1] :qpos [8] :dofs [1] :ranges [[-1 1]] :joints [:j] :lift-m 0 :gain 1 :w-lift 0 :onset-s 0}`,
  `ctrl = (cm/reflex-controller inner [r])`, which is `{:kind :reflex :n 3 :inner inner :reflexes [r] :t 0}`.
  `qpos` = a 9-long `#js` array of zeros except `qpos[8] = 0.1`.
  `obs = {:nv 3 :qpos qpos :senses [{:up [0.6 0 0.8] :clearance 1 :jacp <9 zeros> :jacr #js [0 0 1 0 1 0 1 0 0]}]}`.
  `(cm/act ctrl obs 0.002)` → actions ≈ `[0 -0.5 0]` (target 0.1 − 0.6 = −0.5, mid 0, half 1),
  with `(:t ctrl')` ≈ 0.002 and `(:inner ctrl')` = `inner`.
  - With `:onset-s 1.5` the strength is 0.002/1.5, so actions ≈ `[0 0.0992 0]`.
  - With `obs` nil → actions `[0 0 0]` and `:t` 0.002.
  - With dt 0 → `:t` 0, and strength 0 when `:onset-s` > 0.
- **X12 The reflex joints leave the twitch; the others keep it.** `tw = (cm/twitch-init 3 1)`,
  with the X11 `r` and `obs`. `[c' a] = (cm/act (cm/reflex-controller tw [r]) obs 0.002)`.
  `(nth a 0)` and `(nth a 2)` equal `(nth (cm/twitch-actions (cm/twitch-step tw 0.002)) 0)` and
  `… 2` (exact). `(nth a 1)` ≈ −0.5. `(:inner c')` = `(cm/twitch-step tw 0.002)`.
- **X13 `sense`.** `r = {:body-id 1 :up [0 0 1] :capsule cap}`, with X8's `xpos` and identity
  `xmat`, and `jp`/`jr` any arrays. `(cm/sense r xpos xmat jp jr 0.5)` →
  `{:up [0 0 1] :clearance ≈0.33 :jacp jp :jacr jr}` (`identical?` arrays).
- **X14 HUD.** `{:mode :running :flag "pony" :kind :horse :stats {:ms 0.94 :steps 8 :dropped 0} :torso 0.3149 :head {:clearance 0.2437 :tilt 1.2}}` →
  `"pony: horse · 0.9 ms/frame · 8 steps · 0 dropped · torso 0.31 m · head 0.24 m 1°"`.
  Without `:head`, the M14 strings are unchanged.

### 7.3 Real MuJoCo: `test/pm/creature_mujoco_test.cljs`

Load MuJoCo with the existing `import-mj` (`js/require`). The place is
`P = {:at [4.5 1.79 -8.0] :yaw 0 :drop-m 0.4}`, ground 1.79. Every `DoubleBuffer` is
`.delete`d, then data, then model.

**Updated:**
- **R1/R2** `check-counts-masses-ranges` loops joints over `(range (count ranges))` (12) instead
  of 9. The counts now come from `(c/counts sp)` = 12/13/12/19/18/12. The masses and bodies
  loops are unchanged (`(range 1 12)`).
- **R5** `(cm/controller :twitch 12 1)`, and hinges are `qpos[7..18]` (`(range 7 19)`). The other
  assertions are unchanged. R3 and R4 are unchanged; R3's bounds still hold (0.8391 / 0.4392).

**New:**
- **R6 Reflex on: the head lifts and levels** (horse **and** dog, `:roll 90`, legs at zero).
  `place (assoc P :roll 90)`. Build the model and data, call `(.mj_forward mj model data)`,
  `rx = (c/reflexes sp)`, `r = (first rx)`, `nv = (.-nv model)` (18), and two
  `(new (.-DoubleBuffer mj) (* 3 nv))` buffers.
  `ctrl = (cm/reflex-controller (cm/controller :still 12 1) rx)`. Run 1500 steps (3 s); each step:
  1. `(.mj_jacBody mj model data jp jr (:body-id r))`
  2. `obs = {:nv nv :qpos (.-qpos data) :senses [(cm/sense r (.-xpos data) (.-xmat data) (.GetView jp) (.GetView jr) 1.79)]}`
  3. `[ctrl a] (cm/act ctrl obs c/timestep)`, `vals (cm/actions->ctrl a (c/ctrl-ranges sp))`
  4. write `ctrl[k] = vals[k]` for k in `(:acts r)` and **`ctrl[k] = 0` for every other k (legs at zero)**
  5. `mj_step`

  Afterwards, with `s = (cm/sense r xpos xmat …)` on fresh data:
  `(:clearance s)` ≥ `(* 0.7 (:lift-m r))` (horse ≥ 0.175, dog ≥ 0.105),
  `(cm/tilt-deg (:up s))` ≤ 20, and every `qpos` is finite. Observed: horse 0.244 m / 1.2°, dog
  0.141 m / 0.6°. The same holds for `:roll -90` (observed identical).
- **R7 Reflex off: it doesn't** (same setup, `:roll 90`, every `ctrl[k] = 0`, no `act`).
  After 1500 steps: `(cm/tilt-deg (:up s))` > 20 **and** `(:clearance s)` < `(* 0.7 (:lift-m r))`.
  Observed: horse 0.022 m / 89.1°, dog 0.021 m / 96.7°.
- **R8 The binding and the Jacobian, by hand** (horse at `P`, roll 0, after one `mj_forward`,
  body 3 = head, nv 18). Column `d` of a Jacobian is `[J[d] J[18+d] J[36+d]]`. The head origin
  is at the neck origin + (0.18, 0, 0.24), and the neck origin is at the torso + (0.3, 0, 0.08).
  | dof | joint | jacp (axis × r) | jacr (axis) |
  |---|---|---|---|
  | 0 | free x | [1 0 0] | [0 0 0] |
  | 3 | free rot x, r = (0.48, 0, 0.32) | [0 -0.32 0] | [1 0 0] |
  | 6 | neck-bend z, r = (0.18, 0, 0.24) | [0 0.18 0] | [0 0 1] |
  | 7 | neck-pitch y, r = (0.18, 0, 0.24) | [0.24 0 -0.18] | [0 1 0] |
  | 8 | head-pitch y, r = 0 | [0 0 0] | [0 1 0] |
  | 9 | head-roll x, r = 0 | [0 0 0] | [1 0 0] |
  | 10 | fl-upper (not an ancestor) | [0 0 0] | [0 0 0] |

  For the dog, dof 3 jacp = `[0 -0.16 0]`, dof 6 jacp = `[0 0.09 0]`, and dof 7 jacp =
  `[0.12 0 -0.09]`; the rest is the same pattern. Also check that `(:dofs (first (c/reflexes horse)))`
  = `[6 7 8 9]` equals `jnt_dofadr` at `(.mj_name2id mj model (.. mj -mjtObj -mjOBJ_JOINT -value) "neck-bend")`
  … `"head-roll"`, and that `(:body-id …)` = 3 = `(.mj_name2id mj model (.. mj -mjtObj -mjOBJ_BODY -value) "head")`.
  **Pin the trap:** calling `mj_jacBody` with two `(js/Float64Array. (* 3 nv))` instead leaves them
  all zeros.

**Count: 30 new numbered cases** (C28–C40 = 13, X1–X14 = 14, R6–R8 = 3) **and 9 updated** (C6,
C9, C14, C15, C20, C21, M12, R1/R2, R5).

---

## 8. Done when

- `bash .crew/gate.sh` → `GATE: PASS` (0 warnings; all old and new cases pass).
- `grep -rniE 'horse|dog|pony|juniper|neck|head-' src/pm/creature*.cljs src/pm/creature.cljc`
  has no hits. The reflex reaches bodies and joints only through species data.
- Without `?pony`, nothing changes. With `?pony`, the game's drop is unchanged (no `:roll`).
- Live (Hal): with `?pony` and `?pony=dog`, the legs kick and within about 2 s the head lifts and
  holds level. The HUD shows `· head 0.24 m 1°`-style numbers. Hal takes a screenshot from
  Michael's current view.
- **Known risk (live only):** in one scratch run with kicking legs, the dog ended upside-down
  with its head under its body (clearance 0). The reflex can't lift through the torso. That is
  for the roll-onto-the-chest round, not a bug here.
