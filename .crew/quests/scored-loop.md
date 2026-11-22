# Quest: the scored content loop (Assignment 5)

**Game:** Prime Meridian. **Asked by:** the course, due Nov 22 11:59 PM ET: "Build an automated,
self-correcting AI loop (Generator → Evaluator → Refiner) that rigorously enforces the specific
aesthetic and narrative rules of your existing capstone game." Graded on: style guide pulled from
the existing GDD (not a new universe); specificity (a stranger can tell which game); the Evaluator
outputs a **SCORE and a REASON**, never binary pass/fail; the loop fixes violations with **no human
intervening**. **Led by:** Hal. Michael reviews the result; he does not steer the loop.

## What exists (read first)

`content/pipeline.clj` (`bb content <request>`): already a Generator → Evaluator → Refiner loop
from Assignment 4: context step pulls named GDD sections, the writer drafts EDN, a deterministic
validator checks shape and guardrails (no XP, no real phone numbers...), an LLM critic returns
findings (blocker / minor, each citing a GDD §), and the writer gets validator errors + blockers
back, up to `:max-drafts`. Every step is logged under `content/runs/<request>-<stamp>/`.
`content/README.md` explains it; `content/requests/*.edn` are the requests; runs from Nov 6 show the
shape. `content/STYLE.md` (new, Hal) is the style guide: 19 rules, each citing a GDD section,
grouped A–D, with the scoring scheme at the bottom.

The class's guidelines also apply: `.crew/CONVENTIONS.md`, gate `.crew/gate.sh`. Don't read
`assets/private/`, `public/kit/`. **Never send keys or prompts to crew members; don't start
services; no whole-disk `find`; scratch files only in your own scratchpad.** The pipeline calls
`claude -p`; one real run is part of done-when, and it costs tokens, so run it once, deliberately.

## Decided (don't reopen)

- **Keep the pipeline; change the Evaluator and the Refiner, not the shape.** The validator stays
  as it is (shape/guardrail errors are still errors). The critic becomes a **scorer**: for each
  rule family in STYLE.md (A who's talking, B what the world is, C what's never said, D look and
  length) it returns `{:score 0-10 :reason "..." :violations [{:where :rule :issue :fix}]}`, plus a
  `:specificity` score with reason (could a stranger tell this is Prime Meridian?). The overall
  score is the weighted mean from STYLE.md (C ×2, A ×1.5, B ×1, D ×1; specificity ×1.5). No
  `:verdict` key: the threshold decides, 8.0 overall and no criterion under 6.
- **The Refiner is its own step with its own prompt**, separate from the Generator: it gets the
  draft, the scores and reasons, the violations with fixes, and STYLE.md, and returns a revised
  draft that changes only what was scored down. (Today the writer re-drafts from the critic's
  blockers; split that out so the three roles are legible in the log.) First draft = Generator;
  later drafts = Refiner.
- **The log shows the score trajectory**: `log.md` gets one line per draft like
  `draft 2: 6.8 (A 7 B 8 C 5 D 8 S 7)`, and a final `scores.edn` with every draft's scores and
  reasons. `bb content` prints the trajectory at the end.
- **STYLE.md is the single source of the rules.** The scorer prompt includes STYLE.md verbatim (it's
  short) plus the GDD sections the request names; the Refiner prompt includes it too. Don't restate
  rules in the prompts.
- **Deterministic tests for the pure parts** in `test/pm/content_test.clj` or next to the pipeline
  (bb tests, since the pipeline is bb): overall score from criterion scores; threshold decision
  (8.0 / none under 6); the trajectory line; parsing a scorer reply with a missing criterion (counts
  as 0 with reason "not scored"); the Refiner prompt names every violation.
- **One real run**, by Le Coureur, on `content/requests/hal-lines.edn` with `:max-drafts 4`:
  commit the run directory (it's the evidence). If the first draft already scores ≥ 8, lower the
  bar is not the answer; the write-up says so and shows the scores anyway.

## Out of scope

New requests, new content kinds, changing the validator's rules, changing the game. Any UI.

## Done when

Gate passes with the tests above; `bb content hal-lines` completes without anyone touching it and
its `log.md` shows the three roles and the score trajectory; `docs/assignment-5-loop.md` (Le
Docteur) explains the loop in ~1 page for a grader: the three roles and their prompts (linked), the
style guide's provenance (STYLE.md → GDD sections), the scoring (table of criteria, weights,
threshold), the real run's trajectory with two quoted reasons and the lines they changed, and what
makes it specific to this game. Le Critique reviews the write-up against the four rubric points.
