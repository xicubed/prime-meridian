# Review: content-in-game

Reviewed against `crew/runs/content-in-game-20261106-1852/spec.md` and
`.crew/quests/content-in-game.md`, using `git diff` for the 8 modified tracked files and
reading all new files `git status` lists.

## Scope note

`resources/content/*.edn`, `content/` (the Assignment-4 generator pipeline and its run logs)
and `bb.edn` are untracked but pre-date this round: `log.md`'s `spec` step (18:52:15) already
shows 114 new files in the working tree before `build` even started, and
`stat resources/content/*.edn` shows mtimes of 18:46–18:51, all before the round's 18:52
start. `git diff --stat` confirms exactly 8 tracked files touched, matching 5 builder files +
3 tester files from the boundary table — no drive-by edits outside it.

## Conformance

- **Gate verified independently**: `bash .crew/gate.sh` → 0 warnings (`:app`, `:test`),
  `Ran 125 tests containing 733 assertions. 0 failures, 0 errors.` Matches `gate-note.md`
  exactly, and matches the spec's predicted total (97 existing + 28 new in `content-test`).
- **`resources/content/*.edn` untouched**: confirmed by mtime (all before round start) and by
  `git diff --stat` showing no tracked changes to them (they aren't tracked at all, and their
  content/mtimes didn't move during the build step).
- **Boundary table**: builder touched exactly `src/pm/content.cljs` (new), `src/pm/world.cljs`,
  `src/pm/plan.cljs`, `src/pm/core.cljs`, `resources/estate.edn`, one sentence in `README.md`.
  Tester touched exactly `test/pm/content_test.cljs` (new), `test/pm/fixtures.cljs` (new), and
  the E1–E3/E2b setup lines (requires + the `W`/`WP` bindings) in `world_test.cljs`,
  `plan_test.cljs`, `overlay_test.cljs` — no existing assertion line changed in any of the
  three (confirmed by diff: only the `:require` and one `def`/`let` binding per file moved).
- **`pm.content` purity and shape**: requires only `cljs.reader`, `clojure.string`,
  `shadow.resource` — no `pm.*` requires, no atoms, no `js/Date`. Every function
  (`parse`, `hal-lines`/`mirror-lines`/`sample-inbox`, `hal-events`, `hal-ms`, `day-number`,
  `name-code`, `fill`, `placeholders`, `line`, `used-lines`, `missing-lines`, `missing-hal`,
  `hal-say`, `hal-on-chore`, `hal-text`, `hal-segment`, `mail-counts`, `mail-line`,
  `hud-segments`) hand-traced against its spec entry and matches signature-for-signature,
  including edge handling (`line`'s negative-seed `mod` wrap, `day-number`'s
  `js/isFinite` guard against `NaN`/`Infinity`, `hal-say`'s unchanged-world return on a
  non-number `now` or a non-string resolved line).
- **No mirror named in code**: `grep -ri "flora|juniper|stock|bales|worms"` over
  `src/pm/content.cljs`, `src/pm/world.cljs`, `src/pm/plan.cljs`, `src/pm/core.cljs` returns
  nothing. `resources/estate.edn` (data, not code) is where the mirror ids and the new
  `:hungry`/`:low`/`:stock-sign` keywords live, exactly per spec.
- **Line picking stable per day, no per-frame flicker**: `w/line-for`'s seed is
  `(+ (day-number now) (name-code mirror-id))`, with no `js/Date.now` or other wall-clock read
  inside `pm.world`/`pm.content` itself — `now` is always the caller's argument. `t16-no-per-
  frame-flicker` exercises this directly: 60 calls to `texts` 16 ms apart (one render frame
  each) are all equal, `label-changes` across one frame is `{}`, and only a day-boundary jump
  (`DAY-END` → `NEXT`) produces a change. Re-ran the gate and this test passes.
- **String rules still work**: `markers`' rewrite resolves each `:say`/`:sign` value through
  `line-for`, which passes a `string?` straight to `c/fill` (unchanged behaviour) and only
  treats a `keyword?` as a table lookup — confirmed by `t17-string-and-keyword-rules-mixed`
  on a generic, code-unnamed `:fern` mirror mixing a literal string rule, a keyword rule
  resolved from a pinned table, a keyword rule that resolves to nothing (dropped, not
  crashing), and a rule with one string + one keyword key where only the keyword drops.
- **Coverage would catch a regenerated file missing a key**: `missing-lines`/`missing-hal`
  are exercised directly against *simulated breakage* in `t28-coverage-catches-a-broken-
  regeneration` (`dissoc`'d key, emptied variant list, wrong-type Hal line) and each reports
  exactly the broken path/event. `t19`/`t20` additionally assert `missing-lines`/`missing-hal`
  are empty against *today's real* `mirror-lines.edn`/`hal-lines.edn`, and `t19`'s third
  assertion walks every variant string at every used path and checks its `{placeholder}`
  set is a subset of that mirror's actual state keys — so a regenerated file with a typo'd
  placeholder (e.g. `{worms}` instead of `{stock}`) fails even though the key itself exists.
  Reran the gate; all of t19/t20/t28 pass against the real files.
- **`pm.world`/`pm.plan` changes match spec exactly**: `fill` now delegates to `c/fill`
  (confirmed: a non-string truthy `s` returns `nil` instead of throwing, per spec and
  `t02-fill`'s `(c/fill 7 {}) → nil`); `line-for` is the spec's code block verbatim;
  `markers` resolves `:say`/`:sign` through `line-for` and drops both unresolved keys and
  emptied markers; `status-line`, `due?`, `chore-done`, `key-event`, `load-estate` are
  untouched (diff ends exactly where `status-line`'s declaration starts, no body change).
  `plan.cljs`'s `scene-plan` resolves a keyword `:idle` through `w/line-for` and passes
  everything else through unchanged; `label-text`'s own signature/behaviour is untouched.
- **`pm.core` changes match spec exactly**: `jack-in!` is called in `start-kit!` just before
  its `requestAnimationFrame` and in `start-scaffold!`'s renderer callback just before its
  own, not in `init!`; `on-key!` wraps `content/hal-on-chore` around `world/chore-done` and
  keeps the existing console log line; `hud-mirrors-suffix` inserts
  `content/hud-segments` between the mirror-line loop and the scene summary, in the order the
  spec's example string shows; `reload!` is untouched (no greet on hot reload, per spec).
- **`resources/estate.edn`**: only Flora's and Juniper's `:say`/`:sign` rule values and Flora's
  jar `:idle` changed from strings to the specified keywords (`:hungry`, `:low`,
  `:stock-sign`), plus the one required comment line above `:mirrors`. Juniper has no `:idle`
  item (unchanged, per spec). Nothing else in the file moved.
- **README**: `git diff` shows exactly one sentence appended, naming `resources/content/*.edn`,
  `bb content`, and "never hand-edited" — nothing else in the file touched.

## Findings

None. Every scope item (pure content namespace, keyword rule resolution, per-day stable
seeding, Hal's HUD lines, the mailbox readout, and the coverage tests) traces to a specific
diff hunk and a specific passing test, the test-file edits are setup-only as the boundary
table requires, the generated content files are verifiably untouched, and the independently
re-run gate matches the claimed numbers exactly.
