# Review: characters-v0

Gate re-checked against `gate-1.txt`: PASS, 0 warnings, 353 tests / 1686 assertions (up from
325/1628 at the end of `avoid-v0`: +28 tests, exactly `test/pm/characters_test.cljs`'s `t01`–`t28`,
one per spec case). `motion_test.cljs` and `avoid_test.cljs` are untouched (not in `git status`)
and their totals (58+65=123 of the 353) are unaffected, satisfying the "keep passing unchanged"
contract; `m/config`, `m/avoid-config` and `(:label-y m/figure)` are untouched by this round (see
isolation method below).

**Isolation method.** `motion.cljs`/`bricks.cljs`/`three.cljs` are also `avoid-v0`'s files, and
`avoid-v0` is still uncommitted, so a plain `git diff` mixes both rounds' hunks. I reconstructed
each file's `avoid-v0`-end state (`git show 00460c6:<file>` + `avoid-v0`'s own diff, already
verified against its spec in the prior review) and diffed that against the current file to get
*only* `characters-v0`'s contribution. All findings below are checked against that isolated diff.

**Scope note — pre-build setup, not this round's work.** `public/index.html`, `public/vendor/three-kit.mjs`,
`public/assets/characters/` and `public/vendor/three-addons-r186/` are modified/untracked, and
`src/pm/kit.cljs` is modified — none are in the builder's/tester's allowed files (`public/**` and
`kit.cljs` are both explicitly off-limits). Mtimes place all of them between 13:51 and 13:55:08,
before `build` started at 14:00:41 (two of `un-architecte`'s blocked `spec` attempts ran in that
window) — manager setup (the quest's own "what exists" bullets: vendored `.glb`s, the unmodified
`GLTFLoader.js`, the import-map entry, `three-kit.mjs`'s re-export list), not a builder/tester
boundary violation. `kit.cljs`'s diff is an unrelated "close the Studio panel" UI fix, also outside
this round's scope. Excluded below.

## Conformance checks

- **GLTFLoader/library import gating:** `gltf-loader!` loads
  `"three/addons/loaders/GLTFLoader.js"` via `((js/Function. "u" "return import(u)") ...)`, memoized
  in `defonce !gltf-loader`, created lazily only when a walker's `load-body!` actually needs it
  (never if no walker has `:character`) — matches §3.3's "created only when some walker has a
  `:character`". Both this and the MotionBricks model's own `import()` call happen only inside
  `start!`'s `(m/bricks-flag? ...)` branch, and `start!` is only invoked as `pm.core`'s kit-ready
  callback (`kit/load! start-kit! ...`, unchanged from `motion-bricks-v0`) — so both only run after
  the kit is ready, and both exclusively through the `js/Function` loader (no bare `import(` found
  anywhere in `src/`).
- **Per-walker fallback with HUD reason:** a character/body/rig failure for one walker
  (`load-body!`'s resolved `#js {:error ...}`, a missing `CHARACTER_RIGS` key, or a `setCharacter`
  exception) only calls `(.setMannequin c)` for *that* walker and records
  `{:name (:name w) :reason reason}` — it does **not** call the generation-failure `fallback!`
  (which still drops *every* walker to kinematic and is reserved for the model/network failure
  path, unchanged). The per-walker errors are collected in walker order and land in
  `:char-errors`, which `char-errors-text`/`hud-text` render as `"mannequin for <name> (<reason>)"`
  after the gen segment and before the detours segment — hand-verified against spec §2.2 and
  matches `characters_test.cljs` t23–t26 exactly, including the "every other mode ignores
  `:char-errors`" rule (t28).
- **Display scale applied once, to the object and the radius:** `t/set-scale!` is called exactly
  once per view — on the kinematic figure in `build-view!`, or on `c.object` in `swap-in!` (before
  it's positioned/added) — never both for the same walker, since the figure is hidden once the
  character swaps in. The port's own `matchSize` (inside `setCharacter`) scales the retargeted
  skeleton to match SOMA's legs; `display-scale` is a separate, outer scale on `c.object`/the
  figure group, so the two don't double-count. The avoidance radius gets the same `ds` exactly
  once, inside `walkers` (`(update w :radius * ds)`, after the avoid-v0 radius default and *not*
  inside `walker` itself, matching "this happens in `walkers` only; `walker` stays unscaled" —
  confirmed by `t16`/`characters_test.cljs`). `t14`/`t15`/`t17` hand-check the scaled numbers
  (0.3×2=0.6, 0.4×2=0.8, 0.3×1.4≈0.42) and that the scaled-radius planner still clears both
  walkers and the statics over a 2400-frame sim.
- **No walker or character names in code:** `grep -rni "polybot|astronaut|un-automate|le-testeur|juniper|flora" src/` is empty.
- **`public/vendor` and `resources/content` untouched by this round:** confirmed by mtime
  (`three-motionbricks-0.2.0` predates everything; nothing under `resources/content` is newer than
  this round's `spec.md`); `public/vendor/three-addons-r186`'s own mtime also predates `build`.
- **Pure logic tested:** `rig-key`, `character`, `display-scale`, `label-height`, `char-errors-text`
  and the scaled `walkers` are all pure (no three.js/atoms/`js/Date`), each has direct test
  coverage (t01–t21), and the `pm.bricks` interop changes carry no new rules — `plan`/`step`/`follow`/
  `advance` are byte-for-byte unchanged from `avoid-v0` in the isolated diff.

## Boundary-table compliance

Builder touched exactly: `src/pm/motion.cljs`, `src/pm/bricks.cljs`, `src/pm/three.cljs` (isolated
diff shows only `set-scale!`/`top-y` added, matching §4 exactly — nothing else in the file
changed), `resources/estate.edn` (§1.1: `:display-scale 1.0` with the specified comment, both
walkers' `:character` entries, nothing else). `src/pm/avoid.cljs` and `src/pm/core.cljs` are
untouched. `src/pm/world.cljs` was touched (mtime inside the build window), satisfying "touch
after editing estate.edn." Tester touched only `test/pm/characters_test.cljs`. No off-limits file
was edited by this round's builder/tester (see scope note above for the pre-build setup files).

## Findings

None rise above a style nit.

1. `bricks.cljs`'s big `.then` body in the `?bricks` load (model+bodies resolution, per-walker
   `cond`) is dense — spec-conformant and correctly ordered, but a good candidate to split into a
   named helper if a future round needs to touch this path again.

**Verdict: pass.**
