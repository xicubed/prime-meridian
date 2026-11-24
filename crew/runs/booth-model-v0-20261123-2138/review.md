# Review — booth-model-v0 (TASKS T-18)

Scope: `git diff` for the 4 modified files (`resources/estate.edn`, `src/pm/plan.cljs`,
`src/pm/scene.cljs`, `src/pm/three.cljs`) plus a full read of the two new files
(`src/pm/models.cljs`, `test/pm/booth_model_test.cljs`), against `spec.md` and the quest
(amended twice: no glass kit override, and the lamp/sign glow split). `87b9dcd` (the quest's
own start commit, before the two in-tree amendment commits `24e06f1`/`a7c27ff` and this round's
own spec commit) is the baseline; nothing else of this round's own work is stacked on it.

Scope note: `git status` also shows an untracked top-level `assets/` (it holds `assets/private/`,
gitignored, plus `assets/blender/phone-booth.blend(1)`, which is **not** covered by `.gitignore`'s
`assets/private/` rule and so shows as untracked clutter). Both `assets/blender/phone-booth.blend`
and `.blend1` have mtimes of 21:14–21:15, a full 23 minutes before this round's own `spec` step
started (21:38:51 per `log.md`) — they predate the round and are Hal's own raw Blender source
files from building the asset, not something this round's builder touched (the boundary table
forbids `tools/blender/` and `public/assets/props/` anyway, and `public/assets/props/phone-booth.glb`
is already committed with an mtime of 21:15, also pre-round). Excluded from this review's findings.

## Gate — verified independently

Re-ran `bash .crew/gate.sh`: `[:app]`/`[:test]` 0 warnings, `Ran 1397 tests containing 7804
assertions, 0 failures, 0 errors`, `bb content-test` 5/42 — matches `gate-1.txt` exactly.
Structural cross-check: committed `deftest` count at `HEAD` is 1351, working tree is 1397, a
delta of exactly 46 — `booth_model_test.cljs`'s own case-1..case-46, matching the spec's "46
numbered cases" (it alone also covers `pm.models`; no separate `models_test.cljs` was wanted).

## Point-by-point

- **npm `GLTFLoader` only parses; every scene-bound object is rebuilt with the active THREE**:
  `three.cljs`'s new `:require` adds `"three/examples/jsm/loaders/GLTFLoader.js"` *only there*;
  `load-model` calls `.loadAsync` on it and returns the raw parsed `gltf` — nothing from that
  call is ever added to a scene. `model-meshes` is the sole consumer: `rebuild-geometry` creates
  a brand-new `BufferGeometry`/`BufferAttribute`/`Matrix4` off `(three)` (the active, kit's-when-
  up THREE) and only copies plain numeric data (`.-array`, `.-elements`) from the parsed side,
  never storing an npm object; `rebuild-material` likewise constructs an active-THREE
  `MeshStandardMaterial`/`Texture`, reading only plain props (`read-material`) and the raw decoded
  `.-image` off the source. `pm.scene` never imports three.js at all and only calls through
  `t/load-model`/`t/model-meshes`/`t/add-y!`/`t/ghost!`/`t/light-glows!` — confirmed by its
  `:require` list, unchanged except for the new `pm.models`.
- **The GLB loads once per URL**: `load-model`'s `defonce` atom caches the *promise itself*
  keyed by URL (`(or (get @!model-cache url) (let [p ...] (swap! ...) p))`), so a second call
  for the same URL — even a rejected one — returns the identical promise object rather than
  re-fetching; matches the spec's explicit "a rejected promise stays cached, so a bad file is
  tried once."
