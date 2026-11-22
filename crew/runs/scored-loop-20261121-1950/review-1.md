# Review — `docs/assignment-5-loop.md` vs. the scored-loop rubric

Scope: `docs/assignment-5-loop.md` against `.crew/quests/scored-loop.md`'s first
paragraph (4 rubric points), checked against the real run directory named in
`crew/runs/scored-loop-20261121-1950/run-note.md`:
`content/runs/hal-lines-20261121-1949/` (log.md, scores.edn, STYLE.md, drafts
1–4). Build for this round was Hal directly (commit `72e39ac`), not the usual
builder/tester pair — noted in this round's own `log.md`, not a boundary issue
for the write-up being reviewed.

## Rubric scores

**1. Style guide pulled from the existing GDD, not invented — 9/10.**
Spot-checked all three examples the write-up cites: A3, C11 and D16 in
`content/STYLE.md` quote verbatim with the same `§` citations the write-up
gives, and `docs/GDD.md` §2.6 (street cred, "no number on screen") and §2.1
(status bubbles) back C11 and D16 for real — nothing invented. Short of 10
only because the write-up doesn't show its own spot-check; it asserts
provenance rather than demonstrating one GDD quote alongside its STYLE.md
rule.

**2. Specificity: could a stranger tell which game — 9/10.** Every quoted
"generic" line (`"That's handled. Back to what you were doing?"`, `"Can't do
this without {program} yet."`) and every quoted "specific" line (the
hardline, the night crew, blueprints by breakfast) is a verbatim, correctly-
drafted quote I traced back to `scores.edn`/the draft files — this section is
accurate and well-evidenced, not asserted.

**3. Evaluator outputs a score and a reason per criterion, never binary —
10/10.** Confirmed directly in `scores.edn`: every criterion (`:A :B :C :D
:S`) carries `{:score :reason :violations}`, no `:verdict` anywhere in the
per-criterion map. `:passes` exists only once, at the top level, computed by
`pipeline.clj` from the scores — exactly the "threshold decides, not the
model" split the quest specifies. The write-up states this correctly.

**4. The loop fixed violations with nobody intervening — 7/10.** `log.md`'s
four drafts run as one unbroken sequence (Generator → Refiner ×3) with no
gap for a human edit — structurally sound, matches the write-up's claim. But
the write-up doesn't mention that the trajectory isn't monotonic (6.9 → 7.9
→ **7.7** → 7.9): draft 3's criterion C fell from a *perfect 10, zero
violations* in draft 2 to 8 in draft 3, i.e. the Refiner introduced new C
problems (`:weekly-card line 2` inventing where the card lives,
`:memorial-moved line 4` blurring "alive vs. remembered") in a draft that
had no C violations to fix — a real instance of the Refiner not holding to
"changes only what was scored down" (the quest's own constraint). The
write-up's trade-off paragraph covers an S/D trade-off in draft 4 but is
silent on this one, even though the data (which it had: `scores.edn`) shows
it plainly.

## Findings (ranked)

1. **docs/assignment-5-loop.md:81-85 — misattributed quote.** The first
   quoted-reasons example opens with `"Hal isn't speaking as himself... he
   doesn't say what's next"` and presents it as the draft-1 criterion-A
   reason for `:memorial-moved`. That phrase is actually the issue text for
   a *different* violation, `:morning-brief` (log.md line 19 /
   `scores.edn` draft 1 `:A :violations[0]`) — the write-up stitches two
   separate violations' text together under one event name. Fix: either
   drop the `:morning-brief` phrase from this example or label it as its
   own, separate quote.
2. **docs/assignment-5-loop.md:74-76 — trajectory dip unexplained (see
   rubric 4 above).** `scores.edn` draft 2→3 shows C regressing 10→8 with
   *new* violations in a draft the Refiner was told to only fix what scored
   low. This is the single most interesting evidence the run directory
   offers about where "automatic, no human intervening" has a real failure
   mode, and the write-up has the data but doesn't surface it. Fix: add one
   sentence noting the C regression and that the Refiner isn't perfectly
   scoped to only the flagged violations.
3. **docs/assignment-5-loop.md §"The real run" — no mention of the run's
   own exit status.** `run-note.md` records `Error: Error while executing
   task: content [exited with code 1]` after draft 4; the write-up frames
   the non-pass only as "did not pass" / "not a bug," which matches the
   quest's intent, but a grader re-running `bb content hal-lines` will see
   a shell error and may read it as a crash rather than an intentional
   non-zero exit on failed threshold. Fix: one line clarifying the task
   exits non-zero by design when the draft doesn't clear the bar.
4. **content/STYLE.md provenance — asserted, not shown (minor, rubric 1).**
   The write-up's provenance paragraph doesn't include even one paired
   GDD-quote/STYLE-rule example inline (it only names section numbers); a
   grader has to go open `docs/GDD.md` themselves to confirm "not
   invented." Low severity since the citations do check out. Fix: inline
   one GDD quote next to one STYLE.md rule, e.g. §2.6's "no number on
   screen" next to C11.
5. **No finding above style/nit level on rubric 3** — the Evaluator/
   threshold split is implemented and documented correctly; nothing to flag.

## Verdict
The write-up's four rubric claims are all true in substance and its quotes
check out against the real run directory with one exception (finding 1,
genuine misattribution). The more consequential gap is editorial, not
factual: the write-up could have used its own evidence to say something
sharper about where the automated loop's self-correction broke down
(finding 2) instead of only reporting the headline numbers.
