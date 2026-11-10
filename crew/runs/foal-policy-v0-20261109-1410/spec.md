# Spec: foal-policy-v0, the learned brain in the game

Quest: `.crew/quests/foal-policy-v0.md`. Architect: un-architecte. Start commit `402a517`. The CPU reference trace landed later in `c5fb04b`.

Every number below was measured in my scratchpad:
- the obs builder and ctrl mapping were run against all 16 samples of `public/creatures/horse/policy_reference.json`;
- onnxruntime-web 1.31.1 was loaded in Node with `require`, with all four ages loaded and two of them run;
- real MuJoCo 4.0.0 WASM rollouts used `creatures/bodies/horse.xml` (identical to `bb creature-mjcf horse`, `:kp 250`) and the east-bank placement.

---

## 0. Summary

- **New namespace `pm.creature-brain`** (`src/pm/creature_brain.cljs`). It holds pure functions only: the brain request from species data and the URL, the `policy.json` checks, the 60-number observation, action → ctrl, the control schedule (a pure state machine), the seeded drop pose, the "up?" test, and the HUD segment.
- **`horse.edn` gets a `:brain` block.** `pm.creature/problems` validates it. `dog.edn` is untouched.
- **`pm.creature-spike` gains a policy path.** It is taken when `brain-request` returns `{:kind :policy}`. Otherwise (no `:brain`, or `?brain=twitch`) today's code runs byte-for-byte as now.
- **The admin panel (ⓘ) gets an age `<select>` and a "drop again" button.** They live in a new `#brain-ui` div next to `#hud` (not inside it: `#hud` is rewritten every 30 frames), and `pm.ui` wires them.
- **Key finding (§8.1): gravity_b in training is one physics substep stale.**
  - Training read the torso orientation that MuJoCo's `xmat`/`xquat` hold right after `mj_step`. That is the pose before the last integration, not `qpos`'s.
  - With that pose, all 16 reference `obs` reproduce to 2.1e-7. From the current `qpos` quaternion they are off by up to 6.6e-3.
  - So the game builds gravity_b from `data.xmat` after the last `mj_step`, never after an extra `mj_forward`.
  - `creatures/sim2sim_cpu.py` uses the current quaternion, which is the one place it differs from training.

---

## 1. Data: the `:brain` block in `resources/creatures/horse.edn`

Add after `:reflexes`. Change nothing else in the file.

```clojure
 ;; The learned brain (foal-gym run 3, pm.creature-brain): an ONNX policy that drives every joint
 ;; at 50 Hz, exactly as in training. Files live under public/ at :dir: :meta (policy.json) and
 ;; policy_<age>.onnx + policy_<age>.onnx.data for each age. ?brain=twitch forces the old
 ;; controller; ?age=<n> picks an age. Each drop puts him at a random pose like training's drops
 ;; (:drop-m = torso height range above the ground, metres). He counts as "up" when the torso is
 ;; above :torso-m and its up axis has world-z above :up-z.
 :brain {:kind :policy
         :dir "/creatures/horse/"
         :meta "policy.json"
         :ages [0 3000 6000 9499]
         :default-age 9499
         :drop-m [0.4 0.8]
         :stand {:torso-m 0.65 :up-z 0.9}}
```

### 1.1 Validation in `pm.creature/problems`

`problems` gains these checks, each run only when `(contains? sp :brain)`. Messages are exact.

| condition | message |
|---|---|
| `:brain` not a map | `":brain must be a map"` (no further brain checks) |
| `(:kind b)` ≠ `:policy` | `":brain :kind must be :policy"` |
| `:dir` not a string that starts and ends with `/` | `":brain :dir must be a string starting and ending with /"` |
| `:meta` not a non-blank string | `":brain :meta must be a non-blank file name"` |
| `:ages` not a non-empty vector of distinct integers ≥ 0 | `":brain :ages must be a non-empty vector of distinct integers >= 0"` |
| `:default-age` not in `:ages` (checked only if `:ages` is ok) | `":brain :default-age must be one of :ages"` |
| `:drop-m` not `[lo hi]` numbers with `0 <= lo <= hi` | `":brain :drop-m must be [lo hi] with 0 <= lo <= hi"` |
| `:stand` not a map with number `:torso-m` > 0 and number `:up-z` in [−1, 1] | `":brain :stand must be {:torso-m > 0 :up-z in [-1, 1]}"` |

Problems are appended after all existing ones, in the table's order. Also add `(defn brain [sp] (:brain sp))` to `pm.creature`: nil when absent.

---

## 2. Facts the index math rests on (verified)

- **qpos:** `qpos[0..2]` is the torso world position and `qpos[3..6]` its quaternion `[w x y z]` (free joint `root`). `qpos[7+k]` is hinge k, for k = 0..17, in `policy.json` `joint_order`. That order is `(c/joints sp)` order and actuator order.
- **qvel:** `qvel[0..2]` is the world-frame linear velocity. `qvel[3..5]` is the torso angular velocity in the torso (body) frame, which is MuJoCo's free-joint convention; `policy_reference.json` `free_joint.qvel_layout` says the same. `qvel[6+k]` is hinge k.
- **Torso:** body id 1, so `xmat[9..17]` is its row-major rotation (body → world).
  - World `−z` in the torso frame is `Rᵀ·(0,0,−1) = −(row 2 of R)` = `[−xmat[15], −xmat[16], −xmat[17]]`.
  - The torso up axis's world z is `xmat[17]`.
- **After `mj_step`:** `xmat`/`xpos` hold the pose from the forward pass at the start of that step, one substep (0.002 s) behind `qpos`. Training's `torso/xquat` is the same stale pose (§8.1).
  - **Rule:** build the observation right after the 10th `mj_step` of a control step, from `data.xmat` as it is, with no `mj_forward` first.
  - **Exception:** after a reset (first drop, drop again) call `mj_forward` once so `xmat` matches the new `qpos`. `qvel` is 0 there, so stale and fresh agree.
