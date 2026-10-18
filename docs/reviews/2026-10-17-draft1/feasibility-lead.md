# Technical Feasibility Lead — Round 1

Document: *Prime Meridian*, GDD first draft (5-page version), October 2026. Judged against 2026 constraints: a 7-week schedule, one human Director (Michael) plus an AI dev crew, browser three.js/WebGPU client, Claude/Hermes/local-model agents.

---

### F1. The MVP cut line leaves out the core loop, and most headline features have no week at all
**Severity: BLOCKING**

**Passage:** Scope (7 weeks): "**MVP = weeks 1–4;** everything after is stretch." The core loop is "observe the estate → spot who needs you ("!") → talk, assign, or answer → agents work → see the result in the world." But "'!' walks to you, jack in/out, closing time" is **Week 5**.

**Problem:** The MVP stops before the "!" mechanic, which is step 2 of the core loop, the subject of the twin rule ("a '!' always means a real question"), and the main expression of Pillar 2 (spatial over textual). Pillar 4 ("It sends you back to your life… locks the door at bedtime") is also stretch. So the version the document commits to doesn't demonstrate the game it describes.

Several systems described as core are missing from the 7-week plan entirely, as either MVP or stretch: the **blueprint** approve/reject flow, **keycards**, the per-agent **autonomy dial**, **night-shift orders** and frontier-desk allocation ("the day's real decision"), **the Construct** first hour ("Five minutes in, you've played the whole game"), the **Architect's room** of monitors, the **Clerk**/mailbox triage, **Pepper**/Stripe, and `:personality` text turned into behavior. The document has no cut list and never says which of these are v1, deferred, or aspirational. As written, a reader can't tell what is getting built.

**What's needed:** An explicit MVP that closes the core loop (talk → assign → work → "!" → answer → approve → world changes) against one mock worker, plus a written list of what is deferred.

---

### F2. The bridge carries the whole design, and it's unspecified and given one week
**Severity: BLOCKING**

**Passage:** Week 3: "the bridge: real statuses, Flora's real worm count, **the snap** end to end." Token budget: "**Observe:** … 0: the bridge reads files and APIs, never a model." Workers show "a progress glow (*reading → writing → tests passing*)." The Architect is "Hermes running inside the editor (Zed), driving Herdr, which reaches across machines." An agent raises "!" "only by calling an explicit `ask_player` tool."

**Problem:** Pillars 1 and 2 ("Real, not simulated"; "Spatial over textual") both depend on one component, a Babashka websocket bridge. That bridge has to normalize live session state from at least three different agent runtimes (Hermes, Claude Code, local open-weight models), routed through a third-party orchestrator (Herdr) across two machines. It also has to do this without a model, because Observe is budgeted at $0. The document never says:
- what each runtime actually exposes (hooks, log files, transcript format) that could yield "reading / writing / tests passing / stuck / idle";
- the event schema the bridge emits to the ClojureScript world map;
- how `ask_player` gets injected into each runtime (MCP server? custom tool per harness?) and how a blocked session resumes when the player answers in-world;
- what a "ghosted blueprint" is for a **code** change, as opposed to a scene change. A preview of an uncommitted change would need a second build or branch loaded live, which is a major subsystem on its own.

One week, shared with the snap animation, is not enough for a component every other feature depends on (talk in Week 4, "!" in Week 5, street cred in Week 6). If the bridge slips, every later week slips with it. The document doesn't name this dependency or plan a fallback.

**What's needed:** A one-page bridge spec covering inputs per runtime, the event schema, and the `ask_player` round trip, plus a de-risking spike before Week 2's mock workers commit to a state model.

---

### F3. "Every connection comes with a sample version" doubles the integration surface, and the real integrations have external gates the document leaves out
**Severity: MAJOR**

**Passage:** "every connection comes with a sample version (pretend inbox, phone, ledger, scripted agents)" and "you play **everywhere your agent lives**: your editor, phone, mail, calendar, blog, and online accounts (Google, Claude, Hermes, Muse, Twilio…)". Week 2 alone is "estate v0, sample connections, three mock workers, HUD." The Constraints table lists Gmail "OAuth and rate quotas", SMS "carrier registration", and iMessage "a local watcher on the Mac".

