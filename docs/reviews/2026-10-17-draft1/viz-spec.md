# Visualization Spec: Prime Meridian Review Board (interactive)

Target file: `review-viz.html` (built by html-builder from `reviews/viz-data.json` + this spec + `reviews/SYNTHESIS.md`).

## 0. What the page has to get across

A reader should come away with four things, in this order:

1. **What is wrong.** There are five top issues, and all five got STRENGTHENED under cross-examination.
2. **What changed.** Cross-examination raised the severity of the board's findings: BLOCKING went from 7 to 11, nothing was fully withdrawn, and one new finding appeared.
3. **Who argued with whom.** Every reviewer engaged with all five colleagues. Conflicts cluster around four unresolved disagreements, and three of those rest on one open product decision.
4. **Where the details are.** Any finding can be traced back to its source.

Six visualizations, in page order:

| # | Name | Pattern | Insight |
|---|------|---------|---------|
| V1 | Top 5 trail cards | Expandable ranked cards with a 3-stage trail | What matters most, and how it got there |
| V2 | Severity shift | Two-column flow diagram (Sankey-style), Round 1 to final | What cross-examination changed |
| V3 | Severity by reviewer | Unit chart (one square per finding), which also works as the reviewer filter | Who found what, at what severity |
| V4 | Cross-examination matrix | 6x6 grid: author by target, conflicts and connections | Who engaged whom; where the clashes are |
| V5 | Disagreement panels | Position-vs-position panels with a shared-root bracket | What the board couldn't settle |
| V6 | Findings explorer | Filterable, sortable table with expandable rows | The complete record |

### Cut, and why
- **Aggregate severity bar:** V2's node heights already show the aggregate totals for Round 1 and after cross-examination, so a separate bar would repeat them.
- **Force-directed cross-exam network:** every reviewer engaged all five others (30 of 30 possible directed pairs), so a node-link graph would be a complete graph with nothing to read in its shape. The V4 matrix shows the same relationships plus their type and weight, without edges crossing.
- **Outcome distribution chart (SURVIVED / STRENGTHENED / WEAKENED):** 5 of 5 top issues are STRENGTHENED. A chart of 5/0/0 adds nothing that a text tag doesn't. Outcomes appear as tags on V1 cards, and the two "just outside" items carry the only SURVIVED and WEAKENED tags.
- **Quick wins as a chart:** there are three text items and no numbers to plot. Render them as a plain list (see Section 8). They don't count toward the six visualizations.

---

## 1. Global design tokens and rules

- Dark theme (default). Background `#16181D`, surface `#1F232B`, hairline `#2A2F38`, primary text `#E8E6E1`, muted `#8A8F98`.
- **Severity colors are the only hues on the page:** BLOCKING `#E5484D`, MAJOR `#F5A623`, MINOR `#5A6270`. Everything else (matrix shading, unchanged ribbons, chips, focus rings) uses neutral greys only.
- **Severity stamp:** inline-block, 1px solid border in the severity color, transparent fill, text in the severity color, uppercase, `letter-spacing: 0.12em`, 12-13px, padding 2px 8px, border-radius 2px. Use it everywhere a severity label appears in text. Exception: unit-chart squares (V3) and flow nodes (V2) are filled, because they are data marks rather than labels.
- **Outcome tag** (STRENGTHENED / SURVIVED / WEAKENED): the same stamp shape in neutral `#E8E6E1`. Outcomes are not severities and must not borrow severity colors.
- **Reviewer chips:** 1px `#8A8F98` outline, monochrome, reviewer name in lowercase as written in the files (`systems-designer`, etc.). When a reviewer is the active filter, the chip becomes filled `#E8E6E1` with `#16181D` text.
- **Fixed reviewer order** everywhere (rows, columns, chips, legends): systems-designer, narrative-critic, player-psychologist, feasibility-lead, adversarial-qa, business-analyst.
- **Type:** system UI stack for body; monospace (`ui-monospace, SFMono-Regular, Menlo, monospace`) for IDs, counts and axis labels. Body text 16px minimum (the builder floor is 14px; we go higher because the page may be projected). Labels 12px minimum.
- **Layout:** single column, max-width 1100px, centered, 64px between sections, 24px side padding. Hairline rules between rows rather than nested boxes. Border-radius 4px or less, no shadows, no gradients.
- **Motion:** opacity and transform transitions of 150ms or less, used only for state changes (expand, filter dim). No entrance animation beyond an optional single fade-up per section. Under `prefers-reduced-motion: reduce`, set all transitions to 0ms.
- **Light scheme** (`prefers-color-scheme: light`): background `#F4F3F0`, surface `#FFFFFF`, hairline `#D9D7D2`, text `#16181D`, muted `#5C616B`. Severity colors stay the same. Matrix grey ramp inverts (lighter = fewer).
- **Accessibility:** color is never the only signal. Every severity mark carries its text label or an accessible name. Every chart has an `aria-label` that summarizes its content in a sentence. Every interactive element is a real `<button>` or has `tabindex="0"`, role and key handlers. Focus ring: 2px solid `#E8E6E1`, offset 2px.
- **Fidelity:** all finding text comes from viz-data.json, which comes from the review files. Trim for length if needed, but never reword. Do not hardcode counts. Compute them from the data and check them against the values in Section 9.

