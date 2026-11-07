# Quest: real state from Hal (the twin's file)

**Game:** Prime Meridian (`docs/GDD.md`: mirrors §2.5; "batteries not included":
sample connections are the test drive, and the swap to real happens in place).
**Asked by:** Michael. **Led by:** Hal.

## The moment we're building

Michael fed Flora today and has 37 worms. Hal (his twin, who keeps Flora's
feeding reminder) knows that; the game doesn't: every reload starts from the
sample state (15 worms, never fed, "I'm hungry"). After this round the game
reads Flora's real state from a file Hal keeps, so it shows 37 worms and no
bubble until she's really due (Sun 5 PM), survives reloads and server
restarts, and when Hal changes the file (Michael texts "fed Flora") the game
follows within a few seconds, without a reload.

## What exists (read first)

- `resources/estate.edn`: the sample estate (inlined at compile time via
  `pm.world/estate-edn`). Flora's `:state {:source :sample :stock 15 :last-fed nil}`.
- `src/pm/world.cljs` (pure, tested): `load-estate`, `due?`, `chore-done`,
  `markers`, `status-line`. `now` and times are epoch ms, always arguments.
- `src/pm/core.cljs`: `!world` atom, `start-kit!` / `start-scaffold!`, the
  frame loop and HUD (`hud-mirrors-suffix`), the F key (`on-key!`).
- `src/pm/scene.cljs` updates labels from the world each frame, so state
  changes show up on their own.
- The page has `<base href="/kit/">`: **every URL the game fetches must be
  absolute** (`/state/mirrors.edn`, not `state/...`).
- Gate: `.crew/gate.sh`. Don't read `assets/private/`, `public/kit/` or `public/state/`.

## Hal's file (the contract)

`public/state/mirrors.edn`, served at `/state/mirrors.edn`, gitignored (it is
private and may not exist):

```clojure
{:as-of 1794013200000                       ; ms; Hal bumps it on every change
 :mirrors {:flora {:state {:stock 37 :last-fed 1794013200000}}}}
```

## Scope

1. **Pure overlay** (in `pm.world` or a new pure namespace): given the world
   and a parsed file, return the world with each listed mirror's `:state`
   merged from the file and `:source :hal` set on it. Ignore: unknown mirrors,
   keys that aren't already in that mirror's sample `:state` (so the file
   can't invent fields), values of the wrong type (numbers stay numbers,
   `nil` allowed for times), a missing/invalid `:as-of`, or a file that isn't
   a map. Same `:as-of` as the last applied one = no change (so an in-game F
   press isn't undone by an unchanged file). Unit-test all of it, for both
   mirrors (Juniper via a test file that lists him).
2. **Loading** (core side): on start (kit and scaffold), fetch
   `/state/mirrors.edn` with a cache-busting query; then poll every 5 s. Parse
   with `cljs.reader/read-string` inside a try; any failure (404, bad EDN) is
   silent and leaves the world as it is. No new npm deps.
3. **HUD**: the status line shows where each HUD mirror's state comes from,
   e.g. `Flora (Hal) · stock 37` vs `Flora (sample) · stock 15`. Keep it short.
4. Docs: one paragraph in `docs/` or the README "Run" section: what the file
   is, that it's private, how to try it (copy the example above).

## Out of scope

Writing back from the game (the F key still changes only the page; a local
helper that writes the file and completes Hal's reminder is a later round),
the feed card UI, Hal's side of updating the file.

## Done when

`.crew/gate.sh` passes with the overlay tests; in the running game with Hal's
file present the HUD reads `Flora (Hal) · stock 37` and she has no "I'm
hungry" bubble; when Hal edits the file (new `:as-of`, stock 36) the HUD
follows within ~5 s without a reload (Hal checks this live).
