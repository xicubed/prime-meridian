# Review — `docs/assignment-5-loop.md` vs. the scored-loop rubric (round `scored-loop-20261121-1950`)

**Third pass, supersedes the prior review** (which flagged the write-up as stale,
still describing the withdrawn first run). The write-up has since been rewritten in
full (now covers both runs, the Refiner fix, and the passing trajectory) and
`TASKS.md`'s A-6 row updated to match. This is a fresh, from-scratch check against
the current files, not a rubber stamp. Superseded findings kept in the appendix.

## What changed since the last pass
`docs/assignment-5-loop.md` (mtime 20:25, after commit `e9ea7b9` and the second real
run) now: names both run directories, documents the Refiner model/prompt fix verbatim
from the commit, shows both trajectory tables (first run 6.9→7.9→7.7→7.9 did-not-pass,
second run 6.8→7.5→8.3 passed), links the installed
`resources/content/hal-lines.edn`, and — notably — writes up the draft-2→3 C
regression from the first run in more depth than my own prior review did, including a
specific, checkable claim: `:memorial-moved` line 4 is byte-identical between draft 2
and draft 3, yet the Evaluator scored C clean in draft 2 and flagged that exact
unchanged line in draft 3.

## Verification performed
- Re-pulled `content/runs/hal-lines-20261121-1949/draft-{2,3}.edn` directly: confirmed
  `:memorial-moved` line 4 (`"{name} stays with us, in memorial now."`) is in fact
  byte-identical in both files — the write-up's sharpest claim is real, not asserted.
- Re-pulled `content/runs/hal-lines-20261121-2011/draft-{1,3}.edn`: both quoted
  `:memorial-moved` and `:morning-brief` examples (draft 1's violation text, draft 3's
  replacement lines) match verbatim, including that draft 3's `:memorial-moved` has
  only 3 variants (the write-up quotes exactly 3, not 4 — consistent).
- Confirmed via `git diff 72e39ac e9ea7b9 -- content/pipeline.clj` that no
  threshold/weight/`passes?` line changed — only the Refiner's model default and
  prompt text. The write-up's "No STYLE.md rule and no threshold moved" is accurate.
- All three relative links into `content/runs/hal-lines-20261121-2011/` and
  `resources/content/hal-lines.edn` resolve to real files.
- GDD inline quote for C11 (§2.6: *"There is no XP... There's no number on screen and
  no comparison with anyone."*) is a real (ellipsis-joined) quote from `docs/GDD.md`
  §2.6 — the provenance gap from the prior review is fixed.

## Rubric scores

**1. Style guide pulled from the GDD, not invented — 10/10.** Now demonstrates
provenance inline for one rule (C11, quoted GDD text alongside the STYLE.md rule) as
the prior review asked for, and the other two citations (A3, D16) still check out
against `docs/GDD.md` §2.5/§2.1.

**2. Specificity: could a stranger tell which game — 9/10.** Every quote (both
"generic" and "specific" examples, across both runs) verified verbatim against the
actual draft/evaluator files. Minor: the "Why this is Prime Meridian" section's
`:next-small-step` example blends two separately-reasoned violations (one about
missing estate detail, one about parroting the GDD's own "overgrown, never ruined"
line back) into one simplified "generic filler" characterization — accurate in
substance, slightly flattened.

**3. Evaluator outputs a score and a reason, never binary — 10/10.** Re-confirmed in
both runs' `scores.edn`: zero `:verdict` keys, `{:score :reason :violations}` per
criterion throughout; `passes?`/`overall` are pipeline functions, not model output.

**4. The loop fixed violations with nobody intervening — 9/10.** The run itself (both
times) is a clean, unbroken Generator→Refiner sequence with no edit in between —
confirmed again in both run `log.md`s. Not a 10 only because: (a) the fix between runs
was itself a human (Hal) editing `pipeline.clj` based on a review of the first run —
honest and clearly disclosed by the write-up as a distinct, separate step, not hidden,
but it is a human touching the *pipeline*, between the two automated loop runs, which
the write-up's framing could state more explicitly as "the automated loop itself never
had a human in it; the two runs bracket one human-directed fix to the Refiner." (b)
`log.md`'s `run` step for both runs is annotated "waited at 1 prompt(s), answered by
someone else" — checked `replies/run-le-coureur.txt`: this is a background-shell-launch
permission prompt for `bb content hal-lines`, not an edit to draft content, so it
doesn't affect the loop's own autonomy claim, but it's exactly the kind of ambiguous
log line a stricter audit should resolve rather than leave unexplained.

## Findings (ranked)
1. **(Minor) docs/assignment-5-loop.md's `:next-small-step` specificity example
   conflates two distinct per-line violation reasons under one quote** (see rubric 2).
   Fix: attribute line 1 and line 2's criticisms separately, or quote only one.
2. **(Minor, observational) `log.md`'s "answered by someone else" on both `run` steps
   is unexplained in the write-up.** It's a benign shell-permission auto-approval, not
   a content edit — confirmed via the runner's own transcript — but worth one
   sentence in the write-up making that explicit, since "nobody intervening" is a
   literal rubric claim and this log line could read otherwise to a skeptical grader.
3. **(Nit) The write-up could state more sharply that the Refiner fix was the one
   human-directed change between the two fully-automated runs**, rather than letting a
   reader infer the loop's autonomy claim spans both runs equally (see rubric 4).
4. No finding above style/nit level on rubric 1 or 3 — both are now fully supported by
   verifiable evidence in the write-up itself.
5. No finding on the install: `resources/content/hal-lines.edn` exists on disk and its
   link resolves; the "Passed... installed" claim is real, not asserted.

## Verdict
This write-up is accurate and well-evidenced now — every quote and link checked traces
back to real files in both run directories, the headline regression finding is
demonstrated with a byte-identical-line comparison rather than just described, and the
fix's own commit confirms the write-up's "no threshold moved" claim. Ready for a
grader; the three findings above are all minor framing nits, not factual errors.

---

## Appendix: superseded findings (kept for the record)

### Pass 1 (before the Refiner fix; against the first run only)
Scores then: provenance 9/10, specificity 9/10, score-not-verdict 10/10,
no-intervention 7/10.
1. Misattributed quote: draft-1 criterion-A reason text for `:morning-brief` presented
   as if about `:memorial-moved`. (Not present in the current write-up — the current
   quotes are each correctly attributed.)
2. Trajectory dip unexplained: draft 2→3 criterion C regressed 10→8 with no violations
   flagged in draft 2 to justify it. **Addressed**: the current write-up now explains
   this in full, including the byte-identical-line evidence, and commit `e9ea7b9`
   fixed the underlying cause.
3. Non-zero exit code on failed threshold wasn't explained as intentional.
   **Addressed**: current write-up states this explicitly under "The scoring."
4. STYLE.md provenance asserted, not demonstrated inline. **Addressed**: C11 now
   carries an inline GDD quote.

### Pass 2 (write-up still describing the withdrawn first run after the fix landed)
Finding then: `docs/assignment-5-loop.md` was stale — mtime predated the second run,
le-docteur's second dispatch treated the (byte-identical) prompt as already done and
never re-read the new `run-note.md`. **Addressed**: the file has since been rewritten
(by Hal directly, per this round's `log.md`) to cover both runs correctly.
