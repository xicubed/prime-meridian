# Quest: real state v1 (the game remembers Hal's last word)

**Game:** Prime Meridian. Follows `real-state-v0` (Hal's file, `pm.overlay`).
**Asked by:** Michael: "you should be the gold standard but the game should
cache updates so even if I start it without you it's still right till you're
back online." **Led by:** Hal.

## The moment we're building

Michael opens the game while Hal (and so `/state/mirrors.edn`) is offline. Today
it falls back to the sample: 15 worms, "I'm hungry". After this round it shows
the last state Hal gave it (37 worms, fed Friday) plus anything Michael did in
the game since (an F press), tagged `Flora (Hal, cached)`. When Hal's file is
reachable again, the tag goes back to `Flora (Hal)`, and if the file is newer
than what the game last applied, the file wins.

## What exists (read first)

- `src/pm/overlay.cljs` (pure): `parse-file`, `apply-file` (merges accepted
  state fields, sets `:source :hal`, records `:state-as-of`; an unchanged
  `:as-of` is a no-op, so local F presses survive an unchanged file),
  `source-tag`, `hud-line`. Tests: `test/pm/overlay_test.cljs`.
- `src/pm/core.cljs`: `fetch-state!` / `start-state-poll!` (every 5 s), the F
  key (`on-key!` → `world/chore-done`), HUD.
- The run's spec from last round: `crew/runs/real-state-v0-20261106-1739/spec.md`.
- Gate: `.crew/gate.sh`. Don't read `assets/private/`, `public/kit/`, `public/state/`.

## Scope

1. **Pure (in `pm.overlay`)**, tested:
   - `(cache-of world)`: what to remember: the last applied `:state-as-of`
     plus, for every mirror whose `:source` is `:hal`, its `:state` fields
     (only fields its sample state has). `nil` if nothing came from Hal yet.
   - `(apply-cache world cache)`: lay a cache (as `cache-of` returned it, read
     back from text) over a freshly loaded world: same acceptance rules as
     `apply-file`, sets `:state-as-of` from the cache and marks the session as
     not yet live (e.g. `:state-live? false`). Garbage in = world unchanged.
   - Precedence, after a cache was applied: a file with the **same** `:as-of`
     changes no state (local changes since are kept) but marks the session
     live; a file with a **different** `:as-of` wins (as today) and marks it
     live.
   - HUD tag: `Hal` when live, `Hal, cached` when the state came from the
     cache and the file hasn't been reached this session, `sample` otherwise.
   Test both mirrors, garbage caches, and the three precedence cases.
2. **Core**: on start, before the first fetch, read the cache from
   `localStorage` (one key, e.g. `"pm.state.v1"`, EDN text) and apply it. After
   any change to the world's mirror states (a file applied, a chore done),
   write `(pr-str (cache-of world))` back, only when the text changed. All
   storage access in try/catch (private mode, quota): failures are silent.
3. README "Run" paragraph: one more sentence on the cache.

## Out of scope

Writing back to Hal (F presses reach Hal's file and reminder later), the feed
card UI, clearing the cache from the UI.

## Done when

The gate passes with the new tests; live: with the file present the HUD says
`Flora (Hal) · stock 37`; with the file moved away and the page reloaded it
says `Flora (Hal, cached) · stock 37` (not the sample's 15); putting the file
back flips it to `(Hal)` within ~5 s (Hal checks this).
