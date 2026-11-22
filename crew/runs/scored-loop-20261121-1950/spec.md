# Spec: the scored content loop (Assignment 5)

*Written by Hal (the manager) after the crew's own spec step was lost to a stuck permission
prompt and a crew restart on Nov 21. Hal implemented it directly (commit `72e39ac`) so the
crew's remaining steps — gate, one real run, write-up, review — could start on time. The
crew's role cards still hold: Le Coureur runs, Le Docteur writes, Le Critique reviews.*

## What changed, and where (`content/pipeline.clj`)

The shape of the pipeline is unchanged (context → draft → validate → score → loop →
install). The validator and the context step are untouched. Three roles are now legible:

| Role | Model | Prompt | Draft |
|---|---|---|---|
| Generator | `WRITER_MODEL` (claude-sonnet-5) | `writer-prompt` + `writer-system` | 1 |
| Evaluator | `CRITIC_MODEL` (claude-opus-5-5) | `scorer-prompt` + `scorer-system` | every draft |
| Refiner | `WRITER_MODEL` | `refiner-prompt` + `refiner-system` | 2..`:max-drafts` |

Every prompt includes `content/STYLE.md` verbatim (`style-guide`), plus the GDD sections the
request names. The rules are not restated in the prompts.

## The Evaluator's reply

```edn
{:A {:score 0-10 :reason "..." :violations [{:where "key/line" :rule 3 :issue "..." :fix "..."}]}
 :B {...} :C {...} :D {...}
 :S {:score 0-10 :reason "..." :violations [...]}}
```

A–D are STYLE.md's rule families; S is specificity (could a stranger tell this is Prime
Meridian?). No `:verdict`: the threshold decides. `parse-scores` normalises the reply; a
missing or malformed criterion is `{:score 0 :reason "not scored"}`.

## Scoring (pure functions, `content/pipeline_test.clj`)

- `overall`: weighted mean, one decimal. Weights A 1.5, B 1, C 2, D 1, S 1.5 (sum 7).
  Hand-worked: A7 B8 C5 D8 S7 → (10.5 + 8 + 10 + 8 + 10.5) / 7 = 47/7 = **6.7**.
  A9 B9 C9 D9 S5 → 57/7 = **8.1** (fails: S under 6).
- `passes?`: `overall ≥ 8.0` and every criterion `≥ 6`.
- `trajectory-line`: `draft 2: 6.7 (A 7 B 8 C 5 D 8 S 7)`.
- `low-criteria`, `all-violations`: what the Refiner is told to fix.

## The loop

Draft 1 is the Generator's. Every draft that parses as EDN is validated and scored (a
validator error still blocks install, and goes to the Refiner alongside the scores). A draft
over the bar installs; otherwise the Refiner gets the draft, the scores and reasons, every
violation with its fix, STYLE.md and the validator errors, and must change only what was
scored down. Up to `:max-drafts` (4 for `hal-lines`). Nobody intervenes.

## The record (`content/runs/<request>-<stamp>/`)

`log.md`: the three roles by name, one `## Draft n — Generator|Refiner` block each with the
validator result, the trajectory line, the five reasons and the violations, and a final
`## Trajectory` list. `scores.edn`: every draft's `:scores`, `:overall`, `:passes`,
`:errors`. Plus `generator-prompt-1.md`, `evaluator-prompt-n.md`, `refiner-prompt-n.md`,
`draft-n.edn`, `evaluator-n.edn`, `validate-n.edn`.

## Tests (`bb content-test`, run by `.crew/gate.sh`)

14 cases: overall (3), threshold (4), trajectory line (1), parsing (4: missing criterion,
non-map reply, clamping, low-criteria), prompts (2: the Refiner names every violation and
reason; the Evaluator is asked for score + reason, never a verdict).

## Who touches what (remaining steps)

Le Coureur: runs the gate and `bb content hal-lines` once; edits nothing. Le Docteur:
`docs/assignment-5-loop.md` (and a TASKS.md row). Le Critique: `review.md`. Nobody edits code.

## Second run (Hal, after Le Critique's first review)

The first real run (`content/runs/hal-lines-20261121-1949/`, `run-note-1.md`, `review-1.md`)
went 6.9 → 7.9 → 7.7 → 7.9 and did not clear the bar: D (look and length) stayed at 5–6
because the Sonnet Refiner kept rewording the variants the Evaluator had called "the same
line reworded" (rule 18), and draft 3 regressed C from 10 to 8 by changing lines it was not
asked to. Commit `e9ea7b9` answers both: the Refiner now runs on the Evaluator's model
(`REFINER_MODEL`, default = `CRITIC_MODEL`) and its prompt says what a rule-18 fix is (each
variant carries its own job, fact or picture) and where placeholders go (behind a label).
No rule and no threshold changed.

Le Coureur runs `bb content hal-lines` once more (`run-note.md`). **Le Docteur's write-up
covers both runs**: the first as the trajectory that exposed the Refiner's weakness, the second
as the result after the fix; it also answers findings 1–4 of `review-1.md` (the misattributed
quote, the C regression, the non-zero exit by design, one inline GDD quote next to a STYLE.md
rule). Le Critique reviews the revised write-up against the same four rubric points
(`review.md`).
