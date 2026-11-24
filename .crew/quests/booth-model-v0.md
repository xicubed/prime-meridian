# Quest: props can be models: the red phone box goes in (TASKS T-18)

**Game:** Prime Meridian. **Asked by:** Michael, Nov 23: "make the phone booth look more like a
real london phone booth … keep with the theme", then fading, rust, moss and vines "like the
house". Hal built the model in Blender; this round teaches the game to load a model for a prop
and swaps the booth's boxes for it. **Led by:** Hal. Michael reviews by eye.

## What exists (read first)

- `public/assets/props/phone-booth.glb` (made by `tools/blender/phone_booth.py`; don't edit
  either). One mesh, origin at the centre of its base on the ground, Y-up, door facing +z,
  ~0.95 m square, 2.5 m tall. Its **material names are the contract**: `booth-red` (baked
  weathered texture), `floor` (baked texture), `glass` (panes), `sign` (TELEPHONE panels),
  `letters`, `metal`, `lamp` (ceiling light), `leaf`, `stem`.
- `resources/estate.edn` `:phone-booth {:at :yaw :look}` and `pm.plan/phone-booth-item`
  (key `[:phone-booth :box]`, its sign shows the jack-out reason); clicking the booth jacks you
  back in (`pm.pick`, `booth-key`). `pm.scene` mounts items through `pm.three` parts.
- `pm.three` runs on **the kit's THREE** (`use-three!`); the kit's scene has `MAT_Hiroshi_Glass`
  (the cottage's windows). The T-17 round (`crew/runs/kit-materials-v0-*`, `pm.materials`)
  shows how a kit material is found by name and cloned per part; `light-glows!` /
  `unlight-glows!` / `:glow true` make a part glow with the kit's own lamps at night.
- npm `three` 0.186 is a dependency (`node_modules/three/examples/jsm/loaders/GLTFLoader.js`).
- Conventions: `.crew/CONVENTIONS.md`; gate `.crew/gate.sh`; `AGENTS.md`. Don't read
  `assets/private/`, `public/kit/`. **Never send keys or prompts to crew members; don't start
  services or the bridge; no whole-disk `find`; scratch files only in your own scratchpad;
  never write `state/` from tests.** No new npm deps.

## Decided (don't reopen)

- **`:model` on a prop is data:** `:phone-booth` gains `:model {:url "/assets/props/phone-booth.glb"
  :materials {"lamp" {:glow true} "sign" {:glow true}}}`.
  **The panes keep the model's own semi-translucent glass** (Michael, Nov 23: "i do like that you
  can see thru it a little"); no kit override for `glass` (the `:kit` override stays supported for
  later). The rebuilt glass material must stay transparent (opacity from the model, `transparent
  true`, `depthWrite false`) so you see the phone inside.
  URLs are absolute (the page has `<base href="/kit/">`). Keep the booth's `:look` as the
  fallback. The loader is general (any item with `:model`), but only the booth gets one now.
- **Parse with npm GLTFLoader, rebuild with the active THREE:** load the GLB once per URL
  (cache the promise) with `GLTFLoader.parse`/`loadAsync`, then rebuild every mesh as the
  active THREE's (`pm.three`'s current THREE, the kit's when it's up) `BufferGeometry`
  (copy each attribute's array/itemSize/normalized, the index and groups) and
  `MeshStandardMaterial` (colour, roughness, metalness, emissive, opacity/transparent, and the
  base-colour map rebuilt as the active THREE's `Texture` from the parsed image with
  `flipY false` and sRGB colour space). Never add an npm-three object to the kit's scene.
- **Material overrides by name** (a pure fn: material name + `:materials` map → `{:kit name}`,
  `{:glow true}` or nil; tested): `:kit` = a per-mesh clone of that kit material (fallback to the
  model's own when missing); `:glow` = the same night glow as `:glow true` parts.
- **Swap in place:** draw the `:look` parts at mount (as today); when the model arrives,
  replace the item's parts with the model's meshes under the same group (same name/key,
  position, yaw), so clicks on the booth still jack you back in and its sign stays where it
  is (raise the sign's anchor to the model's top if it would sit inside it). Load failure or
  no WebGL: keep the `:look`, warn once in the console.
- **Size:** the model is real-size; no `:scale` change.
- **Demo build:** `public/assets/props/` is copied into `dist/demo` already; nothing to add.
- **Everyone's game:** nothing names a person, pet or member in code; the booth's data names
  its own file.

## Out of scope

Models for other props (benches, lamp, mailbox); swaying vines; editing the model; animations.

## Done when

Gate passes with tests for: the material-override fn (kit, glow, none, unknown name); the
`:model` validation (url string starting with "/", optional materials map; bad shapes ignored →
`:look` used); the item still carries `[:phone-booth :box]` and its label position with a
model. The builder leaves a note in the run dir: what was built and what Hal should check
headless (the booth in the kit, see-through panes with the phone visible inside, a click on it while jacked
out, its lamp at night).
