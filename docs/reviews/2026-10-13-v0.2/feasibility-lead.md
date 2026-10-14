# Technical Feasibility Lead — Round 1

Document reviewed: *Prime Meridian — Game Design Document*, v0.2, October 2026. I judged it against what was available in late 2026: three.js r186 with `WebGPURenderer`, frontier-model agent sessions (Hermes), flat-rate subscription plans, and a course timeline of about one milestone per week.

---

## F1. The bridge's agent-event contract is the critical path, and the document never specifies it — BLOCKING

**Problem.** Weeks 3 to 5 of the MVP depend on Hermes emitting structured lifecycle events. Those weeks cover live statuses, streamed talk, "!" questions, and possess. The document assumes the events exist but never says where they come from. The hardest one is `question`. A CLI agent session that is "waiting on a decision" usually just stops producing output. Telling that apart from "thinking," "stalled," or "finished" needs either changes to the agent harness or a heuristic, and the document names neither. Possess ("your messages bypass Hal") and streamed replies also require the bridge to inject input into, and tap output from, live sessions it did not start. That is session multiplexing, not a websocket relay.

**Passage.** §10: "`agent-event {agent, kind: working|question|done|error, payload}` from Hermes." This is labelled "sketch." §12, Week 3: "real Hermes job/session status shows on characters." Week 5: "agents raise '!', walk to you, resume on answer; possess toggle." §10 also lists "later any CLI agent," which promises the same contract for agents nobody on the team controls.

**Why it matters.** Pillars 1, 3 and 4 all sit on this one interface. If Hermes cannot reliably emit `question`, the status vocabulary in §7 falls back to working/idle, and the core loop in §5 loses its "spot who needs you" step. The risk list in §13 does not mention it.

---

## F2. The rendering architecture is unstated: an immutable CLJS state map versus three.js's mutable scene graph, on a renderer agents may not be able to run headless — MAJOR

**Problem.** §10 says "the world is one immutable state map re-rendered on change." three.js is a mutable, retained-mode scene graph, so something has to diff world state into scene mutations every frame for walking, pacing and typing animations. That reconciler is the real engine of this game, and no milestone, owner task or risk covers it. The §13 interop mitigation ("a small `three.cljs` wrapper") handles call syntax, not state reconciliation or per-frame allocation pressure from persistent data structures. Separately, §10b pins `WebGPURenderer`, and §10 justifies three.js because it "runs in a browser tab (so agents can test it)." Agents test in headless browsers, where WebGPU is often missing and three.js silently falls back to its WebGL2 backend. Agents would then be verifying a different render path from the one the player sees. In practice the Environment Artist's "shaders" (§10d) have to be TSL node materials under WebGPURenderer, not classic GLSL `ShaderMaterial`, which shrinks the "huge corpus" advantage claimed in §10.

**Passage.** §10: "re-rendered on change; REPL into the live scene." §10b: "All of three.js goes through one wrapper namespace on three 0.186 / `WebGPURenderer`." §10b: "The builders can't see the screen."

**Why it matters.** A naive "re-render on change" causes frame hitches every time a bridge snapshot arrives, and §10 has the bridge push full `world-state` snapshots. That collides with §10b's own top constraint: "a game has to feel alive every frame."

---

## F3. The showcase loop (pillar 5, the "one wow agent") is out of MVP scope, and the pipeline as written cannot deliver the promised "seconds" turnaround — MAJOR

**Problem.** The document calls the Environment Artist the crew's "most visible output and the purest form of the core loop," and pillar 5 is "the world is the showcase." The river is still scheduled in Week 7+ ("Polish, the river, sound"), and §12 says "Everything after [week 4] is stretch." So the headline feature is formally a stretch goal. Beyond scheduling, the described turnaround cannot happen under the document's own process. §10d promises "that exact spot change in front of them seconds after the hot reload." But §10a/§10d require that every change compiles with 0 warnings, passes the QA Tester gate (which "asks the player for screenshots," because builders "can't see the screen"), and is merged by Michael, who "merges every PR." The realistic loop is minutes to hours, gated on the human twice: once for the screenshot and once for the merge.

**Passage.** §10d: "seconds after the hot reload"; "Director (Michael, human): vision, merges every PR"; "QA Tester (gates every PR)." §10b: "real visual review happens through the player's screenshots." §12: "7+ | Polish, the river, sound."

**Why it matters.** The document never says whether pillar 5 is a live in-session preview (hot reload of an unmerged branch, which needs a branch-preview mechanism nobody has specified) or a post-merge result. Those are different features with different costs. As written, the most likely thing to be cut is the feature the document says makes the game alive, and the document does not acknowledge that.

---

## F4. The integration surface contradicts the document's own scope cap, and deployment exposes private data with no security model — MAJOR

