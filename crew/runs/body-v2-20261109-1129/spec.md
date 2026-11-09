# Spec: body-v2 — shoulders, hips and a spine (TASKS M-14)

Quest: `.crew/quests/body-v2.md`. Architect: un-architecte. Start commit `31bfa65`.

## 0. In one paragraph

This round changes **data only**. The two species files get new bodies:
- **Upper legs:** each gets two hinges, splay then swing.
- **Torso:** splits into `:torso` (front half, still the root) and `:hind` (hind half), joined by `:spine-bend` then `:spine-twist`.
- **Hind legs:** now hang from `:hind`.
- **Joints:** 12 → 18 for each species.

The builder copies the two files below and regenerates `creatures/bodies/*.xml`. I ran the real `pm.creature` (in bb) on these exact files: they validate, and `bodies`, `joints`, `counts`, `reflexes`, `mjcf`, `rest-bottom`, `spawn` and `body-parts` all handle the nested `:hind` and the `:joints` on the legs unchanged. I also ran the controllers in real MuJoCo (a 1:1 JS port that reproduces last round's numbers exactly on the old bodies): `pm.creature-motion` and `pm.creature-spike` already work by count (twitch, `stiffness`, `limp`, reflex, HUD). **No source file needs to change.** Most of the work is the tester's: about 23 existing assertions hard-code the old body, and 11 new cases are added.

**Stand-hold finding** (§7, numbers in §6 S1–S2):
- At today's `:kp` 150 the new foal **passes the quest's literal 5 s check** (torso 0.803 m, tilt 0.2°), but it is already falling at that moment and is down at 5.47 s.
- By a stand that has to last (30 s still, and 10 s after a 0.1 m/s push forward or sideways), the **smallest `:kp` that holds is 225**. kp 225 still creeps forward and falls at 58 s; **250** is the first with margin.
- Today's 12-joint body fails the same way at 150 (literal 5 s check: 0.416 m, 62.5°). Its smallest holding `:kp` is also 225.
- `:kp` stays 150 in `horse.edn`; Michael decides.
- The dog holds at its `:kp` 40 with all variants.

## 1. Frame and sign conventions

The MuJoCo body frame: x forward, y left, z up. Angles in the EDN are in degrees. Every hinge's positive direction is the right-hand rotation about its `:axis`, in the parent body's frame.

| joint | axis | + means | why that sign |
|---|---|---|---|
| `*-splay`, left legs (`fl`, `hl`, hip at +y) | `[1 0 0]` | foot moves **outward** (+y) | R_x(θ)·(0,0,−1) = (0, sin θ, −cos θ) |
| `*-splay`, right legs (`fr`, `hr`, hip at −y) | `[-1 0 0]` | foot moves **outward** (−y) | R_x(−θ)·(0,0,−1) = (0, −sin θ, −cos θ) |
| `*-swing`, all four | `[0 1 0]` | foot moves **back** (−x), as before | R_y(θ)·(0,0,−1) = (−sin θ, 0, −cos θ) |
| `spine-bend` | `[0 0 1]` | the hind end swings **right** (−y); the body curves concave-right | R_z(θ)·(−1,0,0) = (−cos θ, −sin θ, 0) |
| `spine-twist` | `[1 0 0]` | the hind's **left** side goes **up** | R_x(θ)·(0,1,0) = (0, cos θ, sin θ) |

- **Mirroring the splay:** this is how "+ moves the foot outward on both sides" works. It is the same hinge line (x); the right side's axis is negated. A splay joint's axis x-sign always equals the sign of its upper leg's `:at` y.
- **Lying on the right side** (`:roll` +90, right legs down): + splay on the down legs moves the hoof **toward the ground**, and − lifts it under the belly. Lying on the left side (`:roll` −90), the same holds for the left legs. R15 checks both.
- **Folding under the body:**
  - A front leg folds with **+ swing** (75° side): the knee goes back under the chest.
  - A hind leg folds with **− swing** (60° side): the knee goes forward under the belly.
  - The other sign throws the knee out in front (front, −) or behind (hind, +).
  - R16 checks this in MuJoCo.
  - A real foal's front fold (forearm forward, cannon tucked back) is − swing plus + `-lower` flexion; both are reachable, and training picks.
- **A C-curve to the left** (head and tail both to the left) is `neck-bend` + with `spine-bend` −.

## 2. Species data (copy verbatim)

### 2.1 `resources/creatures/horse.edn` (complete file)

```clojure
;; A foal: ~1.0 m at the withers, ~0.9 m body. MuJoCo frame: x forward, y left, z up; metres.
;; Angles in degrees. Read by pm.creature (the game, and `bb creature-mjcf horse`).
{:kind    :horse
 :density 1000          ; kg/m³: each body's mass = its capsule's volume × density
 :kp      150           ; position-actuator stiffness, N·m/rad (one actuator per joint)
 :damping 5             ; joint damping, N·m·s/rad
 :rest-kp    15         ; N·m/rad: muscles that aren't working are soft (10 % of :kp), so he lies, not stands
 :kick-above 0.8        ; a twitch segment whose random target |to| >= this is a kick (full :kp); else rest stiffness
 :colors  {:coat 0x9b6b43 :dark 0x4a3426 :hoof 0x2e2622}
 ;; Body v2: the torso is two halves joined by a spine at x = 0. :torso (the root, the front half)
 ;; and :hind each carry half of the old capsule's volume, so mass and length are unchanged.
 ;; Upper legs: splay first (nearest the torso), then swing. Splay + moves the foot outward on
 ;; both sides: axis [1 0 0] on the left legs (+y), [-1 0 0] on the right legs (-y).
 ;; Swing (axis [0 1 0], as before): + moves the foot back. A front leg folds under the chest
 ;; with + (knee back toward the belly), a hind leg with - (knee forward under the belly).
 :bodies
 [{:name :torso :capsule {:from [0.1 0 0] :to [0.3 0 0] :r 0.15} :color :coat}
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
  {:name :fl-upper :parent :torso :at [0.28 0.1 -0.05]
   :joints [{:name :fl-splay :axis [1 0 0] :range [-25 25]}    ; + = foot out (left)
            {:name :fl-swing :axis [0 1 0] :range [-60 75]}]   ; + = foot back
   :capsule {:from [0 0 0] :to [0 0 -0.38] :r 0.05} :color :coat}
  {:name :fl-lower :parent :fl-upper :at [0 0 -0.38]
   :joint {:axis [0 1 0] :range [-10 100]}
   :capsule {:from [0 0 0] :to [0 0 -0.37] :r 0.04} :color :hoof}
  {:name :fr-upper :parent :torso :at [0.28 -0.1 -0.05]
   :joints [{:name :fr-splay :axis [-1 0 0] :range [-25 25]}   ; + = foot out (right)
            {:name :fr-swing :axis [0 1 0] :range [-60 75]}]
   :capsule {:from [0 0 0] :to [0 0 -0.38] :r 0.05} :color :coat}
  {:name :fr-lower :parent :fr-upper :at [0 0 -0.38]
   :joint {:axis [0 1 0] :range [-10 100]}
   :capsule {:from [0 0 0] :to [0 0 -0.37] :r 0.04} :color :hoof}
  {:name :hind :parent :torso :at [0 0 0]
   :joints [{:name :spine-bend  :axis [0 0 1] :range [-25 25]}  ; + = hind end swings right
            {:name :spine-twist :axis [1 0 0] :range [-20 20]}] ; + = hind's left side up
   :capsule {:from [-0.3 0 0] :to [-0.1 0 0] :r 0.15} :color :coat}
  {:name :hl-upper :parent :hind :at [-0.28 0.1 -0.05]
   :joints [{:name :hl-splay :axis [1 0 0] :range [-25 25]}
            {:name :hl-swing :axis [0 1 0] :range [-60 75]}]
   :capsule {:from [0 0 0] :to [0 0 -0.38] :r 0.05} :color :coat}
  {:name :hl-lower :parent :hl-upper :at [0 0 -0.38]
   :joint {:axis [0 1 0] :range [-100 10]}
   :capsule {:from [0 0 0] :to [0 0 -0.37] :r 0.04} :color :hoof}
  {:name :hr-upper :parent :hind :at [-0.28 -0.1 -0.05]
   :joints [{:name :hr-splay :axis [-1 0 0] :range [-25 25]}
            {:name :hr-swing :axis [0 1 0] :range [-60 75]}]
   :capsule {:from [0 0 0] :to [0 0 -0.38] :r 0.05} :color :coat}
  {:name :hr-lower :parent :hr-upper :at [0 0 -0.38]
   :joint {:axis [0 1 0] :range [-100 10]}
   :capsule {:from [0 0 0] :to [0 0 -0.37] :r 0.04} :color :hoof}]
 ;; Reflexes: hand-written controllers layered over the twitch, per species (pm.creature-motion).
 ;; :head-level: lift :body's lowest point :lift-m above the ground and turn its :up axis to world up,
 ;; by Jacobian transpose on :joints only; strength ramps 0 -> 1 over :onset-s after the drop.
 :reflexes [{:kind :head-level :body :head
             :joints [:neck-bend :neck-pitch :head-pitch :head-roll]
             :up [0 0 1] :lift-m 0.25 :gain 2 :w-lift 40 :onset-s 1.5
             ;; A newborn's neck: weak, shaky, tires fast (pm.creature-motion effort).
             ;; force cap = :strength × :torque-nm on each reflex actuator (MJCF forcerange);
             ;; tremor :tremor-deg fades with time awake (÷e every :steady-s); energy 1 → 0 at
             ;; full effort in :energy-s, refills 0 → 1 in :recover-s; lets go below :low, tries again above :high.
             :effort {:strength 0.35 :torque-nm 100 :tremor-deg 6 :steady-s 90
                      :energy-s 2.5 :recover-s 4 :low 0.15 :high 0.6 :seed-salt 7}}]}
```

### 2.2 `resources/creatures/dog.edn` (complete file)

```clojure
;; A mid-size dog: ~0.55 m at the shoulder. MuJoCo frame: x forward, y left, z up; metres.
;; Angles in degrees. Read by pm.creature (the game, and `bb creature-mjcf dog`).
{:kind    :dog
 :density 1000          ; kg/m³: each body's mass = its capsule's volume × density
 :kp      40            ; position-actuator stiffness, N·m/rad (one actuator per joint)
 :damping 1.5           ; joint damping, N·m·s/rad
 :rest-kp    4          ; N·m/rad: muscles that aren't working are soft (10 % of :kp), so he lies, not stands
 :kick-above 0.8        ; a twitch segment whose random target |to| >= this is a kick (full :kp); else rest stiffness
 :colors  {:coat 0xc49a6c :dark 0x6b4a32}
 ;; Body v2 (same layout and signs as horse.edn): torso = front half (root), :hind = hind half,
 ;; spine at x = 0; upper legs splay (+ = foot out, axis ±x by side) then swing (+ = foot back).
 :bodies
 [{:name :torso :capsule {:from [0.07 0 0] :to [0.22 0 0] :r 0.1} :color :coat}
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
  {:name :fl-upper :parent :torso :at [0.2 0.07 -0.03]
   :joints [{:name :fl-splay :axis [1 0 0] :range [-25 25]}
            {:name :fl-swing :axis [0 1 0] :range [-60 75]}]
   :capsule {:from [0 0 0] :to [0 0 -0.2] :r 0.035} :color :coat}
  {:name :fl-lower :parent :fl-upper :at [0 0 -0.2]
   :joint {:axis [0 1 0] :range [-10 100]}
   :capsule {:from [0 0 0] :to [0 0 -0.18] :r 0.03} :color :dark}
  {:name :fr-upper :parent :torso :at [0.2 -0.07 -0.03]
   :joints [{:name :fr-splay :axis [-1 0 0] :range [-25 25]}
            {:name :fr-swing :axis [0 1 0] :range [-60 75]}]
   :capsule {:from [0 0 0] :to [0 0 -0.2] :r 0.035} :color :coat}
  {:name :fr-lower :parent :fr-upper :at [0 0 -0.2]
   :joint {:axis [0 1 0] :range [-10 100]}
   :capsule {:from [0 0 0] :to [0 0 -0.18] :r 0.03} :color :dark}
  {:name :hind :parent :torso :at [0 0 0]
   :joints [{:name :spine-bend  :axis [0 0 1] :range [-25 25]}
            {:name :spine-twist :axis [1 0 0] :range [-20 20]}]
   :capsule {:from [-0.22 0 0] :to [-0.07 0 0] :r 0.1} :color :coat}
  {:name :hl-upper :parent :hind :at [-0.2 0.07 -0.03]
   :joints [{:name :hl-splay :axis [1 0 0] :range [-25 25]}
            {:name :hl-swing :axis [0 1 0] :range [-60 75]}]
   :capsule {:from [0 0 0] :to [0 0 -0.2] :r 0.035} :color :coat}
  {:name :hl-lower :parent :hl-upper :at [0 0 -0.2]
   :joint {:axis [0 1 0] :range [-100 10]}
   :capsule {:from [0 0 0] :to [0 0 -0.18] :r 0.03} :color :dark}
  {:name :hr-upper :parent :hind :at [-0.2 -0.07 -0.03]
   :joints [{:name :hr-splay :axis [-1 0 0] :range [-25 25]}
            {:name :hr-swing :axis [0 1 0] :range [-60 75]}]
   :capsule {:from [0 0 0] :to [0 0 -0.2] :r 0.035} :color :coat}
  {:name :hr-lower :parent :hr-upper :at [0 0 -0.2]
   :joint {:axis [0 1 0] :range [-100 10]}
   :capsule {:from [0 0 0] :to [0 0 -0.18] :r 0.03} :color :dark}]
 ;; (same comment as horse.edn)
 :reflexes [{:kind :head-level :body :head
             :joints [:neck-bend :neck-pitch :head-pitch :head-roll]
             :up [0 0 1] :lift-m 0.15 :gain 2 :w-lift 40 :onset-s 1.5
             ;; A grown dog: strong and steady, so little tremor and long holds.
             :effort {:strength 0.9 :torque-nm 10 :tremor-deg 1 :steady-s 30
                      :energy-s 30 :recover-s 4 :low 0.15 :high 0.6 :seed-salt 3}}]}
```

**Why the bodies are in this vector order.** `:hind` comes right after `:fr-lower`, and the hind legs follow it. The neck and head stay first, so the head reflex keeps body-id 3, acts `[0 1 2 3]`, qpos `[7 8 9 10]` and dofs `[6 7 8 9]`, and `C37`/`C38` stay unchanged. The `:bodies` indices used by the old tests also stay put: 1 = neck, 2 = head, 3 = fl-upper, 4 = fl-lower. The new index 7 is `:hind`.

**Why the halves have this size.** The horse's halves are 0.2 m segments with the old radius 0.15. Two capsules with πr²(0.2 + 4r/3) = πr²·0.4 have exactly the old πr²·0.8 volume. The ends stay at x = ±0.45 and the radius stays 0.15, so `rest-bottom`, `spawn` and every placement number are unchanged. The two hemispherical caps overlap across the spine; geoms don't collide with each other (`contype 0`), so that is only a slight waist (0.111 m half-thickness at x = 0). The dog uses 0.15 m segments (round numbers): mass −0.77 %, ends still at ±0.32.

### 2.3 The 18 joints (both species; MuJoCo order = `c/joints` order = actuator order)

`k` is the actuator/ctrl index, qpos index = 7 + k, dof index = 6 + k.

| k | joint | body (parent of the hinge's child) | axis | range ° | range rad (`c/fmt`) | + means |
|---|---|---|---|---|---|---|
| 0 | `:neck-bend` | `:neck` (on `:torso`) | `[0 0 1]` | −70…70 | −1.22173 1.22173 | head left |
| 1 | `:neck-pitch` | `:neck` | `[0 1 0]` | −40…45 | −0.698132 0.785398 | head forward/down |
| 2 | `:head-pitch` | `:head` (on `:neck`) | `[0 1 0]` | −60…60 | −1.047198 1.047198 | nose down |
| 3 | `:head-roll` | `:head` | `[1 0 0]` | −70…70 | −1.22173 1.22173 | head's left side up |
| 4 | `:fl-splay` | `:fl-upper` (on `:torso`) | `[1 0 0]` | −25…25 | −0.436332 0.436332 | foot out (+y) |
| 5 | `:fl-swing` | `:fl-upper` | `[0 1 0]` | −60…75 | −1.047198 1.308997 | foot back; + folds under chest |
| 6 | `:fl-lower` | `:fl-lower` (on `:fl-upper`) | `[0 1 0]` | −10…100 | −0.174533 1.745329 | knee flexes, hoof back |
| 7 | `:fr-splay` | `:fr-upper` (on `:torso`) | `[-1 0 0]` | −25…25 | −0.436332 0.436332 | foot out (−y) |
| 8 | `:fr-swing` | `:fr-upper` | `[0 1 0]` | −60…75 | −1.047198 1.308997 | as fl |
| 9 | `:fr-lower` | `:fr-lower` (on `:fr-upper`) | `[0 1 0]` | −10…100 | −0.174533 1.745329 | as fl |
| 10 | `:spine-bend` | `:hind` (on `:torso`) | `[0 0 1]` | −25…25 | −0.436332 0.436332 | hind end right |
| 11 | `:spine-twist` | `:hind` | `[1 0 0]` | −20…20 | −0.349066 0.349066 | hind's left side up |
| 12 | `:hl-splay` | `:hl-upper` (on `:hind`) | `[1 0 0]` | −25…25 | −0.436332 0.436332 | foot out (+y) |
| 13 | `:hl-swing` | `:hl-upper` | `[0 1 0]` | −60…75 | −1.047198 1.308997 | foot back; − folds under belly |
| 14 | `:hl-lower` | `:hl-lower` (on `:hl-upper`) | `[0 1 0]` | −100…10 | −1.745329 0.174533 | − = hock flexes, hoof forward |
| 15 | `:hr-splay` | `:hr-upper` (on `:hind`) | `[-1 0 0]` | −25…25 | −0.436332 0.436332 | foot out (−y) |
| 16 | `:hr-swing` | `:hr-upper` | `[0 1 0]` | −60…75 | −1.047198 1.308997 | as hl |
| 17 | `:hr-lower` | `:hr-lower` (on `:hr-upper`) | `[0 1 0]` | −100…10 | −1.745329 0.174533 | as hl |

- **Shared values:** the angles are the same for the dog (only the geometry differs). The `:lower` joints keep their names (single `:joint`, named after the body).
- **Hip and shoulder positions** (`:at` of each upper leg, relative to its parent):
  - Horse: front `[0.28 ±0.1 −0.05]` on `:torso`, hind `[−0.28 ±0.1 −0.05]` on `:hind`.
  - Dog: front `[0.2 ±0.07 −0.03]`, hind `[−0.2 ±0.07 −0.03]`.
- **`:hind`** sits at `:at [0 0 0]` on `:torso`.

## 3. Derived values (hand-checked; I re-ran each in bb against the real `pm.creature`)

**Counts.** `c/counts` → `{:nbody 13 :njnt 19 :nu 18 :nq 25 :nv 24 :ngeom 13}` for both (before: 12/13/12/19/18/12).

**Bodies.** `c/bodies` order (DFS): `[:torso :neck :head :fl-upper :fl-lower :fr-upper :fr-lower :hind :hl-upper :hl-lower :hr-upper :hr-lower]`. MuJoCo body id = index + 1, so `:hind` = 8, `:hr-lower` = 12.

**Masses.** Capsule mass = ρ·π·r²·(ℓ + 4r/3).

| | before (C16/C17) | after |
|---|---|---|
| horse `:torso` | 56.548668 (ℓ 0.6) | 28.274334 = 1000·π·0.0225·(0.2 + 0.2) |
| horse `:hind` | — | 28.274334 |
| horse **total** | **88.829579** | **88.829579** (ratio 1.000000) |
| dog `:torso` | 18.011798 (ℓ 0.44) | 8.901179 = 1000·π·0.01·(0.15 + 0.133333) |
| dog `:hind` | — | 8.901179 |
| dog **total** | **27.177656** | **26.968217** (ratio 0.992294, −0.77 %) |

All other bodies are unchanged: horse neck 4.297699, head 5.439144, upper 3.508112, lower 2.127905; dog neck 1.335962, head 1.544616, upper 0.949285, lower 0.622035.

**Length** (x extent of the torso and hind capsules, caps included):
- Horse: front 0.1−0.15 … 0.3+0.15 = −0.05…0.45; hind −0.45…0.05 → **0.9** (before 0.9).
- Dog: −0.03…0.32 and −0.32…0.03 → **0.64** (before 0.64).

**Placement.** `c/rest-bottom`: horse −0.84, dog −0.44; roll ±90 gives −0.15 / −0.105. All unchanged, so `spawn`, C18, C19, C22, C39 and C40 are unchanged.

**`c/body-part`.** The front `:torso` part:
- horse `{:shape :capsule :size [0.5 0.3 0.3] :at [0.2 0 0] :rot [0 0 0] :color 0x9b6b43}`
- dog `{… :size [0.35 0.2 0.2] :at [0.145 0 0] :rot [0 0 0] :color 0xc49a6c}`

`:hind`: the same `:size`/`:rot`/`:color`, with `:at` horse `[-0.2 0 0]` and dog `[-0.145 0 0]`. (`:rot` comes out as `[0 -0.0 0.0]`; compare with `approx=`.)

**`(cm/actions->ctrl (vec (repeat 18 0)) (c/ctrl-ranges horse))`** (the range mids) → `[0 0.043633 0 0 0 0.1309 0.785398 0 0.1309 0.785398 0 0 0 0.1309 -0.785398 0 0.1309 -0.785398]`. The swing mid is 7.5° = 0.1308997.

**Generated MJCF** (horse at the C20 place `{:at [4.5 1.79 -8.0] :yaw 0 :drop-m 0.4}`; the leading spaces are the real indentation):
```
      <geom name="torso" type="capsule" fromto="0.1 0 0 0.3 0 0" size="0.15" density="1000" contype="0" conaffinity="1"/>
      <body name="fl-upper" pos="0.28 0.1 -0.05">
        <joint name="fl-splay" type="hinge" axis="1 0 0" limited="true" range="-0.436332 0.436332" damping="5"/>
        <joint name="fl-swing" type="hinge" axis="0 1 0" limited="true" range="-1.047198 1.308997" damping="5"/>
        <geom name="fl-upper" ...
        <joint name="fr-splay" type="hinge" axis="-1 0 0" limited="true" range="-0.436332 0.436332" damping="5"/>
      <body name="hind" pos="0 0 0">
        <joint name="spine-bend" type="hinge" axis="0 0 1" limited="true" range="-0.436332 0.436332" damping="5"/>
        <joint name="spine-twist" type="hinge" axis="1 0 0" limited="true" range="-0.349066 0.349066" damping="5"/>
        <geom name="hind" type="capsule" fromto="-0.3 0 0 -0.1 0 0" size="0.15" density="1000" contype="0" conaffinity="1"/>
        <body name="hl-upper" pos="-0.28 0.1 -0.05">
          <joint name="hl-splay" type="hinge" axis="1 0 0" limited="true" range="-0.436332 0.436332" damping="5"/>
    <position name="fl-splay" joint="fl-splay" kp="150" ctrllimited="true" ctrlrange="-0.436332 0.436332"/>
    <position name="fl-swing" joint="fl-swing" kp="150" ctrllimited="true" ctrlrange="-1.047198 1.308997"/>
    <position name="spine-bend" joint="spine-bend" kp="150" ctrllimited="true" ctrlrange="-0.436332 0.436332"/>
    <position name="spine-twist" joint="spine-twist" kp="150" ctrllimited="true" ctrlrange="-0.349066 0.349066"/>
    <position name="hr-splay" joint="hr-splay" kp="150" ctrllimited="true" ctrlrange="-0.436332 0.436332"/>
```
- **Element counts:** `<body ` 12, `<joint ` 18, `<position ` 18, `<geom ` 13, `<freejoint ` 1, and still exactly 4 `forcerange=` (neck/head only).
- **Dog:** the same lines with `damping="1.5"` and `kp="40"`, torso geom `fromto="0.07 0 0 0.22 0 0" size="0.1"`, hind geom `fromto="-0.22 0 0 -0.07 0 0" size="0.1"`.

**Regenerated files.** These are what `bb creature-mjcf horse|dog` prints, written to `creatures/bodies/{horse,dog}.xml`:
- 82 lines each.
- sha256: horse `2e3ed43e60c68132bcdb06b1f14d4789e71e0e30c296e04492117661b1413523`, dog `0492a913e680da42934572af641a421735ee7627b3d23b2f62c5a90fdcb5ddfb`.
- They depend only on the data, not the EDN comments, so the critic can check them with `shasum -a 256`.

## 4. Public functions

**None new, none changed.** The existing API already covers body v2. These signatures and behaviours are the contract this round relies on; the tests in §6 pin them:

| fn | signature | body-v2 result / edge case |
|---|---|---|
| `pm.creature/parse` | `[text] → sp` | both v2 files parse; throws `ex-info` on any problem (unchanged) |
| `pm.creature/problems` | `[sp] → [string]` | `[]` for both. Upper legs use the `:joints` form and lowers keep `:joint`. Joint names are unique across bodies (`joint :x: duplicate name`). A body with both forms → `use :joint or :joints, not both` |
| `pm.creature/bodies` | `[sp] → [body]` | DFS: `:hind`'s subtree follows the front legs (§3) |
| `pm.creature/body-joints` | `[body] → [{:name :axis :range}]` | `:fl-upper` → splay, swing; `:fl-lower` → `[{:axis [0 1 0] :range [-10 100] :name :fl-lower}]`; `:hind` → spine-bend, spine-twist; `:torso` → `[]` |
| `pm.creature/joints` | `[sp] → [{:name :body :axis :range(rad)}]` | 18 entries, table §2.3 |
| `pm.creature/ctrl-ranges` | `[sp] → [[lo hi]]` | 18 pairs (rad) |
| `pm.creature/counts` | `[sp] → map` | §3 |
| `pm.creature/body-masses` | `[sp] → [[name kg]]` | 12 pairs, §3 |
| `pm.creature/rest-bottom` | `[sp]`, `[sp roll-deg]` → m | unchanged values |
| `pm.creature/spawn` | `[sp place] → {:ground :torso :quat}` | unchanged values |
| `pm.creature/reflexes` | `[sp] → [compiled]` | unchanged (body-id 3, acts 0–3) |
| `pm.creature/mjcf` | `[sp place] → xml` | §3 |
| `pm.creature/body-parts` | `[sp] → [{:name :id :part}]` | 12 parts, ids 1–12 |
| `pm.creature-motion/controller` | `[kind n seed]` | the spike calls `(count ranges)` = 18 |
| `pm.creature-motion/stiffness` | `[ctrl rest-frac kick-above] → [σ]` | 18 entries for an 18-channel twitch; reflex acts 0–3 → 1 |
| `pm.creature-motion/limp` | `[targets qpos sigmas] → [ctrl]` | qpos index 7 + k still holds: every joint is a 1-dof hinge |
| `pm.creature-motion/actions->ctrl` | `[actions ranges]` | 18 values |

`pm.creature-spike` reads the root body (`xpos[5]`, body 1) as "torso". After the split, body 1 is still `:torso` (the front half), so the HUD's torso number is the front half's centre height.

**If** the gate shows a source change is needed after all, it must not add a species name or a fixed joint or body count (the critic checks).

## 5. Who touches what

| member | may edit | must not edit |
|---|---|---|
| **un-automate-monde** (builder) | `resources/creatures/horse.edn`, `resources/creatures/dog.edn` (verbatim from §2); regenerate `creatures/bodies/horse.xml` and `dog.xml` **only** with `bb creature-mjcf horse > creatures/bodies/horse.xml` (same for dog), never by hand. Source (`src/pm/creature*.clj[cs]`) only if the gate proves it necessary (§4). | `:kp` (stays 150/40), any `test/` file, `creatures/foal/*`, `creatures/reports/*`, `scripts/*`, `package.json`, `public/*` |
| **le-testeur** (tester) | `test/pm/creature_test.cljs`, `test/pm/creature_motion_test.cljs`, `test/pm/creature_mujoco_test.cljs` | `src/`, `resources/`, `creatures/`, `test/pm/creature_effort_test.cljs`, `test/pm/creature_reflex_test.cljs` (they pass unchanged: their fixtures and 12-channel controllers don't depend on the species' joint count) |
| **un-architecte** | this spec only | everything else |
| **later, Le Coureur (omarchy)** | `creatures/foal/task.py`; not this round | — |

## 6. Test cases

Conventions:
- `horse`/`dog` = `(c/parse (get species/texts kind))` (the new files).
- `P = {:at [4.5 1.79 -8.0] :yaw 0 :drop-m 0.4}`, ground z 1.79.
- `approx=` with the file's default eps 1e-6 unless stated.
- `id(sp, name)` = 1 + index of `name` in `(mapv :name (c/bodies sp))`.
- `cap(sp, name)` = that body's `:capsule`.
- MuJoCo numbers are from my scratchpad run on XML produced by the real `c/mjcf`.

### 6.1 Updated existing cases — `test/pm/creature_test.cljs`

**C4.** The upper legs now use `:joints`, so the single-`:joint` checks move to the lower leg, and one `:joints` check is added for the upper leg.
- `(c/problems (assoc-in dog [:bodies 4 :joint :range] [45 -45]))` → `["body :fl-lower: :joint needs a non-zero :axis [x y z] and :range [lo hi] with -180 <= lo < hi <= 180"]`.
- Same message for `(assoc-in dog [:bodies 4 :joint :axis] [0 0 0])`.
- `(c/problems (assoc-in dog [:bodies 3 :joints 0 :range] [30 -30]))` → `["body :fl-upper: joint 0: needs a keyword :name, a non-zero :axis [x y z] and :range [lo hi] with -180 <= lo < hi <= 180"]`.

**C13.** `(mapv :name (c/bodies horse))` and `(… dog)` = `[:torso :neck :head :fl-upper :fl-lower :fr-upper :fr-lower :hind :hl-upper :hl-lower :hr-upper :hr-lower]`. The fixture `f` lines are unchanged.

**C14.**
- `(mapv :name (c/joints horse))` = `[:neck-bend :neck-pitch :head-pitch :head-roll :fl-splay :fl-swing :fl-lower :fr-splay :fr-swing :fr-lower :spine-bend :spine-twist :hl-splay :hl-swing :hl-lower :hr-splay :hr-swing :hr-lower]`.
- `(mapv :body …)` = `[:neck :neck :head :head :fl-upper :fl-upper :fl-lower :fr-upper :fr-upper :fr-lower :hind :hind :hl-upper :hl-upper :hl-lower :hr-upper :hr-upper :hr-lower]`.
- `(c/ctrl-ranges horse)` and `(c/ctrl-ranges dog)` both approx= `[[-1.22173 1.22173] [-0.698132 0.785398] [-1.047198 1.047198] [-1.22173 1.22173] [-0.436332 0.436332] [-1.047198 1.308997] [-0.174533 1.745329] [-0.436332 0.436332] [-1.047198 1.308997] [-0.174533 1.745329] [-0.436332 0.436332] [-0.349066 0.349066] [-0.436332 0.436332] [-1.047198 1.308997] [-1.745329 0.174533] [-0.436332 0.436332] [-1.047198 1.308997] [-1.745329 0.174533]]`.
- The existing axis checks (`[0 0 1]` first, `[1 0 0]` at index 3) still hold.

**C15.** `(c/counts horse)` = `(c/counts dog)` = `{:nbody 13 :njnt 19 :nu 18 :nq 25 :nv 24 :ngeom 13}`; `f` is unchanged.

**C16.** `(c/body-masses horse)` approx= `[[:torso 28.274334] [:neck 4.297699] [:head 5.439144] [:fl-upper 3.508112] [:fl-lower 2.127905] [:fr-upper 3.508112] [:fr-lower 2.127905] [:hind 28.274334] [:hl-upper 3.508112] [:hl-lower 2.127905] [:hr-upper 3.508112] [:hr-lower 2.127905]]`, total 88.829579.

**C17.**
- `(c/body-masses dog)` approx= `[[:torso 8.901179] [:neck 1.335962] [:head 1.544616] [:fl-upper 0.949285] [:fl-lower 0.622035] [:fr-upper 0.949285] [:fr-lower 0.622035] [:hind 8.901179] [:hl-upper 0.949285] [:hl-lower 0.622035] [:hr-upper 0.949285] [:hr-lower 0.622035]]`, total 26.968217.
- The all-positive checks stay.
- The density-2000 check becomes `(second (first (c/body-masses (assoc dog :density 2000))))` approx= 17.802358.

**C20** (horse at `P`):
- Counts: `<body ` 12, `<joint ` 18, `<freejoint ` 1, `<position ` 18, `<geom ` 13, plane 1.
- Every existing `contains?` line still holds; the head-pitch position line keeps `forcerange="-35 35"`.
- Add the `contains?` lines from §3: fl-splay joint, fr-splay joint, spine-bend joint, spine-twist joint, the hind body/geom lines, and the fl-splay, fl-swing, spine-bend and spine-twist position lines.

**C21** (dog at `P`):
- Counts as in C20. The existing lines still hold.
- Add `<joint name="hr-splay" type="hinge" axis="-1 0 0" limited="true" range="-0.436332 0.436332" damping="1.5"/>`, `<geom name="hind" type="capsule" fromto="-0.22 0 0 -0.07 0 0" size="0.1" density="1000" contype="0" conaffinity="1"/>` and `<position name="spine-twist" joint="spine-twist" kp="40" ctrllimited="true" ctrlrange="-0.349066 0.349066"/>`.

**C26.**
- The first assertion becomes `(c/body-part horse (get-in horse [:bodies 0]))` approx= `{:shape :capsule :size [0.5 0.3 0.3] :at [0.2 0 0] :rot [0 0 0] :color 0x9b6b43}`.
- Add `(c/body-part horse (get-in horse [:bodies 7]))` approx= `{:shape :capsule :size [0.5 0.3 0.3] :at [-0.2 0 0] :rot [0 0 0] :color 0x9b6b43}`.
- Other assertions unchanged (indices 1–4 are the same bodies).

**C27.**
- `(c/body-parts dog)`: count 12, `:id`s `[1 … 12]`, names as C13.
- First part approx= `{:shape :capsule :size [0.35 0.2 0.2] :at [0.145 0 0] :rot [0 0 0] :color 0xc49a6c}`.
- Part 3 is unchanged (`{:size [0.27 0.07 0.07] :at [0 -0.1 0] :rot [0 0 -90]}`); part 2's color is 0x6b4a32.
- Add: part 7 has `:name :hind`, and its `:part` approx= `{:shape :capsule :size [0.35 0.2 0.2] :at [-0.145 0 0] :rot [0 0 0] :color 0xc49a6c}`.

**C28.**
- `(c/body-joints (get-in horse [:bodies 3]))` = `[{:name :fl-splay :axis [1 0 0] :range [-25 25]} {:name :fl-swing :axis [0 1 0] :range [-60 75]}]`.
- `(c/body-joints (get-in horse [:bodies 4]))` = `[{:name :fl-lower :axis [0 1 0] :range [-10 100]}]`.
- `(c/body-joints (get-in horse [:bodies 7]))` = `[{:name :spine-bend :axis [0 0 1] :range [-25 25]} {:name :spine-twist :axis [1 0 0] :range [-20 20]}]`.
- The neck and torso lines are unchanged.

**C32.**
- `(c/problems (assoc-in horse [:bodies 1 :joints 0 :name] :fl-swing))` = `["joint :fl-swing: duplicate name" "reflex 0: unknown joint :neck-bend"]`. The old `:fl-upper` is no longer a joint name; with it you'd only get the reflex line.
- Add `(c/problems (assoc-in dog [:bodies 7 :joints 1 :name] :spine-bend))` = `["joint :spine-bend: duplicate name"]`.
- The root line is unchanged.

**F5.**
- Replace the `fl-upper` position line with `<position name="fl-swing" joint="fl-swing" kp="150" ctrllimited="true" ctrlrange="-1.047198 1.308997"/>` (no force limit).
- Still exactly 4 `forcerange=` (horse `-35 35`, dog 4 × `-9 9`).

### 6.2 Updated existing cases — `test/pm/creature_motion_test.cljs`

**M12.** The species line becomes `(cm/actions->ctrl (vec (repeat 18 0)) (c/ctrl-ranges c-horse))`: count 18, approx= the mids vector of §3. The first four fixture lines are unchanged.

### 6.3 Updated existing cases — `test/pm/creature_mujoco_test.cljs`

Each of these was re-run on the new bodies.

**R1/R2 helper.** `check-counts-masses-ranges` checks **every** body: `(range 1 (inc (count masses)))`, so 12 bodies. The counts come from `c/counts` (§3).
- Horse `qpos[0..2]` = `[4.5 8 3.03]` and dog `[4.5 8 2.63]` (unchanged).
- `body_mass` in MuJoCo, horse: `0 28.274334 4.297699 5.439144 3.508112 2.127905 3.508112 2.127905 28.274334 3.508112 2.127905 3.508112 2.127905`.

**R3.** Unchanged. Measured: horse torso 0.8387 (band 0.80–0.86), dog 0.4388 (0.40–0.46), never below r, all finite.

**R5.** The twitch uses `nu = (count (c/ctrl-ranges sp))` channels (18), not 12. The hinge-moved check runs over `(range 7 (+ 7 nu))`.
- Measured after 1000 steps: horse torso 0.160, dog 0.123 (both > 0.05); hinges moved; finite.
- The old 12-channel form also still passes (0.165 / 0.125), but it would leave 6 actuators idle.

**R6.** Unchanged. Measured (no-effort, ±90): horse clearance 0.244 / tilt 1.2°, dog 0.141 / 0.6° (needs ≥ 0.175 / ≥ 0.105, ≤ 20°).

**R7.** Unchanged. Measured: horse clearance 0.022, tilt 89.1°; dog 0.030, 94.2°.

**R8.** `(= 18 nv)` → `(= 24 nv)`. Everything else is unchanged: the head is body 3, dofs 6–9, and the same expected columns (col 10 is now `fl-splay`, still zero for the head).

**R9.** The not-limited loop becomes `(range 4 18)` (14 leg/spine actuators with `forcelimited` 0); 0–3 are still ±35 / ±9.

**R10** (horse, 30 s side drop, effort).
- The torso check also covers the hind half: with `hid = id(sp, :hind)` (8) and `hcap = cap(sp, :hind)`, `capsule-clearance … hid hcap` ≤ 0.02 for t ≥ 3 s.
- Measured:
  - 4 clears.
  - Resting from 9.5, 16.1, 22.8 and 29.9 s; active again at 11.3, 17.9 and 24.6 s.
  - Peak force 35.00.
  - Torso low point max 0.002, hind 0.002.
  - Finite throughout.

**R11** (dog, same with the hind check). Measured: held 29.2 s, never rested, final energy 0.93, peak force 9.00, torso max 0.000, hind 0.000.

**R12** (game drop, roll 0, 18-channel twitch with kicks, limp, seed 1). Add the hind check (≤ 0.02 for t ≥ 5 s).
- Measured (torso/hind max): horse 0.008/0.011, dog 0.010/0.009.
- Seeds 2–3, for reference: horse 0.006/0.007 and 0.006/0.011; dog 0.003/0.003 and 0.005/0.008.

**R13** (dog table contrast). Also return the hind's low point.
- Stiff (σ 1): torso 0.339, hind 0.339 (both > 0.3).
- Limp (σ 0.1): torso 0.000, hind 0.001 (both ≤ 0.02).
- Torso centre: 0.439 stiff vs 0.100 limp. The limp dog now lies flat on its side (in effort-v0 it sat propped at 0.209), because the splay lets the legs fold out.

### 6.4 New cases — pure (`creature_test.cljs` B1–B4, `creature_motion_test.cljs` B5)

**B1 — the 18 joints' axes and the sign rule.** For both species:
- `(mapv :axis (c/joints sp))` = `[[0 0 1] [0 1 0] [0 1 0] [1 0 0] [1 0 0] [0 1 0] [0 1 0] [-1 0 0] [0 1 0] [0 1 0] [0 0 1] [1 0 0] [1 0 0] [0 1 0] [0 1 0] [-1 0 0] [0 1 0] [0 1 0]]`.
- For every joint whose name ends in `-splay`: let `b` be its `:body` and `y` = `(second (:at b))`. Then `(first (:axis j))` has the same sign as `y`: +1 for `fl` and `hl`, −1 for `fr` and `hr`, so 4 checks per species.
- Every `-swing` range is `[-1.047198 1.308997]`, every `-splay` range `[-0.436332 0.436332]`, spine-bend `[-0.436332 0.436332]` and spine-twist `[-0.349066 0.349066]`.
- `(count (c/joints sp))` = 18.

**B2 — mass and length within 5 % of before.**
- The before values are constants, from the old C16/C17: horse 88.829579, dog 27.177656.
- After `(reduce + (map second (c/body-masses sp)))`: horse approx= 88.829579, ratio approx= 1.0; dog 26.968217, ratio approx= 0.992294.
- Both satisfy `(<= (js/Math.abs (- ratio 1)) 0.05)`.
- Length = (max of `x + r` over the `:torso` and `:hind` capsule ends) − (min of `x − r`). Both bodies sit at x-offset 0; `:hind :at` is `[0 0 0]`, so assert that too.
- Horse length 0.9 (before 0.9 = 0.6 + 2·0.15), dog 0.64 (before 0.44 + 2·0.1); ratio 1.0, within 5 %.

**B3 — the tree.**
- The root `(first (:bodies sp))` has `:name :torso` and no `:parent`.
- `:hind`'s `:parent` is `:torso`, and its `:at` is `[0 0 0]`.
- `:hl-upper`'s and `:hr-upper`'s `:parent` is `:hind`.
- `:fl-upper`'s and `:fr-upper`'s `:parent` is `:torso`.
- The compiled reflex is unchanged: `(select-keys (first (c/reflexes sp)) [:body-id :acts :qpos :dofs])` = `{:body-id 3 :acts [0 1 2 3] :qpos [7 8 9 10] :dofs [6 7 8 9]}`.
- `(c/rest-bottom horse)` approx= −0.84; `(c/rest-bottom dog 90)` approx= −0.105.

**B4 — MJCF nesting and actuator order** (horse at `P`; `lines` = `str/split-lines`):
- The line `      <body name="hind" pos="0 0 0">` exists (6 spaces).
- The next three lines are, after trimming, `<joint name="spine-bend" …`, `<joint name="spine-twist" …` and `<geom name="hind" …`.
- The line `        <body name="hl-upper" pos="-0.28 0.1 -0.05">` exists with **8** spaces (nested in `:hind`), and its index is greater than the hind line's.
- In `<body name="fl-upper"`, the next two lines start (trimmed) with `<joint name="fl-splay"` then `<joint name="fl-swing"`.
- The `name` of every `<position name="…"` in order = `(mapv (comp name :name) (c/joints horse))` (18).
- The same order check holds for the dog.

**B5 — everything downstream adapts by count** (`creature_motion_test.cljs`; `n = (count (c/ctrl-ranges c-horse))`):
- `n` = 18.
- `(count (cm/twitch-actions (cm/twitch-step (cm/controller :twitch n 1) 0.002)))` = 18.
- `(cm/stiffness (cm/reflex-controller (cm/controller :twitch n 1) (c/reflexes c-horse) 1) 0.1 0.8)` has 18 entries: entries 0–3 = 1, and every other entry is 1 or 0.1.
- `(count (cm/limp (vec (repeat 18 0)) (js/Float64Array. 25) (vec (repeat 18 0.1))))` = 18, all 0.

### 6.5 New cases — real MuJoCo (`creature_mujoco_test.cljs`)

**R14 — the v2 bodies compile with the right joints and actuators.** For horse and dog at `P`:
- `nu` = 18 and `njnt` = 19.
- For joint id `j` = 1..18 (id 0 is the free root): `jnt_axis[3j .. 3j+2]` approx= `(:axis (nth (c/joints sp) (dec j)))`. In particular, id 8 (`fr-splay`) is `[-1 0 0]` and id 11 (`spine-bend`) is `[0 0 1]`.
- For actuator `k` = 0..17: `actuator_trnid[2k]` = k + 1 (each actuator drives its own joint).
- `mj_name2id(mjOBJ_BODY, "hind")` = 8 = `id(sp, :hind)`.
- `body_mass[8]` approx= 28.274334 (horse) / 8.901179 (dog).

**R15 — kinematic splay check, lying on his side** (numbers by hand, §1). No stepping: a fresh `MjData`, one hinge's qpos set, then `mj_kinematics`.
- **Model:** horse/dog at `(assoc P :roll 90)`, so the right legs are down.
- **Hoof tip:** for lower body `id` and lower length `L` (horse 0.37, dog 0.18), tip z = `xpos[3id+2] + xmat[9id+8]·(−L)`. Report Δ = tip z − torso z (`xpos[5]`).
- **Hand formula:** the leg is straight, ℓ = upper + lower (horse 0.75, dog 0.38), hip y = −0.1 / −0.07. The roll maps body y → world z, so Δ = y_hip − ℓ·sin θ for a right leg.

| species | joint (qpos idx) | θ | Δ expected | hand |
|---|---|---|---|---|
| horse | `fr-splay` (14), tip of body 7 | 0 | −0.100000 | −0.1 |
| horse | `fr-splay` | +25° | **−0.416964** | −0.1 − 0.75·0.4226183 |
| horse | `fr-splay` | −25° | +0.216964 | −0.1 + 0.3169637 |
| horse | `hr-splay` (22), body 12 | +25° | −0.416964 | same, hind |
| horse | `fl-splay` (11), body 5 (up side) | +25° | +0.416964 | +0.1 + 0.3169637 (moves away) |
| dog | `fr-splay` (14), body 7 | 0 / +25° | −0.070000 / **−0.230595** | −0.07 − 0.38·0.4226183 |
| dog | `hr-splay` (22), body 12 | +25° | −0.230595 | |

- **Assertions:** each Δ approx= the table value (eps 1e-6). Assert also that +25° moves the down-side hoof **down** (Δ(+25) < Δ(0)) and the up-side hoof up.
- **The other side** (`:roll -90`, left legs down): `fl-splay` (11, body 5) and `hl-splay` (19, body 10) at +25° give Δ = −0.416964 (horse) / −0.230595 (dog); `fr-splay` at +25° gives +0.416964 / +0.230595.
- **Absolute check:** the horse's torso centre is 2.34 at this place, so the right front hoof's lowest point (tip − r 0.04) above the ground goes from 0.410000 at 0° to **0.093036** at +25°.

**R16 — fold sign, standing pose.** The model is at `{:at [0 0 0] :yaw 0 :drop-m 0}` (torso z = 0.84 horse / 0.44 dog); a fresh `MjData`, one swing qpos, then `mj_kinematics`. Report the knee (the lower body's origin) − the torso origin, `[x y z]`.

| species | joint (qpos idx) | θ | knee − torso expected | hand |
|---|---|---|---|---|
| horse | `fl-swing` (12), body 5 | +75° | [−0.087052 0.1 −0.148351] | x 0.28 − 0.38·sin 75°, z −0.05 − 0.38·cos 75° |
| horse | `fl-swing` | −60° | [0.609090 0.1 −0.24] | 0.28 + 0.38·sin 60° |
| horse | `hl-swing` (20), body 10 | −60° | [0.049090 0.1 −0.24] | −0.28 + 0.3290897 |
| horse | `hl-swing` | +75° | [−0.647052 0.1 −0.148351] | |
| horse | `fr-swing` (15), body 7 | +75° | [−0.087052 −0.1 −0.148351] | mirror |
| horse | `hr-swing` (23), body 12 | −60° | [0.049090 −0.1 −0.24] | mirror |
| dog | `fl-swing` (12), body 5 | +75° | [0.006815 0.07 −0.081764] | 0.2 − 0.2·sin 75°, −0.03 − 0.2·cos 75° |
| dog | `fl-swing` | −60° | [0.373205 0.07 −0.13] | |
| dog | `hl-swing` (20), body 10 | −60° | [−0.026795 0.07 −0.13] | −0.2 + 0.2·sin 60° |
| dog | `hl-swing` | +75° | [−0.393185 0.07 −0.081764] | |

Assert the values (eps 1e-6). Also assert the **fold rule**: |x| of the folded knee (+75 front, −60 hind) is less than the hip's |x| (0.28 / 0.2), and the other sign makes it greater.

**R17 — spine signs, standing pose** (as R16):
- **`spine-bend`** (qpos 17) = +25°: the hind end point = `xpos[8] + xmat[8]·[-0.3 0 0]` (dog `[-0.22 0 0]`), minus the torso origin.
  - Horse: approx= `[-0.271892 -0.126785 0]` (= −0.3·[cos 25°, sin 25°, 0]).
  - Dog: `[-0.199388 -0.092976 0]`.
- **`spine-twist`** (qpos 18) = +20°: the `hl-upper` origin (body 9) − the torso.
  - Horse: approx= `[-0.28 0.111070 -0.012783]`. By hand: y = 0.1·cos 20° + 0.05·sin 20°; z = 0.1·sin 20° − 0.05·cos 20°. The left hip rises from −0.05.
  - Horse `hr-upper` (body 11): `[-0.28 -0.076868 -0.081187]` (falls).
  - Dog: hl `[-0.2 0.076039 -0.004249]`, hr `[-0.2 -0.055518 -0.052132]`.

**S1 — the quest's stand-hold measurement, reported** (both species, shipped `:kp`).
- **Model:** `(c/mjcf sp {:at [0 0 0] :yaw 0 :drop-m 0})`: ground z 0, torso centre at standing height (0.84 / 0.44), legs straight under it.
- **Run:** `mj_forward`, then 2500 steps (5 s). Before each step write ctrl = 0 for all `nu` actuators (full `:kp`, no limp).
- **At the end:** h = `xpos[5]`; tilt = `(cm/tilt-deg (cm/head-up (.-xmat data) 1 [0 0 1]))`.
- **Print** one line per species, e.g. `stand-hold horse kp 150: torso 0.803 m, tilt 0.2° after 5 s`.
- **Measured:**
  - horse h **0.8028**, tilt **0.17°**; at 4 s it was 0.8372, so it is sinking.
  - dog h **0.4388**, tilt **0.05°**.
- **Assertions (the quest's literal criterion):** both finite; horse 0.70 ≤ h ≤ 0.84 and tilt ≤ 20; dog 0.42 ≤ h ≤ 0.445 and tilt ≤ 1.
- **For comparison:** the old 12-joint horse gave h 0.4159 and tilt 62.5° here. Not asserted; that body is gone.

**S2 — does it hold a stand (the finding), and the smallest `:kp` in steps of 25.**
- **`holds?(sp, kp)`:** build the S1 model from `(assoc sp :kp kp)`. With `H = (- (c/rest-bottom sp))` (0.84 / 0.44), a run **fails** at the first step where `xpos[5] < (* 5/6 H)` (0.70 / 0.3667) or tilt > 20°. Stop at the first failure. `holds?` is true only if all three runs pass:
  - (a) still for 30 s (15000 steps);
  - (b) `qvel[0]` = 0.1 (a 0.1 m/s forward push) set once after `mj_forward`, then 10 s;
  - (c) `qvel[1]` = 0.1 (sideways), 10 s.
- **Horse:** `(mapv #(holds? horse %) [150 175 200 225])` = `[false false false true]`, so the smallest holding `:kp` = **225**.
- **Dog:** `(holds? dog 40)` = true, so the smallest is **40** (today's).
- **Print** `stand-hold finding: horse needs :kp 225 (has 150); dog holds at 40`.
- **Measured fall times** (first failing step, still / forward push / side push):
  - kp 150: 5.47 / 1.79 / 1.76 s
  - kp 175: 5.26 / 2.16 / 2.05 s
  - kp 200: 8.93 / 3.24 / 2.61 s
  - kp 225: never / never / never
  - dog kp 40: never in all three, and also with 0.2 m/s pushes
- **Runtime:** about 25 k steps for horse kp 225, about 10 k for the failing ones, and 25 k for the dog. These are plain ctrl writes, cheaper than R10.

**Count.**
- **11 new cases:** B1–B5 = 5, R14–R17 = 4, S1–S2 = 2.
- **23 updated:** C4, C13, C14, C15, C16, C17, C20, C21, C26, C27, C28, C32, F5, M12, R1, R2, R5, R8, R9, R10, R11, R12, R13. R3, R6 and R7 are re-verified and unchanged.

## 7. Findings and honest notes

1. **Stand-hold (for Michael, and Hal's run log).**
   - **The literal check:** the 18-joint foal at `:kp` 150 passes the quest's 5 s check (0.803 m, 0.2°), but it is a passing frame of a fall: down at 5.47 s, and at 1.8 s after a 0.1 m/s nudge.
   - **What holds:** a stand that lasts needs **`:kp` 225**, the smallest in steps of 25, with 30 s still and 0.1 m/s pushes both ways. At 225 he still creeps forward slowly and falls at 58 s. **250** holds 120 s still and a 0.2 m/s forward push, but neither 225 nor 250 survives a 0.2 m/s *sideways* push (down at 1.8 / 2.4 s): his hooves are only 0.2 m apart, so that is mostly geometry, not strength.
   - **Today's body is the same:** the 12-joint body also fails at 150 (falls at 4.28 s) and needs 225. Body v2 didn't make him weaker.
   - **A rough hand check:** for the simplest sway (legs parallel, torso level), gravity's tipping stiffness is about g·(66.3 kg·0.75 m + legs) ≈ 580 N·m/rad, against 4·kp from the four upper joints. 4·150 = 600 is barely above it, and the knees in series take the rest. 4·225 = 900 is the first with real margin.
   - **Not changed:** `:kp` stays 150 in `horse.edn`.
2. **The regenerated `horse.xml` also gains effort-v0's force caps.** The committed `creatures/bodies/*.xml` predate effort-v0: they have no `forcerange`, though `bb creature-mjcf` has emitted one since effort-v0. Regenerating now adds `forcelimited="true" forcerange="-35 35"` to the horse's four neck/head actuators (dog: ±9). The omarchy task loads `horse.xml` as-is, so training run 2 will have a weaker neck than run 1 had (35 N·m instead of 150 N·m/rad × error). This is correct per the design (effort-v0), but Le Coureur and Michael should know.
3. **`creatures/foal/task.py` will need Le Coureur** (out of scope here). It lists the old joint names `"fl-upper" … "hr-lower"` (lines 30–37) as joints; the upper ones are now `*-splay` and `*-swing`. Body names are unchanged (`fl-lower`, etc., and `_FOOT_LOCAL_POS (0, 0, −0.37)` still matches), and "torso" is still the root body at standing height 0.84.
4. **Lying down got better.** With splay and the spine, the limp foal and dog now lie flat on their side: R13's limp dog has its torso centre at 0.100, where in effort-v0 it sat propped on its front legs at 0.209. The effort-v0 caveat about "sitting with the front propped up" no longer applies to a zero-control drop. The torso's low point stays ≤ 0.011 m in every drop I ran.
5. **The HUD's "torso" is the front half.** It is still body 1 (`xpos[5]`), the front half's centre at x = 0 of the old torso, so the standing reading stays 0.84 m. With the spine bent, the hind half can differ; R10–R13 check both halves.
6. **The live check (Hal).** `?pony` and `?pony=dog` load, flop and lift their heads. The twitch drives 18 channels, so the shoulders, hips and spine wiggle, and the spine's ±25°/±20° is visible as the two torso capsules hinging at the middle.
