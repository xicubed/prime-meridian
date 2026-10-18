# Visualization Audit: review-viz.html

Auditor: viz-reviewer (Round 5, Phase 3)
Inputs: `review-viz.html`, `reviews/viz-data.json`, `reviews/viz-spec.md`, the six reviewer files, `reviews/SYNTHESIS.md`.
Method: I read the code. I compared every embedded record against the JSON and checked the JSON against the review files. I traced the render logic to work out what each view shows, including the interactive states nobody has exercised yet. Layout figures are computed from the CSS at the 1100px max width, not measured in a browser, so treat pixel values as estimates.

## What checks out

- **Headline counts.** Total findings 31 (30 in Round 1), BLOCKING 11 (7 in Round 1), 4 unresolved disagreements. All are computed from the data and match SYNTHESIS.md and `aggregate_counts`.
- **Every severity.** All 31 findings have the correct Round 1 and final severity against the review files: six reviewers with 5 findings each, plus adversarial-qa F6, the new MAJOR finding from X3. Severity transitions are B→B 7, M→B 4, M→M 14, M→m 2, m→M 2, m→m 1, new→M 1. That gives 6 upgraded, 2 downgraded, 22 unchanged, 1 new, 0 fully withdrawn, and per-reviewer B/M/m counts match `counts_by_reviewer`.
- **Revision qualifiers.** The `q` values come from `cross_exam_revisions.change_type`, and all 31 match.
- **Round 2 items.** All 23 conflicts and 34 connections are present, none duplicated, and their summaries and titles match the JSON word for word. I checked the JSON against the review files: every C/X item in all six files is present, and none is invented.
- **Builder's word-for-word claim.** It holds for titles, descriptions, notes, top-issue text, disagreement text, quick wins and the verdict, with the one exception noted in A7. The fields that were dropped (passage, omission, fix, flagged_by_detail, parsing notes, engagement summaries) are really absent and nothing else depends on them.
- **Synthesis content.** Top 5 titles, severity (all BLOCKING), outcomes (all STRENGTHENED) and the two "just outside" items (narrative-critic F1 SURVIVED, adversarial-qa F1 WEAKENED) match SYNTHESIS.md. So do D1-D4: both sides, the reviewers on each side, the "also on the table" positions, and the escalated decisions. The shared-root bracket over D1-D3 is supported by the verdict paragraph.
- **Hand-mapped "Source exchanges".** These are correct for #1, #3, #4, #5, both "just outside" cards, and all of D1-D4. The exception is #2 (see A5).
- **V4 matrix counts.** Every off-diagonal cell is non-zero, as the spec expected. The diagonal R counts (5/5/5/6/6/5) match `revisions_listed`, and the row totals add up to 57 = 23 + 34.

---

## 1. Accuracy errors

**A1. V4 disagreement markers are mostly wrong.** (MUST-FIX; see U1)
`dOf()` (line 486) puts a D-marker on any cell that has a conflict, if both reviewers appear *anywhere* in a disagreement. D1 lists all six reviewers once the "also on the table" positions are counted. So **all 18 conflict cells get "D1" and the inset outline**, and only 6 of them actually hold a D1 exchange.

