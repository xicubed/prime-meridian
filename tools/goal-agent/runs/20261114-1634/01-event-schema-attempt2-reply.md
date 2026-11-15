Confirmed it's wired into the HUD already. Everything checks out.

FILES: src/pm/events.cljc
WHAT: `pm.events` defines the four bridge events (`agent-event`, `answer`, `chore-done`, `jack-out`) as plain EDN data, each requiring a valid `:source` (`:agent`/`:session`/`:machine`, non-blank strings, no stray keys); `valid-event?` refuses anything malformed or sourceless, and `valid-source?` now correctly returns `false` (not `nil`) for `nil`/`{}` instead of short-circuiting the `and`.
HOW TO SEE IT: the HUD footer shows a live `events N/M ok` segment (via `events/hud-segment` on the sample events in `pm.core`), counting how many of the sample bridge events validate.