**Problem:** Each connection has to be built twice: a mock with believable behavior, and a real adapter. Packing every mock connection plus the estate, mock workers, and a HUD into Week 2 is not credible. The mock strategy is a good testing decision (the "test drive is also the test rig" paragraph is the strongest technical idea in the document), but it isn't free. The real-integration column also understates the external, non-engineering lead times:
- Reading Gmail content uses restricted OAuth scopes. Any distribution beyond the developer's own test users requires Google app verification and a third-party security assessment, which takes weeks to months and costs money.
- US application-to-person SMS (e.g., Twilio) requires 10DLC brand and campaign registration, which has approval latency outside the team's control.
- An iMessage "local watcher on the Mac" means reading the Messages database, which needs Full Disk Access and an undocumented schema that can break with OS updates.

None of this blocks a self-hosted demo on the author's own machine. It does block the stated audience ("people who already run an AI agent") and Pepper's "ready to sell the game". The document doesn't separate "works for Michael" from "ships to others".

**What's needed:** Pick 1-2 connections for the MVP (Flora/chores via local data plus one mock worker), and add the platform-approval lead times to the schedule or state plainly that public distribution of real connectors is out of scope.

---

### F4. The physics claims don't fit the stated stack
**Severity: MAJOR**

**Passage:** Stack: "**Physics:** Rapier through three.js's own helper. Characters are active ragdolls with a balance controller, so they stumble, recover, or fall like real bodies; Flora swims as a physical spine." The Architect reaches "a GPU box for local models and physics." Week 7: "Flora swims, one agent walks as an active ragdoll."

**Problem:**
1. three.js's bundled Rapier helper is a thin convenience wrapper for basic rigid bodies. Active ragdolls with a balance controller need articulated joints, motor control, and a tuned controller. That is research-grade character work that teams spend many months on, not a helper call. Here it's one stretch week, shared with Flora's swim.
2. "a GPU box for … physics": Rapier runs in WASM on the CPU inside the browser. Running real-time character physics on a remote machine and streaming the results to a browser frame loop would add network latency to every step. The document never explains the split, and the claim doesn't hold up as written.
3. Interdependency: the core interaction has agents "walk toward you" and "walk to wherever you are". If locomotion goes through ragdoll physics, every "!" depends on pathfinding plus balance stability. The document should commit to animation-driven locomotion with physics limited to reactions, and say so.

The Stack section presents ragdolls as fact, while the schedule treats them as stretch. That inconsistency needs resolving. As written, a reader would expect them in the product.

**What's needed:** Move active ragdolls and the spine swim explicitly to post-v1, state that locomotion is kinematic/animated, and remove or explain "GPU box for physics."

---

### F5. The production model depends on the product, and on one human's approval bandwidth and plan usage caps
**Severity: MAJOR**

**Passage:** "The agents that **build** the game. Each is … visible as a worker in the game's own workshop." "**The Director (Michael, human; the Oracle) approves every change.**" "**v1 staffs five:** the Hero, Lead Architect, Gameplay Engineer, Environment Artist, QA." Token table: "**Assign:** a small task … ~2.5M in (mostly cached) + ~25k out", with the note "Talk turns were measured …; the rest are estimates." "In practice it runs on flat-rate plans, so the binding limit is **usage caps**."

**Problem:**
- **Bootstrapping loop:** the dev crew is meant to work inside the game's workshop, but the workshop, the bridge, and the "!" mechanic are what the crew is building. Until Week 5, the crew can't use its own in-game interface. The document doesn't say the crew works through terminals/Herdr until then, though it presumably must.
- **Single approver:** with five concurrent agents and "approves every change," Michael's review time becomes the throughput ceiling for a 7-week schedule. The document has no estimate of approvals per day.
- **Shared, unmeasured quota:** the per-task cost (~2.5M input tokens) is an estimate, not a measurement. Building the game with a five-agent crew and playtesting it both draw on the same capped plans. At roughly 2.5M tokens per small task, plus revisions and QA reruns, cap exhaustion could stall development for hours per window. Neither the schedule nor the risk table accounts for this. The Claude constraint row covers player-side usage only, not production-side.

**What's needed:** State the pre-Week-5 dev workflow outside the game, measure Assign cost on a real task before relying on it, and budget production token usage separately from play usage, with an approval-batching policy for the Director.

---

**Summary of severities:** 2 BLOCKING (F1 scope/cut line, F2 bridge), 3 MAJOR (F3 integrations, F4 physics, F5 production model). Credit where due: the mock-connection test rig and "the world never waits on a model" event-driven design are sound decisions. The problem is that the schedule doesn't reflect how much they cost to build.

---

## Round 2 — Cross-examination

I re-checked `gdd.txt` before writing this so that every rebuttal points to the text and not just to my Round 1 notes.