| Disagreement | Correctly marked | Wrongly marked |
|---|---|---|
| D1 | systems→psych (C2), systems→QA (C2), narrative→psych (C1), psych→QA (C1), QA→psych (C2), BA→psych (C1) | systems→narrative, narrative→systems, narrative→BA, psych→systems, psych→BA, feasibility→QA, feasibility→BA, feasibility→systems, feasibility→narrative, QA→systems, BA→feasibility, BA→narrative (12 cells) |
| D2 | QA→systems, QA→psych | systems→psych, systems→QA, psych→QA, psych→systems |
| D3 | — | narrative→BA (that cell's C3 is about film IP, not the metric) |
| D4 | — | feasibility→BA (C3, MVP order), BA→feasibility (C2), BA→narrative (C3, business model) |

The lede says "D = cell feeds an unresolved disagreement", and that is false for 20 marker placements. The detail panel then offers "See disagreement D1 below" from exchanges that have nothing to do with closing time. For example, feasibility-lead → adversarial-qa holds C1 (chore-event severity) and C2 (forged "!" scope). Going the other way, feasibility-lead X5, which is a D1 source, never produces a marker because the rule requires a conflict. The correct mapping is already on the page in `D.dis[].src` and should drive the markers.

**A2. Top issue #4: two "R2" chips are wrong.** The page shows `adversarial-qa R2`, `player-psychologist R2`, `systems-designer R2`. Only adversarial-qa contributed in Round 2 (X1, X3). SYNTHESIS says player-psychologist #3 and systems-designer F1 are Round 1 findings that feasibility-lead X3 *traced* as dependencies. Those two reviewers wrote nothing in Round 2 about the bridge. The chips should read `player-psychologist F3` and `systems-designer F1`, which is how they already appear in the card's own Round 1 trail.

**A3. Top issue #5: two chips have no ID and no R2 tag.** player-psychologist and business-analyst show as bare chips because `rel` has no finding of theirs. SYNTHESIS credits them through Round 2 connections (player-psychologist X6, business-analyst X5). Under the page's own convention from #1 and #2, a bare chip next to `narrative-critic F3` reads as a Round 1 flag. They should be `R2`, or better, `X6` / `X5`.

**A4. V6 "Top 5" column and "Part of top issue #N" link mis-assign findings.** Line 334 assigns each finding the *lowest* rank among all top issues whose `related_findings` include it.
- `adversarial-qa F1` shows **#4**, but SYNTHESIS lists it as its own item *just outside* the top 5 (WEAKENED). It is only a traced bridge dependency in #4. V1 and V6 now contradict each other.
- `adversarial-qa F5` shows **#4**, but SYNTHESIS names it as a flagger of **#5**. Again, #4 is only a traced dependency.
- Findings in more than one issue are collapsed to one rank: player-psychologist F3 is in #2 and #4, adversarial-qa F4 in #2 and #4, adversarial-qa F5 in #4 and #5.
- narrative-critic F1, the other "just outside" item, shows nothing.

Show all ranks (for example "#4 #5"), count only primary flaggers, and mark the "just outside" findings (for example "out").

**A5. Top issue #2 "Source exchanges" leaves out business-analyst X2.** SYNTHESIS credits "Connections from narrative-critic and business-analyst (X2, X4)". narrative-critic never numbered its connections, so X2 and X4 can only be business-analyst's. BA X2 is the connector-rack connection that explicitly cites forgeable "!" via unvetted tools. The page shows `business-analyst X4` and `narrative-critic X3`. narrative-critic X3 is relevant, but its ID was assigned by the extractor. **business-analyst X2 is missing.** The other #2 buttons are correct: player-psychologist X1/X2, adversarial-qa X1/X2, feasibility-lead C2, systems-designer X1.

**A6. "Severity as of: Round 1" counts adversarial-qa F6 as a Round 1 MAJOR.** `sevOf()` (line 337) falls back to `f.sf` when there is no Round 1 severity. With basis set to Round 1 and only MAJOR on, the bar reads "Showing 21 of 31", but Round 1 had 20 MAJOR findings and F6 did not exist yet. V3's Round 1 view correctly leaves F6 out, so the two views disagree.

**A7. (Benign) One embedded text differs from the JSON.** The F6 `round2_change_note` has "(from X3)" inserted: HTML "New finding in Round 2 (from X3). No single…" versus JSON "New finding in Round 2. No single…". It matches the source file and the spec asked for "from X3" to be kept, but it is the one exception to the builder's word-for-word claim. The builder also wrote the `cav` caveat lines and the `src` mappings itself. I checked those against SYNTHESIS: the caveats are faithful, and the mappings are covered in A5.

**Traceability caveat (not an error against the JSON).** The C/X IDs for systems-designer and narrative-critic (for example "narrative-critic C2", "systems-designer X3") were assigned by the extractor in reading order. They do not appear in those review files. The page also uses normalized F-numbers, while SYNTHESIS refers to "player-psychologist #3" and "adversarial-qa #1". A reader checking the page against the source files will not find these labels. A one-line footnote would fix that (see U19).

---

## 2. Usability issues

### MUST-FIX

**U1. V4 D-markers make the matrix misleading** (see A1). 18 of 30 off-diagonal cells carry the 2px inset outline and a D label, so the marker no longer picks anything out, and the links it offers point at the wrong disagreements. The disagreement layer is the main reason V4 exists alongside V5. Fix: mark a cell Dn only if the cell contains an item listed in `D.dis[n].src`. Consider allowing connections too, so feasibility-lead X5 and player-psychologist X2 show up.

### SHOULD-FIX

**U2. The sticky V6 table header is hidden behind the filter bar.** `.ft th{top:48px}` assumes a 48px bar. At 1100px the bar content (~820px of reviewer chips plus ~217px of severity stamps, then the basis switch and count) is wider than the 1052px row, so the bar wraps to two lines, about 70px. The column headers and sort buttons slide under it (z-index 20 vs 5) while the table scrolls. Measure the bar height into a CSS variable, or stop the table header from sticking.

**U3. Scroll targets land under the sticky bar.** "Part of top issue #N" and "See disagreement Dn below" use `scrollIntoView({block:"start"})` with no `scroll-margin-top`. The card's rank and title, or the D panel's heading, end up behind the bar, so the user lands on a panel without seeing which one it is.

**U4. The filter bar sits right above V1, but V1 ignores it.** A first-time reader who clicks "BLOCKING" or a reviewer sees nothing change in the Top 5 (only chip fills). The views that do respond (V2, V3, V4, V6) are below the fold. Add a short label such as "Filters apply to the charts and table below", or place the bar after V1.

**U5. Every V3 row wraps.** `.duo` is fixed at `620px 1fr`, which leaves V3 about 392px. After the 150px label and 96px count column, the squares get about 146px, which fits 4 squares. Every reviewer has 5 or 6 findings, so every row wraps to 4+1 or 4+2. That breaks the side-by-side comparison the unit chart is for. The header legend "B=BLOCKING M=MAJOR m=MINOR" (`white-space:pre`, about 172px) also spills about 75px out of its 96px column, past the column's right edge. Narrow the V2 column to about 520px, or stack V2 and V3 sooner.

**U6. The V4 matrix scrolls sideways at full width.** The table is about 713px (145px row header, 6 × ~86px cells, ~52px "out:" column). The `.mx` grid gives it at most 1052 − 380 − 32 = 640px, so the last column and the row totals are clipped behind a horizontal scrollbar. Shrink the cells to about 72px, or shorten the row labels.

**U7. V2 labels are clipped when a reviewer filter is on.** The right-hand labels start at x=506 in a 620-wide viewBox. "1 of 11 BLOCKING" at 14px mono is about 134px, which overruns to about 640 and gets cut off (for example with systems-designer selected). Widen the viewBox or move the label inside the node.

**U8. The "R2" chip has no legend and is used inconsistently.** Nothing on the page says what "R2" means. It is applied wrongly in #4 (A2) and missing in #5 (A3). Add a line under the V1 lede, for example "R2 = contributed only in Round 2", and apply it consistently.

**U9. Chips and stamps that look clickable but aren't.** The filter-bar reviewer chips and severity stamps are buttons. The same chip and stamp styling appears, inert, in V1, V5 and V6. In V5, clicking a chip does nothing. In V1 and V6 it toggles the card or row instead. Either make the display chips set the reviewer filter, or give the filter controls a distinct look.

**U10. The Safari regex lookbehind breaks the whole page.** `f.d.split(/(?<=[.?!])\s/)` (line 388) is a regex literal, so Safari before 16.4 fails to parse the entire script. That leaves a page of empty section headings: no verdict, no stats, no charts, no table. There is no `<noscript>` or static fallback. The fix is one line, for example a `match(/^.*?[.?!](?=\s|$)/)`. It is cheap insurance for a page that will run on whatever browser is attached to the projector.

**U11. The first-sentence trim produces misleading fragments in the V1 trail.** The splitter breaks at "..." inside quotations:
- adversarial-qa F4 ends at `…“The Director...`
- adversarial-qa F5 ends at `…“household members are never agents...`
- player-psychologist F5 shows only `Three faults.`

Each looks like a complete quotation or says nothing. Split on sentence ends outside quotes, or show the first ~200 characters.

**U12. There is no way to trace a finding back to the GDD on the page.** `passage_referenced` and `omission_noted` were dropped, so expanded V6 rows have no Source section. The spec's goal 4 ("any finding can be traced back to its source") is only half met: findings link to Round 2 exchanges but never to a GDD section. Restore the passage line in muted italic.

**U13. Several text blocks are below the spec's 16px minimum.** V1 trail sentences, caveats and V4 panel items are 15px, and links are 14px, against "Body text 16px minimum… because the page may be projected". Chips (13px), stamps (12px) and matrix labels (12px) will be hard to read from the back of a room.

### NICE-TO-FIX

- **U14.** Top issue #4's trail lists adversarial-qa F6 (a Round 2 finding, shown as "NEW") under the "ROUND 1 · ORIGINAL FINDINGS" stage label.
- **U15.** "Open in table" and V3 square clicks call `S=DEF()`, which silently clears every filter the user set, including a reviewer just chosen in V3.
- **U16.** Pinning a matrix row or column gives no visual state on the grid. Only the panel title changes.
- **U17.** The "NOT IN R1" node in V2 has no `data-node`, so it is the only node with no hover tooltip.
- **U18.** The popover is `position:fixed` and stays open while the page scrolls, so it drifts away from its button.
- **U19.** IDs: the page uses normalized F-numbers, while SYNTHESIS and the source files use "#3", "1." and so on. Some C/X IDs were assigned by the extractor (see the traceability caveat). Show `original_label` in the expanded row, or add a footnote.
- **U20.** The "just outside" cards say "OUTSIDE TOP 5" in the rank gutter directly under a "JUST OUTSIDE THE TOP 5" heading. Top issue #2's caveat repeats a sentence already in its cross-examination paragraph.
- **U21.** V6's expanded F6 row reads "New in Round 2: New finding in Round 2 (from X3)…", which repeats itself.
- **U22.** V5 reviewer chips have no item IDs (the spec asked for "business-analyst C1"). The IDs appear only inline in the argument text.
- **U23.** The "Chore events" card's Source exchanges leave out adversarial-qa C3, which is where QA narrowed its own core. Quick Win 1 cites it.
- **U24.** V1 cross-examination is one dense paragraph rather than per-reviewer bullets. This is an accepted deviation, but the bullets would scan better on a projector.

**On the builder's listed deviations:**
- One-paragraph V1 cross-examination: acceptable (U24).
- No Source section in V6: should be restored (U12).
- No fade-up: fine, it was optional.
- Normalized F-numbers: acceptable with a footnote (U19).
- Sticky filter bar: fine, but its height breaks the table header (U2, U3).
- R2 chips: good idea, but needs a legend and fixing (U8, A2, A3).
- Lookbehind: should be fixed (U10).

---

## 3. Verdict

**Needs another pass before it is shown.** The core data is accurate. Every count, severity, transition, Round 2 item, disagreement and quick win traces correctly to the review files, and the main structure works: the masthead, V1 cards, V2 flow, V5 panels and V6 table. But V4's disagreement overlay is wrong for most of its markers (U1/A1), and that is the one MUST-FIX.

Four smaller attribution errors should be fixed in the same pass:
- the #4 and #5 chips (A2, A3)
- the V6 Top-5 column, which contradicts the "just outside" cards (A4)
- the missing business-analyst X2 on #2 (A5)

Layout should be checked at 1100px for the sticky header, V3 wrapping, matrix overflow and clipped V2 labels (U2-U7), and the one-line Safari fix applied (U10).

All of these are local fixes to the existing builder output, not a redesign. A targeted re-run of the html-builder with this audit should be enough.

**Totals:** 7 accuracy errors (A1-A6 substantive, A7 benign) · 24 usability issues (1 MUST-FIX, 12 SHOULD-FIX, 11 NICE-TO-FIX).