---

## 2. Page skeleton and shared filter state

```
[Masthead: title, verdict line, 3 stat blocks]
[Sticky filter bar]  (appears once the masthead scrolls out of view)
V1  Top 5 trail cards                         full width
V2  Severity shift  |  V3 Severity by reviewer  side by side at >=1000px, stacked below
V4  Cross-examination matrix + detail panel   full width
V5  Disagreement panels                       full width
    Quick wins (plain list)
V6  Findings explorer                         full width
[Footer: method note + source line]
```

### Masthead (not a visualization)
- Eyebrow "DESIGN REVIEW BOARD · INTERACTIVE". Title from SYNTHESIS.md H1 ("Prime Meridian GDD (first draft)"). Date 2026-10-17.
- Verdict line: the first sentence of the SYNTHESIS verdict ("This document is not ready to drive production."), followed by the "single change that matters most" sentence, trimmed.
- Three stat blocks: **Total findings** (final count, with "30 in Round 1" muted beneath), **BLOCKING** (final count in BLOCKING red, with "7 in Round 1" beneath), **Unresolved disagreements** (count). Numbers 56px monospace. Values come from the aggregate counts in the JSON.

### Shared filter state
A single JS state object drives V2, V3, V4 and V6:

```
state = {
  reviewer: null | reviewerName,        // single-select
  severities: Set(['BLOCKING','MAJOR','MINOR']),
  severityBasis: 'final' | 'round1',    // which severity the severity filter tests
  change: Set(['UPGRADED','DOWNGRADED','UNCHANGED','NEW']),
  flowSelection: null | {from, to},     // set by clicking a V2 ribbon
  search: ''
}
```

**Sticky filter bar** (48px tall, surface background, hairline bottom border):
- Reviewer chips (6) plus an "All" chip.
- Three severity stamps acting as toggles (aria-pressed). Off state = 40% opacity.
- Basis switch: "Severity as of: Round 1 | Final" (segmented, 2 buttons).
- A live summary in monospace: "Showing 11 of 31 findings".
- "Clear filters" text button. It appears only when any filter differs from the default.

How each section responds to the filter state:

| Section | Effect of `reviewer` | Effect of severity, change and search filters |
|---|---|---|
| V1 | Matching chips render filled; nothing is hidden | none |
| V2 | Ribbons recomputed from that reviewer's findings only; node labels read "n of N" | Ribbons whose findings are all filtered out drop to 10% opacity |
| V3 | Other reviewers' rows dim to 30% | Filtered-out squares dim to 30% |
| V4 | That reviewer's row and column stay full strength; other cells dim to 30% | none (V4 is about Round 2 exchanges, not findings) |
| V5 | Matching chips render filled | none |
| V6 | Rows filtered | Rows filtered |

---

## V1. Top 5 trail cards (the centerpiece)

**Pattern:** five full-width ranked cards. Each expands in place to show a three-stage trail: Round 1, then cross-examination, then final status. Below them sit two compact "just outside the top 5" cards in the same format.

**Data used**
- Synthesis top issues: rank, title or problem statement, severity, flagged-by (reviewers plus finding IDs), cross-exam outcome, cross-exam narrative text.
- Synthesis "just outside" items (if the JSON has them; otherwise take them from SYNTHESIS.md, which the builder also reads).
- Findings: for each linked finding ID, its reviewer, ID, title, Round 1 severity, final severity and revision note.
- Round 2 conflicts and connections whose IDs appear in the trail (for the "source exchanges" links).

