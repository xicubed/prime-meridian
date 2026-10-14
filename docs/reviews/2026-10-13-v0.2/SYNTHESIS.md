# Review Board Synthesis — Prime Meridian GDD v0.2

Moderator synthesis of six reviews (Round 1 + Round 2 cross-examination), 2026-10-13.
Sources: `systems-designer.md`, `narrative-critic.md`, `player-psychologist.md`,
`feasibility-lead.md`, `adversarial-qa.md`, `business-analyst.md`. Every point below
traces to those files; the moderator adds no critiques of their own.

---

## 1. Top 5 issues (severity × confidence)

### #1 — BLOCKING — There is no persistent cast: workers die with their task, so XP, levels, trust, and "familiar helpers" have nothing to attach to
- **Flagged by:** systems-designer (F1, BLOCKING), narrative-critic (F1, BLOCKING), adversarial-qa (F2, BLOCKING → MAJOR), player-psychologist (F4, MAJOR); business-analyst (R2 X1) and feasibility-lead (R2 C4) engaged with the fix.
- **Cross-examination: STRENGTHENED.** Five of six reviewers reached it on their own. The board split the problem into a root cause and a symptom. The root cause is that the cast doesn't persist, and the board held this BLOCKING; systems-designer argues it gets locked in by the week 2–3 bridge mapping. The symptom is the broken XP system. Player-psychologist and adversarial-qa rate that MAJOR because progression ships in week 6. The board converged on one fix: a **persistent persona record** (name, hat, XP, role), probably the §10d crew roles, re-bound to a fresh short-lived session for each task. Feasibility-lead showed that persistent *sessions* would break the §10c cost model. The trust-unlock no-op is part of this issue: the level-3 reward, "ask before merging", is already the §8 default. Systems-designer, adversarial-qa and player-psychologist all flagged it.

### #2 — BLOCKING — The bridge protocol is unspecified in both directions: no source for the `question` event, no write-back for "mark it done"
- **Flagged by:** feasibility-lead (F1, BLOCKING), adversarial-qa (F4, MAJOR, write-back); systems-designer, narrative-critic, player-psychologist and business-analyst all built Round 2 connections on it.
- **Cross-examination: STRENGTHENED.** Feasibility-lead and adversarial-qa together showed that both core-loop crossings are undefined. Inbound "needs you" signals have no defined source, and nothing carries reverse-loop writes outbound. Colleagues then traced the downstream damage:
  - Player-psychologist: a guessed "!" makes the status signal *wrong*, not just hard to read. They upgraded their status finding to MAJOR.
  - Narrative-critic: a guessed `question` breaks Pillar 1's "literally true".
  - Systems-designer: triage is the loop's only decision, and it loses its signal.
  - Adversarial-qa: the despawn trigger and the XP save point have nothing to fire on.

  The proposed remedy is a week-2 spike on an explicit `ask_oracle` tool call, with week 3 gated on it (feasibility-lead C1, business-analyst X5).

### #3 — BLOCKING (beyond localhost) — An exploit chain leads to remote code execution on the machine that holds mail, SMS and repo credentials
- **Flagged by:** adversarial-qa (F4 security note → standalone BLOCKING), feasibility-lead (F4, MAJOR → BLOCKING beyond localhost), business-analyst (F5 privacy; C4 scope fix), player-psychologist (R2 "presentation mode" connection).
- **Cross-examination: STRENGTHENED.** The two halves were separate in Round 1: shareable script-type `:source` mirrors (adversarial-qa) and a publicly hosted client reaching an unauthenticated bridge (feasibility-lead). Combined, they form a concrete chain: unauthenticated `possess` → an agent edits `mirrors/*.edn` → an arbitrary script runs on the bridge host. Adversarial-qa added that conversational mirror creation ("Hal, I have three chickens") would let any email or SMS the Clerk reads trigger the same path. The board split on the remedy, not the finding. Business-analyst argues the cheap answer is scope (localhost only, cut shared configs) rather than building auth.

### #4 — BLOCKING/MAJOR (disputed) — The "seconds after the hot reload" wow moment contradicts the QA gate and Director merge, and the ungated path has no rollback
- **Flagged by:** adversarial-qa (F1, BLOCKING), feasibility-lead (F3, MAJOR), player-psychologist (F2, MAJOR), systems-designer (F3, MAJOR, latency on every task), business-analyst (F2, MAJOR, the differentiator is outside the MVP); narrative-critic withdrew the river as its signature moment in Round 2.
- **Cross-examination: STRENGTHENED.** Every reviewer accepts the contradiction. Feasibility-lead and adversarial-qa together found that agents verify on headless WebGL2 while the player runs WebGPU. A shader can therefore pass every agent check and still crash the player's client, so the gate doesn't protect against the ungated failure. Adversarial-qa notes that §8 hot-reloads *every* agent edit, so this failure exists from week 1, not only for the river. Severity stays split. Most reviewers say MAJOR because the river is in week 7+, which is stretch. Adversarial-qa says BLOCKING, and feasibility-lead concedes BLOCKING if the river moves into the MVP.

