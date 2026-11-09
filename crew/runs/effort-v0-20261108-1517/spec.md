# Spec: effort-v0 (lifting his head is hard work)

Quest: `.crew/quests/effort-v0.md`. Builds on head-reflex-v0 (commit `13494af`; spec
`crew/runs/head-reflex-v0-20261108-1435/spec.md` holds for everything not changed here).
Architect: un-architecte, 2026-11-08. Builder: un-automate-monde. Tester: le-testeur.

Every name, signature, string and number below is binding.

**Checked in real MuJoCo first** (`@mujoco/mujoco` 4.0.0, Node 26, the exact algorithm of §5,
in a scratch script). Each species was dropped on its side with the legs at zero, for 30
simulated seconds:
- **Horse** (seed 1): 4 tries, clearing 0.25 m at 1.4, 12.5, 19.4 and 27.1 s, with the head
  sinking below 0.125 m at 10.6, 17.7 and 24.9 s. It stops trying (energy below 0.15) at
  9.8, 17.0 and 24.2 s, and tries again (energy above 0.6) at 11.6, 18.8 and 26.1 s. The force
  cap really engages: the peak force on the neck/head actuators is exactly 35.00 N·m.
- **Dog:** the head is up and level for 29.2 of the 30 s. It never tires (energy 0.926 at 30 s),
  and its peak force is 9.00 N·m.
- **Robustness:** the same holds on both sides (roll ±90°), for seeds 1–3, and with the legs
  twitching (the legs move the head by about 1 cm). There was no NaN.
- **The existing tests:** R3 (stand with zero controls) still passes with the force limits
  (0.8389 m / 0.4392 m). R6 and R7 pass unchanged on the species with `:effort` stripped.

