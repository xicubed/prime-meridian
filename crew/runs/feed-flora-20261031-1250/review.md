# Review: feed-flora round

Spec: `crew/runs/feed-flora-20261031-1250/spec.md`. Scope checked: `src/pm/world.cljs` (new),
`resources/estate.edn` (new), `test/pm/world_test.cljs` (new), `src/pm/core.cljs` (edit). The
`.gitignore`/`AGENTS.md`/`docs/GDD.md`/`shadow-cljs.edn` diffs predate this round (already in the
working tree before `build` started per `log.md`) and are out of this round's scope.

Verified independently: `npx shadow-cljs compile app` → 0 warnings; `npx shadow-cljs compile test`
+ `node out/test.js` → 32 tests, 121 assertions, 0 failures. Matches `gate-note.md`.

Spec conformance: `resources/estate.edn` is byte-for-byte the spec's data. `pm.world.cljs`
implements every function with the exact signature and ordering the spec lists (`due?` check
order, `chore-done` check order, `markers` condition set, `status-line` part order, `key-event`
lookup) — hand-traced against cases t01–t32 and all pass. No three.js, `js/Date`, atoms, or side
effects in `pm.world`; no mirror-specific name (`flora`, `juniper`, worm, axolotl, horse, hay)
appears outside `estate.edn` and the test file — `pm.core` only reaches Flora through `:hud`
data, as required. `core.cljs` changes match spec §4 exactly (HUD suffix format, keydown listener
added only in `init!`, `^js` hint, repeat/ctrl/meta/alt guard). Boundary table respected: builder
touched only its three files, tester only `test/pm/world_test.cljs`.

Findings (minor style only; nothing blocks merge):

1. `src/pm/world.cljs:38` — `(let [last (last-done ...)] ...)` shadows `clojure.core/last`. Rename
   to `last-at` (or similar); shadowing core names is the one real idiom violation in the diff.
2. `src/pm/world.cljs:65-66` — in `chore-done`, the destructured `chore` (an id keyword) is
   immediately rebound to the resolved chore map one line later. Rename the destructure target to
   `chore-id` so the two bindings don't read as the same thing.
3. `test/pm/world_test.cljs:2` — `testing` is pulled in via `:refer` but never called; drop it
   from the refer list (or wrap related assertions in `(testing "...")` blocks for readability).
4. `src/pm/world.cljs:61` — private helper named `log` reads ambiguously next to `core.cljs`'s
   `js/console.log` call it feeds a few lines away in the diff; `log-event` would be clearer.

No findings on purity, naming leakage, or spec/test mismatch.
