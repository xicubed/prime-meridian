# Spec: characters v0 (Polybot and the Astronaut on the walkers)

Quest: `.crew/quests/characters-v0.md`. Round dir: `crew/runs/characters-v0-20261107-1354/`.

This builds on `motion-bricks-v0` and `avoid-v0`, which are in the working tree, uncommitted: `pm.motion`, `pm.avoid`, `pm.bricks`, 59 + 66 tests.

**What changes.**
- With `?bricks`, each walker that names a `:character` gets a retargeted glTF body through the port's `setCharacter`, instead of `setMannequin`.
- The port's `matchSize` scales the model so its legs match SOMA. Then `:motion :display-scale` scales the whole walker object in both modes, and also the walker's avoidance radius.
- A walker whose file, rig or retarget fails keeps the mannequin, and the HUD names it.
- The rules are pure and tested (`pm.motion`). The interop stays in `pm.bricks`.
- No code names a walker or a character.

## 0. Who touches what

| Who | May edit | Must not touch |
|---|---|---|
| builder (`un-automate-monde`) | `src/pm/motion.cljs`, `src/pm/bricks.cljs`, `src/pm/three.cljs` (add the 2 fns of §4 only), `resources/estate.edn` (exactly §1.1), then `touch src/pm/world.cljs` | `public/**` (vendor, assets, index.html), `src/pm/avoid.cljs`, `src/pm/core.cljs`, every other `src/` file, `resources/content/*.edn`, `docs/GDD.md`, all tests |
| tester (`le-testeur`) | `test/pm/characters_test.cljs` (new, ns `pm.characters-test`) | every `src/`, `resources/`, `public/` file; all existing tests |

**Contract:** `motion_test.cljs` and `avoid_test.cljs` keep passing unchanged. So `m/config`, `m/avoid-config` and `(:label-y m/figure)` = 1.95 stay as they are.

Nobody reads `assets/private/`, `public/kit/` or `public/state/`. There are no npm deps. The build must stay at 0 warnings. Nobody commits.

## 1. Data

### 1.1 `resources/estate.edn`

- `:motion` gains `:display-scale 1.0`, with the comment `; whole walker, both modes, and its avoidance radius (Hal tunes, ~1.4)`.
- The first walker gains `:character {:file "/assets/characters/polybot.glb" :rig :mixamo}`.
- The second walker gains `:character {:file "/assets/characters/astronaut.glb" :rig :mixamo}`.
- Nothing else changes.

### 1.2 Walker state addition

`m/walker` adds `:character` (§2.1), or nil:

```clojure
{:id :un-automate-monde :name "Un Automate" … :radius 0.3
 :character {:file "/assets/characters/polybot.glb" :rig :mixamo}}
```

### 1.3 Status addition (`:mode :bricks` only)

```clojure
{:mode :bricks :backend "webgpu" :walkers 2 :gen-ms 420.4 :detours 0
 :char-errors [{:name "Un Automate" :reason "Failed to fetch"}]}   ; [] when every character loaded
```

## 2. `pm.motion` additions (builder; tester tests them)

```clojure
(def default-rig :mixamo)
(def label-gap 0.35)   ; metres from the top of a measured body to its name label
```

### 2.1 Functions

| Signature | Rules |
|---|---|
| `(rig-key rig)` | Keyword → the port's `CHARACTER_RIGS` key (a string, camelCase). The keyword's `name` is split on `"-"`. The first part is kept as is; each later part gets its first character upper-cased and the rest kept; the parts are joined. A non-keyword → nil. |
| `(character c)` | Returns `{:file f :rig r}` or nil. `c` must be a map. `:file` must be a non-blank string starting with `"/"` or `"https://"`; a relative URL would resolve under `<base href="/kit/">`. `:rig` is `default-rig` when missing (nil), the keyword itself when it's a keyword, and otherwise the result is nil. Other keys are dropped. |
| `(display-scale world)` | `(get-in world [:motion :display-scale])` when it is a positive number, else `1.0`. |
| `(label-height top ds)` | The label's y above the walker's ground point. When `top` is a finite positive number → `(+ (* top ds) label-gap)`. Otherwise → `(* (:label-y figure) ds)`. |
| `(char-errors-text errs)` | nil when `errs` is empty or nil. Otherwise `"mannequin for "`, followed by the entries joined with `", "`, each as `name " (" reason ")"`. |

