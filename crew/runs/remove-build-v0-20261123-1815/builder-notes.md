# Builder notes — remove a real build, try-first v0 (TASKS T-15)

## What was built

- `resources/estate.edn`: `:blueprint-text` only — eight new keys (`:remove` `:close`
  `:remove-it` `:keep` `:removing` `:removing-wait` `:removed` `:no-bridge-remove`),
  wording exactly as spec §2.1.
- `src/pm/blueprint.cljs`: `statuses` gains `:removing`. `sign-text` has a new
  first-matching branch for `:removing`. `built-plan` draws a `:removing` item as a
  removal ghost (`:ghost? true :tint :removal`) with a sign; `drawable-changes` and
  `merge-file`'s kept-even-when-dropped set both gain `:removing`. `apply-real-builds`
  now also reads `:removed` (wins over `:real`; a `:removing` build stays `:removing`
  through the 5 s re-apply). New: `start-removal`, `keep-it`, `finish-removal`,
  `fail-removal`, `open-card`, `close-card`, `card-of`, `card` (the generalised T-14
  card: `:trial` unchanged via `trial-card`, `:real` → Remove…/Close, `:removing` →
  Remove it/Keep it).
- `src/pm/bridge.cljc`: `validate`'s `/real` branch takes a `"remove"` action →
  `{:ok true :action :remove-build :id ...}` (same id rule as `"build"`). New
  `remove-real-build` (drops `:real`, adds to `:removed`, refuses an unknown/not-real/
  already-removed id). `add-real-build` also refuses an id in `:removed`, and keeps
  `:removed` in its output when non-empty.
- `src/pm/crew.cljs`: new `remove-build-body`.
- `src/pm/blueprint-file.cljc`: new `removed-ids`. `add` gains a 6-arity taking
  `removed-ids` (5-arity = `#{}`); refuses a removed id between the estate check and
  the already-in-file check.
- `scripts/crew_bridge.clj`: `make-real!` gets a `:remove-build` case
  (`b/remove-real-build` → `write-edn!` → `{:ok true :reason "removed"}` /
  `{:ok false :reason "not a real build"}` / `"not saved"`); `:remove-build` added to
  the set of actions routed there. `state/blueprints.edn` is never touched.
- `scripts/blueprint_add.clj`: reads `state/builds.edn` (`PM_BUILDS_FILE` override,
  `#{}` if missing/unreadable) and passes its `removed-ids` as the 6th arg to `bf/add`.
- `src/pm/three.cljs`: `ghost!` gains an optional `tint` arg (`:proposed` default) and
  a `ghost-tints` map (`:proposed` unchanged blue, `:removal` warm red/edge); same
  opacity/edges/userData/clone behaviour either way.
- `src/pm/scene.cljs`: `mount-item!` passes `(:tint item)` through to `ghost!`.
  `sync-blueprints!`'s status loop: new `:removing` branch (unlight, red-tint ghost,
  builds or reuses the sign sprite, shows it with the removal text); `:rejected` now
  unlights the glow before removing the group (a removed, previously-real lamp stops
  shining); `:real` (Keep it / a failed removal) unchanged.
- `src/pm/ui.cljs`: no structural change — added `#pm-trial-card
  .trial-remove-it{min-width:118px}` beside the existing `.trial-real` rule, so
  "Removing…" doesn't make Keep it slide.
- `src/pm/core.cljs`: `trial-card!` now calls `bp/card` (not `bp/trial-card`
  directly); its `on-action` grows `:remove`/`:close`/`:remove-it`/`:keep` cases.
  `on-blueprint-click!` ignores clicks entirely while `@!saving`; `:pending`/`:approved`
  close any open card first; `:real` opens the card (`bp/open-card`); `:removing` is a
  no-op (only the card's own buttons act on it). New `remove-click!` (guards
  `@!saving`/status, posts `/real` `remove` through the bridge, `finish-removal` on
  success, `fail-removal` with the reason on failure or no bridge).

## Checked by hand

- Ran the gate (`.crew/gate.sh`): **0 warnings**; 1250 tests, 7491 assertions (the
  tester's `pm.remove-build-test`, already landed, is green along with the rest).
- Hand-traced every case in spec §5 against the final code before and after fixing one
  bug the gate caught: `distinct` on a `PersistentHashSet` throws in both the cljs test
  build and babashka (`nth not supported on this type`) — `remove-real-build` and
  `add-real-build`'s `:removed` output were calling `distinct` on sets that were
  already distinct by construction; dropped those two `distinct` calls (`sort` alone
  on a set works fine) and the gate went green.
- Did not open the compiled page myself this run (no browser here; never started the
  bridge or wrote to `state/`, per the quest's rules). Hal/Michael, headless or by
  hand, once a build is real (e.g. `state/builds.edn` `:real [:lamp-post-by-canal]`):
  - click the real build (`[:blueprint :lamp-post-by-canal]`'s group) → the card shows
    **Remove…** (`#pm-trial-card [data-action="remove"]`, `.trial-remove`) and
    **Close** (`[data-action="close"]`, `.trial-close`)
  - Remove… → the item turns into a warm-red removal ghost, its sign reads "will be
    removed", and the card now shows **Remove it**
    (`[data-action="remove-it"]`, `.trial-remove-it`; reads "Removing…" and is
    `:disabled` while a request is in flight) and **Keep it**
    (`[data-action="keep"]`, `.trial-keep`)
  - Keep it → back to real exactly as it was, card closed
  - Remove it, with no bridge running (or a refusal) → back to real, card still open to
    retry, caption "Start the bridge (bb crew-bridge) to remove it."
  - Remove it, with `bb crew-bridge` running → the build vanishes for good; reload
    still shows it gone (`state/builds.edn`'s `:removed`)
  - `bb blueprint-add <member> <file>` with that same id, afterwards → refused:
    "id :x was removed; pick a new id" (exit 1)

## Known limits (spec §7, not fixed)

Removing anything that isn't a blueprint (mailbox, creatures, kit props); moving or
editing a build; a bot-drafted removal; undoing a removal (add it again with a new
id) — all unchanged/out of scope, as the spec says.
