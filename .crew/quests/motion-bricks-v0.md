# Quest: MotionBricks v0 — two crew walkers on the estate, one model driving both

**Game:** Prime Meridian. **Asked by:** Michael ("one model driving several characters, that
sounds perfect!"). **Led by:** Hal. This is a spike that stays in the game: it answers *does
NVIDIA MotionBricks run on our estate, with the kit's three.js, at a playable frame rate?* and
leaves behind the motion layer W3-0 (the crew on the estate) will use.

## The moment we're building

Two crew members pace the grass on the west bank of the creek, near Juniper, walking a short
route back and forth and pausing at each end, each with its name floating over it.

- **Default load (no flag):** they're simple figures (a capsule body and a head) gliding along
  the route at walking pace, facing where they go. No download.
- **With `?bricks` in the URL:** they're NVIDIA's SOMA human mannequins and their walking is
  *generated* by MotionBricks, one model shared by both (one download, one worker; their
  generations take turns). The motion never loops. If anything fails (no network, worker,
  memory), they fall back to the simple figures and the HUD says why.
- The HUD status line gains a `motion:` segment: `motion: kinematic`,
  `motion: loading model 37%`, `motion: bricks webgpu · 2 walkers · gen 420 ms`, or
  `motion: kinematic (bricks failed: <short reason>)`.

## What exists (read first)

- `public/vendor/three-motionbricks-0.2.0/` (Hal vendored it; read `README.md` there and the
  API in `index.mjs`: `MotionBricksCharacter`, `{rig: 'soma', model: <pack folder>}`, `load(onProgress)`,
  `loadFrom(other)`, `setMannequin()`, `moveTo(Vector3, 'walk')`, `stop()`, `update(dt)`,
  `.object`, `.ready`, `.backend`, `controller.stats.averageInferenceMs`). Don't edit vendored files.
- `public/vendor/three-kit.mjs` + the import map in `public/index.html`: the library's
  `import ... from "three"` resolves to the kit's THREE (`window.hiroshi.THREE`), so it must be
  imported only after the kit has published `window.hiroshi` (see `pm.kit`).
- shadow-cljs `:browser` output can't contain `import()`. Load the module with
  `((js/Function. "u" "return import(u)") url)`, which returns a Promise of the module.
- `pm.three` (all three.js goes through it; it already builds with the kit's THREE),
  `pm.scene` (placed items, labels), `pm.core` (frame loop, HUD), `t/drop-ray` (the `?probe` ray:
  hits as `[y "mesh/material"]`), `resources/estate.edn` (`:placed`, `:mirrors`).
- Gate `.crew/gate.sh`. Don't read `assets/private/`, `public/kit/`, `public/state/`.
  Don't edit `resources/content/*.edn`.

## Data (add to `resources/estate.edn`; `touch src/pm/world.cljs` after editing it)

```clojure
:motion  {:bricks {:lib   "/vendor/three-motionbricks-0.2.0/index.mjs"
                   :rig   :soma
                   :model "https://repalash.com/three-motionbricks/soma-model/"}
          :ground ["SM_Hiroshi_Terrain" "SM_Hiroshi_Gravel"]   ; meshes you can stand on
          :speed  1.2}                                         ; kinematic m/s
:walkers [{:id :un-automate-monde :name "Un Automate"
           :route [[-6.5 -6.5] [-6.0 -10.0] [-5.0 -14.0]] :pause-s 3}
          {:id :le-testeur :name "Le Testeur"
           :route [[-5.0 -14.0] [-6.0 -10.0] [-6.5 -6.5]] :pause-s 5}]
```

Route points are `[x z]`; heights come from the ground (Hal probed: about 2.1, 2.65 and 3.1).
Walkers ping-pong along their route (1→2→3→2→1…) and pause `:pause-s` at each point.

## Scope

1. **Pure `pm.motion`** (tested): walker state from data; next target and pauses (ping-pong);
   a kinematic `step` (move toward the target at `:speed`, face the direction of travel, arrive
   within 0.3 m, then pause); ground height from a list of ray hits (only hits whose mesh name
   starts with one of `:ground`, the highest such hit; nil when none, keep the last height);
   the HUD `motion:` text for each state above; nothing names a walker in code.
2. **`pm.bricks`** (interop, small): `?bricks` check; import the library after the kit is ready;
   one `MotionBricksCharacter` per walker, the first `load`s (progress → HUD), the rest
   `loadFrom` it; `setMannequin`; the character's object added to the kit's scene; each frame
   `update(dt)`, and `moveTo` only when `pm.motion` picks a new target (arrival from the
   character's own root position); y offset of the character's object from the ground height
   under it (smoothed, so it doesn't jitter); stats for the HUD. Any error → fall back to
   kinematic for all walkers, with the reason.
3. **Kinematic figures** through `pm.three`, and name labels like the creatures' signs.
4. Wire into `pm.core`'s frame loop and HUD.

## Out of scope

Real crew status and the red "!" (W3-0), sitting/jumping (NVIDIA's primitive clips are
evaluation-only: don't load them), KayKit or other characters (retargeting), Flora and Juniper,
hosting the model ourselves.

## Done when

The gate passes with `pm.motion` tests (ping-pong order, pauses, kinematic arrival and heading,
ground-hit filtering, HUD text). Live, Hal checks: default load shows two figures pacing the bank
with `motion: kinematic`; `?bricks` downloads once (a reload uses the browser cache), shows two
mannequins walking with generated motion on the grass (not floating, not sunk), and the HUD's
backend and generation time; the frame rate stays playable; the kit's camera and our 💭/🔊 still work.