### 2.2 Changed functions

- **`walker`** adds `:character (character (:character entry))`. Nothing else changes.
- **`walkers`**: after the radius default of avoid-v0, each walker's `:radius` is multiplied by `(display-scale world)`. This happens in `walkers` only; `walker` stays unscaled.
- **`hud-text`**: for `:mode :bricks` only, when `(char-errors-text (:char-errors status))` is non-nil, it is appended as `" · " text` **after** the gen segment and **before** the detours segment. Every other mode ignores `:char-errors`.

## 3. `pm.bricks` changes (builder; interop, no rules)

### 3.1 State

`start!` also stores:
- `:ds (m/display-scale world)`;
- per view, `:label` (the sprite) and `:top nil` (the measured body top, unscaled object units).

### 3.2 Kinematic figure

- In `build-view!`, call `(t/set-scale! figure ds)`.
- The label goes at `[0 (m/label-height nil ds) 0]`. With ds 1.0 that is 1.95, the same as today.
- `fallback!` also puts every label back at that height.

### 3.3 The `?bricks` load, replacing the current `.then (fn [^js mod] …)` body

1. **Characters.** Create one `(C. #js {:rig (name (:rig b)) :model (:model b) :shadows true})` per walker, with `onError` → `fallback!`. **Don't** call `setMannequin` here; the body is set in step 4.
2. **Bodies, in parallel with the model load.** For each walker, a Promise that **never rejects**:
   - no `:character` → resolves `nil`;
   - otherwise it gets the loader (below), then `(.loadAsync loader (:file ch))`, and resolves `#js {:scene (.-scene gltf)}`;
   - any error → resolves `#js {:error <(m/short-reason (or (.-message e) e))>}`.

   **The loader** is one memoized promise per page (`defonce`):
   - `((js/Function. "u" "return import(u)") "three/addons/loaders/GLTFLoader.js")`;
   - then `(new (.-GLTFLoader mod))`;
   - it is created only when some walker has a `:character`.

   If it fails, every character walker gets that error.
3. **Model.** This is today's chain: the first character's `.load(on-progress)`, then `loadFrom` for the rest.
4. **When the model and all the bodies are done:** for each walker `w`, character `c` and body `r`:
   - When `r` has a `scene`:
     - `rig = (aget (.-CHARACTER_RIGS mod) (m/rig-key (:rig (:character w))))`.
     - With no rig → the error `(str "no rig " key)`.
     - Otherwise `(try (.setCharacter c scene rig) (catch e → error))`.
     - The port's defaults give `matchSize` true, shadows from the character's options, and source `"soma"` from `rig: 'soma'`.
   - When `r` has an `error`, or step 4 failed → `(.setMannequin c)`. Then add `{:name (:name w) :reason reason}` to `char-errors`, in walker order.
   - A walker without `:character` → `(.setMannequin c)`, and no error.
   - Then, with `c.object` still at the origin, scale 1 and yaw 0: `top = (t/top-y (.-object c))`.
5. **`swap-in!`** gets `tops` and `char-errors`. Per walker:
   - `(t/set-scale! (.-object c) ds)`, before it is positioned and added;
   - the label goes to `[0 (m/label-height top ds) 0]`, and the view stores `:top top`;
   - the status gains `:char-errors (vec char-errors)`.
   - Everything else is as today: position, yaw, add under the root, hide the figure, the first `steer-command`.

**Why scaling `c.object` is enough.** The controller works in the object's local frame. `moveTo` and `getPosition` convert through `worldToLocal`/`localToWorld`, so world goals are still reached exactly, and the strides scale with the body, so the feet don't slide. `tick-bricks!` sets only `position.y`, so the scale and yaw it got at swap-in are kept.

