# Review: feed-flora-polish round

Quest: `.crew/quests/feed-flora-polish.md` (apply the 4 findings from
`crew/runs/feed-flora-20261031-1250/review.md`, nothing else; behavior and
the 32 tests must be unchanged).

Diffed against the previous round's saved copies in
`crew/runs/feed-flora-20261031-1250/output/`:
`diff -u .../output/src/pm/world.cljs src/pm/world.cljs`,
`diff -u .../output/test/pm/world_test.cljs test/pm/world_test.cljs`,
`diff -u .../output/resources/estate.edn resources/estate.edn` (empty — unchanged).

Verified independently: `npx shadow-cljs compile app` → 0 warnings;
`npx shadow-cljs compile test` + `node out/test.js` → 32 tests, 121
assertions, 0 failures. Matches `gate-note.md`/`gate-1.txt`.

## Scope

Only `src/pm/world.cljs` and `test/pm/world_test.cljs` changed inside this
round's window (mtimes 13:08:xx, after the `build` step started 13:07:55).
`src/pm/core.cljs` (mtime 12:59:26) and `resources/estate.edn` (mtime
12:56:51) predate this round and are byte-identical to the previous round's
output — not touched. `.gitignore`/`AGENTS.md`/`docs/GDD.md`/`shadow-cljs.edn`
diffs also predate this round (unchanged since the last review). Builder
touched only `src/pm/world.cljs`; tester touched only
`test/pm/world_test.cljs` — boundary respected.

## Findings vs. the four assigned items

1. `src/pm/world.cljs` `due?`: `last` renamed to `last-at` throughout its
   `let` (both the binding and the two uses) — no more `clojure.core/last`
   shadow. Matches finding 1.
2. `src/pm/world.cljs` `chore-done`: destructure keeps `chore` as the raw id
   from the event, then binds `chore-id chore` (the id) and rebinds `chore`
   to the looked-up chore map via `chore-def`. This satisfies the letter of
   finding 2 (a `chore-id` name now exists) but `chore` is still rebound
   from id to chore-map one line later, which was the actual readability
   complaint — a cleaner fix would have been `chore-id` for the id and
   `chore` only ever bound once, e.g. drop the original destructured `chore`
   and go straight to `chore-id` then `chore (chore-def ... chore-id)` (one
   rebind, not two names each rebound once). Not a regression, just leaves
   the finding only partially addressed.
3. `test/pm/world_test.cljs`: `testing` dropped from the `:refer` list;
   `:refer [deftest is]` now. No test logic touched. Matches finding 3.
4. `src/pm/world.cljs`: private `log` renamed to `log-event`, all 5 call
   sites in `chore-done` updated consistently. Matches finding 4.

## Other checks

- No three.js, `js/Date`, atoms, or other side effects introduced into
  `pm.world`.
- No mirror-specific name (`flora`, `juniper`, `axolotl`, `worm`, `hay`,
  `horse`) appears in `src/` outside `resources/estate.edn` and the test
  file (re-checked, unchanged from the previous round).
- Behavior unchanged: hand-traced `due?` and `chore-done` logic is
  line-for-line the same except the renames; no test assertion values
  changed, only the one `:refer` edit — all 32 `deftest` names and bodies
  are otherwise identical to the pre-polish file.

## Ranked findings (none blocking)

1. `src/pm/world.cljs:64-66` — `chore-done`'s destructure still rebinds one
   name (`chore`) from a keyword id to a chore map two lines apart, which is
   the same shape finding 2 flagged; consider binding `chore-id` straight
   off the event and never reusing `chore` for two different shapes, e.g.
   `(let [{:keys [mirror chore-id at]} event, chore (chore-def world mirror chore-id)] ...)`.

No findings on purity, naming leakage, scope, or spec/test conformance —
clear to merge.
