# Review — round `scored-loop-20261121-1816`

## Scope note
There is no diff to review. This round stopped at the `spec` step:
un-architecte hit an interactive Bash permission prompt (a `bb -e '...'` call
to hand-verify the overall-score formula) and never got a yes/no answer, so
it timed out "blocked" after 3282s against the round's `timeoutMinutes: 50`.
`crew/runs/scored-loop-20261121-1816/spec.md` was never written, so `build`,
`gate`, `run`, `write-up` and `review` never ran. `diff.txt` for this round
is correspondingly non-empty only for unrelated files (see below) — no
`content/pipeline.clj`, no bb test file, no `docs/assignment-5-loop.md`.

Earlier same-day dir `crew/runs/scored-loop-20261121-1633/` is a **dry-run**
of the round tooling, not a real round: every `replies/*.txt` in it is
literally `[dry-run] would prompt <agent>: ...` with no agent output, and the
whole thing completed in ~3s (impossible for a step that includes
`bb content hal-lines`, documented as a ~10-minute call). Its `spec.md`,
`gate-note.md`, `run-note.md`, `review.md` and `docs/assignment-5-loop.md`
don't exist on disk despite log.md's "✓" markers — they were never real
files, just the dry-run's simulated checklist. Nothing there is reviewable
either.

The working-tree edits visible right now (`src/pm/core.cljs`,
`src/pm/talk.cljs`, `.crew/tmp_badge.js`, `.crew/tmp_brook.js`,
`.crew/tmp_brook2.js`) are not this round's work: the round's only active
member during the `spec` step is un-architecte, and its own transcript shows
it only reading files and running one `bb -e` score-check that stalled on
the approval prompt — it never touched `src/pm/`. `tmp_brook.js`/
`tmp_brook2.js` predate the round's own start (17:05–17:06 vs. round start
18:16) and `talk.cljs`/`core.cljs`/`tmp_badge.js` fall inside the window but
match no step un-architecte was assigned; most likely concurrent human
debugging in the shared tree (the prior commit `ef54d6c` was already about
audio/brook work). Excluded from this review; flagging for the manager to
confirm and either stage or discard before the next round starts clean.

## Findings
1. `.crew/rounds/scored-loop.json` step `spec`, un-architecte's `bb -e`
   score-verification call — blocks on an interactive y/n permission prompt
   with no auto-approve, burning the full 50-minute timeout for zero
   progress. Fix: either run the gate/spec step's shell calls with
   `--dangerously-skip-permissions`-equivalent auto-approve wired in for
   this crew's sandboxed tree, or have the architect's role card use a
   non-interactive verification path (e.g. write the `bb` snippet to a
   scratch file and `bb <file>` instead of `bb -e`) so it never hits the
   prompt.
2. `crew/runs/scored-loop-20261121-1633/log.md` records five `✓` file
   markers (spec.md, gate-note.md, run-note.md, docs/assignment-5-loop.md,
   review.md) for a run whose own replies are all `[dry-run]` stubs and
   where none of those files exist on disk — the dry-run's log format is
   indistinguishable from a real PASS at a glance. Fix: prefix dry-run log
   headers/results with `DRY-RUN` so a manager skimming `crew/runs/` can't
   mistake it for a completed round (this one nearly reads as an already-
   reviewed, passing round).
3. Stray uncommitted edits to `src/pm/core.cljs`, `src/pm/talk.cljs` and
   three `.crew/tmp_*.js` scratch files sit in the shared tree with no
   round or commit claiming them — confirm with Hal whether these are
   in-progress human work to keep or debris to `git checkout`/`rm` before
   starting round `scored-loop` again, so the next spec step's "working
   tree" baseline is clean.

## Verdict
Nothing built, so nothing to score against purity/naming/idiom/spec-match
checks. The round needs a fresh `spec`→`build`→`gate` pass once finding 1
is fixed; this review's three findings are process/hygiene, not code.
