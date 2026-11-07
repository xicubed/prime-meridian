# Review: request-todos

Reviewed against `crew/runs/request-todos-20261106-2102/spec.md` and
`.crew/quests/request-todos.md`, using `git diff` for tracked files, full reads of all new/
heavily-changed files, and `stat` mtimes to separate this round's work from the four earlier
uncommitted rounds sharing the tree (content-in-game, talk-captions, and the two click-chores-
adjacent rounds that added the play cache and voice persistence, none of which this round's
spec mentions).

## Scope note

Both the builder and tester replies are logged as `blocked` in `log.md` (an approval prompt),
but the working tree shows the full intended diff after that step and the gate passes with
exactly the spec's predicted totals — so, as with earlier "blocked but the deliverable is
complete" cases, I verified the actual file contents rather than trusting the status label.
`stat` confirms the boundary table was respected: `src/pm/overlay.cljs` (20:14), `plan.cljs`
(19:03), `content.cljs`, `three.cljs`, `pick.cljs`, `scene.cljs` (all from click-chores'
20:4x window) are untouched — their mtimes predate this round's 21:09:54 build start.
`test/pm/overlay_test.cljs`, `plan_test.cljs`, `world_test.cljs`, `content_test.cljs`, and
`test/pm/fixtures.cljs` likewise carry only earlier rounds' mtimes — none of them are in this
round's E1/E2 edit list, and none show a mtime inside this round's build window. Every file the
boundary table allows this round to touch (`world.cljs`, `talk.cljs`, `chores.cljs`, `ui.cljs`,
`core.cljs`, `estate.edn`, `index.html`, `README.md`, and the three touched/new test files) has
an mtime inside or just after the 21:09:54–21:19:59 build window.

## Conformance

- **Gate verified independently**: `bash .crew/gate.sh` → 0 warnings, `Ran 202 tests containing
  1240 assertions. 0 failures, 0 errors.` Matches `gate-note.md` and the spec's predicted total
  (179 existing + 23 new in `requests-test`) exactly.
- **Boundary table**: confirmed via mtime above; `resources/estate.edn`'s diff shows only the
  new `:past "fed"` on each of the two chores (nothing else in the file moved beyond what
  click-chores already had); `public/index.html` gained exactly one new style rule
  (`#talk-list li.request .text`), no markup change; `README.md` gained exactly one sentence.
- **Only creature lines from a due rule get a box**: `pm.world/asks` requires all three of a
  rule's `:when` being literally `[:due? c]`, the rule holding at `now`, and its `:say`
  resolving through `line-for` to a non-nil string — a `:sign`-only rule, an unresolved
  keyword `:say`, or a `:when` that isn't `[:due? _]` are all excluded (confirmed by `t01`'s
  generic `:x` mirror fixture mixing all three cases, and duplicate `:due? :c` rules
  correctly collapsing to one entry via `distinct`). `observe`'s mirror loop only sets
  `:asks c` when `c = (first (w/asks ...))` is non-nil; a plain bubble with no due rule behind
  it is appended exactly as before, with no `:asks`/`:done?` fields (`t03`).
- **tick/untick map to do/undo/redo exactly once each**: `pm.chores/click` now calls
  `tk/link-chore` (not `add-chore`) on a counted chore, which ticks the *existing* open
  request for `[mirror chore]` in place (`set-done`, no new entry) and only falls back to a
  brand-new chore line when nothing was open for it — `t06`/`t07` cover both paths, and the
  rewritten `t12` in `chores_test.cljs` (per spec's E2) specifically proves a click-after-
  untick re-ticks the *same* box (`count` stays 1) rather than adding a second one, closing
  the exact duplicate-box bug this round exists to fix. `untick` now applies to any `tk/box?`
  entry and, after undoing, resets `:last-say` to the restored bubble text so `observe` won't
  immediately re-log it as a new line (`t10`'s "no second 'Hay, please?'" assertion). `tick`
  redoes via the kept `:chore` record when one exists, or does the chore fresh with `:via
  :chat` when the box was an un-answered request — both are exercised (`t08` chat-via, `t11`
  tick-via redo) and both still gate on `due?` through `chore-done`/`redo-chore`, so an
  attempt on a not-yet-due chore leaves the box unticked (`t09`).
- **Old histories restore safely**: `clean-restored-entry`'s `:asks`-keyword branch
  independently re-validates `:chore` via `chore-record` and forces `:done?` to `(and
  (true? (:done? e)) (some? cleaned-chore))` — so a ticked request with no record, a garbage
  record, or a non-boolean `:done?` all land unticked rather than wrongly ticked (`t19`'s five
  hand-checked cases, including a string `:asks` dropping all three fields and reverting to a
  plain line). A request-free history (recorded before this round) gets a box only when
  `observe`'s `adopt` path finds a *standing, unboxed* line whose text still matches today's
  request (`t21`), and explicitly not when the creature moved on to a different line or isn't
  asking any more (`t22`); `drop-repeats` passes every box through untracked so two same-text
  request boxes a minute apart both survive a restore (`t20`).
- **No mirror names in code**: `grep -ri "flora|juniper|stock|bales|worms"` over
  `src/pm/world.cljs`, `talk.cljs`, `chores.cljs`, `ui.cljs`, `core.cljs` returns nothing.
  `pm.world/asks` and `pm.talk`'s new functions all work from rule/entry shape, never a
  literal id.
- **Checkboxes are real inputs**: `pm.ui/render-chore-row!` is unchanged in this respect from
  click-chores — still a real `<input type="checkbox">` in a `<label>`, now with
  `box-class` choosing `"chore request"` (never struck through) vs. `"chore"`/`"chore
  undone"` depending on `(keyword? (:asks entry))`, and the row's text now comes from the
  passed-in `label-of` (`pm.core`'s `talk/box-label` + `world/chore-past` composition) instead
  of the fixed `chore-label`.
- **`resources/content/*.edn` untouched**: mtimes (20:31–20:51) are unchanged from the prior
  rounds' reviews and still predate this round's start; this round's boundary table forbids
  touching them and the builder's file list (confirmed by mtime) never includes them.
- **Pure logic is tested**: `pm.world/asks` and `chore-past`, and `pm.talk`'s new `box?`,
  `box-chore`, `open-box`, `link-chore`, `box-label`, plus the updated `append`, `set-done`,
  `save-text`, `restore`/`clean-restored-entry`/`valid-restored-entry?`, `drop-repeats`, and
  the rewritten `observe` mirror loop (`strip-unasked`, `supersede`, `adopt`) are all pure (no
  atoms, no DOM, no `js/Date` beyond finite-number guards) and covered end to end by the 23
  new tests, hand-traced against the spec's worked values — all pass under the independently
  re-run gate, alongside the E1 (`talk_test.cljs`) and E2 (`chores_test.cljs`) edits, which I
  confirmed touch only the lines the spec names (the `E2`/`E3` fixtures and `t09`/`t10`'s
  expected maps gaining `:asks`/`:done? false`; `t12`'s full rewrite; `t20`'s `:last-say`
  addition) with no other assertion loosened.

## Findings

None. Every item the task called out traces to a specific function, diff hunk, and passing
test; the boundary table held (verified independently via mtime, not just `git status`); and
the gate's numbers match exactly.