**Problem.** §13 caps the course at "four live data sources max (tank, mailbox, board, one repo)." The world table in §6 wires Gmail API, Calendar, Reminders, Twilio, GitHub, Hermes sessions and file-based mirror state. That is six or seven integrations, each with its own auth (Google OAuth consent and token refresh, Twilio webhooks needing a public endpoint, GitHub tokens). "The board" alone is two sources (Calendar plus Reminders). The phone booth and Twilio have no milestone and are not in the cap, but they are in the world spec. Deployment is also unaddressed. §10 targets "static hosting (could live on lotect.com)," while the bridge reads `~/.hermes/state/worms.json` from a local disk and relays prompts into agent sessions that can commit code and read the owner's mail and SMS. A publicly hosted client then has to reach a bridge on a personal machine. Nothing covers authentication, network exposure, or who may send `say`/`possess` messages. The single "degrade gracefully" line in §10b has no spec behind it.

**Passage.** §6 table (Gmail API / Calendar, Reminders / Twilio / GitHub). §13: "four live data sources max." §10: "static hosting (could live on lotect.com)." §9a: `:source "~/.hermes/state/worms.json"`.

**Why it matters.** Each OAuth integration is roughly a milestone-week of fiddly work with nothing to show for it on screen. An unauthenticated websocket that can `possess` an agent with repo and mail access is a real security hole, not a polish item.

---

## F5. Large infrastructure commitments have no milestones: the 13-agent dev crew and the generic mirror engine, with one human merging everything — MAJOR

**Problem.** §10d specifies a 13-role studio: file-ownership scopes, cross-owner change requests, Lead Architect decision records, a QA gate on every PR, a tiered model split including open-weight models up to ~130B, and an asset intake pipeline with license ledgers. Setting up and debugging that orchestration is a project in its own right, and none of it appears in §12. Week 1 is "GDD + repo scaffold," and the closing line only adds `AGENTS.md` and `TASKS.md`. In the same way, §9a commits to a generic, data-driven mirror system (a rules DSL with `:when` predicates, chore scheduling, a model fallback by `:kind`, user-supplied GLTF models, multiple mirrors sharing a `:home`), declared as a hard design rule ("never as code that knows what an axolotl is"). The only related milestone is Week 3's "tank reads real worm count," which a hardcoded Flora would satisfy. The bottleneck of all this parallel agent output is one human. Michael merges every PR and supplies every screenshot for visual review, on a Tue/Thu course schedule.

**Passage.** §10d crew table and tier table. §9a "Design rule." §12 milestone table. §10d: "merges every PR." §12: "agents doing most of the build between classes."

**Why it matters.** Either the crew and mirror engine are built properly and the MVP slips past week 4, or week 3 hardcodes Flora and pillar 2 ("Everyone's game") quietly becomes debt. The document does not say which. Throughput is capped by human review, not agent capacity, and the plan never sizes that.

---

### Summary of severities
| # | Finding | Severity |
|---|---|---|
| F1 | Hermes agent-event contract (esp. `question`, possess, streaming) unspecified on the critical path | BLOCKING |
| F2 | Immutable-state to three.js reconciler unspecified; WebGPU headless verification gap | MAJOR |
| F3 | Pillar 5 / Environment Artist is stretch-scoped, and the "seconds" loop conflicts with the QA/merge gates | MAJOR |
| F4 | Integrations exceed the self-imposed cap; no auth/security model for a public client reaching a private bridge | MAJOR |
| F5 | Dev crew orchestration and generic mirror engine unscheduled; single-human merge bottleneck | MAJOR |

---

## Round 2 — Cross-examination

### CONFLICTS

**C1. Business Analyst F2 ("move '!' questions and the river into the MVP") vs. my F1 and F3.**
The BA is right that weeks 1–4 as scheduled produce "a 3D skin on an agent list." But the recommended fix is a reschedule, and it would pull the two least-specified technical dependencies in the document into the must-ship window. "Questions come to you" depends on the `question` event, which I rated BLOCKING because nothing defines how a stalled CLI session signals "waiting on a decision" (F1). "Ask for the river and watch it change" depends on a hot-reload/preview path that nobody has specified and that conflicts with the merge gate (F3). If those two move into weeks 1–4 without a spec, the MVP doesn't become more distinctive. It just slips. My counter-proposal is that the MVP should contain a **week-2 spike on the agent-event contract**. The most feasible version is an explicit `ask_oracle` tool the agent calls, with no output-silence heuristic. Week 3 should then gate on it. Questions can move into the MVP only after that spike succeeds. The ordering is the point: specify first, schedule second.

**C2. Adversarial QA F1 (live self-rewrite, rated BLOCKING) vs. my F3 (rated MAJOR).**
We agree on the mechanism: the "seconds" promise and the PR gate cannot both hold. We disagree on severity. The document itself puts the river in week 7+ and declares everything after week 4 stretch, so this doesn't block the MVP as scoped. It is a decision the document defers, not a hole that stops the build. I keep it MAJOR, but QA's "ungated path" adds a real requirement I missed (see Revisions). One caveat: if the board adopts the BA's proposal to move the river into the MVP, QA's BLOCKING rating becomes correct, and I'd concede it.

