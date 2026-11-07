# Review: click-chores

Reviewed against `crew/runs/click-chores-20261106-2034/spec.md` and
`.crew/quests/click-chores.md`, using `git diff` for tracked files, full reads of all new files,
and `stat` mtimes to separate this round's work from earlier uncommitted rounds sharing the tree.

## Scope note (stacked, uncommitted rounds)

This round builds on content-in-game, talk-captions, and two further uncommitted rounds not
under review here that added a "play cache" (`pm.overlay/play-of`/`apply-play`, referenced by
this round's own tests) and a voice-persistence feature (`pm.voice` localStorage key,
`arm-voice!`, the `speak!` 3-arg start callback, the 🔇/🔊 icon pair) — both visible in the
diff but outside this round's boundary table and spec text. I isolated click-chores' own work
by `stat` mtime: `src/pm/overlay.cljs` and `src/pm/plan.cljs` (both "must not touch" for this
round) last changed at 20:14 and 19:03 respectively — before this round's 20:34 start — and
`test/pm/overlay_test.cljs` likewise at 20:14, matching its own §7.1 instruction of **no edit**.
Every file this round was allowed to touch (`world.cljs`, `content.cljs`, `talk.cljs`,
`three.cljs`, `scene.cljs`, `core.cljs`, `ui.cljs`, `estate.edn`, `index.html`, `README.md`, and
the other four test files) has an mtime inside the build window (20:41:39–20:53:06), confirmed
against `git diff`/full reads below. `resources/content/hal-lines.edn`'s mtime (20:31:41) traces
to a real content-pipeline run, `content/runs/hal-lines-20261106-2029/`, that finished at 20:31 —
before this round even started — regenerating the two new Hal events the quest says already
exist there; not a crew edit.

## Conformance

- **Gate verified independently**: `bash .crew/gate.sh` → 0 warnings, `Ran 179 tests containing
  1107 assertions. 0 failures, 0 errors.` Matches `gate-note.md` exactly. Exact `(deftest ` counts
  per file: world 29, plan 34, content 28, talk 28, overlay 32 (unchanged), chores 28 — summing
  to 179, matching the spec's "151 existing + 28 new chores" tally precisely.
- **Boundary table**: builder's files match the allowed set (`world.cljs`, new `chores.cljs`/
  `pick.cljs`, `talk.cljs`, `content.cljs` (only `hal-events` changed — confirmed by reading the
  file: identical to the content-in-game version except the `hal-events` map), `three.cljs` (one
  new fn, `pick-names`), `scene.cljs` (two new getters, `mounted-root`/`current-plan`, nothing
  else touched), `core.cljs`, `index.html` (style rules + the three `li.chore` rules — no markup
  change; `<base>` untouched), `resources/estate.edn` (only `:hud :keys` removed, confirmed by
  diff), one README sentence. Tester's files match exactly: new `chores_test.cljs`, plus the
  E1–E5 edits (confirmed line-by-line against the diff) with **no existing assertion changed**
  (`t03`'s `:hud` value and `t11`'s log shape are updated because the spec changed, not loosened).
- **Undo restores the exact previous done-time and stock**: `pm.world/undo-chore` restores
  `prev-at` to the chore's `:done-at` state field (or the generic `:done` map when the chore has
  none) and adds the recorded `:consumed` delta back onto the state field — both read straight
  from the record, not recomputed. `chores_test.cljs` t10 confirms a full round trip returns the
  mirror's state to byte-identical equality with the pre-click world
  (`(= (get-in U [:mirrors :juniper]) (get-in W [:mirrors :juniper]))`); t13 covers the guard
  that refuses to undo when the chore's done-time no longer matches the record (`:not-current`,
  e.g. a later click or Hal's file changed it since); t14 covers a zero-stock chore (no bale
  invented back) and a chore with no `:done-at` (the generic `:done` map, correctly emptied by
  `dissoc` rather than left with a stale key).
- **Redo only when due**: `pm.chores/tick` calls `w/redo-chore`, which is just `chore-done` with
  `:via :tick` — so it goes through the exact same `due?` gate as a click or key press; when not
  due it falls through to the same `:chore-not-due` branch as `click`, and the box is never
  reset to done (confirmed: `tick` only calls `tk/set-done ... true ...` inside the `:counted`
  branch). `t11` (ticking when due redoes it, new `:id`/`:prev-at` on the fresh record) and `t12`
  (ticking right after a same-day re-click leaves the box unticked, `:hal` gets the
  `:chore-not-due` line, `:log` gains an `:ignored` entry) both pass.