- **policy.json:** 18 joints. `control_timestep` 0.02 and `simulation_timestep` 0.002 give `decim` = 10. Input name `"obs"`, shape [1, 60]; output name `"actions"`, shape [1, 18].
- **onnxruntime-web** (`types.d.ts`/`onnxruntime-common`):
  - `InferenceSession.create(uriOrBuffer, {executionProviders, externalData: [{path, data}]})`. `path` must be the file name the .onnx references (`policy_<age>.onnx.data`, confirmed in the model bytes). `data` is a URL string (browser) or bytes (Node).
  - `session.run({obs: new Tensor("float32", Float32Array, [1 60])})` resolves to `{actions: Tensor}`, read with `.data` (a Float32Array of 18).
  - Set `ort.env.wasm.numThreads = 1`. In the browser also set `ort.env.wasm.wasmPaths = "/vendor/onnxruntime/"`; the page's `<base href="/kit/">` makes relative paths unsafe.

---

## 3. Public functions: `pm.creature-brain` (new, `src/pm/creature_brain.cljs`)

Requires `pm.creature` (as `c`) and `pm.creature-motion` (as `cm`, for `rng-seed`/`rng-next` only). Pure: no atoms, no DOM, no three.js, no species names.

**Array arguments.** `qpos`, `qvel`, `xmat`, `xpos`, `jnt-qposadr`, `jnt-dofadr`, `actuator-trnid` are JS array-likes read with `aget`: MuJoCo's typed arrays, or `(clj->js v)` / `(js/Float64Array.from …)` in tests. `last-action`, `action`, ranges and the rest are CLJS vectors.

### 3.1 Request and files

**`(brain-request sp search)`** returns nil, `{:kind :twitch}` or a policy request.
- `(c/brain sp)` nil → nil.
- `search` has `brain=twitch` → `{:kind :twitch}`. Any other `brain` value is ignored.
- Otherwise:
  ```clojure
  {:kind :policy :dir "/creatures/horse/" :meta-url "/creatures/horse/policy.json"
   :ages [0 3000 6000 9499] :age 9499 :drop-m [0.4 0.8] :stand {:torso-m 0.65 :up-z 0.9}}
  ```
  - `:meta-url` is `(str dir meta)`.
  - `:age` is `(js/parseInt (.get params "age") 10)` when that is an integer in `:ages`, else `:default-age`.
- `search` nil or "" means no params. Parsing uses `js/URLSearchParams`, like `cm/spike-request`.

**`(age-files br age)`** returns `{:model (str dir "policy_" age ".onnx") :data (str dir "policy_" age ".onnx.data") :data-path (str "policy_" age ".onnx.data")}`.

### 3.2 policy.json

**`(parse-meta text)`** returns `(js->clj (js/JSON.parse text) :keywordize-keys true)`. Keys stay snake_case keywords: `:joint_order`, `:control_timestep`, `:simulation_timestep`, `:observation {:total_dim :input_name :layout [{:name :size} …]}`, `:action {:output_name :size :scale :offset :ctrlrange_lo :ctrlrange_hi}`.

**`(meta-problems m joint-names)`** returns a vector of strings, [] when ok. `joint-names` is a vector of strings, `(mapv (comp name :name) (c/joints sp))`. Let n = `(count joint-names)`. Checks, in order:
1. `(:joint_order m)` ≠ `joint-names` → `"policy.json: joint_order doesn't match the body's joints"`
2. layout names ≠ `["joint_pos" "joint_vel" "gravity_b" "ang_vel" "last_action"]` → `"policy.json: observation layout must be joint_pos, joint_vel, gravity_b, ang_vel, last_action"`
3. layout sizes ≠ `[n n 3 3 n]`, or `:total_dim` ≠ 3n+6 → `"policy.json: observation sizes must be [n n 3 3 n], total 3n+6"`, with n replaced by its value: for 18, `"... [18 18 3 3 18], total 60"`.
4. `:action :size` ≠ n, or any of `:scale :offset :ctrlrange_lo :ctrlrange_hi` is not n numbers → `"policy.json: action needs n of size, scale, offset, ctrlrange_lo, ctrlrange_hi"`, with n replaced: `"... needs 18 of ..."`.
5. `|simulation_timestep − c/timestep| > 1e-9` → `"policy.json: simulation_timestep must be 0.002"`
6. ratio r = `control_timestep / simulation_timestep`; if `|r − round(r)| > 1e-6` or `round(r) < 1` → `"policy.json: control_timestep must be a whole number of physics steps"`

**`(meta->policy m joint-names)`** throws `(ex-info (str "bad brain: " (first probs)) {:problems probs})` when `meta-problems` isn't empty. Otherwise it returns:
```clojure
{:n 18 :decim 10 :input "obs" :output "actions"
 :scale [...18] :offset [...18] :lo [...18] :hi [...18]}   ; :lo/:hi from ctrlrange_lo/_hi
```

**`(address-problems n jnt-qposadr jnt-dofadr actuator-trnid)`** returns [] when, for every k < n, `actuator-trnid[2k]` = k+1, `jnt-qposadr[k+1]` = 7+k and `jnt-dofadr[k+1]` = 6+k.
- Otherwise it returns one string per bad k: `(str "actuator " k " drives joint " j " (qpos " qa ", dof " da "); expected joint " (inc k) " (qpos " (+ 7 k) ", dof " (+ 6 k) ")")`, where j = trnid[2k], qa = qposadr[j], da = dofadr[j].
- The spike calls it on the compiled model and fails the spike if it isn't empty. This is the guard for the qpos/qvel index math.

### 3.3 Rotation helpers

