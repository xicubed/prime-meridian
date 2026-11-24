# Review — kit-materials-v0 (TASKS T-17)

Scope: `git diff` for the 3 modified files (`resources/estate.edn`, `src/pm/scene.cljs`,
`src/pm/three.cljs`) plus a full read of the two new files (`src/pm/materials.cljs`,
`test/pm/materials_test.cljs`), against `spec.md` and the quest. `49ef370` (the quest+spec
commit, code/tests unchanged) is the baseline; nothing else is stacked on it.

## Gate — verified independently

Re-ran `bash .crew/gate.sh`: `[:app]`/`[:test]` 0 warnings, `Ran 1351 tests containing 7690
assertions, 0 failures, 0 errors`, `bb content-test` 5/42 — matches `gate-1.txt` exactly.
Structural cross-check: committed `deftest` count at `HEAD` is 1300, working tree is 1351, a
delta of exactly 51 — `materials_test.cljs`'s own case-1..case-51, matching the spec's own "51
numbered cases" exactly.

## Point-by-point

- **Only the kit's THREE; no npm `three`/`three/examples` objects in the kit scene**:
  `three.cljs`'s `:require` is unchanged (`"three/webgpu"`, `clojure.string`, now also
  `pm.materials` — a pure namespace, no three.js import of its own). Every new class
  (`BoxGeometry`, `EdgesGeometry`, `LineBasicMaterial`, `LineSegments`, `MeshStandardMaterial`)
  is pulled off `(three)` — the same `@!three` atom `use-three!` points at the kit's own THREE —
  exactly the existing pattern, never a separately-imported module. Grepped the whole file for
  `examples`/a second `require`: none.
- **Each part gets its own cloned material and texture clones; the kit's shared materials are
  never mutated**: `kit-part-material` and its helpers (`clone-with-repeat`, `clone-tex!`) only
  ever call `set!`/`.set` on the *local* clone bindings (`m`, `t`, `mx`, `my`, `mz`) — never on
  the `material`/`tex` parameters that reference the kit's own objects. `.clone()` is called
  first in every path before anything is touched; for `:box`, three independent clones (`mx` `my`
  `mz`) back the six-slot material-index array `[mx mx my my mz mz]`, matching `BoxGeometry`'s
  face-group order. The palette colour is never set on the clone, so the kit material's own
  colour survives untouched (confirmed by reading every line of `kit-part-material`/
  `clone-with-repeat`: no `.color`/`.setHex` call anywhere in them).
- **`box-face-repeats`/`shape-repeat`/`bevel-radius`/`round-box-vertex` are pure and tested**:
  `pm.materials.cljs` requires only `clojure.string` — no three.js/DOM/`js/Date`/atoms anywhere
  in the file. Hand-traced `box-face-repeats`'s seat/leg numbers, `shape-repeat`'s cylinder/cone
  formula, `bevel-radius`'s `min(0.02, smallest/4)`, and `round-box-vertex`'s face-middle/edge/
  corner cases (including the "same extents as today's box" property: face-middle vertices land
  exactly on `±size/2 ∓ 0` after `box_i + normal_i·r` collapses to `box_i + r` on the one
  non-middle axis) against the spec's §2.4–2.7 formulas and the test file's case-19..case-45 —
  all match, including the sign-agnostic corner case (`case-41`).
- **`ghost!`/`unghost!` save and restore textured materials exactly**: `ghost!` now stores the
  mesh's *actual* current material (single object or array, textured or flat) in
  `userData.ghostMat` — only on first ghosting (`if (some? ghostMat) dispose-the-old-ghost-
  material : save-the-real-one`), so a double-ghost doesn't clobber the originally-saved
  reference. `unghost!` disposes the ghost material and reassigns `.material` to the exact saved
  reference (`(set! (.-material o) saved)`, not a re-clone), then deletes `ghostMat`; a mesh
  never ghosted is left completely untouched. Edge lines are built from a plain `BoxGeometry
  (userData.boxSize)` when the mesh was bevelled, so the outline stays a crisp box rather than
  tracing every bevel segment — exactly the spec's stated rationale.
- **Flat-colour fallback with no kit or a missing name, warned once**: `mount!` builds the
  material index from `(t/material-pairs scene)` *before* adding our own root (so our clones,
  which inherit the kit material's `.name`, are never indexed), and only calls
  `mats/missing-names` (and warns) when that index is non-empty — a scaffold build (no kit) has
  an empty index and never warns. `mount!`'s own `(when-not @!mounted ...)` guard means this runs
  at most once per page load, so a genuinely-missing name warns exactly once, not per frame.
  `material-for` returns nil (flat colour via `stand-in`'s unset `:kit` opt) for any unmapped
  key, a missing name, or a kit that isn't up (`index` empty/nil) — confirmed by the pure-fn
  tests (`case-12`–`case-14`).
- **`" / wind"` materials never used**: `index-materials` drops any pair whose name contains
  `wind-marker`; `material-for` independently re-checks the *kit-materials entry's own* name for
  the marker too (`case-15` forces a wind name directly into the index to prove this second,
  independent guard — not just relying on `index-materials` having already filtered it);
  `missing-names` always reports a wind name as missing (never satisfiable), matching "never
  used" with two layers rather than one.
- **Raycasts unchanged**: mesh names are set exactly as before regardless of the new opts; no
  raycast-related code changed in `scene.cljs`/`three.cljs` (array materials raycast using
  three.js's own native per-face behaviour, needing no special-casing here); ghost edge lines
  keep their `(set! (.-raycast lines) (fn [] nil))` no-op, unchanged.
- **No blueprint/person/pet named in code; no npm deps**: grepped `src/pm` for crew codenames and
  blueprint ids — the only hit (`pick.cljs`'s pre-existing `le-testeur` docstring) predates this
  round's diff. `package.json` has no diff.
- **Boundary table**: `git status` matches exactly — builder's files (new `materials.cljs`,
  `three.cljs`'s listed functions, `scene.cljs`'s `mount-item!`/`mount!`/`sync-blueprint-draw!`,
  `estate.edn`'s `:kit-materials` entry only) plus the tester's one new file; `pm.bricks` and
  `pm.creature-spike` (which also call `t/stand-in`) are untouched and don't need to be — the new
  `:bevel`/`:kit` opts default to nil, leaving their calls byte-for-byte unchanged, matching the
  spec's explicit design goal.

## Findings

None. `props-item?` correctly draws the only distinction the quest requires (a `:mirror` key
marks a creature/walker item, built that way by the existing, untouched `pm.plan` mirror-item
constructor) — note for the record, not a defect: a prop like the phone booth also gets rounded
box edges (via `props-item?` being true for it) even though its `:booth-red`/`:glass` colours
stay flat-painted, since the bevel and the kit-material lookup are independent; the spec's own
wording ("characters, creatures and the phone booth stay painted") is specifically about colour/
texture, and the builder's own note and GDD wording both confine the "stay painted" promise the
same way.