**Collapsed card (about 160px tall)**
- Left gutter of 120px: rank numeral, 90px, condensed display stack (`"Arial Narrow", "Helvetica Neue Condensed", sans-serif`), muted color at 35% opacity.
- Line 1: severity stamp plus outcome tag (STRENGTHENED, etc.).
- Line 2: problem statement at 26-28px, weight 600. Use the H3 text from SYNTHESIS, trimmed after the colon if it runs past two lines.
- Line 3: "Flagged by" plus reviewer chips. The JSON usually gives finding IDs per chip; if so, show them in monospace inside the chip ("feasibility-lead F1").
- Right edge: a "Show trail" button with `aria-expanded`. The whole card header is clickable and keyboard-focusable.

**Expanded trail.** This is a vertical three-stage timeline. A 1px hairline runs down the left with a stage label at each node.

1. **ROUND 1: Original findings.** One row per linked finding: reviewer chip, ID (mono), Round 1 severity stamp, finding title, and the first sentence of the problem text. Each row has a "Open in table" link that sets the V6 filters to show that finding, expands its row and scrolls to it.
2. **ROUND 2: Cross-examination.** Show the synthesis "Cross-examination" paragraph for this issue, split into reviewer-attributed bullets where it names a reviewer. For example, take "Psych X1: approvals multiply..." and bold "player-psychologist X1" as the lead-in. Beneath it, add a "Source exchanges" line listing the Round 2 item IDs (such as `adversarial-qa X1`, `feasibility-lead C2`) as small buttons. Clicking one opens a popover with that item's full text from the JSON (Esc closes it, and focus returns to the button).
3. **FINAL: Outcome.** The outcome tag, large (20px). Next to it, a severity path for each linked finding, for example `player-psychologist #3  MAJOR → BLOCKING` (stamps with a text arrow). Then any scoping caveat the synthesis names (for #2, the feasibility-lead C2 caveat; for #3, feasibility-lead's F3 downgrade).

**Interaction**
- Click or Enter/Space toggles a card. Several cards can be open at once. An "Expand all / Collapse all" text button sits above card 1.
- Hovering a card header lightens the surface by one step (`#252A33`) and does nothing else.
- The global reviewer filter fills matching chips but never hides cards.

**Just outside the top 5:** two cards with the same structure but no rank numeral. Their header is the muted label "OUTSIDE TOP 5" and the statement is 20px.
- Thesis reversed (narrative-critic F1): SURVIVED.
- Chore events unverified (adversarial-qa F1): WEAKENED. Its final stage should note "kept as a defect; listed as Quick Win 1" verbatim from the synthesis.

Add one muted sentence under the section title: "All five top issues were strengthened in cross-examination. The only SURVIVED and WEAKENED outcomes are just outside the top 5." That is a factual summary of SYNTHESIS.md, not a new critique.

**Link table** (from SYNTHESIS.md, used if the JSON's flagged-by data has no finding IDs)

| Rank | Round 1 findings (primary) | Round 2 items named in the synthesis |
|---|---|---|
| 1 | feasibility-lead F1, business-analyst F1, adversarial-qa F3 (MVP bullet) | feasibility-lead C3, X1; business-analyst C2; systems-designer (MVP connection); narrative-critic (Feasibility F1 + Business #1 connection); player-psychologist X4, revision #2 |
| 2 | adversarial-qa F2, adversarial-qa F4, player-psychologist #3, player-psychologist #5, systems-designer F4 | player-psychologist X1, X2; adversarial-qa X1, X2; feasibility-lead C2; systems-designer (max-autonomy connection); narrative-critic / business-analyst X2, X4 connections |
| 3 | player-psychologist #1, business-analyst F3, narrative-critic F2, feasibility-lead F3, systems-designer F3 | business-analyst X3; player-psychologist X3; systems-designer F3 revision; feasibility-lead F3 revision |
| 4 | feasibility-lead F2 | feasibility-lead X3; adversarial-qa X1, X3 |
| 5 | narrative-critic F3, adversarial-qa F5 | adversarial-qa X5; player-psychologist X6; business-analyst X5; narrative-critic F3 revision and conflict vs. player-psychologist #5 |
| out | narrative-critic F1 (SURVIVED); adversarial-qa F1 (WEAKENED) | systems-designer conflict vs. narrative-critic F1; business-analyst C3; systems-designer and feasibility-lead C1 vs. adversarial-qa F1; player-psychologist C2 |

**Size:** full width (1100px). Collapsed stack is about 5 × 176px. An expanded card is typically 500-800px.

---

## V2. Severity shift (finding flow)

**Pattern:** a two-column flow diagram, Sankey-style. Draw it in SVG; no library.

**Data used:** for each finding, its Round 1 severity, its final severity (after Round 2 revisions), a revision qualifier, and whether it is new in Round 2. Revision qualifiers include upgraded, downgraded, strengthened, narrowed, reframed, partially withdrawn and unchanged. Use the aggregate counts only as a cross-check.

**Layout**
- SVG of about 620 × 380px.
- Left column header "ROUND 1", right column header "AFTER CROSS-EXAMINATION", both 12px mono, muted, uppercase.
- Left nodes from top to bottom: BLOCKING, MAJOR, MINOR, then a fourth node "NOT IN ROUND 1" drawn with a 1px dashed muted outline and no fill.
- Right nodes: BLOCKING, MAJOR, MINOR.
- Node height is proportional to count, with a 10px minimum so a count of 1 stays visible and clickable. Nodes are 18px wide and filled with the severity color. Each node is labeled on its outer side with the severity word and count, for example "BLOCKING 7" and "11 BLOCKING", at 14px mono.
- Ribbons are cubic Bézier bands whose width is proportional to count.
  - **Same-severity ribbons** (B→B, M→M, m→m) are neutral `#3A404B` at 60% opacity. They are the background.
  - **Changed ribbons** are filled with the *destination* severity color at 55% opacity. The NEW ribbon gets the destination color with a dashed 1px outline.
  - Each changed ribbon has an inline label at its midpoint in 12px mono `#E8E6E1`, for example "4 UP", "2 DOWN", "2 UP", "1 NEW". Add a dark text halo if the label overlaps a ribbon.
- Under the chart, a one-line legend in text: "Grey = severity unchanged. Colored = severity changed (color shows the new severity)."
- Then a muted summary line computed from the data, in the form "6 upgraded · 2 downgraded · 22 unchanged · 1 new · 0 fully withdrawn". The "0 fully withdrawn" part is a fact from the files. Partial withdrawals (narrative-critic F5 and the bedtime sub-point of adversarial-qa F3) stay inside their findings, and the "unchanged" tooltip lists them.

**Interaction**
- **Hover a ribbon:** raise it to 85% opacity, dim the others to 25%, and show a tooltip (max 360px wide) listing every finding in the ribbon as `reviewer ID: title` plus its revision qualifier. For unchanged ribbons, group the list by qualifier: strengthened / narrowed / reframed / partially withdrawn / unchanged.
- **Hover a node:** highlight all its ribbons. The tooltip shows "MAJOR in Round 1: 20 findings → 14 stayed MAJOR, 4 to BLOCKING, 2 to MINOR" (computed).
- **Click a ribbon (or Enter when focused):** sets `state.flowSelection`. V6 filters to exactly those findings, and a dismissible chip appears in the filter bar ("Flow: MAJOR → BLOCKING (4) ×"). Do not auto-scroll. A text link under the chart, "Show these 4 findings in the table", does the scroll. Clicking the same ribbon again clears the selection.
- **Reviewer filter active:** recompute ribbons from that reviewer's findings. Node labels become "2 of 7". Ribbons with zero findings disappear (they don't collapse to hairlines).
- **Keyboard:** ribbons are focusable in order from top to bottom. Each has an `aria-label` such as "4 findings moved from MAJOR to BLOCKING".

**Chart aria-label:** "Severity of 30 Round 1 findings and 1 new finding before and after cross-examination; BLOCKING rose from 7 to 11." Compute the numbers from the data.

**Size and position:** left half of the V2/V3 row (about 620px). At widths below 1000px it goes full width, stacked above V3. Section title: "What cross-examination changed".

---

## V3. Severity by reviewer (unit chart that also works as the filter)

**Pattern:** six rows, one per reviewer. Each finding is one square. The row label is the reviewer filter control.

**Data used:** findings (reviewer, ID, title, Round 1 severity, final severity, `is_new`).

**Layout**
- About 440px wide. Each row is 44px tall with hairlines between rows.
- The row label (150px, 14px) is the reviewer name, rendered as a button.
- To its right are squares of 28 × 28px with a 4px gap. Each square is filled with its severity color and shows the finding ID centered in 11-12px mono: dark `#16181D` text on BLOCKING and MAJOR, `#E8E6E1` on MINOR. Order squares BLOCKING, then MAJOR, then MINOR, and by ID within each severity.
- At the right edge of each row, the counts in mono read `B 2  M 3  m 0`. A header row above shows three severity stamps over those count columns so the abbreviations are defined.
- Above the rows, a segmented toggle: "Round 1 | After cross-examination", defaulting to "After". This toggle sets `state.severityBasis` too, so it stays in sync with the filter bar switch.
- In the "After" view, squares whose severity changed get a 2px `#E8E6E1` outline (a non-color cue). The new finding (adversarial-qa's quota-stall finding) has a dashed 2px outline and only appears in the "After" view.
- Legend line under the chart: "Each square is one finding. Outlined = severity changed in Round 2. Dashed = new in Round 2."

**Interaction**
- **Hover or focus a square:** tooltip with `reviewer ID`, title, and the severity path (`MAJOR → BLOCKING`, or `BLOCKING (unchanged)`).
- **Click a square:** sets V6 to show that finding (it clears conflicting filters, expands the row and scrolls to it). This is a deliberate scroll because the user asked for one specific item.
- **Click a row label:** toggles `state.reviewer` to that reviewer. The label gets a filled-chip style while active. A second click clears it.
- **Toggle Round 1 / After:** squares re-sort and re-color. Animate with a 150ms cross-fade, or switch instantly under reduced motion. Changed squares stay identifiable by their outline only in the After view.

**Why keep this next to V2:** V2 shows how severity moved across the whole board. V3 shows how each reviewer's findings break down. For example, feasibility-lead and adversarial-qa came in with two BLOCKING findings each, and feasibility-lead is the only reviewer whose findings moved down. It also gives the reader a quick way to filter by reviewer.

**Size and position:** right half of the V2/V3 row (about 440px), or full width below 1000px. Section title: "Who found what".

---

## V4. Cross-examination matrix

**Pattern:** a 6 × 6 grid. Rows are the Round 2 author ("who wrote it"). Columns are the reviewer whose findings were engaged ("whose finding"). Each cell counts conflicts and connections. The diagonal shows self-revisions. A pinned detail panel shows the exchanges behind a cell.

**Data used:** Round 2 cross-examination data per reviewer:
- conflicts: author, target reviewer(s), target finding ID(s), summary
- connections: author, the reviewers combined, finding IDs, summary
- revisions: author, finding ID, direction, reason

The matrix also uses the unresolved disagreements (participants on each side, title) to mark cells.

**Counting rules (state these in a tooltip on the "?" next to the title)**
- A conflict or connection item is counted in cell (author, target) once for each distinct colleague it names. An item naming two colleagues (for example, systems-designer's "vs. Player Psychologist F4 and Adversarial QA F3") counts in both cells.
- Connections that name several colleagues count in each named colleague's cell.
- The diagonal (author = target) counts that author's Round 2 revisions.

**Cell rendering (each cell 84 × 84px)**
- Top-left: `C n` (conflicts). Bottom-right: `X n` (connections). Both 14px mono. Leave zeros as an empty string so the eye finds the non-zero cells. These labels match the C1/X1 numbering the reviewers used in their own files.
- Background is a neutral grey ramp by total (C + X): 0 is `#1F232B`, 1-2 is `#2A2F38`, 3-4 is `#353B46`, 5 or more is `#424956`. No hue.
- **Disagreement marker:** if cell (a, b) holds at least one conflict *and* a and b both appear in the same unresolved disagreement from SYNTHESIS, give it a 2px solid `#E8E6E1` inset border and a 11-12px mono label in the top-right corner naming the disagreement(s), for example `D1` or `D1 D2`. Disagreement numbering follows SYNTHESIS (D1 closing time, D2 autonomy suggestion, D3 "needing the game less", D4 Construct script).
- Diagonal cells use a hatched background (1px diagonal lines `#2A2F38`) and show `R n` (revisions) centered.
- Margins: row totals on the right ("engaged out: n") and column totals along the bottom ("engaged by others: n"), both 12px mono muted. The column totals answer "whose lens did others build on most".

**Expected pattern to check against (not to hardcode):** every off-diagonal cell is non-zero. Mutual conflicts exist between systems-designer and player-psychologist, systems-designer and adversarial-qa, systems-designer and narrative-critic (D3), player-psychologist and adversarial-qa (D1 and D2 area), narrative-critic and business-analyst, and feasibility-lead and business-analyst. feasibility-lead's conflict with narrative-critic (D4) is one-way.

**Interaction**
- **Hover or focus a cell:** tooltip with the author → target heading, then the item headings as written (for example, `C1 vs. Adversarial QA #1 (chore event...)`), each 1 line, truncated at 90 characters.
- **Click a cell (or Enter):** pins it. A 2px `#E8E6E1` outline stays on the cell, and the **detail panel** fills with the full summary text of every item in that cell, grouped "Conflicts" then "Connections" then (on the diagonal) "Revisions". Each item shows author chip, item ID, named target finding IDs as mono tags, and the summary text. If the cell has a D marker, the panel ends with a link, "See disagreement D1 below", which scrolls to the V5 panel and briefly outlines it. That outline is static and is removed on the next click; it is not animated.
- **Clicking a row header** (author name) pins the whole row, and the panel lists all of that author's Round 2 items. **Clicking a column header** pins the whole column, and the panel lists every item that engaged that reviewer.
- **Keyboard:** the grid uses `role="grid"`. Arrow keys move between cells, Enter pins, and Esc unpins.
- **Global reviewer filter:** that reviewer's row and column stay at full strength; every other cell dims to 30%.

**Detail panel:** at 1000px and wider, a 380px column to the right of the matrix (the matrix itself is about 640px including labels). Below 1000px it sits below the matrix. Empty state text: "Select a cell to read the exchanges behind it."

**Chart aria-label:** "Matrix of Round 2 cross-examination: rows are the reviewer writing, columns are the reviewer whose findings they engaged; each cell counts conflicts and connections. Every reviewer engaged all five colleagues." (Verify the last clause against the data before including it.)

**Size and position:** full-width section after V2/V3. Section title: "Who argued with whom".

---

## V5. Disagreement panels

**Pattern:** stacked position-vs-position panels, one per unresolved disagreement, with a bracket marking the disagreements that share a root decision.

**Data used:** unresolved disagreements, each with: id, title, side A (label, reviewers, argument), side B (label, reviewers, argument), any additional positions ("also on the table"), and the escalated decision. Source conflict item IDs come from the Round 2 data for the "Source exchanges" links.

**Layout (per panel)**
- Header: `D1` in mono (muted) plus the title at 22px, for example "Closing time: enforced lock or honest nudge?"
- Two equal columns separated by a 1px vertical hairline. Each column has its position label as a 12px uppercase muted heading ("KEEP THE LOCK MEANINGFUL"). Below that, one block per reviewer on that side: reviewer chip with their item ID ("business-analyst C1") and the argument text at 17-18px.
- **Also on the table** (when present, as in D1 with systems-designer and adversarial-qa C2, and D3 with business-analyst C3): a full-width row under the columns. It is labeled in muted caps and lists each reviewer chip with their argument. It sits outside both columns because those reviewers took neither side.
- **Decision escalated:** a full-width final row with a 1px `#E8E6E1` left rule, 18px, the escalated question verbatim.
- **Source exchanges:** a line of small buttons listing the Round 2 item IDs behind the panel. Clicking one opens the same popover as in V1.

**Shared-root bracket:** above D1, a 1px muted vertical bracket runs down the left margin across panels D1, D2 and D3, labeled "Open product decision: productivity tool or wellness game? (business-analyst F2)". The source is the SYNTHESIS verdict: "it sits underneath the bedtime, autonomy and long-term-win disputes". D4 sits outside the bracket. Below 1000px, replace the bracket with a single text line above D1: "D1–D3 rest on one open product decision: ...".

**Interaction**
- No collapse. The four panels are always open, because they are short and are the second most important content.
- A cell's "See disagreement Dn" link in V4 scrolls here and outlines the matching panel with a 2px `#E8E6E1` border until the next click.
- The global reviewer filter fills matching chips. Nothing is hidden.
- Empty state, if the JSON holds zero disagreements: "The board reached consensus. Rerun Round 2 if that seems too easy."

**Size and position:** full width, after V4. Section title: "What the board could not settle".

---

## V6. Findings explorer

**Pattern:** a filterable, sortable table of every finding (30 from Round 1 plus 1 new), with rows that expand.

**Data used:** findings: reviewer, ID, title, description, source/passage, Round 1 severity, final severity, change type, revision note, and a `top5_rank` derived from the V1 link table. If the JSON's conflicts and connections carry target finding IDs, also derive "cited by" (the list of colleagues whose Round 2 items name the finding).

**Columns**

| Column | Width | Content | Sortable |
|---|---|---|---|
| Reviewer | 170px | chip | yes (fixed reviewer order) |
| ID | 56px | mono, e.g. `F4`, `#3` (keep each reviewer's own numbering style) | yes |
| Finding | flex | title, 16-18px, max 2 lines collapsed | no |
| Round 1 | 110px | severity stamp, or muted "NEW" | yes |
| Final | 110px | severity stamp | yes (default sort) |
| Change | 110px | text tag: UP / DOWN / SAME / NEW in mono; UP and DOWN in `#E8E6E1`, SAME muted | yes |
| Cited by | 80px | integer (only if derivable; otherwise omit the column entirely and say nothing) | yes |
| Top 5 | 56px | `#2` in mono if the finding is linked to a top issue, else blank | yes |

- **Default sort:** Final severity (BLOCKING first), then Round 1 severity, then reviewer order, then ID. Clicking a header toggles ascending and descending. Show the active sort as a text marker (`▲`/`▼` characters are acceptable, or the words "asc"/"desc") and set `aria-sort` on the header.
- **Filters:** driven by the sticky filter bar (reviewer, severities, basis, change, flow selection) plus a search box above the table that matches title, description and ID. Change-type toggles (UP / DOWN / SAME / NEW) sit next to the search box and set `state.change`.
- **Count line:** "Showing n of 31 findings" (computed). Empty state: "No findings match these filters." followed by a "Clear filters" button.

**Expanded row** (click the row, or Enter/Space on the focused row, with `aria-expanded`): a panel indented under the row containing:
1. **Problem:** the full description.
2. **Source:** the passage or omission referenced, in muted italic.
3. **Round 2 revision:** the author's own revision note verbatim with its direction, for example "Upgraded MAJOR → BLOCKING: Combined with QA F4 and Narrative F1...". If there was no revision, write "No change stated in Round 2."
4. **Referenced in Round 2 by:** chips with item IDs (for example "player-psychologist X1"). Each one opens the item popover. Only show this if it can be derived.
5. **Top 5 link:** if `top5_rank` is set, "Part of top issue #2", which scrolls to the V1 card and expands it.

Several rows may be expanded at once. Hairlines go between rows. An expanded row gets the surface background `#1F232B`.

**Size and position:** full width, last section before the footer. The table header sticks under the filter bar while the table scrolls. Section title: "Every finding".

---

## 8. Non-visual content blocks

- **Quick wins:** a numbered plain list of the three quick wins from SYNTHESIS, each with its attribution line in muted text. Place it after V5. Use no stamps, because the quick wins carry no severity.
- **Footer:** "Six isolated agent contexts · parallel review · cross-examination · moderated synthesis" plus the source line "Source: Prime Meridian GDD, first draft (gdd.txt). Reviews: reviews/*.md. Synthesis: reviews/SYNTHESIS.md."

---

## 9. Reference values for cross-checking

The builder must compute everything from viz-data.json. The table below is the designer's reading of the review files, given so the builder and auditor can catch extraction errors. If the JSON disagrees, the review files win. Note the discrepancy in the builder summary rather than silently picking one.

**Totals (stated in SYNTHESIS.md):** Round 1: 30 findings (7 BLOCKING, 20 MAJOR, 3 MINOR). Final: 31 findings (11 BLOCKING, 17 MAJOR, 3 MINOR). Unresolved disagreements: 4.

**Severity transitions (V2 ribbons):** B→B 7 · M→B 4 · M→M 14 · M→m 2 · m→M 2 · m→m 1 · new→M 1. That is 22 unchanged, 6 upgraded, 2 downgraded and 1 new.

**Per-finding (V3 / V6):**

| Reviewer | ID | Short title | R1 | Final | Round 2 qualifier |
|---|---|---|---|---|---|
| systems-designer | F1 | "Real decision" (night-shift allocation) unmodeled | MAJOR | MAJOR | strengthened |
| systems-designer | F2 | Chapter win is a 7-day streak | MAJOR | MAJOR | strengthened |
| systems-designer | F3 | Two progression tracks never connect | MAJOR | MAJOR | one claim revised |
| systems-designer | F4 | Long-term win has no hook; autonomy dial has no feedback | MAJOR | BLOCKING | upgraded |
| systems-designer | F5 | Worm stock has no refill | MINOR | MAJOR | upgraded |
| narrative-critic | F1 | Thesis stated but structurally reversed | BLOCKING | BLOCKING | evidence corrected |
| narrative-critic | F2 | Cast contradiction; who is the Oracle for non-authors | MAJOR | MAJOR | strengthened |
| narrative-critic | F3 | No in-world response to real failure or loss | MAJOR | BLOCKING | upgraded |
| narrative-critic | F4 | Emotional peaks written in film quotes | MAJOR | MAJOR | strengthened |
| narrative-critic | F5 | Supporting cast are job titles | MINOR | MINOR | partially withdrawn |
| player-psychologist | #1 | First real connection undesigned | BLOCKING | BLOCKING | narrowed |
| player-psychologist | #2 | Waiting is unfilled | MAJOR | MAJOR | unchanged |
| player-psychologist | #3 | Approval fatigue; spatial fails for non-visual work | MAJOR | BLOCKING | upgraded |
| player-psychologist | #4 | Jack-outs and bedtime lock have no override | MAJOR | MAJOR | reframed |
| player-psychologist | #5 | Win conditions agent-driven; overgrowth shames | MAJOR | MAJOR | strengthened |
| feasibility-lead | F1 | MVP cut line leaves out the core loop | BLOCKING | BLOCKING | strengthened |
| feasibility-lead | F2 | Bridge unspecified, one week | BLOCKING | BLOCKING | strengthened |
| feasibility-lead | F3 | Sample + real doubles integration; external gates | MAJOR | MINOR | downgraded (7-week plan only) |
| feasibility-lead | F4 | Physics claims don't fit stack | MAJOR | MINOR | downgraded |
| feasibility-lead | F5 | Production model depends on product, approver, caps | MAJOR | MAJOR | strengthened |
| adversarial-qa | F1 | Chore events unverified, duplicated, no restock | BLOCKING | BLOCKING | narrowed |
| adversarial-qa | F2 | "!" can be forged | BLOCKING | BLOCKING | strengthened |
| adversarial-qa | F3 | Closing time bypassable; hanging state; not in MVP | MAJOR | MAJOR | one point withdrawn, one handed off |
| adversarial-qa | F4 | Autonomy dial contradicts "approves every change" | MAJOR | MAJOR | scope widened |
| adversarial-qa | F5 | Jack-outs have no data source; no pet death | MAJOR | MAJOR | strengthened |
| adversarial-qa | F6 (new) | Quota-stalled agent invisible; keycard never returned | (new) | MAJOR | new in Round 2 |
| business-analyst | F1 | MVP lacks core loop, win condition, main pillar | BLOCKING | BLOCKING | narrowed ask |
| business-analyst | F2 | Several products at once | MAJOR | MAJOR | strengthened |
| business-analyst | F3 | Audience undefined | MAJOR | BLOCKING | upgraded |
| business-analyst | F4 | No business model | MAJOR | MAJOR | figure corrected |
| business-analyst | F5 | Film IP | MINOR | MAJOR | upgraded |

The ID "F6" for adversarial-qa's new finding is a label for display. The review file calls it "New, from X3". If the JSON uses a different ID, use the JSON's ID and keep "from X3" in the revision note.

---

## 10. Build checklist (for the html-builder)

- [ ] One state object, one `render()` per section, each called on state change. No section reads the DOM of another.
- [ ] All counts computed from embedded data; masthead, V2 labels, V3 counts and the V6 count line agree with each other.
- [ ] Severity color appears only on severity marks (stamps, V2 nodes and changed ribbons, V3 squares, the BLOCKING stat number).
- [ ] Every chart and grid has an aria-label; every clickable thing is reachable by Tab and operable by Enter/Space; Esc closes popovers and unpins the matrix.
- [ ] Nothing animates under `prefers-reduced-motion: reduce`.
- [ ] Below 1000px: V2/V3 stack, the V4 detail panel moves below the matrix, and the V5 bracket becomes a text line.
- [ ] No auto-scroll except on explicit "open in table / see disagreement / show these findings" links and square clicks.