**`(quat->xmat [w x y z])`** returns the 9-vector row-major rotation (MuJoCo `mju_quat2Mat`):
```
[1-2(y²+z²)  2(xy-wz)    2(xz+wy)
 2(xy+wz)    1-2(x²+z²)  2(yz-wx)
 2(xz-wy)    2(yz+wx)    1-2(x²+y²)]
```

**`(quat-integrate q w dt)`** rotates q by body-frame angular velocity w for dt seconds (MuJoCo `mju_quatIntegrate`).
- Let a = |w|·dt and u = w/|w|. The result is `q ⊗ [cos(a/2), u·sin(a/2)]`, normalized.
- |w| = 0 → q normalized. dt may be negative.
- Tests use it to rebuild the stale pose: `(quat-integrate q w -0.002)`.

**`(gravity-b xmat id)`** returns `[(- xmat[9id+6]) (- xmat[9id+7]) (- xmat[9id+8])]`.

### 3.4 Observation and action

**`(observation n qpos qvel xmat last-action)`** returns a CLJS vector of 3n+6 numbers:

| index | value |
|---|---|
| `k` (0 ≤ k < n) | `qpos[7+k]` |
| `n+k` | `qvel[6+k]` |
| `2n, 2n+1, 2n+2` | `(gravity-b xmat 1)` = `−xmat[15], −xmat[16], −xmat[17]` |
| `2n+3, 2n+4, 2n+5` | `qvel[3], qvel[4], qvel[5]` |
| `2n+6+k` | `(nth last-action k)` |

For n = 18 the gravity indices are 36..38, ang_vel 39..41 and last_action 42..59. The spike sends `(js/Float32Array.from (clj->js obs))` with dims `#js [1 (count obs)]`.

**`(action->ctrl policy action)`** returns, for each k, `(max lo_k (min hi_k (+ (* a_k scale_k) offset_k)))` as an n-vector.

### 3.5 The control schedule

State:
```clojure
{:decim 10 :k 0 :owed 0 :ready nil :waiting? false :gen 0 :last-action [0 … n zeros] :boundaries 0}
```
- `:k` counts substeps since the last control boundary (0 = at a boundary).
- `:owed` is physics steps the clock has granted but not yet run.
- `:ready` is an action that arrived for this boundary.
- `:waiting?` means an inference is in flight.
- `:gen` is bumped by restart.
- `:boundaries` counts actions applied.

**`(schedule-init decim n)`** returns the state above.

**`(add-owed st steps cap)`** returns `[st' dropped]`, with owed' = `min(owed + steps, cap)` and dropped = `max(0, owed + steps − cap)`.

**`(plan st)`** returns `{:st st' :ops [...] :action a-or-nil}`. It loops while owed > 0:
- k > 0 → emit `:step`; k ← (k+1) mod decim; owed −1.
- k = 0 and `:ready` a → emit `:apply :step`; `:action` a; `:last-action` ← a; `:ready` nil; boundaries +1; k ← 1 mod decim; owed −1.
- k = 0 and `:waiting?` → stop.
- k = 0 otherwise → emit `:ask`; `:waiting?` true; stop.

At most one `:apply` per plan. `:ask` is always last. The ops are executed in order by the caller:
- `:apply` writes `(action->ctrl policy action)` to `data.ctrl`.
- `:step` is one `mj_step`.
- `:ask` builds `(observation n qpos qvel xmat (:last-action st'))` and starts the inference.

**`(receive st gen action)`** sets `:ready` action and `:waiting?` false, but only when `gen` = `(:gen st)` and `(:waiting? st)`. Otherwise it returns st unchanged: a late answer for an old drop or age is ignored.

**`(restart st)`** returns `(assoc (schedule-init (:decim st) (count (:last-action st))) :gen (inc (:gen st)))`.

**How physics waits.** The only path to `:step` at k = 0 is through `:apply` of an action computed from the observation taken at that boundary. The game's frame pacing only changes how many steps are owed, never which action a step uses.
- **Frame:** a frame adds its `cm/fixed-steps` count to `:owed` (cap `cm/max-steps` = 25; the excess is counted as dropped) and runs `plan`.
- **Inference callback:** it calls `receive`, then runs `plan` again, which spends the steps that were owed while waiting. So async inference keeps real time instead of halving it.
- **Slow inference:** if inference stays slower than real time, owed hits the cap, steps are dropped, and he moves in slow motion. He never runs on a stale action.
- **Verified:** obs sequences were bit-identical for frame patterns `[25]`, `[8 9 7 25 1 0 3]` and `[1]` (test R9).

### 3.6 Drops, "up", HUD

**`(drop-seed seed try)`** returns `(+ (* 1000 seed) try)`.
- `seed` is the request's `:seed` (default 1; `?seed=` works).
- `try` is 0 for the first drop and +1 per "drop again".

