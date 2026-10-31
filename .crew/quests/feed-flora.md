# Quest: feed Flora (the worm loop)

**Game:** Prime Meridian (`docs/GDD.md`). **Asked by:** Michael, the Director. **Led by:** Hal.

## The moment we're building

GDD §1 "The snap", cut down to what the world rules need (no 3D yet):
you mark Flora fed, the worm jar shows one fewer, her "I'm hungry" bubble
pops, and when the jar runs low its sign says so. The same code must run a
second, different mirror (a horse with a hay counter), because nothing in
code may know what Flora is (AGENTS.md / GDD §2.5 "everyone's game").

## What exists today

`src/pm/core.cljs` (render loop, HUD line), `src/pm/three.cljs` (interop
wrapper), `src/pm/audio.cljs`. There is no `pm.world` and no
`resources/estate.edn` yet. The `:test` build in `shadow-cljs.edn` runs
`*-test` namespaces under `test/` on Node; `resources/` is on the classpath.
The gate is `.crew/gate.sh` (compile app + test with 0 warnings, tests pass).

## Scope (this round)

1. **`resources/estate.edn`**: a map with a `:mirrors` vector in the GDD §2.5
   shape. Two mirrors:
   - `:flora`, `:kind :creature`, `:model :axolotl`, `:home :canal`; state
     `{:stock 15 :last-fed <ms or nil>}`; chore `:feed` every `"2d"` at
     `"17:00"`, `:who :me`, `:consumes [:stock 1]`; rules: hungry bubble
     when the feed chore is due, and a sign when stock is 2 or less.
   - a second, different mirror from the same code path (GDD suggests a
     horse in a barn with a hay counter), with its own counter field name,
     interval, and lines.
   State is mocked in the file this round (the bridge reads real state later).
2. **`src/pm/world.cljs`**: pure functions, no three.js, no js/Date inside
   (time `now` is always an argument, in ms):
   - load the estate (EDN text → world map keyed by mirror id),
   - `due?` for a mirror's chore at `now` (interval strings like `"2d"`,
     `"12h"`; never-done chores are due),
   - `chore-done` applying the GDD's `chore-done` event
     (`{:mirror :chore :at :via :who}`): consume per `:consumes` (never below
     0), record when it was done; **only one counts per due window**, so a
     text and a click don't drop two worms: a second event before the chore
     is due again changes nothing except a log entry saying it was ignored;
     events for unknown mirrors or chores are ignored the same way,
   - `markers` for a mirror at `now`: the bubbles and signs its rules say
     should show (e.g. `[{:say "I'm hungry"} {:sign "2 worms left!"}]`).
3. **`test/pm/world_test.cljs`**: tests from the spec, for both mirrors.
4. **In the running game** (`src/pm/core.cljs`): load the estate into the
   world atom, show Flora's markers and worm count on the HUD line, and let
   the **F** key send a `chore-done` for Flora's feed chore (via `:key`).
   Keep the existing HUD fields (backend, frame, t) so the liveness check
   still works.

## Out of scope

Rendering mirrors in 3D, the bridge, SMS/NFC channels, restocking,
`:who`-based reminders, memorial/virtual modes.

## Done when

`.crew/gate.sh` passes (0 warnings, tests pass), both mirrors are covered by
tests, and the diff touches only the files above.
