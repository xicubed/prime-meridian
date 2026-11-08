# Quest: a Herdr status dot before each walker's name (W3-0, first slice)

**Game:** Prime Meridian. **Asked by:** Michael: "put the Herdr dot/color in front of their name,
which tracks with Herdr, so you can tell if they're busy etc." **Led by:** Hal. First slice of
TASKS W3-0 (the crew on the estate). Follows `senses-v0`.

## The moment we're building

Over Un Automate's head: `● Un Automate`, the dot coloured by his real Herdr state, updating
within a few seconds when he starts or stops work in his pane. Same for Le Testeur. A walker
whose agent isn't running shows a hollow grey `○`. When the status file is missing or stale
(older than 15 s: the watcher stopped), dots go hollow grey too, rather than lying.

## Data flow

- Hal's watcher `bb crew-status` (exists: `scripts/crew_status.clj`) writes
  `public/state/crew.edn` every 2 s: `{:as-of <ms> :agents {:un-automate-monde {:status :working} …}}`.
  Gitignored, like `mirrors.edn`; served at `/state/crew.edn`.
- The game polls it like it polls `/state/mirrors.edn` (see `pm.core`'s state poll; reuse that
  pattern, 2 s here), keeps the last good file, and ignores garbage.
- Each walker names its agent: `:agent :un-automate-monde` in `:walkers` (default: the walker's
  own `:id`). Nothing names a walker or an agent in code.

## Colours (data, `resources/estate.edn` `:motion :status-dots`)

`{:working 0xf2b134 :blocked 0xe5484d :done 0x46a758 :idle 0x9aa0a6 :unknown 0x9aa0a6}`;
`:blocked` also gets a slow pulse, since it means "waiting for a human". A missing agent or a stale
file: hollow `○` in `:idle`'s grey.

## Rules (pure, tested)

- Parse/validate the file (map, integer `:as-of`, `:agents` a map of keyword → `{:status kw}`;
  unknown statuses → `:unknown`).
- Staleness: `now − :as-of > 15 s` → every dot hollow.
- Dot for a walker: its agent's status, or hollow when absent.
- Label text: `"● " + name` (or `"○ "`), with the dot coloured and the name in the existing style.
- The `ⓘ` status panel gains `crew: 2/2 live` (or `crew: stale`).

## What exists (read first)

`pm.core` (state poll, HUD), `pm.bricks`/`pm.scene` (walker name labels; how labels are drawn),
`pm.motion` (walkers from data), `public/index.html` (the ⓘ panel). Gate `.crew/gate.sh`.
Don't read `assets/private/`, `public/kit/`; you may read `public/state/crew.edn` (no secrets in it:
names and states only); don't edit `public/vendor/` or `resources/content/`.

## Out of scope

The red "!" over a blocked worker, walking over to the player, answering the prompt in the game
(next W3-0 slices); the creatures.

## Done when

The gate passes with tests for parsing, staleness, the dot per status, missing agent, and the
label text. Live (Hal checks): dots match `herdr agent list` within ~3 s; stopping the watcher
turns them hollow after 15 s; 60 fps unchanged.