**`(drop-pose seed ranges [lo-m hi-m] [gx gy gz])`** returns a qpos vector of 7 + n, training's drop distribution (`task.py reset_foal`) made deterministic.
- **Draws:** x = `(cm/rng-seed seed)`, then draws u1, u2, … by repeated `(cm/rng-next x)` (each returns `[x' u]`), with no warm-up discards. Uses u1..u(4+n).
- **Height:** h = lo + u1·(hi − lo).
- **Orientation:** with a = u2, b = u3, c = u4 (Shoemake, `task.py`'s formula and order), the quaternion is `[√a·cos 2πc, √(1−a)·sin 2πb, √(1−a)·cos 2πb, √a·sin 2πc]`.
- **Joints:** joint k = lo_k + u(5+k)·(hi_k − lo_k), with `ranges` = `(c/ctrl-ranges sp)` in radians.
- **Result:** `[gx gy (+ gz h) qw qx qy qz j0 … j(n-1)]`. qvel is all 0 (the caller resets).

**`(up? stand ground-z xpos xmat)`** returns `(and (> (- xpos[5] ground-z) (:torso-m stand)) (> xmat[17] (:up-z stand)))`.

**`(hud-segment b)`** returns:

| b | text |
|---|---|
| nil | `""` |
| `{:kind :twitch}` | `" · brain twitch"` |
| `{:kind :policy :age 3000 :loading? true}` | `" · brain 3000 loading"` |
| `{:kind :policy :age 9499 :up? true}` | `" · brain 9499 · up"` |
| `{:kind :policy :age 0 :up? false}` | `" · brain 0 · down"` |

`pm.creature-motion/hud-text` is unchanged. The spike composes the HUD (§4).

---

## 4. The spike (`src/pm/creature_spike.cljs`), policy path

`start!` computes `br (cb/brain-request sp (.. js/window -location -search))` after `c/parse`.
- **No `:brain`, or `{:kind :twitch}`:** the existing body runs unchanged. In the twitch case, `:status` additionally carries `:brain {:kind :twitch}`.
- **`{:kind :policy}`:**
  1. **Load.** `Promise.all` of the MuJoCo loader (as now), `(js/fetch (:meta-url br))` → `.text` → `cb/parse-meta`, and the ORT module (`load-ort`, a new optional 4th arg of `start!`, defaulting to the browser `import("onnxruntime-web")`).
     - Then set `numThreads` 1 and `wasmPaths`, and call `(cb/meta->policy m joint-names)`.
     - Create the session for `(:age br)` with `(cb/age-files br age)`: `create(model, #js{:executionProviders #js["wasm"] :externalData #js[#js{:path data-path :data data}]})`.
     - Status stays `:loading` (HUD `pony: horse loading`) until all of this resolves. Any failure → `fail!` (HUD `pony: horse failed (...)`).
  2. **Model.** Build it as now (same `c/mjcf sp req`). Check `cb/address-problems` (non-empty → `fail!` with the first problem).
     - Write `(cb/drop-pose (cb/drop-seed seed 0) ranges (:drop-m br) (:ground sw))` into `qpos`. Then `mj_forward`, then place the groups (as now).
     - State gains `:br br :policy pol :session s :sched (cb/schedule-init (:decim pol) (:n pol)) :try 0`. The reflex/twitch controller is not created.
  3. **`tick!` (policy).**
     - `{:keys [steps dropped acc]} (cm/fixed-steps acc dt)`, then `[sched d2] (cb/add-owed sched steps cm/max-steps)`, then `(run-brain!)`.
     - Then poses from `xpos`/`xquat` as now. Status:
       ```clojure
       {:mode :running … :torso torso
        :brain {:kind :policy :age age :up? (cb/up? (:stand br) ground-z xpos xmat)}}
       ```
       `:head`/`:effort` are absent. stats dropped = `dropped + d2`.
  4. **`run-brain!`** (private).
     - `(cb/plan sched)` → store `:sched`; execute ops (§3.5).
     - On `:ask`, capture `gen`. Then `(.run session (js-obj (:input pol) tensor))` → `.then`:
       - `(swap! !state update :sched cb/receive gen (vec (.-data (aget out (:output pol)))))`, then `(run-brain!)`.
       - Skip both if status isn't `:running`. `.catch` → `fail!`.
  5. **`drop-again!`** (public, no args).
     - `try` +1, `mj_resetData`, write the drop pose with `(cb/drop-seed seed try)`, `mj_forward`.
     - `:sched` ← `(cb/restart sched)`.
  6. **`set-age!`** (public, `[age]`).
     - Status `:brain` gets `:loading? true` (physics keeps running on the old age meanwhile).
     - Create the new session. On success, release the old one (`.release`), store the new one with `:age`, then `drop-again!`.
     - A failed load keeps the old age and logs a warning; it doesn't fail the spike.
  7. **`hud-segment`.**
     ```clojure
     (let [s (:status @!state)
           t (cm/hud-text s)]
       (if (and t (= :running (:mode s)))
         (str t (cb/hud-segment (:brain s)))
         t))
     ```
     Example: `pony: horse · 16.7 ms/frame · 25 steps · 0 dropped · torso 0.70 m · brain 9499 · up`.
  8. **`fail!`** also releases the session (`(.release s)` in a try).
  9. **Admin UI.** Once running with a policy, call `(ui/show-brain-ui! ages age set-age! drop-again!)`.

**Admin UI (`pm.ui`, `public/index.html`).**
- In `index.html`, inside `<details id="admin">` right after `<div id="hud">…</div>`, add:
  ```html
  <div id="brain-ui" hidden><label>age <select id="brain-age"></select></label> <button id="brain-drop">drop again</button></div>
  ```
  Plus minimal CSS (same font/colours as `#hud`).
- `pm.ui/show-brain-ui! [ages age on-age on-drop]`:
  - fills `#brain-age` with one `<option value="<age>">` per age (text = the age), selects `age`, and un-hides `#brain-ui`;
  - sets `.onchange` to call `on-age` with `(js/parseInt value 10)` and `#brain-drop`'s `.onclick` to call `on-drop`. Use properties, not `addEventListener`, so calling it again doesn't double-wire.

---

## 5. Who touches what

| member | files | not |
|---|---|---|
| builder (un-automate-monde) | `resources/creatures/horse.edn` (the `:brain` block only); `src/pm/creature.cljc` (`brain` + validation in `problems`); **new** `src/pm/creature_brain.cljs`; `src/pm/creature_spike.cljs` (policy path, `drop-again!`, `set-age!`, `hud-segment`); `src/pm/ui.cljs` (`show-brain-ui!`); `public/index.html` (`#brain-ui` + CSS) | `dog.edn`, `creatures/bodies/*.xml`, `public/creatures/horse/*`, `creatures/**/*.py`, `creature_motion.cljs`, tests |
| tester (le-testeur) | **new** `test/pm/creature_brain_test.cljs` (pure: B1–B15); **new** `test/pm/creature_brain_mujoco_test.cljs` (real MuJoCo + ORT: R1–R11); `test/pm/creature_test.cljs` (V1–V3 added; nothing existing edited) | source files |

**Node loading in R tests:**
- MuJoCo exactly as `creature_mujoco_test.cljs` does (`js/require` of `node_modules/@mujoco/mujoco/mujoco.js`, `.default()`).
- ORT via `(js/require (str (.cwd js/process) "/node_modules/onnxruntime-web"))` (Node resolves `dist/ort.node.min.js`), then `(set! (.. ort -env -wasm -numThreads) 1)`.
- Model and data files are bytes from `(.readFileSync (js/require "fs") path)`, passed as `#js{:externalData #js[#js{:path "policy_9499.onnx.data" :data bytes}]}`.
- Reference: `public/creatures/horse/policy_reference.json` via `fs` + `JSON.parse`.
- Model XML for R tests: `(c/mjcf sp c/origin)`, which is byte-equal to `creatures/bodies/horse.xml`: ground at z 0, matching the reference's initial qpos.
- Each R test runs one episode with `await`-style chaining. Every episode in R5–R10 does `n` control steps of: obs → `run` → `action->ctrl` → 10 × `mj_step`.
- Unless a test says otherwise, R tests drive the loop directly (no `plan`); R9 drives it through `plan`.

---

## 6. Test cases

Tolerance `≈ε` means |a − b| ≤ ε elementwise. Expected values are worked out by hand where marked (H), otherwise measured in my scratchpad run (M).

### Pure (`creature_brain_test.cljs`)

**B1 brain-request (horse).** sp = parsed `horse.edn` with the block of §1. **(H)**
- `search "?pony"` → `{:kind :policy :dir "/creatures/horse/" :meta-url "/creatures/horse/policy.json" :ages [0 3000 6000 9499] :age 9499 :drop-m [0.4 0.8] :stand {:torso-m 0.65 :up-z 0.9}}`
- `"?pony&age=3000"` → same with `:age 3000`.
- `"?pony&age=123"` → `:age 9499`; `"?pony&age=abc"` → `:age 9499`; `"?pony&age=0"` → `:age 0`.
- `"?pony&brain=twitch"` → `{:kind :twitch}`; `"?pony&brain=other"` → `:kind :policy`.
- `nil` → `:age 9499`.

**B2 brain-request (the second mirror, dog).** Parsed `dog.edn`: `"?pony=dog"`, `"?pony=dog&brain=twitch"` and `"?pony=dog&age=9499"` all → `nil` (no brain, no twitch flag). **(H)**

**B3 age-files.** `(age-files {:dir "/creatures/horse/"} 3000)` → `{:model "/creatures/horse/policy_3000.onnx" :data "/creatures/horse/policy_3000.onnx.data" :data-path "policy_3000.onnx.data"}`. With `{:dir "/x/"}` and age 0 → `{:model "/x/policy_0.onnx" :data "/x/policy_0.onnx.data" :data-path "policy_0.onnx.data"}`. **(H)**

**B4 meta ok.** m = `parse-meta` of the real `public/creatures/horse/policy.json` text (read with `fs`); names = `(mapv (comp name :name) (c/joints horse))`. **(H, from the file)**
- `(meta-problems m names)` → `[]`.
- `(meta->policy m names)` gives:
  - `:n 18 :decim 10 :input "obs" :output "actions"`
  - `(nth (:scale p) 1)` ≈1e-9 0.741765022277832; `(nth (:offset p) 6)` ≈1e-9 0.785398006439209
  - `(nth (:lo p) 14)` ≈1e-9 −1.7453290224075317; `(nth (:hi p) 5)` ≈1e-9 1.3089970350265503

**B5 meta problems.** From the same m. **(H)**
- **(a)** `(assoc m :joint_order (vec (reverse names)))` → `["policy.json: joint_order doesn't match the body's joints"]`
- **(b)** `(assoc-in m [:observation :total_dim] 61)` → `["policy.json: observation sizes must be [18 18 3 3 18], total 60"]`
- **(c)** `(assoc-in m [:observation :layout 2 :name] "gravity")` → `["policy.json: observation layout must be joint_pos, joint_vel, gravity_b, ang_vel, last_action"]`
- **(d)** `(update-in m [:action :scale] pop)` → `["policy.json: action needs 18 of size, scale, offset, ctrlrange_lo, ctrlrange_hi"]`
- **(e)** `(assoc m :simulation_timestep 0.001)` → `["policy.json: simulation_timestep must be 0.002"]`
- **(f)** `(assoc m :control_timestep 0.021)` → `["policy.json: control_timestep must be a whole number of physics steps"]`
- **(g)** `(meta->policy (assoc m :simulation_timestep 0.001) names)` throws; `ex-message` = `"bad brain: policy.json: simulation_timestep must be 0.002"`
- **(h)** with the dog's joint names (also 18, same names as horse) → `[]`. The check is by names, not species.

**B6 address-problems.** **(H)**
- `n` 2, qposadr `#js[0 7 8]`, dofadr `#js[0 6 7]`, trnid `#js[1 0 2 0]` → `[]`.
- Trnid `#js[2 0 1 0]` → `["actuator 0 drives joint 2 (qpos 8, dof 7); expected joint 1 (qpos 7, dof 6)" "actuator 1 drives joint 1 (qpos 7, dof 6); expected joint 2 (qpos 8, dof 7)"]`.

**B7 quat->xmat, gravity-b.** **(H)**
- `(quat->xmat [1 0 0 0])` → `[1 0 0 0 1 0 0 0 1]`.
- `(quat->xmat [0.70710678 0.70710678 0 0])` ≈1e-6 `[1 0 0 0 0 -1 0 1 0]` (90° about x).
- `(gravity-b (clj->js (into [9 9 9 9 9 9 9 9 9] [1 0 0 0 0 -1 0 1 0])) 1)` ≈1e-12 `[0 -1 0]` (his y-axis points up, so down is −y).
- `(gravity-b (clj->js [1 0 0 0 1 0 0 0 1]) 0)` → `[0 0 -1]` (`-0` compares equal).

**B8 quat-integrate.** **(H)**
- `(quat-integrate [1 0 0 0] [0 0 Math.PI] 0.5)` ≈1e-9 `[0.7071067812 0 0 0.7071067812]`.
- `(quat-integrate [0.7071067812 0 0 0.7071067812] [0 0 Math.PI] -0.5)` ≈1e-9 `[1 0 0 0]`.
- `(quat-integrate [2 0 0 0] [0 0 0] 0.1)` → `[1 0 0 0]` (normalised, zero ω).

**B9 observation, hand case (n = 2).** **(H)**
- qpos `#js[0 0 0.5 1 0 0 0 0.1 0.2]`, qvel `#js[0 0 0 0.3 0.4 0.5 1.1 1.2]`.
- xmat `(clj->js (into [1 0 0 0 1 0 0 0 1] [1 0 0 0 0 -1 0 1 0]))`, last-action `[0.7 0.8]`.
- Result ≈1e-12 `[0.1 0.2 1.1 1.2 0 -1 0 0.3 0.4 0.5 0.7 0.8]`, count 12 = 3·2+6.

**B10 observation parity, all 16 reference samples** (ages 0 and 9499 × drop/chest/standing). **(M)**
- For each sample s, let q = `qpos[3..6]` and w = `qvel[3..5]`. Then `xmat = (clj->js (into (vec (repeat 9 0)) (quat->xmat (quat-integrate q w -0.002))))`.
- `(observation 18 (clj->js (:qpos s)) (clj->js (:qvel s)) xmat (:last_action s))` ≈1e-5 `(:obs s)`. Measured worst 2.1e-7.
- **Control (documents §8.1):** the same with `(quat->xmat q)` (no back-step) is **not** within 1e-5 for sample `9499` / `drop` / step 10. Measured 6.6e-3 at index 36..38.

**B11 action->ctrl.** p = B4's policy. **(H)**
- `(action->ctrl p (vec (repeat 18 0)))` = offsets: index 1 ≈1e-9 0.04363301396369934, 5 ≈ 0.13089948892593384, 6 ≈ 0.785398006439209, 14 ≈ −0.785398006439209, 0 = 0.
- Action with a_1 = 1, others 0 → index 1 ≈1e-7 0.785398 (= hi, 0.741765 + 0.043633).
- a_5 = −1 → index 5 ≈1e-7 −1.047198 (= lo).
- All +100 → `(:hi p)` exactly; all −100 → `(:lo p)` exactly.
- **Parity (M):** for all 16 samples, `(action->ctrl p (:onnx_action s))` ≈1e-6 `(:ctrl s)`. Measured worst 3.3e-8.

**B12 schedule, synchronous answers.** **(H)** Start with `st0 (schedule-init 10 2)`.
1. `[s1 d] (add-owed st0 25 25)` → d 0. `(plan s1)` → ops `[:ask]`, action nil, st `:owed 25 :waiting? true :k 0`.
2. `(plan <that st>)` → ops `[]`, st unchanged.
3. `receive` gen 0 `[0.5 -0.5]`, then plan → ops `[:apply` + 10 × `:step` + `:ask]` (12 ops), action `[0.5 -0.5]`, st `:owed 15 :k 0 :boundaries 1 :waiting? true :last-action [0.5 -0.5]`.
4. `receive` 0 `[1 1]`, plan → same 12-op shape, `:owed 5 :boundaries 2`.
5. `receive` 0 `[2 2]`, plan → `[:apply :step :step :step :step :step]`, st `:owed 0 :k 5 :waiting? false :ready nil :boundaries 3 :last-action [2 2]`.
6. `add-owed` 8 cap 25, plan → `[:step :step :step :step :step :ask]`, st `:owed 3 :k 0 :waiting? true`.
7. `(add-owed (assoc st0 :owed 20) 10 25)` → `[st' 5]` with `:owed 25`.

**B13 schedule edge cases.** **(H)**
- **decim 3:** `(schedule-init 3 1)`, owed 7 → `[:ask]`; receive 0 `[9]` → plan `[:apply :step :step :step :ask]`, `:owed 4 :k 0`.
- **decim 1:** `(schedule-init 1 1)`, owed 3 → `[:ask]`; receive → `[:apply :step :ask]`, `:owed 2 :k 0 :waiting? true`.
- **receive guards:** `(receive st 1 [5 5])` on a gen-0 waiting st → st unchanged. `receive` on a non-waiting st → unchanged.
- **restart:** `(restart <B12 step-5 st>)` → `{:decim 10 :k 0 :owed 0 :ready nil :waiting? false :gen 1 :last-action [0 0] :boundaries 0}`. Then `(receive <that> 0 [1 1])` (an old answer) is ignored.

**B14 drop-seed, drop-pose.** **(H for the two-joint case; M (6 dp) for 18)**
- `(drop-seed 1 0)` → 1000, `(drop-seed 1 1)` → 1001, `(drop-seed 3 2)` → 3002.
- **Two-joint case:** `(drop-pose 7 [[-1 1] [0 2]] [0.5 0.5] [1 2 3])` ≈1e-6 `[1 2 3.5 -0.11118 0.270672 0.935112 -0.199853 -0.475724 1.503347]`.
  - Hand check: rng-seed 7 → 8; first draw x = 134456, u1 = 6.26e-5; h = 0.5 since lo = hi; z = 3 + 0.5.
- **Horse, seed 1001:** `(drop-pose 1001 (c/ctrl-ranges horse) [0.4 0.8] [0 0 0])` ≈1e-6 `[0 0 0.403137 -0.815781 0.298318 0.331877 -0.367921 0.813115 -0.109015 -0.756694 -0.678192 0.135262 0.277648 0.342067 -0.039091 0.430789 1.056588 0.059828 0.181231 -0.007258 0.634142 -1.394989 0.41532 -0.290985 -1.464534]`.
  - Hand check: x0 = 1002, x1 = 16807·1002 = 16840614, u1 = 0.007842022, h = 0.4 + 0.4·u1 = 0.403137. The quaternion norm is 1 to 1e-12.
- **Dog** (the second mirror; same joint ranges as the horse in degrees): `(drop-pose 1001 (c/ctrl-ranges dog) [0.2 0.4] [0 0 0])` → index 2 ≈1e-6 0.201568 (= 0.2 + 0.2·u1); indices 3..24 equal the horse's.
- **East bank, seed 1000:** `(drop-pose 1000 (c/ctrl-ranges horse) [0.4 0.8] [4.5 8 1.79])` → first 7 ≈1e-6 `[4.5 8 2.193134 0.634347 0.440934 -0.369122 0.516652]`.

**B15 up?, hud-segment.** stand `{:torso-m 0.65 :up-z 0.9}`. **(H)**
- xpos `#js[0 0 0 0 0 2.5]`, xmat `#js[0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0.95]`, ground 1.79 → true (0.71 > 0.65, 0.95 > 0.9).
- Same with xpos z 2.4 → false (0.61). Same with xmat[17] 0.85 → false.
- `hud-segment`: the five rows of the §3.6 table exactly.

### Species validation (`creature_test.cljs`, added)

**V1.** The real `horse.edn` (with `:brain`): `(c/problems sp)` → `[]`; `(c/brain sp)` = the §1 map. `dog.edn`: `(c/brain sp)` → nil, problems `[]`. **(H)**

**V2.** Horse sp with each mutation gives exactly one problem, the §1.1 message. **(H)**
- `(assoc sp :brain 1)` → `":brain must be a map"`
- `(assoc-in sp [:brain :kind] :onnx)` → `":brain :kind must be :policy"`
- `(assoc-in sp [:brain :dir] "creatures/horse")` → `":brain :dir must be a string starting and ending with /"`
- `(assoc-in sp [:brain :meta] " ")` → `":brain :meta must be a non-blank file name"`
- `(assoc-in sp [:brain :ages] [0 0])` → `":brain :ages must be a non-empty vector of distinct integers >= 0"`
- `(assoc-in sp [:brain :default-age] 5)` → `":brain :default-age must be one of :ages"`
- `(assoc-in sp [:brain :drop-m] [0.8 0.4])` → `":brain :drop-m must be [lo hi] with 0 <= lo <= hi"`
- `(assoc-in sp [:brain :stand :up-z] 2)` → `":brain :stand must be {:torso-m > 0 :up-z in [-1, 1]}"`

**V3.** `(c/mjcf horse c/origin)` (with `:brain`) is still byte-equal to `creatures/bodies/horse.xml`: the brain block doesn't touch the body. **(M: equal today)**

### Real MuJoCo + onnxruntime-web (`creature_brain_mujoco_test.cljs`)

**R1 ages load.** All four ages create a session from bytes plus externalData. Each has `inputNames` = `["obs"]` and `outputNames` = `["actions"]`. A run on 60 zeros gives `dims` `[1 18]`. **(M)**

**R2 ONNX parity, age 0.** For the 8 age-0 samples, `run` on `Float32Array` of `(:obs s)` → `actions.data` ≈1e-4 `(:onnx_action s)`. Measured worst 5.2e-8. **(M)**

**R3 ONNX parity, age 9499.** Same for the 8 age-9499 samples. Measured worst 7.6e-6; magnitudes up to ±47, so 1e-4 is absolute. **(M)**

**R4 addresses.** On the compiled horse model, `(address-problems 18 m.jnt_qposadr m.jnt_dofadr m.actuator_trnid)` → `[]`. `m.nq` 25, `m.nv` 24, `m.nu` 18. **(M)**

**R5 game rollout vs the CPU trace** (the game's obs, §3.4). **(M)**
- Load `fixed_chest_rollout_3s_cpu`, age 9499, model `(c/mjcf horse c/origin)`.
- Set qpos ← `initial_qpos`, qvel ← `initial_qvel` (zeros), then `mj_forward`. last-action = 18 zeros.
- For i = 0..149: record `h_i = qpos[2]` and `up_i = 1 − 2(qx² + qy²)` from `qpos[3..6]` (the reference's own definitions). Then obs `(observation 18 qpos qvel xmat la)` → action → ctrl → 10 steps.
- **Assert:** for i = 0..49 (the first 1 s), `|h_i − trace[i].height| ≤ 0.01` m. Measured worst 0.0048 at i = 16.
- **Reference points** (game / CPU trace):

  | i | h game | h CPU | up game | up CPU |
  |---|---|---|---|---|
  | 0 | 0.38245 | 0.38245 | 0.99994 | 0.99994 |
  | 10 | 0.75248 | 0.75142 | 0.93420 | 0.93563 |
  | 20 | 1.01282 | 1.01101 | 0.91369 | 0.90945 |
  | 49 | 0.71807 | 0.72223 | 0.98582 | 0.98802 |
  | 149 | 0.71951 | 0.69628 | 0.98517 | 0.97032 |

- No assertion past 1 s: the run is chaotic; the worst gap over 3 s is 10 cm at i = 84.

**R6 engine identity (diagnostic: Node WASM = Python CPU MuJoCo).** **(M)**
- As R5, but the obs uses `xmat` = `(clj->js (into (vec (repeat 9 0)) (quat->xmat qpos[3..6])))`. This is sim2sim_cpu.py's formula, the current quaternion.
- **Assert:** for i = 0..49, `|h_i − trace[i].height| ≤ 0.001` m. Measured worst 0.00011.
- This pins the schedule and physics to `creatures/sim2sim_cpu.py` exactly. The only game-vs-CPU difference is §8.1.

**R7 ends standing, vs the Warp trace.** **(M)**
- Same episode as R5 (game obs).
- **Assert:** for every i in 100..149, `h_i > 0.65` and `up_i > 0.9`. Game min 0.7006 / 0.9537; Warp trace min 0.685 / 0.959.
- **Assert:** at i = 149, `h > 0.65` and `up > 0.9`. Game 0.7195 / 0.9852; Warp 0.7058 / 0.9691.
- After the 150th control step: game 0.7214 / 0.9863.

**R8 age 0 doesn't stand.** **(M)**
- R5's episode with age 0.
- **Assert:** no i in 0..149 has `h_i > 0.65 and up_i > 0.9`.
- **Assert:** final `h < 0.4` and `up < 0.5`. Measured: final h 0.1500, up 0.0074 (lying on his side); max h 0.727 at landing, never upright.

**R9 schedule invariance in real MuJoCo.** **(M)**
- R5's start, age 9499, driven through `schedule-init`/`add-owed`/`plan`/`receive`. Each `:ask` is answered by awaiting `run` and then re-planning, exactly as the spike's callback does.
- Frame patterns of `steps` per frame: `[25]`, `[8 9 7 25 1 0 3]` (cycled) and `[1]`. Cap 25. Each run continues until 150 asks.
- **Assert:** the 150 recorded observation vectors are identical (`=`) across the three patterns, and dropped is 0 in all three.
- **Assert:** the `[25]` run's 150 obs equal R5's 150 obs (the plain loop).

**R10 random drops, both ends of training.** **(M)**
- Model `(c/mjcf horse {:at [4.5 1.79 -8.0] :yaw 0 :drop-m 0.4})` (the east bank: ground z 1.79, at mj x 4.5, y 8). Each episode: `mj_resetData`, qpos ← `(drop-pose (drop-seed 1 t) ranges [0.4 0.8] [4.5 8 1.79])`, `mj_forward`, then 300 control steps (6 s) with the game obs.
- Standing at the end means `up?` with stand `{:torso-m 0.65 :up-z 0.9}` and ground 1.79.
- **Assert:** age 9499, t = 1..10: all 10 standing.
  - Measured end h/up: 0.70/0.98, 0.71/0.98, 0.70/0.98, 0.70/0.98, 0.68/0.98, 0.70/0.98, 0.70/0.98, 0.70/0.98, 0.70/0.98, 0.70/0.98.
  - t = 11..20 also all stand (20/20).
- **Assert:** age 0, t = 1..3: none standing. Measured all 0.15/≤0.01 (0/20 over t = 1..20).

**R11 the species without a brain is untouched.** `dog.edn` parses, and `(brain-request dog "?pony=dog")` → nil. All existing tests pass unchanged; the gate's count rises only by the new tests. **(H)**

**Count:** B1–B15 (15) + V1–V3 (3) + R1–R11 (11) = **29 new test cases**. No existing test is edited.

---

## 7. Live check for Hal (not automated)

- **`?pony`:** HUD `… · brain 9499 · down` while he lands at a random pose, then `· up` within a few seconds.
- **ⓘ panel:** the age select shows 0/3000/6000/9499.
  - Picking 0 → HUD `brain 0 loading`, then a fresh drop where he flops (`down`).
  - "drop again" drops at a new pose (seed 1001, 1002, …).
- **`?pony&age=3000`** starts at 3000. **`?pony&brain=twitch`** → the old twitch + head reflex, HUD `… · brain twitch`.
- **`?pony=dog`** is exactly as before.
- **`dropped`** in the HUD stays 0 at 60 fps.

---

## 8. Findings

1. **gravity_b is one substep stale in training, and in the game it's MuJoCo's own `xmat` after `mj_step`.**
   - Rebuilding each reference sample's torso orientation 0.002 s back (`quat-integrate q w −0.002`) reproduces all 16 `obs` gravity triples to 2.1e-7. The current `qpos` quaternion misses by up to 6.6e-3.
   - `creatures/sim2sim_cpu.py` builds gravity_b from the current quaternion, so its CPU trace is very slightly not the training observation. That is why R5 (game obs) uses a 1 cm tolerance (measured 4.8 mm) while R6 (sim2sim's formula) matches to 0.11 mm.
   - Suggest Le Coureur switch sim2sim to `data.xquat` without an extra `mj_forward` in a later round. No action needed here.
2. **He settles at 0.700 m, so "torso > 0.7" is a coin flip.**
   - Run 15 s from the chest start: 0.711 at 4 s, then 0.700 from 8 s on.
   - The CPU reference itself ends at 0.696, so the spec's "ends standing" and the HUD's "up" use 0.65 m and up-z 0.9.
   - The Warp reference passes both (min 0.685 / 0.959 over the last second).
3. **Today's spawn would hide the moment.**
   - The game's drop is upright with legs straight, from 0.4 m. Age 9499 lands on his feet and is "up" from 0.05 s; age 3000 is up by 5.05 s; age 6000 is not up at 6 s (0.789 m, up-z 0.894).
   - So with a brain, every drop (first and again) uses training's random drop, seeded and reproducible: he flails, rolls and gets up.
   - Measured on the east bank: age 9499 stood 20 of 20 (seeds 1001–1020); age 0 stood 0 of 20. Without a brain the spawn is unchanged.
4. **Async inference keeps real time.** Steps owed while waiting run in the inference callback. Obs sequences are identical under any frame pacing (R9), and nothing is dropped unless inference itself falls behind (then slow motion, never a stale action).
5. **Out of scope, noted.** He has no fatigue with this brain: the effort caps are only MJCF forceranges (±35 N·m on the neck) and still apply. "Drop again" doesn't exist for the twitch path.