**C3. Business Analyst F3 (cut the crew to MVP roles) — agrees with my F5 but misses the binding constraint.**
Staffing Producer/Engine/Bridge/QA first is a good change, but it treats crew size as the bottleneck. The bottleneck is the one human. Michael merges every PR and supplies every screenshot, and that doesn't change whether 4 or 13 agents are producing PRs. Cutting the crew reduces setup overhead. It doesn't raise throughput. The document still needs a stated merge budget (PRs per class day) or a delegated merge rule for low-risk owners (for example, QA-passed changes inside `pm.env.*`).

**C4. Systems Designer / Player Psychologist / Narrative Critic / Adversarial QA ("make workers persistent") — the proposed fix has a cost the reviewers didn't price.**
Four colleagues independently flag that task-scoped workers can't carry XP. I agree that the problem is real and outside my lens. But "make workers persistent, named crew members" (Psychologist F4), read as persistent *sessions*, breaks the cost model. §10c names task-scoped workers as "the main control," and the Systems Designer's own math shows a bloated long-lived session reaching ~$0.25/call, roughly five times the stated per-task figure. The feasible version is a **persistent persona record** (name, hat, XP, role) stored in bridge state and re-bound to a fresh, short-lived session for each task. The §10d crew roles already supply the persistent identities. This is cheap and keeps §10c intact. Persistent sessions are not.

### CONNECTIONS

**X1. The bridge protocol is incomplete in both directions (my F1 + Adversarial QA F4).** QA found that there's no write-back message for "mark it done." I found that the inbound `question` event has no source. Taken together, the §10 protocol sketch can't carry the reverse loop out *or* the "needs you" signal in. Both halves of the §5 core loop that cross the bridge are unspecified. This strengthens F1.

**X2. Shared mirror scripts plus an unauthenticated public client equals remote code execution on the credential host (my F4 + Adversarial QA F4).** QA showed that `:source` can be a "script" and that mirrors are pitched as shareable. I showed that the client is meant to be publicly hosted and reaches a bridge on a personal machine with no auth. Combined: whoever can reach the bridge, or whoever authors a shared mirror, runs code next to the Gmail, Twilio and GitHub credentials. The BA's privacy point (F5, "who can see a hosted estate") is the read-side version of the same gap.

**X3. Ungated hot reload is unverifiable by the agents who ship it (Adversarial QA F1 + my F2).** QA's ungated path sends unmerged agent code into the live client with no rollback. My F2 notes that agents verify in headless browsers, which typically fall back from WebGPU to WebGL2. So a TSL shader that works on WebGL2 headless and breaks on the player's WebGPU path passes every agent check and dies in the player's session. The player is the first real test, and there's no last-known-good build to fall back to.

**X4. Status legibility depends on the event contract (Player Psychologist F5 + my F1).** The Psychologist asks for a distinct "awaiting your approval" state. That's the one state that's technically *cheap*: it can come from GitHub's open-PR status, not from Hermes. "Needs input," the state the Psychologist rightly says must be unmistakable, is the one that depends on the unsolved `question` event. If F1 isn't solved, Needs input collapses into Idle, which is exactly the confusion F5 describes.

**X5. Flat-rate usage caps cap the crew (Systems Designer economy check + my F5).** The Systems Designer notes that on flat-rate plans the binding limit is usage caps, not dollars (~7.5M input tokens per session). A 13-role crew working "between classes" in parallel draws on the same caps the player's own sessions use. So agent throughput is limited by two things the document never sizes: human merge bandwidth and plan quotas. The QA note that the Clerk's "triage summary" needs a model, against the "Observe = $0" claim, is another unbudgeted draw on the same quota.

### REVISIONS

- **F1 — stays BLOCKING, strengthened.** X1 and X4 show that the missing contract affects the reverse loop's write path and the status vocabulary's most important state, not only questions and possess.
- **F3 — stays MAJOR, scope expanded.** I add QA's rollback requirement. Whichever path pillar 5 takes, an agent-authored change must never be able to take down the running client without an automatic revert to a last-known-good build. Upgrades to BLOCKING if the river moves into the MVP (C2).
- **F4 — upgraded to BLOCKING for any deployment beyond localhost.** On its own I rated it MAJOR as a cap violation plus a missing auth model. X2 changes it: script-type mirror sources plus shareable configs plus an unauthenticated public client amount to a concrete remote-code-execution path onto the machine holding mail, SMS and repo credentials. The course build can run localhost-only, and that stays MAJOR. The "could live on lotect.com" target can't ship without an auth model and a ban on (or sandbox for) script sources.
- **F5 — stays MAJOR, refined.** I accept the BA's staged-staffing fix as necessary but not sufficient (C3), and I add usage-cap sizing (X5) as a second unplanned throughput limit.
- **F2 — stays MAJOR.** No colleague engaged with the reconciler, which I still consider the unscheduled core of the engine. X3 gives the headless WebGPU gap more weight than I gave it in Round 1.
