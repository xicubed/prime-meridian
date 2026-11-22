# Assignment 5: the scored content loop

*Prime Meridian — [xicubed/prime-meridian](https://github.com/xicubed/prime-meridian). Written by
Le Docteur (a crew agent) for the grader, edited by Hal (the crew's manager, a bot) for Michael
Lopez. This one page is the submission; everything it claims links to the file that proves it.*

## Abstract

Prime Meridian is a cozy estate game in which the player's AI agents are the crew and the player's
digital twin, Hal, speaks to them in short bubbles. This assignment builds the automated,
self-correcting loop that writes those lines: a **Generator** drafts them, an **Evaluator** scores
the draft 0–10 *with a reason* on each family of the game's style guide — rules lifted verbatim
from the existing GDD, never invented — plus a specificity criterion ("could a stranger tell this is
Prime Meridian?"), and a **Refiner** rewrites only what scored low, up to four drafts, with nobody
intervening. A weighted threshold (8.0 overall, no criterion under 6) decides; the model never
returns pass/fail. Two real runs are committed as evidence. The first (6.9 → 7.9 → 7.7 → 7.9) did
not clear the bar and exposed two real limits: a Refiner that reworded instead of fixing, and a
draft where it edited past its brief. One targeted change to the Refiner (model and prompt, no rule
or threshold touched) and the second run cleared the bar on draft 3 (6.8 → 7.5 → 8.3) and installed
the lines the game now speaks.

**What's where (all in this repo):**

| | |
|---|---|
| The loop | [`content/pipeline.clj`](../content/pipeline.clj) — [`parse-scores`](../content/pipeline.clj#L268), [`overall`](../content/pipeline.clj#L283), [`passes?`](../content/pipeline.clj#L290), the three prompts ([Generator](../content/pipeline.clj#L338), [Evaluator](../content/pipeline.clj#L347), [Refiner](../content/pipeline.clj#L371)), [`run-request`](../content/pipeline.clj#L410) |
| The style guide | [`content/STYLE.md`](../content/STYLE.md) — 19 rules, each citing its GDD section; [`docs/GDD.md`](GDD.md) is the source |
| The tests | [`content/pipeline_test.clj`](../content/pipeline_test.clj) — 14 deterministic cases on the scoring, threshold, parsing and prompts, run by the repo's gate |
| Run 1 (failed, 4 drafts) | [`content/runs/hal-lines-20261121-1949/`](../content/runs/hal-lines-20261121-1949/) — [`log.md`](../content/runs/hal-lines-20261121-1949/log.md), [`scores.edn`](../content/runs/hal-lines-20261121-1949/scores.edn), every prompt and draft |
| Run 2 (passed, 3 drafts) | [`content/runs/hal-lines-20261121-2011/`](../content/runs/hal-lines-20261121-2011/) — [`log.md`](../content/runs/hal-lines-20261121-2011/log.md), [`scores.edn`](../content/runs/hal-lines-20261121-2011/scores.edn) |
| The output the game loads | [`resources/content/hal-lines.edn`](../resources/content/hal-lines.edn) |
| The request | [`content/requests/hal-lines.edn`](../content/requests/hal-lines.edn) — the brief, the events, the GDD sections |
| How the work was done | [`crew/runs/scored-loop-20261121-1950/`](../crew/runs/scored-loop-20261121-1950/) — the agent crew's record: [spec](../crew/runs/scored-loop-20261121-1950/spec.md), [gate note](../crew/runs/scored-loop-20261121-1950/gate-note.md), run notes ([1](../crew/runs/scored-loop-20261121-1950/run-note-1.md), [2](../crew/runs/scored-loop-20261121-1950/run-note.md)), and Le Critique's reviews of this write-up ([first](../crew/runs/scored-loop-20261121-1950/review-1.md), [final](../crew/runs/scored-loop-20261121-1950/review.md)) |
| The brief we worked from | [`.crew/quests/scored-loop.md`](../.crew/quests/scored-loop.md) |

## Background

Prime Meridian already had a Generator → Evaluator → Refiner loop (`content/pipeline.clj`,
Assignment 4) that drafted, validated and critiqued voice lines. This assignment made the
Evaluator a **scorer** (a number and a reason per rule family, never a verdict) and split the
**Refiner** into its own step, so a stranger reading the log can see three distinct roles arguing
over one draft. The validator (shape, placeholders, guardrails like no XP or real phone numbers)
and the context step are unchanged. Two real runs happened, and both matter to this write-up: the
first exposed a real weakness in the Refiner, the second shows the loop passing after the fix.

## The three roles

| Role | Model | Prompt | Writes |
|---|---|---|---|
| **Generator** | `WRITER_MODEL` (`claude-sonnet-5`) | [`writer-prompt`](../content/pipeline.clj#L338) | draft 1 |
| **Evaluator** | `CRITIC_MODEL` (`claude-opus-5-5`) | [`scorer-prompt`](../content/pipeline.clj#L347) | a score + reason per criterion, every draft |
| **Refiner** | `REFINER_MODEL`, default = `CRITIC_MODEL` | [`refiner-prompt`](../content/pipeline.clj#L371) | drafts 2.. |

All three prompts carry [`content/STYLE.md`](../content/STYLE.md) verbatim plus the GDD sections
the request names (`writer-system`/`scorer-system`/`refiner-system`, just above each `-prompt` fn,
set each role's persona). The rules are never restated inline — one source, three readers. The
Evaluator's reply has no `:verdict` key; `pipeline.clj`'s `overall`/`passes?` apply the threshold,
not the model. The Refiner is told exactly what to change and ordered to leave everything else
word for word (`refiner-system`, above).

**The Refiner's model changed between the two runs** (commit `e9ea7b9`, after the first run and
Le Critique's review of it): it now defaults to `REFINER_MODEL` = `CRITIC_MODEL` (Opus) instead of
riding on `WRITER_MODEL` (Sonnet), and the prompt now spells out what fixing STYLE.md rule 18
("variety is real variety") means — each variant needs its own job, fact or picture — and that
placeholders belong behind a label. No rule and no threshold changed.

The actual prompts sent in the second (passing) run are in its run directory, e.g.
[`generator-prompt-1.md`](../content/runs/hal-lines-20261121-2011/generator-prompt-1.md),
[`evaluator-prompt-1.md`](../content/runs/hal-lines-20261121-2011/evaluator-prompt-1.md),
[`refiner-prompt-2.md`](../content/runs/hal-lines-20261121-2011/refiner-prompt-2.md). The first
run's prompts are under [`content/runs/hal-lines-20261121-1949/`](../content/runs/hal-lines-20261121-1949/).

## The style guide's provenance

[`content/STYLE.md`](../content/STYLE.md) is 19 rules in four families (A who's talking, B what
the world is, C what's never said, D look and length), each one lifted from `docs/GDD.md` and
citing its section — nothing in it is invented for this assignment. Three examples, one with the
GDD line it comes from quoted alongside it:

- **C11** "No XP, levels, points, scores, streaks, high scores" (§1, §2.6) ↔ GDD §2.6: *"There is
  no XP... There's no number on screen and no comparison with anyone."*
- **A3** "Characters are the twins of something real... Lines come from that reality (a worm
  count, a stalled session), never from invented lore" → GDD §1, §2.5.
- **D16** "Bubble-length: one or two sentences; a status plate carries at most ~64 characters" →
  GDD §2.1 ("status bubbles"), `pm.scene/plate`.

## The scoring

The Evaluator scores five criteria 0–10 with a reason each; `overall` is the weighted mean, one
decimal.

| Criterion | What it checks | Weight | Threshold |
|---|---|---|---|
| A — who's talking | STYLE.md rules 1–5 | 1.5 | ≥ 6 |
| B — what the world is | rules 6–10 | 1.0 | ≥ 6 |
| C — what's never said | rules 11–15 | 2.0 | ≥ 6 |
| D — look and length | rules 16–19 | 1.0 | ≥ 6 |
| S — specificity | could a stranger tell this is Prime Meridian? | 1.5 | ≥ 6 |
| **Overall** | weighted mean of the above (sum of weights 7) | — | **≥ 8.0** |

A draft installs only if overall ≥ 8.0 **and** no single criterion is under 6; otherwise every
score, reason and violation (with its suggested fix) goes to the Refiner, which may change only
what was scored down. `bb content <request>` exits non-zero when the final draft still doesn't
clear the bar — that is the task reporting an honest failure, by design, not a crash.

## The first run: where the loop broke

`bb content hal-lines`, `:max-drafts 4`, one real run, nobody touching it. Full log:
[`content/runs/hal-lines-20261121-1949/log.md`](../content/runs/hal-lines-20261121-1949/log.md).
(The crew round's own `log.md` notes that Le Coureur, the crew member who typed the command,
"waited at 1 prompt, answered by someone else": that is Claude Code asking permission to run
the shell command, auto-approved by the crew's prompt-fielder service. Nothing in the loop's
drafts, scores or prompts was touched by a person in either run.)

| Draft | Role | Overall | A | B | C | D | S |
|---|---|---|---|---|---|---|---|
| 1 | Generator | 6.9 | 7 | 8 | 8 | 5 | 6 |
| 2 | Refiner | 7.9 | 8 | 7 | 10 | 6 | 7 |
| 3 | Refiner | 7.7 | 8 | 8 | 8 | 6 | 8 |
| 4 | Refiner | 7.9 | 7 | 8 | 10 | 5 | 8 |

**Did not pass.** Four drafts closed in from 6.9 to 7.9 but never cleared 8.0, and D (look and
length, specifically rule 18's "variety is real variety") never rose past 6: the Generator's
`:morning-brief` lines — `"Morning card's pinned. Items: {count}."`, `"Card's ready. Items:
{count}."`, `"Pinned the card for today. Items: {count}."`, `"Up on the board — items: {count}."`
— were flagged in draft 1 as, in the Evaluator's words, *"Several events... are one line reworded
four times... :morning-brief is always '\<verb\>. Items: {count}.'"* By draft 4 the four variants
differed in substance, not just wording — `"Morning card's pinned: {count} items. Want the first
one now?"`, `"Card's on the board. Want me to read it while you walk?"`, `"Today's card is up. The
first thing on it can wait for coffee."`, `"Card's up. Nothing on it needs you before noon."` — but
D still scored 5, because the same reworded-variant pattern persisted in six other events
(`:last-call`, `:blueprint-ready`, `:agent-stalled`, `:memorial-moved`, `:chore-done`,
`:weekly-card`) that the Refiner never got to.

The run also surfaced a second, more specific failure: **the Refiner regressed C (what's never
said) from a clean 10 to 8 on draft 3**, in a draft where C carried no violations to fix. Two
things happened at once. First, the Refiner was told to fix a D18 (variety) violation on
`:coach-habit` — draft 2's four variants all restated "habit + goal + today?" — and in
diversifying them it rewrote line 2 from `"{habit}: you set {goal}. One today?"` to `"{habit} fits
before lunch, if you want it. Your goal: {goal}."`, inventing a schedule fact Hal has no source
for; the Evaluator caught this as a new C12 violation ("the twin acts on stated preferences, not
guesses"). Second, and separately, `:memorial-moved`'s line 4, `"{name} stays with us, in memorial
now."`, is **byte-identical** between draft 2 and draft 3 — the Refiner touched nothing there —
yet draft 2's Evaluator called C clean while draft 3's flagged that exact unchanged line as a C14
violation ("stays with us... edges toward treating a pet who died as still present"). So the dip
is two different failures stacked: the Refiner widening its edit past what was scored down while
fixing something else, and the Evaluator itself disagreeing with its own prior verdict on text it
never saw change. Both are real limits on "the loop fixes violations with no human intervening."

## The fix, and the second run

Commit `e9ea7b9` moved the Refiner from `WRITER_MODEL` (Sonnet) to `REFINER_MODEL` (default
`CRITIC_MODEL`, Opus — the same model that scores the draft) and told its prompt explicitly what a
rule-18 fix looks like: each variant needs its own job, fact or picture; placeholders go behind a
label. No STYLE.md rule and no threshold moved.

`bb content hal-lines` again, same request, nobody touching it:
[`content/runs/hal-lines-20261121-2011/log.md`](../content/runs/hal-lines-20261121-2011/log.md).

| Draft | Role | Overall | A | B | C | D | S |
|---|---|---|---|---|---|---|---|
| 1 | Generator | 6.8 | 7 | 8 | 8 | 4 | 6 |
| 2 | Refiner | 7.5 | 7 | 9 | 8 | 5 | 8 |
| 3 | Refiner | 8.3 | 8 | 9 | 9 | 7 | 8 |

**Passed.** Draft 3 cleared 8.0 with no criterion under 6, and `bb content` installed
[`resources/content/hal-lines.edn`](../resources/content/hal-lines.edn).

Two quoted reasons and the lines they changed, draft 1 of this run against the installed draft 3:

1. **Draft 1, criterion A**, on `:memorial-moved`: *"Clinical status confirmations for a pet who
   died. There's no warmth, which a companion would bring here."* — flagging all four lines,
   including `"Moved {name} to memorial."` and `"{name}'s with memorial now."`. Draft 3 replaces
   the set with `"{name}'s in the memorial now. A quiet spot on the estate, kept for good."`,
   `"Nothing more will ask you about {name}. Just the memorial, whenever you want it."`, and
   `"I'm sorry about {name}. I'll keep the spot tidy."` — each one a sentence of Hal speaking to
   the loss, not a status line reporting it. (Draft 3's own Evaluator still dings the first of
   these as "generic" on specificity — the fix solved A without fully solving S.)
2. **Draft 1, criterion D**, on `:morning-brief`: *"Several keys give four rewordings of one
   sentence... The 'Label: {count}' pattern... reads like a system readout, not the cozy diorama
   voice."* — flagging `"Morning card's up. Items waiting: {count}."`, `"Pinned the brief. On
   there: {count}."`, `"Card's on the board — waiting: {count}."`, `"Here's the morning card.
   Count on it: {count}."`. Draft 3's four variants each do a different job instead of restating
   one: `"Morning card's up on the board. It's holding {count} for you."`, `"Pinned the brief at
   the notice board. Read it now or on your way past."`, `"Morning. The day fits on one card; I
   counted {count} on it."`, `"Card's pinned. Take the lines in any order you like."` — an offer
   to read later, a count, and a genuine choice, not one template said four ways. Overall D rose
   from 4 to 7 across the run.

## Why this is Prime Meridian, not a generic game

A generic version of this loop would score generically — "is the tone warm?", "is it too long?" —
and the Refiner would smooth prose until it was merely pleasant. Both runs keep rejecting pleasant
lines that could belong to any app: the second run's draft 3 still loses specificity points for
`:next-small-step`'s "Estate's a little overgrown. One small thing and a path clears," which is
cozy-game filler with no estate fact in it, while the lines that score well are tied to things
only this game has: the hardline ringing for a jack-out (`"The hardline's ringing: {name} needs you
out there."`), a bedtime the player set rather than one Hal imposes (`"Last call. You asked me to
send you home at {bedtime}."`), programs that load as SAMPLE before they're real (`"Sample or real,
it shows on the badge."`), and a memorial creature given a tidy spot but no voice, because the GDD
says it has none. The loop isn't grading prettiness; it's grading whether a stranger reading the
line, on its own, would know it came from Prime Meridian and not a reskin — and the two runs above
show that grading catching real misses, in both the Generator's drafts and, in the first run, in
the Refiner's own fixes.

## Conclusion

The loop does what the assignment asks, and the evidence is the two run directories rather than
this page. The style guide is the GDD's own rules with their section numbers attached; the
Evaluator never says pass or fail, only a score and a reason per criterion, and the threshold is
code; and between draft 1 and the installed result nobody edits a line — the only human-directed
change in the whole exercise sits *between* the two runs, in a commit that touched the Refiner and
nothing else.

Three things we learned are worth more than the passing score. First, **a Refiner that is told
"this is the same line reworded" will reword it again** unless the prompt says what a fix is; the
first run spent four drafts proving that. Second, **"change only what scored down" is a constraint
models drift from** — the first run's draft 3 lost two points on a criterion that had no violations
to fix, and the Evaluator itself re-judged an unchanged line. Scores with reasons made both failures
legible in the log; a pass/fail critic would have hidden them. Third, **the honest outcome of a
scored loop is sometimes a non-pass**: `bb content` exits non-zero when the final draft is under the
bar, the run directory is kept either way, and that failed run turned out to be the more instructive
half of this submission.

What's next is already visible in the second run's own scores: specificity (S) plateaued at 8 while
the Evaluator kept flagging "cozy-game filler" lines with no estate fact in them. The next
iteration of STYLE.md will say what an estate fact *is* (a place, a crew member, a creature's real
chore, SAMPLE/REAL) so the Refiner has something to reach for, and the same loop will run on the
game's other two requests, the mirror lines and the sample inbox.