### 1. CONFLICTS

**C1. vs. Adversarial QA #1 (chore event unverified/duplicated, BLOCKING): I'd rate it MAJOR from a build standpoint, and the part that can't be fixed is a wording problem, not an engineering one.**
QA is right that nothing dedupes "fed" events, nothing restocks `:stock`, and nothing says which SMS senders are accepted. But two of these fixes are small once the bridge has an event schema (my F2). The data model already gives a natural idempotency key: `:feed :every "2d" :at "17:00"` defines a due window, so "at most one completion per chore per window" collapses the text-plus-canal double count without new infrastructure. Restock can be a manual player event in the MVP. Sender allow-listing is one config line on an SMS adapter that the MVP doesn't need anyway ("gradable without a single real account"). The part that can't be engineered is verification: nothing in the stack (no sensors, "never from location tracking") can tell whether Flora actually ate. That doesn't block Week 3. It means Pillar 1's "it's literally true" has to be reworded to "it's literally what you reported." I'm keeping QA's points but disagree on the severity. This is a spec gap with a cheap fix, not a blocker on the build.

**C2. vs. Adversarial QA #2 (forgeable "!", BLOCKING): it blocks connecting real accounts, not the 7-week plan the document actually commits to.**
The document limits the MVP to sample connections: "the MVP is buildable, demoable, and gradable **without a single real account**… the red team attacks only pretend services." In mock mode, no attacker-controlled content reaches an agent, so the forged-"!" attack has no entry point inside the scoped build. Where QA is right, and where I'm updating my own view, is that the "test drive is also the test rig" line gives false comfort for everything after the MVP. A red team attacking pretend services can't validate real third-party MCP servers. The Construct's rack of "**every skill and MCP connector out there**" is exactly the untested surface. So I'd attach QA's BLOCKING to a specific gate: no real connector ships until `ask_player` events carry provenance (which agent asked, and what untrusted input it read in that session), and Clerk output never feeds an agent that holds keycards. Both need fields in the bridge schema, which adds to F2.

**C3. vs. Business Analyst #1 ask ("Move '!' and jack-out ahead of streaming chat polish"): the dependency order doesn't allow that.**
I agree with BA's diagnosis (it matches my F1). The proposed reordering breaks a dependency, though. The Answer row says "You answer in-world; it resumes," and the answer goes through the conversation channel built in Week 4 ("talk: converse, replies stream as bubbles"). "!" can't come before talk because the player would have no way to respond. "!" also needs the `ask_player` → blocked session → resume round trip, which is the least specified part of the bridge (F2). A buildable order is: bridge spike first, then talk and a *static* "!" marker together inside the MVP, with "walks to you" pathing deferred. Jack in/out and closing time are cheap UI states and can move into the MVP as BA asks. I support that part.

**C4. vs. Systems Designer #1 fix ("a gauge that maps to the real cap window"): the document names no data source for that gauge.**
Observe is "$0: the bridge reads files and APIs, never a model," and §4 says the binding limit is "usage caps" on "flat-rate plans." The document doesn't say where the bridge would read remaining-cap telemetry from. On a vendor-enforced flat-rate plan, the dependable signal is reactive (a limit hit), not a live gauge. A gauge built on a guess would also violate the twin rule ("never a fake passed off as real"). Buildable alternative: the frontier desk count is declared data (fixed N), and a "desk went dark: cap reached" event is shown when it happens. This keeps Systems' goal of a visible, countable resource and drops the telemetry dependency.

**C5. vs. Narrative Critic #4 ("Original lines for the Construct" now): this is off the critical path.**
The Construct isn't in any week of the schedule (my F1, BA #1). Writing original dialogue for an unscheduled feature costs effort before the onboarding flow itself is buildable. I support BA's "cut or explicitly defer the Construct." Once it's scheduled, Narrative's request becomes a real dependency and should be scoped then.

### 2. CONNECTIONS

**X1. Signature Moment #2 can't happen in the MVP, by the document's own definitions.** (my F1/F3 + BA #1 + Narrative #4)
§4 says "Swapping in a real part is the 'I know kung fu' moment," and the same paragraph defines the MVP as working "without a single real account." So the "One wow" can only run after the MVP, and only on real connectors, which are the surface QA #2 shows to be unsafe and my F3 shows to be externally gated (Gmail verification, 10DLC). The document's headline moment sits behind every unresolved risk at once.

