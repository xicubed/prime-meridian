# Builder notes — live blueprints v0 (TASKS T-13 part b)

## What was built

- `src/pm/blueprint_file.cljc` (new): the one validator (`problems`/`valid?`), `normalize`,
  `add`, `remove-id`, `parse-args`. Portable (bb + cljs).
- `src/pm/blueprint.cljs`: `valid-blueprint?` replaced by `bf/valid?` (no behaviour change).
  New: `merge-file` (file → `!world`, pure), `seat-ids` (seat only the new ones),
  `author-line` (the talk entry for a newly arrived blueprint's author), `drawable-changes`
  (what the scene must mount/unmount to catch up).
- `src/pm/bridge.cljc`: `real-id-re` now `bf/id-re`; new `known-build-ids` (estate ids ∪
  valid file ids).
- `scripts/crew_bridge.clj`: `make-real!`'s `:real-build` reads `state/blueprints.edn` at
  request time through `known-build-ids`.
- `scripts/blueprint_add.clj` (new) + `bb.edn`'s `blueprint-add` task: all the I/O, none of
  the logic (that's `pm.blueprint-file`'s). Exit 0 ok, 1 refused, 2 usage.
- `src/pm/core.cljs`: `fetch-blueprints!` (first in every state-poll tick, before
  `fetch-state!`/`fetch-builds!`), `apply-blueprint-file!` (merges, applies real builds,
  seats the new ones once the kit's ground is up, appends each new one's author line),
  `!last-builds`/`!kit` atoms, `ground-at-fn` pulled out of `seat-blueprints!` so both it
  and `apply-blueprint-file!` share it.
- `src/pm/scene.cljs`: `mount!` now remembers `:bp-drawn`; `sync-blueprints!` calls the new
  `sync-blueprint-draw!` first, which mounts/unmounts exactly the blueprint groups
  `bp/drawable-changes` says changed — never a remount, never anything else moved.

## Checked by hand

With `PM_BLUEPRINTS_FILE` pointing at a temp file (never `state/`):

- add → `added bench-by-creek`, exit 0; file holds `{:as-of ... :blueprints [...]}` with
  `:by` set and no other input keys.
- duplicate id → `blueprint-add: id :bench-by-creek is already in state/blueprints.edn`,
  exit 1.
- id already in `resources/estate.edn` → `... is already in the estate`, exit 1.
- invalid blueprint (`:id "bad"`) → `blueprint-add: invalid blueprint: :id must be a
  keyword`, exit 1.
- unreadable/missing input file → `blueprint-add: can't read <path>`, exit 1.
- bad usage (no args) → usage line on stderr, exit 2.
- `--remove bench-by-creek` → `removed bench-by-creek`, exit 0; file's `:blueprints` back
  to `[]`.
- Did not open the compiled page myself this run (no browser here); `.crew/gate.sh` passed
  (`compile app test` → 0 warnings, 1154 existing tests green). Hal/Michael: to see it
  live, run `npx shadow-cljs watch app` with the kit up (`public/kit/` present), load the
  page, then run the example below in another terminal — the ghost should appear within
  one 5 s poll, seated, with no reload, and 💭 should show "Un Automate: drew a blueprint:
  proposed: a bench by the creek. Click it to try it."

## For Hal: the example blueprint

`crew/runs/live-blueprints-v0-20261123-1331/bench-by-creek.edn` — a bench like the
estate's own (`:id :bench-by-creek`), on level grass the builder picked near the creek; the
game seats it (`pm.blueprint/seat`) and its sign says if it's too steep.

```bash
bb blueprint-add un-automate-monde crew/runs/live-blueprints-v0-20261123-1331/bench-by-creek.edn   # → added bench-by-creek
bb blueprint-add --remove bench-by-creek                                                           # → removed bench-by-creek
```

## Known limits (spec §6, not fixed)

- Two agents running `bb blueprint-add` in the same instant can lose one write (read →
  modify → atomic-rename isn't locked across processes).
- Removing a file blueprint that was made real keeps it for the rest of this session (it's
  already in `!world` as `:real`), but it won't come back after a reload — `builds.edn`
  only remembers the id, not the blueprint's shape, and the file it came from is gone.
