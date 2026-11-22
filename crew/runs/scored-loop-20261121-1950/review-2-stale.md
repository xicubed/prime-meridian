# Review — `docs/assignment-5-loop.md` vs. the scored-loop rubric (round `scored-loop-20261121-1950`)

**This review supersedes the one written earlier for the same path** (before Hal's
Refiner fix, commit `e9ea7b9`, and the second real run). That prior review's findings
are kept as an appendix below since they're what prompted the fix. This pass re-checks
the current `docs/assignment-5-loop.md` against the current
`crew/runs/scored-loop-20261121-1950/run-note.md` from scratch.

## Critical: the write-up is stale and describes the wrong run

`run-note.md` now points at a **second, passing** run:

```
draft 1: 6.8 (A 7 B 8 C 8 D 4 S 6)
draft 2: 7.5 (A 7 B 9 C 8 D 5 S 8)
draft 3: 8.3 (A 8 B 9 C 9 D 7 S 8)
Final Score: 8.3 — PASSED — Installed resources/content/hal-lines.edn (draft 3, 8.3)
```

— run directory `content/runs/hal-lines-20261121-2011/`, Refiner now running on
`CRITIC_MODEL` (opus) per the fix. `resources/content/hal-lines.edn` exists on disk
(mtime 20:20), confirming the install really happened.

But `docs/assignment-5-loop.md` on disk right now (mtime 20:04, **before** the second
run even started at 20:10) still describes the **first, failed** run
(`content/runs/hal-lines-20261121-1949/`: 4 drafts, 6.9→7.9→7.7→7.9, did not pass,
nothing installed) — every trajectory number, every quoted reason, every link in the
current file is about a run that is no longer the one named in `run-note.md`.

`log.md`'s own timeline explains why: le-docteur was re-dispatched for `write-up` at
20:20:39 (after the second `run` step finished), replied `done`, and the log even
records "✓ docs/assignment-5-loop.md" — but le-docteur's own transcript
(`replies/write-up-le-docteur.txt`) shows it treated the re-dispatch as a repeat of
already-finished work ("This is the same task I just finished... Nothing further to
do") and skipped reading the new `run-note.md`/run directory entirely. The file's
mtime never moved. A `✓` in `log.md` here just means "a file with that name exists,"
not "it was rewritten this step" — it was not.

**This is not a quoting/accuracy problem in the prose, it's that the prose is about a
different, superseded result than the one the round's own `run-note.md` names.** Every
rubric point below has to be re-scored twice: once for whether the CURRENT TEXT is
internally honest about the (wrong) run it describes, and once for whether it's actually
what the grader needs (it is not, because it's the wrong run).

## Rubric scores (scoring the write-up as it exists on disk)

**1. Style guide pulled from the GDD, not invented — 9/10** (unaffected by the stale
run: STYLE.md's provenance section doesn't depend on which run it is). A3/C11/D16
citations verified against `docs/GDD.md` §2.5/§2.6/§2.1 again — still accurate.

**2. Specificity — 9/10 for the text as written**, but it is now evidence from a
withdrawn draft: the "generic vs. specific" quotes (`"That's handled..."`,
`"Can't do this without {program} yet."`) are real quotes from the *first* run's
scores.edn, not the run that actually got installed. Not wrong, just not current.

**3. Evaluator outputs a score and reason, never binary — 10/10.** Confirmed again in
the NEW run's `scores.edn`: zero occurrences of `:verdict`, same `{:score :reason
:violations}` shape per criterion in all 3 drafts. Still true of the current code and
would be true of either run's write-up.

**4. The loop fixed violations with nobody intervening — 3/10, this is where the staleness
actually bites.** The real, current story is *better* than what's written: a human
(Hal) read my prior review, changed the Refiner's model/prompt in response to a
specific regression it found, and the loop then passed cleanly in 3 drafts with no
dip (6.8 → 7.5 → 8.3, monotonic, unlike the first run's 6.9→7.9→7.7→7.9). The
write-up as it stands describes the *old*, non-passing trajectory, and so misses
telling the actual, stronger "nobody touched the loop while it ran, and the loop
reached the bar" story the quest asks for. Scored low because the one fact the write-up
asserts here — "did not pass" / "not a bug to fix" — is now false of the real result
the round is shipping.

## Findings (ranked)

1. **docs/assignment-5-loop.md is entirely stale — wrong run, wrong trajectory, wrong
   pass/fail.** Fix: re-dispatch le-docteur with an explicit instruction to re-read
   `run-note.md` fresh (it now points to `content/runs/hal-lines-20261121-2011/`,
   PASSED at 8.3 on draft 3) and rewrite the trajectory table, both quoted-reason
   examples, and the closing paragraph against the new run — not reuse the first.
2. **Process gap: a step that re-runs after upstream data changed needs a non-cached
   instruction.** Le-docteur's reply that "this is the same task I just finished"
   shows it pattern-matched on the *prompt text* (byte-identical between the two
   `write-up` dispatches) rather than checking whether the input file it's supposed to
   summarize (`run-note.md`) had itself changed. Fix: the round's `write-up` prompt on
   a second dispatch should say explicitly that the run directory changed and name the
   new one, rather than repeating verbatim text a model can recognize as already done.
3. **`log.md`'s `✓ docs/assignment-5-loop.md` is misleading evidence of progress.**
   The checkmark format records file *existence*, not "modified this step" — it looked
   identical whether le-docteur actually rewrote the file or silently no-op'd. Fix: have
   the round runner diff the file's mtime/hash before vs. after a step and flag a
   no-op explicitly (e.g. "⚠ unchanged") rather than reporting success.
4. **TASKS.md's new A-6 row (added during the first write-up) also needs updating** —
   it currently says "4 drafts, trajectory 6.9 → 7.9 → 7.7 → 7.9, did not clear the 8.0
   bar," which will be wrong once the write-up is corrected to the passing run.
5. **Minor, carried over, still valid:** the provenance section (rubric 1) still only
   asserts GDD provenance by section number rather than quoting GDD text inline next to
   each STYLE.md rule — low severity, unchanged from before.

## Verdict
Do not hand this write-up to a grader as-is: it reports the loop as having failed to
clear the threshold when the current, installed result is a pass. Re-run the
`write-up` step pointed explicitly at `content/runs/hal-lines-20261121-2011/` before
anything else in this round is considered done.

---

## Appendix: findings from the review that triggered Hal's fix (superseded run)

Scores given then: provenance 9/10, specificity 9/10, score-not-verdict 10/10,
no-human-intervention 7/10.

1. Misattributed quote: draft-1 criterion-A reason text for `:morning-brief` was
   presented as if it were about `:memorial-moved`.
2. Trajectory dip unexplained: draft 2→3 criterion C regressed 10→8 on new violations
   in a draft where C had nothing flagged to fix — the Refiner wasn't holding to
   "change only what was scored down." (This is the finding Hal's commit `e9ea7b9`
   addressed directly: Refiner now runs on the Evaluator's model and is told what a
   rule-18 fix actually is.)
3. The run's non-zero exit code on a failed threshold wasn't explained as intentional.
4. STYLE.md provenance asserted rather than demonstrated inline (carried forward above
   as finding 5).