**X2. Restock via receipts runs through the riskiest pipe in the system.** (Systems #5 + QA #1 + QA #2 + my F3)
Systems and QA both ask for a receipt→`:stock` path, and the document points to "receipts" as a data source. In practice receipts arrive as mail. That means Gmail restricted scopes (F3), read by the Clerk, the agent that "reads untrusted mail," whose output would then modify game state. A spoofed receipt email could reset a real animal's food counter. Recommendation: in v1, restock is a manual player event only. Receipt ingest waits for the provenance rule in C2.

**X3. The bridge picks up a new requirement from nearly every colleague.** (my F2 + QA #1, #2, #5 + Psych #3 + Systems #1 + QA #4)
Across all six reviews, the unspecified Babashka bridge is now also expected to provide: dedupe/idempotency keys (QA #1); `ask_player` provenance (QA #2); presence events like "Biscuit at the door" and "Dinner!", for which the stack has no sensor or input source (QA #5); a non-visual rendering of code blueprints (Psych #3, the same gap as my "ghosted blueprint for a code change"); cap state (Systems #1); and per-runtime enforcement of the autonomy dial. On that last one, QA #4's contradiction can only be resolved by mapping each dial setting to each runtime's permission model (Hermes, Claude Code, local models). The document describes none of those permission models. This is one week of schedule carrying most of the open problems on the board.

**X4. The Director is the first approval-fatigue test subject.** (my F5 + Psych #3 + QA #4)
Psych #3 predicts rubber-stamp fatigue from approving every change through hard-to-judge blueprints. My F5 notes that Michael must approve every change from a five-agent crew on a 7-week schedule. These are the same problem at two levels. The production process will show the player-side failure first, and it can be measured cheaply. Logging Michael's approvals per day and time per approval during Weeks 2–5 gives real data for the batching/autonomy design that Psych, Systems #4, and QA #4 all ask for. Separately, Narrative #2 is right that "The Director approves every change" sits in the dev-crew section. QA #4 partly reads it as a player rule. The in-game contradiction (blueprints vs. "fully autonomous") still stands, but the Director line is about production, and production is where the bottleneck shows up first.

**X5. The bedtime lock can only be soft, so Psych and QA are describing the same fact from opposite sides.** (Psych #4 + QA #3)
The only lock the stack can enforce is on the browser view. Agents remain reachable in Zed and by text, which the Platform line advertises ("you play everywhere your agent lives"). QA calls that a bypass. Psych asks for an override. From a build view, the override comes for free and a hard lock can't be built without revoking the player's own agent access. The document should describe Pillar 4 as a nudge with a fiction-acknowledged override. That's also cheaper than either reviewer's implied alternative.

**X6. The token economics hit the build schedule before they hit the customer.** (my F5 + BA #4 + Psych #2)
BA #4 shows a $7 session and dependence on vendor caps. Psych #2 shows multi-minute waits per Assign. The same estimated (not measured) 2.5M-token Assign cost also limits development: five Claude Code crew members on the same flat-rate caps as playtesting. If the estimate is off by 2x, both play pacing and the 7-week build slip together.

### 3. REVISIONS

- **F1 (MVP cut line): BLOCKING, strengthened.** BA #1 and QA #3 reached it independently. I'm adding X1: the "One wow" is post-MVP by the document's own definition. I'm also folding in my former F3 point about Week 2's load ("sample connections" for *every* connection in one week). It belongs here as a scope problem.
- **F2 (bridge): BLOCKING, strengthened.** See X3. It is now the component that holds most of the board's unresolved fixes.
- **F3 (integrations): downgrade MAJOR → MINOR for the 7-week plan.** I underweighted the document's explicit line that the MVP is "gradable without a single real account." The external gates (Gmail verification, 10DLC, iMessage DB) are real but fall after the course. The pre-distribution security gate from C2 now matters more than the platform lead times I flagged. For any real-connector release it remains MAJOR or worse.
- **F4 (physics): downgrade MAJOR → MINOR.** Week 7 is explicitly stretch, and no colleague's findings depend on ragdolls. The remaining ask is a one-line commitment that locomotion is kinematic so that "!" walking doesn't depend on balance control, plus correcting "GPU box for physics."
- **F5 (production model): MAJOR, unchanged, strengthened** by X4 and X6.
- **Credit revision:** in Round 1 I called the mock test rig "the strongest technical idea in the document." I still think it's the right MVP strategy. QA #2 shows it validates nothing about the real connectors, which carry the real risk, so I no longer count it as a safety argument.