### #5 — BLOCKING — The document is three products for three audiences and never picks one, so the board can't settle several other findings
- **Flagged by:** business-analyst (F1, BLOCKING), player-psychologist (F1, "no first hour", BLOCKING but conditional on this); narrative-critic (R2, mostly concedes); feasibility-lead (F4/F5, the generic mirror engine as debt).
- **Cross-examination: STRENGTHENED.** Business-analyst showed that the severity or fix of at least five colleague findings depends on the audience decision:
  - Player-psychologist F1 and F3
  - Feasibility-lead F4 and F5
  - Adversarial-qa F4

  Player-psychologist agreed that "no first hour" drops to MINOR if v1's audience is narrowed to Michael. Business-analyst framed the guilt problem (player-psychologist F3) the same way: the same feature carries a developer tool but harms a cozy "everyone's game". Narrative-critic accepted the narrowing, provided the epigraph's "for all and none" is rewritten or reinterpreted to match.

---

## 2. Unresolved disagreements

### D1. Is the progression/persistence problem BLOCKING now, or MAJOR until week 6?
- **systems-designer (BLOCKING):** The real decision is persistent character versus disposable process. It gets locked in at week 2 (mock agent identity) and week 3 (session-to-character mapping). After that, persistent roles become a refactor.
- **player-psychologist / adversarial-qa (MAJOR):** Gamify v0 is week 6, outside the MVP, so no player of the course build meets the broken XP loop. Both accept that the *cast* problem is BLOCKING.
- **Escalated:** Does the persona-vs-session model have to be decided before the week 2 milestone, even though progression itself stays in stretch?

### D2. Should XP be able to buy autonomy at all?
- **systems-designer:** Keep a real trust ladder. Start it below the default and fund it only with XP sources the agent can't generate itself (merged-without-rework judged after the merge, or XP granted by the player).
- **adversarial-qa:** On a persistent role, farmed XP builds up indefinitely, and parallel instances of one role multiply the farming rate. Take autonomy off the XP ladder entirely: a human sets trust per role, and XP stays cosmetic. Player-psychologist's alternative is to move progression to the player and the estate.
- **Escalated:** Is trust earned by the agent or set by the human?

### D3. Should the wow loop (river, live change) be pulled into the MVP?
- **business-analyst:** Yes. Without it the course ends with the "3D skin on an agent list" that §2 criticizes. The cheap version is a hot reload of a branch on the local client only, with "reload main" as the rollback.
- **feasibility-lead:** Not before it's specified. Pulling the two least-specified dependencies (`question` and the preview path) into weeks 1–4 just makes the MVP slip. Specify first, schedule second.
- **adversarial-qa:** Only if a sandbox, a last-known-good build and a watchdog move in with it. Neither colleague's estimate includes that work.
- **Escalated:** Does the MVP ship without the differentiator, or take on the safety work needed to include it?

### D4. Where should pressure and scarcity live, and how loud can "overdue" get?
- **systems-designer:** The loop needs scarcity. Cap concurrent worker desks and show a visible approval queue. Michael's merge bandwidth is already the real scarce resource.
- **player-psychologist:** Desk slots are fine, but reject the token allowance. Absence must never escalate. A missed chore should be **one capped, steady overdue state**, not a ladder of increasingly alarming signals.
- **adversarial-qa:** Missed chores need a defined overdue/escalation state. Desk caps also deadlock while the player is away: workers awaiting a merge or input hold their desks, so nothing new can start unless those states release their slot.
- **Escalated:** What is the ceiling on on-screen pressure, and do blocked or awaiting workers count against the cap?

### D5. Should in-character framing wrap live agent output?
- **narrative-critic:** Write a character bible and a rule for how in-character framing wraps live coding-agent output. Otherwise players mostly hear "whatever a coding agent's default register happens to be."
- **business-analyst:** Write a one-page Hal voice sheet and one human-written persona line per agent in the system prompt. A Writer model rewriting frontier output in v1 would add latency and the risk of softening real errors.
- **Escalated:** Is a runtime voice layer in scope for v1?

---

## 3. Quick wins

1. **Fix the trust-ladder contradiction and drop the bad XP source.** Make "ask before anything" the default, or change the level-3 unlock. Remove "question answered quickly" from the XP sources, because it rewards agents for interrupting the player and penalizes absence. (systems-designer F1, adversarial-qa F3, player-psychologist F4)
2. **Bind the bridge to localhost and remove `script` as a mirror `:source` for v1.** This removes the RCE surface instead of defending it, and the public build runs as a demo estate on mock data. (business-analyst C4, feasibility-lead F4, adversarial-qa C1)
3. **Add a persistent "awaiting your approval" state.** Drive it from GitHub open-PR status, not Hermes, and show it as the PR shelf. It's technically cheap, and it fixes "Done looks identical to Idle". (player-psychologist F5, feasibility-lead X4, systems-designer R2 connection)

---

## 4. Verdict

The board finds that the document is not ready to drive production. No reviewer disputes the pitch. The blockers are definitions the plan depends on but never gives. Three come from the core design. First, nobody persists across tasks, so the cast, the progression and the trust ladder have no subject. Second, the bridge has no way to say "this agent needs you" or to write back "Flora is fed", so both directions of the core loop are unspecified. Third, the headline wow moment contradicts the review gate the document also requires. A fourth blocker is the security exploit chain that makes any deployment beyond localhost unsafe. Business-analyst argues convincingly that the audience decision is upstream of several of these. Even so, the single change that the most reviewers converged on, which several resolutions depend on and which the schedule locks in earliest (week 2–3), is this: **define a persistent persona record for each crew role, re-bound to a fresh task-scoped session, and specify the bridge events that drive it** (an explicit `question`/`ask_oracle` call and a chore write-back). That one decision gives the estate a cast, gives progression a subject, keeps the §10c cost model intact, and turns the 13-role crew from overhead into content.