**Limp when not trying** (Michael's added requirement, §5.4) was also checked in real MuJoCo,
with `:rest-kp` = 10 % of `:kp` and the full model above:
- **Side drop, legs limp at zero, 30 s:** the torso capsule's lowest point is 0.000 m (horse) and
  0.004 m (dog) from 3 s on. The horse still clears 4 times, now resting from 9.8, 16.6 and
  23.4 s (active again at 11.6, 18.4 and 25.2 s). The dog holds 29.2 s.
- **The game's own drop** (upright, roll 0, twitching legs with kicks, 30 s, seeds 1–5): both
  bodies flop onto their side. The torso's lowest point is 0.000 m (horse) and ≤ 0.004 m (dog)
  from 5 s on. The horse clears 4–5 times, the dog holds 28.3–29.6 s, and there was no NaN.
- **The table, for contrast:** the dog upright with every joint held at 0 at full stiffness
  stands on straight legs, with the torso's lowest point 0.339 m up.
- **Limp but no kicks, upright:** both sit pitched, front up 0.22 m (dog) and 0.34 m (horse).
  The front legs prop the chest because the ±45° leg hinges can't fold under it, and this is the
  same at 5 %, 10 % or 15 % rest stiffness. Lying on the chest needs that fold and is this
  quest's out-of-scope "rolling onto the chest", so this case is reported, not asserted. With the
  game's kicks, they roll onto their side, as above.

How the numbers were found (all are species data, §3): lifting a foal's head off the grass on
its side needs about 30–40 N·m at the neck-bend (130 N·m peaks with no limit). Holding it costs
about 9 N·m. A 14 N·m cap never lifts it; 35 N·m lifts it slowly. With the quest's example
`:energy-s 6`, one hold lasted about 20 s, because holding level is cheap. `:energy-s 2.5` gives
holds of about 5 s, then a rest of about 2 s.

---

## 1. What changes

```
resources/creatures/{horse,dog}.edn   each reflex gains :effort {...}; top level gains :rest-kp, :kick-above
pm.creature (cljc)        :effort/:rest-kp/:kick-above validation; compiled into (reflexes sp); forcerange; rest-fraction
pm.creature-motion (cljs) pure: effort state machine, effort amount, tremor, stiffness + limp; the :reflex layer; HUD
pm.creature-spike         obs gains :forces (actuator_force); seed into reflex-controller; limp before writing ctrl; HUD
```

No new namespaces and no new files in `src/`. A reflex **without** `:effort` behaves exactly as
before: the same compiled map, the same MJCF, the same controller map, the same actions, so every
head-reflex-v0 test keeps passing.

## 2. Who touches what

| Path | Builder (un-automate-monde) | Tester (le-testeur) |
|---|---|---|
| `src/pm/creature.cljc` | edit (§4) | read only |
| `src/pm/creature_motion.cljs` | edit (§5) | read only |
| `src/pm/creature_spike.cljs` | edit (§6) | — |
| `resources/creatures/horse.edn`, `dog.edn` | add `:effort`, verbatim from §3 | read only |
| `test/pm/creature_test.cljs` | — | update C20, C21, C37 (§7.1); add F1–F6, G1–G2 |
| `test/pm/creature_effort_test.cljs` | — | **new**: E1–E9, T1–T4, A1–A3, L1, H1, K1–K3 (§7.2) |
| `test/pm/creature_mujoco_test.cljs` | — | update R6, R7 (§7.3); add R9–R13 |

Nobody edits `package.json`, `public/vendor/`, `creatures/` (Hal regenerates the bodies),
`pm.core`, `pm.three`, `pm.species`, the estate, `bb.edn`, `docs/GDD.md`, `assets/private/` or
`public/kit/`. The architect writes only this file. Nobody commits.

---

## 3. Species data

Add `:effort` to the existing reflex entry. Everything else in both files stays as is.

**horse.edn**, in the `:head-level` reflex, after `:onset-s 1.5`:

```clojure
             ;; A newborn's neck: weak, shaky, tires fast (pm.creature-motion effort).
             ;; force cap = :strength × :torque-nm on each reflex actuator (MJCF forcerange);
             ;; tremor :tremor-deg fades with time awake (÷e every :steady-s); energy 1 → 0 at
             ;; full effort in :energy-s, refills 0 → 1 in :recover-s; lets go below :low, tries again above :high.
             :effort {:strength 0.35 :torque-nm 100 :tremor-deg 6 :steady-s 90
                      :energy-s 2.5 :recover-s 4 :low 0.15 :high 0.6 :seed-salt 7}
```

**dog.edn**, in the same place:

```clojure
             ;; A grown dog: strong and steady, so little tremor and long holds.
             :effort {:strength 0.9 :torque-nm 10 :tremor-deg 1 :steady-s 30
                      :energy-s 30 :recover-s 4 :low 0.15 :high 0.6 :seed-salt 3}
```

The force caps work out to horse 0.35 × 100 = **35 N·m** and dog 0.9 × 10 = **9 N·m**.

**Rest stiffness (both files, top level, right after `:damping`):**

```clojure
 :rest-kp    15         ; N·m/rad: muscles that aren't working are soft (10 % of :kp), so he lies, not stands
 :kick-above 0.8        ; a twitch segment whose random target |to| >= this is a kick (full :kp); else rest stiffness
```

For the dog, `:rest-kp 4` (10 % of its `:kp` 40), with the same `:kick-above 0.8`. The
rest fraction σ = rest-kp / kp is **0.1** for both. `:rest-kp` lives only on the controller side:
the MJCF keeps `kp` (the strong value) and is unchanged by it.

`:effort` keys:
- `:strength` 0–1: scales the gain and caps the force.
- `:torque-nm`: full-strength force range per reflex actuator.
- `:tremor-deg`: tremor amplitude at wake.
- `:steady-s`: tremor decay time constant.
- `:energy-s`: full-effort drain time (1 → 0).
- `:recover-s`: refill time (0 → 1).
- `:low` / `:high`: the hysteresis thresholds.
- `:seed-salt`: an integer added to the URL seed for the tremor noise.
- Optional, with defaults: `:ramp-s` **0.5** (strength ramp out/in) and `:tremor-speed` **8**
  (tremor noise runs 8× faster than the twitch, so it shakes rather than nods).

---

## 4. `pm.creature` (cljc)

### 4.1 Validation (in each reflex entry, after the existing `:onset-s` check)

Each problem text is prefixed with `"reflex <i>: "`. The checks run in this order, and all of
them are collected:

| # | rule | text after the prefix |
|---|---|---|
| F-a | `:effort`, if present, is a map (if not, no other F check runs) | `:effort must be a map` |
| F-b | `:strength` is a number with 0 < s ≤ 1 | `:effort :strength must be a number in (0, 1]` |
| F-c | `:torque-nm` is a number > 0 | `:effort :torque-nm must be a positive number` |
| F-d | `:tremor-deg` is a number ≥ 0 | `:effort :tremor-deg must be a number >= 0` |
| F-e | `:steady-s` is a number > 0 | `:effort :steady-s must be a positive number` |
| F-f | `:energy-s` is a number > 0 | `:effort :energy-s must be a positive number` |
| F-g | `:recover-s` is a number > 0 | `:effort :recover-s must be a positive number` |
| F-h | `:low` is a number in [0, 1] | `:effort :low must be a number in [0, 1]` |
| F-i | `:high` is a number in [0, 1] | `:effort :high must be a number in [0, 1]` |
| F-j | when F-h and F-i pass: `:low` < `:high` | `:effort :low must be below :high` |
| F-k | `:seed-salt` is an integer | `:effort :seed-salt must be an integer` |
| F-l | `:ramp-s`, if present, is a number > 0 | `:effort :ramp-s must be a positive number` |
| F-m | `:tremor-speed`, if present, is a number > 0 | `:effort :tremor-speed must be a positive number` |

No `:effort` key at all is valid.

**Top-level rest stiffness checks**, after P5 (`:colors`) and before P6. Both keys are optional:

| # | rule | text |
|---|---|---|
| G-a | `:rest-kp`, if present, is a number with 0 < rest-kp ≤ `:kp` (checked only when P3 passed) | `":rest-kp must be a number in (0, :kp]"` |
| G-b | `:kick-above`, if present, is a number in [0, 1] | `":kick-above must be a number in [0, 1]"` |

- **`(rest-fraction sp)`** → `(/ rest-kp kp)`, or `1` when `:rest-kp` is absent (everything
  stiff: today's behaviour). Horse and dog give 0.1.
- **`(kick-above sp)`** → `:kick-above`, or `0.8` when absent.

### 4.2 Compiled reflexes

`(reflexes sp)` adds `:effort` **only when the species reflex has one**, with the defaults
filled in:
`{:strength :torque-nm :tremor-deg :steady-s :energy-s :recover-s :low :high :seed-salt :ramp-s :tremor-speed}`.
It also adds **`:rest-frac (rest-fraction sp)`**, the species' σ, which the reflex needs while
he rests. Horse:
`{:strength 0.35 :torque-nm 100 :tremor-deg 6 :steady-s 90 :energy-s 2.5 :recover-s 4 :low 0.15 :high 0.6 :seed-salt 7 :ramp-s 0.5 :tremor-speed 8 :rest-frac 0.1}`.
Without `:effort`, the map has no `:effort` key (`contains?` is false), exactly as today.

### 4.3 MJCF force limits

For every actuator `k` in the `:acts` of a reflex with `:effort` (the first such reflex wins if
two claim one), the `<position .../>` line gains ` forcelimited="true" forcerange="-F F"` just
before `/>`, with `F = (* strength torque-nm)` printed by `fmt`. All other actuators are
unchanged. Horse (shown exactly as generated):

```xml
<position name="neck-bend" joint="neck-bend" kp="150" ctrllimited="true" ctrlrange="-1.22173 1.22173" forcelimited="true" forcerange="-35 35"/>
```

The architect compiled this in real MuJoCo: `actuator_forcerange[0..1]` = `[-35 35]` and
`actuator_forcelimited` = `1,1,1,1,0,…`. For the dog it is `forcerange="-9 9"`.

---

## 5. `pm.creature-motion` (cljs): pure

### 5.1 Energy with hysteresis

```clojure
(effort-init) ;=> {:energy 1 :mode :active :level 1}
```

- **`(effort-amount forces acts fmax)`** → `clamp(Σ_{k∈acts} |forces[k]| / (count(acts) · fmax), 0, 1)`.
  `forces` is a JS array (`aget`). It returns `0` when `forces` is nil, `acts` is empty or
  `fmax ≤ 0`.
- **`(effort-step st e amount dt)`** → st'. If `dt` is not a finite number > 0, it returns `st`
  unchanged. Otherwise, with `a = clamp(amount, 0, 1)`:
  1. `energy' = (if active: max(0, E − dt·a/energy-s); if resting: min(1, E + dt/recover-s))`
  2. `mode'`: `:active` with `energy' < low` → `:resting`; `:resting` with `energy' > high` →
     `:active`; otherwise unchanged. **No flapping:** between low and high, nothing changes.
  3. `level'` moves toward the target (1 when `mode'` is `:active`, 0 when `:resting`) by
     `dt / ramp-s`, clamped to [0, 1].

  Resting refills whatever the effort. Only the active mode drains.
- **`(effort-label st r sense)`** → `"resting"` when `:resting`. Otherwise `"holding"` when
  `(:clearance sense) ≥ (:lift-m r)` and `(tilt-deg (:up sense)) ≤ 20`, else `"trying"`.

### 5.2 Tremor

- **`(tremor-deg-at e t)`** → `(:tremor-deg e) · exp(−max(0, t) / (:steady-s e))`, in degrees.
- The tremor noise is a twitch controller of `(count (:joints r))` channels, seeded
  `(+ seed (:seed-salt e))`. It is stepped by `dt · (:tremor-speed e)`, and its channel values
  `v_j` ∈ [−1, 1] are multiplied by `rad(tremor-deg-at e t') · level'`, so a resting head
  doesn't shake.

### 5.3 The `:reflex` layer

- **`(reflex-controller inner reflexes)`** = `(reflex-controller inner reflexes 1)`.
- **`(reflex-controller inner reflexes seed)`** → the old map
  `{:kind :reflex :n :inner :reflexes :t 0}`, plus `:efforts` **only if some reflex has
  `:effort`**. `:efforts` is one entry per reflex: `nil` (no effort) or
  `(assoc (effort-init) :tremor (twitch-init (count (:joints r)) (+ seed (:seed-salt e))))`.
  With no effort anywhere, the map is identical to today's, whatever the seed.

**`act` `:reflex` branch:** steps 1–3 are as before (inner first, `t' = t + dt'`, and nil `obs`
is transparent: `:efforts` unchanged). For each reflex `r` at index `i`:
- **Without `:effort`:** exactly as before.
- **With `e = (:effort r)` and `st = (nth (:efforts ctrl) i)`:**
  1. `fmax = strength · torque-nm`, `amount = (effort-amount (:forces obs) (:acts r) fmax)`.
  2. `st1 = (effort-step st e amount dt')`, and `tremor' = (twitch-step (:tremor st) (* dt' (:tremor-speed e)))`.
  3. `s = (reflex-strength t' onset-s) · strength`. This is the gain scaled by strength. The
     level is **not** in `s`; it blends in step 5.
  4. `targets = (reflex-targets r sense q s nv)`. Then `T_j = clamp(target_j + rad(tremor-deg-at e t') · (:level st1) · v_j, lo_j, hi_j)`,
     with `v = (twitch-actions tremor')`.
  5. **Blend trying with resting:** with `L = (:level st1)` and `σ = (:rest-frac e)`, the final
     target is `final_j = L · T_j + (1 − L) · (q_j + σ · (0 − q_j))`. At L = 1 this is the reflex,
     at full stiffness. At L = 0 it is a soft spring toward the neutral pose at rest stiffness
     (torque = rest-kp · (0 − q)), so the head sinks under gravity. Write
     `(target->action final_j range_j)`. All ranges contain 0, so `final_j` is in range.
     Store `(assoc st1 :tremor tremor')` at `[:efforts i]`.

`obs` gains **`:forces`**, the MuJoCo `actuator_force` array of the previous step. A missing
`:forces` gives amount 0.

**The controller pattern:** the effort is state inside the reflex layer, like the walker's pause
and wait inside `pm.motion`'s walker map. Pure in, pure out, and time is an argument (`dt`). The
interop only supplies observations (`:forces` now joins `:qpos` and `:senses`). A trained policy
can still replace the inner controller or the whole layer.

### 5.4 Limp when not trying (stiffness from data, pure)

MuJoCo's position servo pushes `kp · (ctrl − q)`. Commanding `ctrl = q + σ · (target − q)` gives
exactly `σ·kp · (target − q)`, which is a servo of stiffness σ·kp toward the same target, and
the MuJoCo model is never touched. The joint damping is unchanged.

- **`(limp targets qpos sigmas)`** → `(mapv (fn [k t σ] (let [q (aget qpos (+ 7 k))] (+ q (* σ (- t q))))) (range) targets sigmas)`.
  `targets` are radians (from `actions->ctrl`), and `qpos` is the JS array (hinge k is at
  `qpos[7 + k]`). When σ = 1 the target comes back unchanged.
- **`(stiffness ctrl rest-frac kick-above)`** → a vector of `(:n ctrl)` σ values:
  - `:twitch`: per channel, **1 when `|(:to ch)| ≥ kick-above` (a kick)**, else `rest-frac`. A
    kick is a whole twitch segment aimed near the end of the range, about 20 % of segments at
    0.8. This draws no extra random numbers, so the seeds replay as before.
  - `:reflex`: `(stiffness (:inner ctrl) …)` with every index in any reflex's `:acts` set to
    1, because the reflex already blends its own rest (§5.3 step 5).
  - Anything else (for example `:still`): `rest-frac` everywhere.
  - With `rest-frac` 1 (no `:rest-kp`), the result is all 1 and `limp` is the identity: today's
    behaviour.

**The pattern:** like the reflex blend, this is pure output shaping between `act` and MuJoCo. The
interop calls `stiffness` on the controller and passes the result to `limp`. A trained policy
would emit its own targets and the same limp step (or its own σ) applies.

### 5.5 HUD

`hud-text` `:running` appends, after the head segment, `" · energy " (.toFixed energy 2) " · " label`
when `(:effort status)` is `{:energy :label}`. Without it, the text is unchanged.

---

## 6. `pm.creature-spike` (builder; not unit-tested)

- `start!`: `(cm/reflex-controller tw rx (:seed req))`.
- `start!`: also store `:rest-frac (c/rest-fraction sp)` and `:kick (c/kick-above sp)`.
- `tick!`, per step: `ctrl-vals = (cm/limp (cm/actions->ctrl actions ranges) (.-qpos data) (cm/stiffness ctrl2 rest-frac kick))`,
  where `ctrl2` is the controller returned by this step's `act`. Then write `ctrl-vals` as now.
- `tick!`: obs gains `:forces (.-actuator_force data)`, read fresh every step. After the loop,
  when `(:efforts ctrl')` has a non-nil first entry `st` and a sense `s0`, status gains
  `:effort {:energy (:energy st) :label (cm/effort-label st (first rx) s0)}`.
- Nothing else changes (buffers, `fail!`, drawing).

---

## 7. Test cases

`horse` and `dog` are the parsed species. "≈" means `|a − b| ≤ 1e-6` unless stated.
`no-effort` = `(fn [sp] (update sp :reflexes (fn [rx] (mapv #(dissoc % :effort) rx))))`.

### 7.1 `test/pm/creature_test.cljs`

**Updated:**
- **C20** The horse's `<position name="head-pitch" ...>` line is now
  `<position name="head-pitch" joint="head-pitch" kp="150" ctrllimited="true" ctrlrange="-1.047198 1.047198" forcelimited="true" forcerange="-35 35"/>`.
  The other C20 lines are unchanged (the hl-lower joint line, the neck joints).
- **C21** The dog's `<position name="neck-bend" ...>` line is now
  `<position name="neck-bend" joint="neck-bend" kp="40" ctrllimited="true" ctrlrange="-1.22173 1.22173" forcelimited="true" forcerange="-9 9"/>`,
  and the `hr-lower` position line is unchanged (no force limit).
- **C37** `(c/reflexes horse)` = the head-reflex-v0 map plus `:effort` as in §4.2. The dog's
  effort is `{:strength 0.9 :torque-nm 10 :tremor-deg 1 :steady-s 30 :energy-s 30 :recover-s 4 :low 0.15 :high 0.6 :seed-salt 3 :ramp-s 0.5 :tremor-speed 8 :rest-frac 0.1}`.
  `(c/reflexes (no-effort horse))` = the old map exactly (no `:effort` key).

**New:**
- **F1** `(c/problems horse)` = `(c/problems dog)` = `[]`, and `(c/problems (no-effort horse))` = `[]`.
- **F2** One problem each, against horse, `[:reflexes 0 :effort <key>]` set to:
  - `:low 0.7` → `["reflex 0: :effort :low must be below :high"]`; `:low 0.6` → the same text
    (equal is not below)
  - `:strength 0` → `["reflex 0: :effort :strength must be a number in (0, 1]"]`; `:strength 1.2` → the same
  - `:torque-nm -5` → `["reflex 0: :effort :torque-nm must be a positive number"]`
  - `:tremor-deg -1` → `["reflex 0: :effort :tremor-deg must be a number >= 0"]`
  - `:steady-s 0` → `["reflex 0: :effort :steady-s must be a positive number"]`
  - `:seed-salt 1.5` → `["reflex 0: :effort :seed-salt must be an integer"]`
  - `:ramp-s 0` → `["reflex 0: :effort :ramp-s must be a positive number"]`
  - `:tremor-speed -8` → `["reflex 0: :effort :tremor-speed must be a positive number"]`
  - `:high 1.5` → `["reflex 0: :effort :high must be a number in [0, 1]"]` (no F-j, because F-i failed)
- **F3** `(c/problems (assoc-in horse [:reflexes 0 :effort] 3))` = `["reflex 0: :effort must be a map"]`.
  `(-> horse (assoc-in [:reflexes 0 :effort :energy-s] 0) (update-in [:reflexes 0 :effort] dissoc :recover-s))` →
  `["reflex 0: :effort :energy-s must be a positive number" "reflex 0: :effort :recover-s must be a positive number"]`.
- **F4** `(c/parse (pr-str (assoc-in horse [:reflexes 0 :effort :low] 0.9)))` throws
  `"bad species :horse: reflex 0: :effort :low must be below :high"`.
- **F5** Force limits in the XML: for `x = (c/mjcf horse {:at [4.5 1.79 -8.0] :yaw 0 :drop-m 0.4})`,
  the count of `"forcerange="` is 4, and the trimmed lines contain the §4.3 neck-bend line and
  `<position name="head-roll" joint="head-roll" kp="150" ctrllimited="true" ctrlrange="-1.22173 1.22173" forcelimited="true" forcerange="-35 35"/>`.
  The `fl-upper` position line has no `forcerange`. The dog gives 4 × `forcerange="-9 9"`.
- **F6** `(c/mjcf (no-effort horse) P)` has 0 `"forcerange="`, and its position lines equal
  head-reflex-v0's (for example `<position name="head-pitch" joint="head-pitch" kp="150" ctrllimited="true" ctrlrange="-1.047198 1.047198"/>`).
- **G1 Rest stiffness validation** (one problem each):
  - `(assoc horse :rest-kp 0)` → `[":rest-kp must be a number in (0, :kp]"]`
  - `(assoc horse :rest-kp 151)` → the same (above `:kp` 150)
  - `(assoc horse :rest-kp 150)` → `[]` (equal to `:kp` is allowed)
  - `(assoc dog :kick-above 1.5)` → `[":kick-above must be a number in [0, 1]"]`
  - `(dissoc horse :rest-kp :kick-above)` → `[]`
  - `(c/mjcf horse P)` = `(c/mjcf (dissoc horse :rest-kp :kick-above) P)`: the MJCF is
    unchanged by rest stiffness.
- **G2** `(c/rest-fraction horse)` ≈ 0.1 (15/150), `(c/rest-fraction dog)` ≈ 0.1 (4/40),
  `(c/rest-fraction (dissoc horse :rest-kp))` = 1. `(c/kick-above horse)` = 0.8, and
  `(c/kick-above (dissoc horse :kick-above))` = 0.8. `(:rest-frac (:effort (first (c/reflexes horse))))` ≈ 0.1.

### 7.2 `test/pm/creature_effort_test.cljs` (new, pure)

`e = {:energy-s 2 :recover-s 4 :low 0.25 :high 0.6 :ramp-s 0.5}` unless stated.
`act! = {:energy 1 :mode :active :level 1}`.

**The energy state machine**
- **E1 Full effort drains** `dt/energy-s`: `(cm/effort-step act! e 1 0.5)` ≈ `{:energy 0.75 :mode :active :level 1}`.
- **E2 Half effort drains half:** amount 0.5, dt 0.5 → `:energy` ≈ 0.875. Amount 2 is clamped,
  so it is the same as amount 1 (0.75). Amount −1 counts as 0 (1.0).
- **E3 Full effort empties it in `:energy-s`:** from `act!`, 20 steps of amount 1, dt 0.1 →
  `:energy` ≈ 0 (never negative) and `:mode :resting`. Along the way, after step 14 the energy is
  ≈ 0.3 and still `:active`, and after step 16 it is ≈ 0.2 and `:resting`. Don't assert step 15:
  the accumulated energy is 0.2499999…, right on `:low`.
- **E4 It lets go below `:low`, with a ramp out:** `(cm/effort-step {:energy 0.3 :mode :active :level 1} e 1 0.2)` ≈
  `{:energy 0.2 :mode :resting :level 0.6}` (0.3 − 0.1; 1 − 0.2/0.5). One more step with dt
  0.3 → `:level` 0 (clamped), and `:energy` ≈ 0.275 (+0.3/4).
- **E5 Hysteresis, no flapping:** at the same energy 0.4 (between low and high), the mode is
  kept. `(cm/effort-step {:energy 0.4 :mode :resting :level 0} e 1 0.4)` ≈
  `{:energy 0.5 :mode :resting :level 0}`. `(cm/effort-step {:energy 0.4 :mode :active :level 1} e 0 0.4)` ≈
  `{:energy 0.4 :mode :active :level 1}` (zero effort, no drain). Alternating amounts 1 and 0
  for 10 steps of dt 0.05, from `{:energy 0.4 :mode :active :level 1}`, never leaves `:active`
  (the energy only falls to ≈ 0.275).
- **E6 Recovery time:** with `e' = (assoc e :recover-s 5)`, from `{:energy 0.25 :mode :resting :level 0}`
  in steps of dt 0.1 (+0.02 each): after 17 steps the energy is ≈ 0.59 and `:resting`; after 18
  it is ≈ 0.61 and `:active` with `:level` 0.2. From `{:energy 0 :mode :resting :level 0}`, 60
  steps → `:energy` 1 (clamped). A full refill takes `:recover-s` (50 steps → ≈ 1).
- **E7 Ramp in:** `(cm/effort-step {:energy 0.7 :mode :resting :level 0} e 0 0.1)` ≈
  `{:energy 0.725 :mode :active :level 0.2}`. Four more steps (amount 0, dt 0.1) → `:level`
  0.4, 0.6, 0.8, 1.0. A fifth gives 1 (clamped).
- **E8 Time is an argument:** dt 0, −1 and `##NaN` return the state unchanged (`=`).
- **E9 `effort-amount`:** `(cm/effort-amount #js [10 -20 5 0 99] [0 1 2 3] 20)` ≈ 0.4375
  (35/80). `#js [40 -40 40 40]` → 1 (clamped). `fmax` 0 → 0. `nil` forces → 0. `acts [1]` with
  the first array → 1.0. `acts []` → 0.

**Tremor**
- **T1 Amplitude:** `(cm/tremor-deg-at {:tremor-deg 6 :steady-s 90} 0)` = 6. At t = 90 it is
  ≈ 2.207277 (6/e). At 180 it is ≈ 0.812012 (6/e²). At −5 it is 6. The dog's
  `{:tremor-deg 1 :steady-s 30}` at 30 is ≈ 0.367879.
- **T2 Seeded:** for `rh = (first (c/reflexes horse))`,
  `(:tremor (first (:efforts (cm/reflex-controller (cm/controller :still 12 1) [rh] 1))))` =
  `(cm/twitch-init 4 8)` (seed 1 + salt 7). Repeatable: two controllers built from T3's `r` with
  seed 1, each stepped 500 times with T3's `obs` and dt 0.002, are `=`, and so are their action
  vectors at every step. With seed 2, the first tremor channel's `:to` differs from seed 1's.
- **T3 Tremor is added to the targets.** `r` is the X11 reflex (`:acts [1] :qpos [8] :dofs [1] :ranges [[-1 1]] :joints [:j] :lift-m 0 :gain 1 :w-lift 0 :onset-s 0`)
  plus `:effort {:strength 1 :torque-nm 10 :tremor-deg 10 :steady-s 1e9 :energy-s 2 :recover-s 4 :low 0.25 :high 0.6 :seed-salt 7 :ramp-s 0.5 :tremor-speed 8 :rest-frac 0.1}`.
  `obs` has `up [0 0 1]`, clearance 1 (no Jacobian change), `qpos[8] = 0.1`, zero Jacobians and
  `:forces #js [0 0 0]`. Then `(cm/act (cm/reflex-controller (cm/controller :still 3 1) [r] 1) obs 0.1)`:
  - The tremor channel is `(cm/twitch-step (cm/twitch-init 1 8) 0.8)`, whose `:v` ≈ 0.179391.
  - The action at index 1 ≈ 0.1 + 0.174533 · 0.179391 = **0.131310** (range [−1, 1], so
    action = target). The indices 0 and 2 are 0.
  - With `:steady-s 0.1` (t' = 0.1, so ×1/e), it is ≈ **0.111518**.
- **T4 No shaking at rest:** as T3, but starting from `:efforts [{:energy 0.1 :mode :resting :level 0 :tremor (cm/twitch-init 1 8)}]`
  (`assoc` it into the controller). Index 1 ≈ **0.09**: level 0 gives no tremor and no reflex
  push, only the rest spring, 0.1 + 0.1 · (0 − 0.1) (§5.3 step 5).

**The layer**
- **A1 Gain scaled by strength:** the X11 setup (inner `:still` 3, `qpos[8] = 0.1`,
  `up [0.6 0 0.8]`, `jacr #js [0 0 1 0 1 0 1 0 0]`) with
  `:effort {:strength 0.5 :torque-nm 10 :tremor-deg 0 :steady-s 1 :energy-s 2 :recover-s 4 :low 0.25 :high 0.6 :seed-salt 0 :ramp-s 0.5 :tremor-speed 8 :rest-frac 0.1}`
  (hand-built effort maps in tests always carry `:ramp-s`, `:tremor-speed` and `:rest-frac`,
  because `act` reads the compiled shape, with the defaults already filled in)
  and `:forces #js [0 5 0]`, at dt 0.002:
  - The actions are ≈ `[0 -0.2 0]`: 0.1 + 0.5 · 1 · (−0.6), against X11's −0.5 without effort.
  - `(dissoc (first (:efforts c')) :tremor)` ≈ `{:energy 0.999 :mode :active :level 1}`
    (amount = 5/(1 · 5) = 1, so 1 − 0.002/2).
- **A2 Without effort, unchanged:** `(cm/reflex-controller inner [r-x11] 5)` =
  `(cm/reflex-controller inner [r-x11])` = `{:kind :reflex :n 3 :inner inner :reflexes [r-x11] :t 0}`
  (no `:efforts` key). X11 and X12 still pass unchanged.
- **A3 Nil obs is transparent:** with A1's controller, `(cm/act ctrl nil 0.002)` gives
  `:efforts` unchanged and `:t` 0.002.
- **L1 `effort-label`:** with `r {:lift-m 0.25}`:
  - `{:mode :resting ...}` → `"resting"`.
  - Active with sense `{:clearance 0.3 :up [0 0 1]}` → `"holding"`.
  - Clearance 0.1 → `"trying"`.
  - Clearance 0.3 with `up [0 -0.5 0.8660254]` (30° tilt) → `"trying"`.
- **H1 HUD:**
  - `{:mode :running :flag "pony" :kind :horse :stats {:ms 0.94 :steps 8 :dropped 0} :torso 0.3149 :head {:clearance 0.2437 :tilt 1.2} :effort {:energy 0.4231 :label "holding"}}` →
    `"pony: horse · 0.9 ms/frame · 8 steps · 0 dropped · torso 0.31 m · head 0.24 m 1° · energy 0.42 · holding"`.
  - With no `:head` and `:effort {:energy 0.1 :label "resting"}` →
    `"pony: horse · 0.9 ms/frame · 8 steps · 0 dropped · torso 0.31 m · energy 0.10 · resting"`.
  - Without `:effort`, the existing strings are unchanged.

**Limp**
- **K1 `limp`:** `qpos` is a 9-long `#js` array of zeros except `qpos[7] = 0.1` and
  `qpos[8] = 0.3`.
  - `(cm/limp [0.5 -0.2] qpos [0.1 1])` ≈ `[0.14 -0.2]` (0.1 + 0.1 · 0.4; σ 1 passes the target
    through).
  - `(cm/limp [0 0] qpos [0.1 0.1])` ≈ `[0.09 0.27]`: a soft spring to 0, and the torque is
    rest-kp · (0 − q).
  - With σ `[0 0]` → `[0.1 0.3]` (= q, no torque).
- **K2 `stiffness`:** `tw = {:kind :twitch :n 3 :chans [{:to 0.9} {:to -0.8} {:to 0.5}]}` (other
  channel keys don't matter).
  - `(cm/stiffness tw 0.1 0.8)` = `[1 1 0.1]` (|−0.8| ≥ 0.8 is a kick).
  - `(cm/stiffness {:kind :still :n 3} 0.1 0.8)` = `[0.1 0.1 0.1]`.
  - `(cm/stiffness (cm/reflex-controller {:kind :still :n 3} [{:acts [1]}]) 0.1 0.8)` = `[0.1 1 0.1]`.
  - `(cm/stiffness tw 1 0.8)` = `[1 1 1]` (no `:rest-kp`: today's behaviour).
  - `(cm/stiffness (cm/twitch-init 12 1) 0.1 0.8)`: every entry is 1 or 0.1, and an entry is 1
    exactly when its channel's `|:to| ≥ 0.8`.
- **K3 Partial rest blend:** as T4 but starting from
  `{:energy 0.1 :mode :resting :level 0.5 :tremor (cm/twitch-init 1 8)}`, with `:tremor-deg 0`,
  at dt 0.1.
  - The effort step leaves the energy at 0.125, still below `:high`, so it stays resting, and the
    level goes 0.5 − 0.1/0.5 = 0.3.
  - The reflex target is q = 0.1 (no push: the head is level and high).
  - Index 1 ≈ 0.3 · 0.1 + 0.7 · 0.09 = **0.093**. The blend uses the post-step level, as in
    §5.3 step 5.

### 7.3 Real MuJoCo: `test/pm/creature_mujoco_test.cljs`

**Updated:** **R6** and **R7** run on `(no-effort horse)` and `(no-effort dog)`. With that
change they pass as before. R1–R5 and R8 are unchanged; R3 still holds with the force limits
(0.8389 / 0.4392).

**New.** `P = {:at [4.5 1.79 -8.0] :yaw 0 :drop-m 0.4}`, ground 1.79, `place (assoc P :roll 90)`,
legs **at zero, limp**. The loop per step (15 000 steps = 30 s) follows R6, with three
additions: `obs` gains `:forces (.-actuator_force data)`, the controller is
`(cm/reflex-controller (cm/controller :still 12 1) rx 1)`, and the legs go through `limp`.
`σ = (c/rest-fraction sp)` and `kick = (c/kick-above sp)`. The steps are:
1. `mj_jacBody`
2. `s = (cm/sense …)`, the pre-step state
3. `[ctrl a] (cm/act …)`
4. `tg = (cm/actions->ctrl a ranges)`, with `tg[k] = 0` for every k **not** in `:acts`
5. write `(cm/limp tg (.-qpos data) (cm/stiffness ctrl σ kick))`. With `:still` as the inner
   controller, this gives the legs `q + 0.1·(0 − q)`, and the reflex joints keep their target
   (σ 1).
6. `mj_step`

Record `t = (i+1) · 0.002`, `s`, the mode `(:mode (first (:efforts ctrl)))`,
`|actuator_force[k]|` for k in `:acts`, and the **torso low point**
`(cm/capsule-clearance (.-xpos data) (.-xmat data) 1 (:capsule (first (c/bodies sp))) 1.79)`
after each step (body 1 = the torso).

- **R9 Force limits compiled:** horse `actuator_forcerange[2k], [2k+1]` = −35, 35 and
  `actuator_forcelimited[k]` = 1 for k = 0..3; `actuator_forcelimited[k]` = 0 for k = 4..11. The
  dog's are −9 and 9.
- **R10 Horse, 30 s: tries, rests, tries again.** Episode counter from t ≥ 1.0 s (skipping the
  fall), starting armed:
  - When armed and `clearance ≥ :lift-m` (0.25), count a clear and disarm.
  - When disarmed and `clearance < 0.5 · :lift-m` (0.125), re-arm. That is a rest.

  Assert: **clears ≥ 2** (so there was a rest in between), the mode was `:resting` at some step
  and `:active` again at a later step, the max `|actuator_force|` over the reflex actuators is
  ≤ 35 + 1e-6 and ≥ 34.9 (the cap engages), **the torso low point is ≤ 0.02 at every step with
  t ≥ 3 s** (lying on the ground, no table legs), and every `qpos` is finite. Observed with limp
  legs (seeds 1–3):
  - 4 clears
  - resting from 9.8, 16.6 and 23.4 s, active again at 11.6, 18.4 and 25.2 s (seed 1)
  - the energy at the switches ≈ 0.15 → 0.60
  - peak force 35.00
  - torso low point max 0.000 m
- **R11 Dog, 30 s: strong and steady.** Held time = the sum of 0.002 over steps with
  `clearance ≥ 0.7 · :lift-m` (0.105) **and** `tilt-deg ≤ 20`. Assert **held ≥ 20 s**, the mode is
  never `:resting`, final energy > 0.8, max `|force|` ≤ 9 + 1e-6, **torso low point ≤ 0.02 for
  t ≥ 3 s**, and every value is finite. Observed: held 29.2 s, peak 9.00, clearance about
  0.141, torso low point 0.004 m.
- **R12 The game's own drop lies down (horse and dog).** Place `P` (roll 0, upright, as the
  game drops it), the live controller `(cm/reflex-controller (cm/controller :twitch 12 1) rx 1)`
  (legs twitching, with kicks), and every actuator written as
  `(cm/limp (cm/actions->ctrl a ranges) (.-qpos data) (cm/stiffness ctrl σ kick))`, with no
  zeroing. Run 30 s. Assert the torso low point is ≤ 0.02 at every step with t ≥ 5 s and every
  `qpos` is finite. Observed (seeds 1–5): horse 0.000 m, dog ≤ 0.004 m. Both flop onto their
  side; the horse still clears 4–5 times and the dog holds 28.3–29.6 s.
- **R13 The table it replaces (contrast; the dog only).** Place `P`, no reflex. Every actuator
  targets 0 through `limp` with σ = 1 (full stiffness) for 30 s: the torso low point at 30 s is
  **> 0.3** (observed 0.339: standing on straight legs). The same with σ = 0.1: the torso low
  point at 30 s is **≤ 0.02** (observed 0.000; it sits pitched with its front about 0.22 m up,
  §8). The horse is not used here: with full stiffness it stands for the first seconds
  (0.84 m, as R3) but topples within 30 s, so the result isn't a clean contrast.

Runtime: R10–R12 are 4 × 15 000 steps (plus R13's 2 × 15 000 without `act`). The architect's JS
port ran each in about 1–3 s. If the CLJS loop is slower, keep the steps but run each species
inside one `async` test.

**Count: 34 new numbered cases** (F1–F6 = 6, G1–G2 = 2, E1–E9 = 9, T1–T4 = 4, A1–A3 = 3, L1,
H1, K1–K3 = 3, R9–R13 = 5) **and 5 updated** (C20, C21, C37, R6, R7). T4's expected value is
0.09 (the rest spring).

---

## 8. Done when

- `bash .crew/gate.sh` → `GATE: PASS` (0 warnings, every old and new case).
- A reflex without `:effort` is byte-identical in MJCF and identical in controller and actions
  (F6, A2, R6, R7).
- Live (Hal): with `?pony`, the head comes up slowly and shaking, holds a few seconds, sinks,
  rests and tries again. The HUD reads like `… · head 0.26 m 4° · energy 0.42 · holding`. With
  `?pony=dog`, the head comes up and stays up, steady. Michael's camera is not moved.
- Live (Hal): lying down, the foal and the dog rest on their side, torso and lower-side legs on
  the grass, with no table legs. The kicks are visible as short stiff jerks between floppy
  legs.
- **Honest note (limp):** stiffness alone can't give a sternal lie ("on the chest, legs folded
  under"). Limp but without kicks, an upright body sits with its front propped 0.22–0.34 m by
  the ±45° leg hinges, which can't fold under the chest. Folding needs wider shoulder/hip
  ranges, and belongs with the out-of-scope "rolling onto the chest" round. The upper-side legs
  of a body lying on its side stay level in the air: hinge-only hips have no sideways freedom,
  and body–body collisions are off by design.
- **Honest note:** "the holds get a little longer" emerges only weakly. Less tremor means a little
  less wasted force (in 30 s the first holds are about 5 s each). A clear trend needs minutes and
  is for the age/checkpoint round, which is out of scope here.