- **A drag is not a click; the kit's camera still works**: `pm.pick/click?` requires
  `dx²+dy² < 36` (strict) and `0 ≤ dt < 400` — `t23` hand-checks the boundary cases (`36` px² and
  `400` ms excluded, `399` ms and `5` px included). `wire-pointer!` in `core.cljs` never calls
  `preventDefault`/`stopPropagation` anywhere in its `pointerdown`/`pointerup`/`pointermove`
  handlers (confirmed by reading the whole function), so the kit's own camera-drag listeners on
  the same canvas/window still see every event untouched.
- **Picking maps meshes to mirrors from data, no mirror names in code**: `pm.pick/pick-mirror`
  builds its lookup from `(:key item) -> (:mirror item)` over the live scene plan — a data
  structure — and never references a mirror id or name as a literal. `grep -ri
  "flora|juniper|stock|bales|worms"` over every touched/new `.cljs` source file
  (`chores.cljs`, `pick.cljs`, `world.cljs`, `talk.cljs`, `content.cljs`, `core.cljs`,
  `three.cljs`, `scene.cljs`, `ui.cljs`) returns nothing; the ids live only in
  `resources/estate.edn` (data) and the test files (explicitly allowed).
- **Checkboxes are real inputs**: `pm.ui/render-chore-row!` creates an actual
  `<input type="checkbox">` (not a styled span/div) inside a `<label>`, sets `.checked` from
  `:done?`, and wires a native `change` event to `on-check` — so Space toggles it natively with
  no extra JS, and it's a real form control for a screen reader/keyboard user. `.time` and
  `.text` (via `tk/chore-label`) are set with `textContent`, matching the no-`innerHTML`-for-
  entry-data rule from the talk-captions round.
- **The chore keys are gone**: `pm.world/key-event` is deleted; `pm.core/on-key!` is deleted;
  the capture-phase keydown listener now only has an Escape branch with no `else` (so every
  other key, including F and G, reaches the kit untouched — nothing calls `on-key!` any more);
  `resources/estate.edn`'s `:hud` is now `{:mirrors [:flora]}` with the `:keys` entry and its
  comment removed. `grep` for `t40`, `g-hay`, `"g"`, `:hud :keys`, `key-event` across
  `world_test.cljs`, `estate.edn`, `world.cljs`, `core.cljs` returns nothing.
- **`resources/content/*.edn` untouched by the crew**: see the scope note above — all three
  files' mtimes predate this round's start, and `hal-lines.edn`'s regeneration is traceable to
  the content pipeline's own run log, not a hand edit.
- **Pure logic is tested**: `pm.chores` (`chore-at-click`, `click`, `untick`, `tick`) and
  `pm.pick` (`click?`, `ndc`, `key-of-name`, `pick-mirror`, `clickable?`) are both pure (no
  atoms, no DOM, no `js/Date` other than the finite-number checks) and covered end to end by the
  28 new tests, hand-traced against the spec's worked values (seeds, clock times, consumed
  deltas) — all pass under the independently re-run gate. `pm.world`'s new `event-id`,
  `last-done-at`, `next-due`, `undo-chore`, `redo-chore` and the counted-branch's new `:id`/
  `:prev-at`/`:consumed` fields are exercised by both `world_test.cljs`'s updated `t11` and
  `chores_test.cljs`'s `t01`–`t03`. `pm.talk`'s new `chore-record`, `add-chore`, `entry`,
  `set-done`, `chore-label`, and its updated `save-text`/`restore`/`drop-repeats`/`last-says`
  (chore entries pass through undeduplicated and don't feed `:last-say`) are covered by `t18`–
  `t22`, including a garbage-restore case that drops an invalid chore record, a non-boolean
  `done?`, and a bare string `:chore`.

## Findings

None. Every item the task called out to check traces to a specific function, diff hunk, and
passing test, the boundary table was respected (confirmed independently via mtime, not just
trusting `git status`), and the gate's numbers match exactly. One thing worth a second look
(not a defect): `public/index.html`'s `#voice-toggle` button already reads `🔇 voice off` in the
static markup, which matches `pm.ui/set-voice-button!`'s off-state text (🔇) — this icon pair
predates this round (the voice-persistence round introduced it in place of talk-captions'
original single-icon 🔊/🔊 pair); click-chores only added the three `li.chore` style rules to
this file, not the button text, so there's nothing for this round to fix.