- **Textures keep `flipY false` and sRGB**: `rebuild-material` sets `(.-flipY tex) false` and
  `(.-colorSpace tex) (.-SRGBColorSpace T)` (active THREE's own enum) unconditionally whenever a
  source `map` exists, alongside carrying over the source's `wrapS`/`wrapT` and `needsUpdate
  true` — matches spec §3.1 exactly.
- **Material overrides: a pure, tested fn of data**: `pm.models/material-override` touches no
  three.js/DOM/`js/Date`/atoms (confirmed reading the whole file: only `clojure.string` and
  `pm.materials` for `wind-marker`). Hand-traced every branch (kit-name validity incl. the wind
  marker, `:glow`'s `true?`/`false?` literal gating, `:light-m`'s `finite-pos?` guard against
  `0`/`-1`/a string/`NaN`/`Infinity`) against cases 1–15 — all match, including the two-layer
  wind defence (`material-override` re-checks a kit name's own string even when the caller might
  already have filtered the index, case 14).
- **The kit's shared materials are cloned, never mutated**: `swap-in-model!`'s `:kit` branch does
  `(set! (.-material m) (.clone km))` — `km` (`(get (:index kit-mats) k)`, the kit's own object)
  is only ever read and cloned, never assigned-into; grepped `swap-in-model!` for any `set!`/`.set`
  call whose target is `km` directly: none.
- **`:look` stays the fallback; failures warn once**: `mount-item!` draws the `:look` parts
  unconditionally, *then* kicks off the model load; `swap-in-model!` only removes a look mesh
  (matched precisely by `look-mesh-name?`'s `"<key>/<i>"` regex, so it never touches an unrelated
  sibling like a ghost-edges line) once real model meshes are in hand, so an empty/`nil`
  `model-meshes` result or a rejected `load-model` promise both leave the look completely
  untouched. All three warn paths (`:load-failed` on a rejected promise, `:model-empty` when
  meshes come back nil/empty, `:kit-missing` per missing kit name) share one `model-warn!`
  helper backed by a `defonce` token set, so each distinct cause warns exactly once per page
  session, never once per frame/poll.
- **The booth keeps its click key and sign**: every model mesh is named `(str (pr-str key) "/"
  i)` — the same convention `pm.pick/key-of-name`/`pick-key` already strip to recover the item's
  key (cases 45–46 exercise this directly against a model-mesh-shaped name) — and the swap never
  renames/repositions/reyaws the outer group `g` itself. The label sprite is only ever nudged up
  (`add-y!`, `pos?`-gated so it never moves on a dy ≤ 0) via `pm.plan/model-label-at`, which is
  pure, tested (cases 39–44, including "never lowered" and every bad-input branch), and leaves
  `:label-at` itself unchanged when its own guards fail.
- **No new npm deps; nothing names a person/pet/member in code**: `package.json` has no diff —
  `GLTFLoader.js` ships inside the already-present `three` 0.186.1 dependency. Grepped `src/` for
  crew codenames and blueprint ids: the only hit (`pick.cljs`'s pre-existing `le-testeur`
  docstring) predates this round. The one new string literal naming anything, `"/assets/props/
  phone-booth.glb"`, is explicitly permitted — it's the booth's own asset file, not a person/pet/
  member/blueprint.
- **Boundary table**: `git status` matches exactly — builder's files (new `models.cljs`,
  `plan.cljs`'s `phone-booth-item`/`model-label-at`, `three.cljs`'s six named fns plus their
  private helpers, `scene.cljs`'s `mount-item!`/`swap-in-model!` plus its private helpers,
  `estate.edn`'s `:phone-booth` entry only) plus the tester's one new file; `test/`, `docs/`,
  `TASKS.md`, any other estate key, `tools/blender/`, and `public/assets/props/` are all
  untouched by this round.

## Findings

None. Every pure fn (`model-spec`, `material-override`, `material-props`, `model-label-at`)
traces to matching code and a passing, hand-checked test, and the hand-tested wiring
(`load-model`, `model-meshes`, `swap-in-model!`, the `light-glows!`/`unlight-glows!`/
`sync-glows!` extension to a no-light glow) correctly implements every rule the quest's two
amendments settled on — including the subtler `userData.lit`-vs-`.light` distinction that lets
the TELEPHONE signs glow without a point light while still being tracked, undone and resynced
exactly like a lit mesh.