### 3.4 Avoidance

The walkers' `:radius` already carries the display scale (`m/walkers`), so the planner needs no change. `pcfg :speed` stays `(:speed cfg)`.

### 3.5 Unchanged

`tick!`, `tick-kinematic!`, `tick-bricks!` (apart from §3.3's swap-in), `?paths`, `hud-segment`, and the generation-error `fallback!` (which still drops to kinematic for all).

## 4. `pm.three` additions (builder)

| Signature | Does |
|---|---|
| `(set-scale! obj s)` | `obj.scale.setScalar(s)`; returns `obj`. |
| `(top-y obj)` | `(.updateMatrixWorld obj true)`, then `max.y` of `new T.Box3().setFromObject(obj)`. Returns it when it is finite, else nil. Never throws; an exception → nil. |

## 5. Test cases (`test/pm/characters_test.cljs`)

The test requires `[cljs.test :refer [deftest is]] [pm.motion :as m] [pm.avoid :as a] [pm.world :as w]`.

**Helpers.**
- `W = (w/load-estate w/estate-edn)`.
- `near?`: tolerance 1e-6.
- `sim`: the same as avoid-v0's, iterating `(m/advance ws statics {:speed 1.2 :horizon-s 2} 0.05 acc)` from acc 0. It returns the final walkers, the minimum walker–walker distance, the minimum static gap `|pos − c| − R − radius`, and each walker's picked `:to`s (the `:to` each time `:leg` changes).

### Rig names

1. `(m/rig-key :mixamo)` = `"mixamo"`.
2. `(m/rig-key :mixamo-plus-z)` = `"mixamoPlusZ"`.
3. `(m/rig-key :robot-expressive)` = `"robotExpressive"`; `(m/rig-key :unitree-h1)` = `"unitreeH1"`.
4. `(m/rig-key "mixamo")` is nil; `(m/rig-key nil)` is nil.

### Character entries

5. `(m/character {:file "/assets/characters/polybot.glb" :rig :mixamo})` = `{:file "/assets/characters/polybot.glb" :rig :mixamo}`.
6. `(m/character {:file "/a.glb"})` = `{:file "/a.glb" :rig :mixamo}` (the default rig).
7. `(m/character {:file "https://x.test/a.glb" :rig :mixamo-plus-z})` = `{:file "https://x.test/a.glb" :rig :mixamo-plus-z}`.
8. `(m/character {:file "/a.glb" :rig :mixamo :scale 3})` = `{:file "/a.glb" :rig :mixamo}` (extra keys are dropped).
9. These are all nil: `(m/character {:file "assets/a.glb"})` (relative); `{:file ""}`; `{:file "   "}`; `{:file "http://x.test/a.glb"}`; `{:rig :mixamo}`; `{:file 7}`; `{:file "/a.glb" :rig "mixamo"}`; `"polybot"`; `nil`.
10. `(mapv :character (m/walkers W))` = `[{:file "/assets/characters/polybot.glb" :rig :mixamo} {:file "/assets/characters/astronaut.glb" :rig :mixamo}]`. This covers both walkers.
11. `(:character (m/walker {:id :f :route [[0 0] [1 0]]}))` is nil. `(:character (m/walker {:id :f :route [[0 0]] :character {:file "x"}}))` is nil: an invalid entry keeps the mannequin, and the walker itself is still valid (`:id :f`).

### Display scale and radius

12. `(m/display-scale W)` = 1.0; `(m/display-scale {})` = 1.0; `(m/display-scale {:motion {:display-scale 1.4}})` = 1.4.
13. `(m/display-scale {:motion {:display-scale 0}})` = 1.0; `-1` → 1.0; `"2"` → 1.0; `nil` → 1.0.
14. `(mapv :radius (m/walkers {:motion {:display-scale 2 :radius 0.3} :walkers [{:id :a :route [[0 0]]} {:id :b :route [[0 0]] :radius 0.4}]}))` = `[0.6 0.8]`.
15. With `W14 = (assoc-in W [:motion :display-scale] 1.4)`: `(mapv :radius (m/walkers W14))` ≈ `[0.42 0.42]`. `(mapv :radius (m/walkers W))` = `[0.3 0.3]` (unchanged at 1.0).
16. `(:radius (m/walker {:id :a :route [[0 0]] :radius 0.4}))` = 0.4: `walker` doesn't scale.
17. **Scaled walkers still avoid each other, Juniper and the rocks** (both walkers): `(sim (m/walkers W14) (a/statics W) 2400)` gives:
    - a minimum walker distance ≥ 0.84 (= 2 × 0.42; the model gives 0.988);
    - a minimum static gap ≥ 0 (model 0.152);
    - each walker's first 8 picks = `[1 2 1 0 1 2 1 0]`, and each final `:leg` ≥ 12 (model 18 and 16).

### Labels

18. `(m/label-height 1.6 1.4)` ≈ 2.59 (1.6·1.4 + 0.35).
19. `(m/label-height 1.6 1)` ≈ 1.95; `(m/label-height 1.8 1)` ≈ 2.15.
20. `(m/label-height nil 1)` ≈ 1.95; `(m/label-height nil 1.4)` ≈ 2.73 (the figure's 1.95 × 1.4).
21. `(m/label-height 0 1)` ≈ 1.95; `(m/label-height -1 1)` ≈ 1.95; `(m/label-height ##NaN 1)` ≈ 1.95; `(m/label-height ##Inf 1)` ≈ 1.95.

### HUD

22. `(m/char-errors-text [])` is nil; `(m/char-errors-text nil)` is nil.
23. `(m/char-errors-text [{:name "Un Automate" :reason "Failed to fetch"}])` = `"mannequin for Un Automate (Failed to fetch)"`.
24. `(m/char-errors-text [{:name "A" :reason "x"} {:name "B" :reason "no rig mixamoX"}])` = `"mannequin for A (x), B (no rig mixamoX)"`.
25. `(m/hud-text {:mode :bricks :backend "webgpu" :walkers 2 :gen-ms 420.4 :char-errors [{:name "Un Automate" :reason "Failed to fetch"}]})` = `"motion: bricks webgpu · 2 walkers · gen 420 ms · mannequin for Un Automate (Failed to fetch)"`.
26. `(m/hud-text {:mode :bricks :backend "wasm" :walkers 2 :char-errors [{:name "A" :reason "x"} {:name "B" :reason "y"}] :detours 1})` = `"motion: bricks wasm · 2 walkers · mannequin for A (x), B (y) · 1 detour"`.
27. `(m/hud-text {:mode :bricks :backend "webgpu" :walkers 2 :gen-ms 420.4 :char-errors []})` = `"motion: bricks webgpu · 2 walkers · gen 420 ms"`. This is the live "no character errors" line.
28. `(m/hud-text {:mode :kinematic :char-errors [{:name "A" :reason "x"}]})` = `"motion: kinematic"`. `(m/hud-text {:mode :failed :reason "z" :char-errors [{:name "A" :reason "x"}]})` = `"motion: kinematic (bricks failed: z)"`.

## 6. Live check (Hal, `?bricks`)

- Polybot (Un Automate) and the Astronaut (Le Testeur) walk the bank on generated motion, with their feet on the grass and not sliding (`matchSize`).
- They stand upright and face where they walk; the `mixamo` rig is −Z forward.
- Their name tags float about 0.35 m over their heads.
- They still pass each other on their right.
- 60 fps, and the HUD reads `motion: bricks <backend> · 2 walkers · gen N ms` with no `mannequin for …`.
- **Fallback checks:**
  - Breaking one `:file` path makes that walker a mannequin, and the HUD reads `mannequin for <name> (…)`.
  - Without `?bricks`, both are figures scaled by `:display-scale`.
- **Tuning:** set `:display-scale 1.4` in the estate. The avoidance model at radius 0.42 keeps 0.988 m between walkers and a 0.152 m gap to statics, with the routes unchanged (case 17).
