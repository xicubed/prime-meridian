# Review: real-state-v1-cache

Reviewed against `crew/runs/real-state-v1-cache-20261106-1749/spec.md` and
`.crew/quests/real-state-v1-cache.md`. This round stacks on the uncommitted `real-state-v0`
round (same shared working tree), so a plain `git diff`/`git status` mixes both rounds for
`README.md`/`src/pm/core.cljs` (both already modified before this round's `build` step per
`log.md`'s `spec` step, 17:49:21). To isolate this round's actual work I diffed the current
`src/pm/core.cljs` and `src/pm/overlay.cljs` against the exact v0 content I verified in last
round's review (saved to scratch and `diff -u`'d), and `git diff -- README.md` separately
since README had no pre-v1 uncommitted baseline conflict in the visible hunk.

## Scope note

`.crew/crew.json` and `.gitignore` are pre-existing round-setup diffs (added before `build`,
confirmed via `log.md`), not this round's output — excluded, same as last round's review.

## Conformance

- **Gate verified independently**: `bash .crew/gate.sh` → 0 warnings (`:app` and `:test`),
  `Ran 97 tests containing 488 assertions. 0 failures, 0 errors.` Matches `gate-note.md`
  exactly. 97 = 84 (v0) + 13 new (t19–t31), confirming no existing test was touched.
- **t01–t18 byte-identical**: diffed the current `test/pm/overlay_test.cljs` lines 1–177
  against the v0 file content — identical, as the boundary table requires ("append only").
- **Boundary table**: builder touched exactly `src/pm/overlay.cljs`, `src/pm/core.cljs`,
  and one added sentence in `README.md`'s existing state-file paragraph (confirmed by
  `git diff -- README.md`: a single appended sentence, no other line moved). Tester touched
  only `test/pm/overlay_test.cljs`, appending t19–t31. `world.cljs`, `plan.cljs`,
  `scene.cljs`, `three.cljs`, `estate.edn` untouched.
- **Purity**: `pm.overlay`'s new code (`merge-entries`, `cache-of`, `apply-cache`, 2-arity
  `source-tag`) adds no new requires and no `js/fetch`/`js/Date`/atom use — only the existing
  `js/isFinite` predicate. All storage/watch side effects landed in `pm.core` only
  (`read-cache`, `write-cache!`, `restore-cache!`, `on-world-change!`), matching "storage
  access lives only in `pm.core`; `pm.overlay` stays pure."
- **No mirror named in code**: `grep -ri "flora|juniper|stock|bales|worms"` over
  `src/pm/overlay.cljs` and `src/pm/core.cljs` returns nothing.
- **Can't invent mirrors/keys**: `apply-cache` routes through the same `merge-entries` →
  `accept?` path as `apply-file` (unchanged `accept?`), so the file's invention guard
  (unknown mirror id, unknown field, wrong type) applies identically to cache restores —
  confirmed by t26 (garbage caches incl. unknown `:rex` mirror, invented fields) and t27
  (a field the sample lacks is dropped even after a cache round-trip, case `:pond`).
- **Precedence, hand-traced**:
  - *Same `:as-of` keeps local changes*: `apply-file`'s branch 3 changed from `world` (v0) to
    `(dissoc world :state-cached?)` — no merge happens at all on that path, so a cached
    mirror's locally-advanced fields (e.g. stock 36 after an F press) are never touched by a
    same-`:as-of` file. Matches t23 exactly (stock stays 36, `:state-cached?` gone, HUD flips
    to `(Hal)`).
  - *Different `:as-of` wins*: falls through to the normal merge branch, now merging onto
    the cache-restored world (not the sample), so file fields overwrite cached ones and
    fields the file doesn't list keep their cached values (`update-in merge`, not
    replacement) — matches t24's partial-file case (`last-fed` stays the cached
    `1794186000000` when the new file only sends `:stock`).
  - *Fresh-world-only cache*: `apply-cache` checks `(some? (:state-as-of world))` — `0` counts
    as "not fresh" (matches t28's `(assoc W :state-as-of 0)` case, since `some?` is true for
    `0`, only `nil` is fresh), so a cache can never re-apply over a file or a prior cache.
  - *A no-op cache doesn't even stamp `:as-of`*: unlike `apply-file` (which always records
    `:as-of` even with nothing accepted), `apply-cache` only touches the world when
    `merge-entries`'s `changed?` is true — matches the spec's explicit "Not `changed?` →
    `world`" and t26's garbage-cache cases, which assert `(o/apply-cache W c) = W` (not
    `(assoc W :state-as-of ...)`).
- **Ordering avoids a spurious self-write**: `init!` does `restore-cache!` *before*
  `(add-watch !world ::state-cache on-world-change!)`, so the `swap!` inside `restore-cache!`
  itself cannot trigger `on-world-change!` — the just-read cache text is never immediately
  re-serialized and written back, even if `pr-str (cache-of ...)` would format slightly
  differently than the stored text. This was worth checking for (a reformat-and-rewrite loop
  on every load) and the ordering rules it out.
- **Storage failures silent**: `read-cache`/`write-cache!` both wrap the only
  `js/localStorage` calls in `try/catch :default _ nil` with no logging; `restore-cache!`'s
  `when-let` swallows a `nil` read (missing key or threw) the same way `fetch-state!` already
  swallows a missing file.
- **`on-world-change!` cheap no-op path**: returns at once when `:mirrors` is `identical?`
  and `:state-as-of` is `=` between old/new — true on every plain `:t` tick, so the watch
  costs nothing on the render loop, matching the spec note.
- **README**: `git diff -- README.md` shows exactly one sentence appended to the existing
  state-file paragraph (localStorage key name, cached-tag behavior, "a newer file always
  wins"), nothing else in the file touched.

## Findings

None. Every decision in the spec (same-`:as-of` precedence, different-`:as-of` precedence,
fresh-world-only cache, no-op cache doesn't stamp `:as-of`, cache can't invent fields/mirrors,
silent storage failures, restore-before-watch ordering) traces to a specific line in the diff
and a specific passing test, and the independently re-run gate matches the claimed numbers.
