# Systems Designer — Round 1

Document reviewed: `gdd.txt` (Prime Meridian GDD v0.2, October 2026).

Lens: whether the loop, progression, pacing, and economy actually interlock, or just coexist on the page.

---

## 1. XP and levels go to agents that don't live long enough to level up, and the trust unlock grants what is already the default. BLOCKING (for §9 as written)

**Problem.** The progression system has nothing to attach to. XP is awarded "for an agent" (§9), but workers, the only agents that do XP-earning work (PRs merged, tests passing, rework-free tasks), "exist while their task exists" (§7), and §13 repeats this as the main cost control: "characters exist for the life of a task." A worker that is spawned for one task and then removed cannot reach level 3. The agents that persist (Hal, and the later Specialists) are the ones §14 Q4 isn't sure should get XP at all. So the persistent agents may not earn XP, and the agents that earn it don't persist.

The one non-cosmetic reward is also empty. §9 says "a level-3 agent may default to *ask before merging* instead of *ask before anything*," but §8 sets the default for every agent to "Ask before merging." The unlock grants the starting state.

**Source.** §7 Workers ("They exist while their task exists"); §8 Autonomy dial ("Default: ask before merging"); §9 XP/Levels; §13 Cost mitigation; §14 Q4.

**Needed.** Decide what carries XP from one task to the next: a persistent named role (e.g. the §10d crew roles, the Environment Artist), a workshop, or the player. Then make the trust ladder start below the default, or move the default down to *Ask before anything*.

---

## 2. The loop has no resource the player has to allocate, so there are no real decisions. MAJOR

**Problem.** Every possible resource in the document is either taken out of play or never priced:
- Tokens/cost: "a soft constraint" (§10c), running on flat-rate plans, and kept hidden from the player.
- Possession: §14 Q3 leans toward no in-game cost.
- Agent count: no cap is stated. Hal spawns a worker per task (§10a), and nothing limits how many tasks are open at once.
- Real-life chores: §9 calls them "a resource system, lightly," but the mirror schema (§9a) only models `:consumes` for the *real* stock. There is no in-game source or sink. Neglecting Flora does nothing in-game except show a bubble.

That leaves "what do I ask for next" as the player's only choice, and it costs nothing and has no opportunity cost. "Observe → spot who needs you → engage" (§5) is triage, but triage needs scarcity: limited attention, limited agents, or a limited budget. The document removes each of these one at a time. The token budget (§10c) is real economy math, but it is an operator concern and never feeds back into play.

**Source.** §5 core loop; §9 "The player's real life is a resource system, lightly"; §9a chore `:consumes`; §10c "Cost is a soft constraint"; §14 Q3.

**Needed.** Choose one scarce thing and show it in-world. Obvious candidates: concurrent worker slots (desks per workshop), which ties directly to §6's "Desks = open tasks", or a per-day context/token allowance shown as something diegetic. Make sure at least one loop decision trades against it.

**Economy math check.** The §10c arithmetic holds up: 20 × $0.04 + 3 × ~$2 ≈ $6.80. It assumes ~90% cache hits and ignores cache-write cost ($5/M). It also excludes the cases the doc itself flags: a bloated session at ~$0.25/call × ~40 calls is ~$10 for one task, five times the stated figure. Possession turns and agent-initiated questions are not costed. On flat-rate plans the binding limit is usage caps, not dollars, and the doc never estimates those (~7.5M input tokens per session).

---

## 3. The loop pays out minutes to hours after the input, and nothing fills the gap. MAJOR

**Problem.** The core loop's payoff is "PRs land, tests go green, the room gains furniture" (§5). In this document's own terms that takes about 40 model calls per small task (§10c), passes the QA gate (§10d), and then waits for a human merge. The default autonomy is "ask before merging," and §10d says "Director (Michael, human): merges every PR". So most payoffs land minutes to hours after the input, and many need the player to come back and act before they land at all.

§10b handles latency at the *frame* level: animations keep the world alive. That is not the same as handling it at the *loop* level. Being given something to watch is different from being given something to do. The things that might fill the wait run on the wrong cadence: the reverse loop fires on real-world schedules (Flora's chore is `:every "2d"`), and the mailbox/notice board are read-only mirrors. The §4 fantasy ("Mornings you stroll through... Afternoons you might possess the gardener") describes an asynchronous, check-in game. The §10c session model ("20 talk turns plus 3 assigned tasks") describes one continuous sitting. The doc never says which one the loop is tuned for.

**Source.** §4 Player fantasy; §5 Result step; §8 Autonomy default; §10b "the world never waits on a model"; §10c session definition; §10d Director merges every PR.

**Needed.** Pick a session shape (check-in vs. sit-down). If sit-down, define a short-cycle action that pays off in seconds (Hal-level answers, small env tweaks, local procedural edits). If check-in, the morning "what happened overnight" summary has to be a designed system, and right now it is one sentence of fantasy.

---

## 4. Ambient tending contradicts agent lifetime and blurs the "real" result signal. MAJOR

**Problem.** §4 and §5 promise that "when nobody has asked them anything, agents tend the estate on their own: one plants trees, another lays out a path." But under §7/§13, an agent nobody has asked anything doesn't exist: workers live only for their task. The Idle status in §7 ("sitting, occasional stretch") has the same problem. Who is idle, and who tends?

Even if this is resolved (e.g. as procedural puppets), ambient tending competes with the core loop's reward. §5 makes the payoff "see the result," and Pillar 1 promises everything on screen is "literally true." Procedural, rule-driven growth that "keeps growing without burning tokens" puts fake change into the same channel as real work. That makes it harder to read what an agent actually accomplished, which is the signal the loop depends on. §9's "Progression never changes the work; it changes the world" doesn't help, because ambient tending also changes the world without any work behind it.

**Source.** §3 Pillar 1; §4 "You → them"; §5 Ambient tending; §7 Workers and Idle status; §13 Cost.

**Needed.** Say who the ambient tenders are: persistent Specialists, unnamed procedural fauna, or the estate itself. Give real-work results a visual signature that ambient growth never uses.

---

## 5. No progression arc or end state across a playthrough, and the progression layer is outside the MVP. MINOR

**Problem.** Estate growth is tied to repo growth with no upper bound or milestones (§9: "workshops gain furniture and lighting as their repo grows"). There is no early/mid/late game, no defined difficulty, and no failure state: a starving axolotl or an overflowing mailbox has no in-game consequence beyond showing that it's happening. That may be on purpose for a cozy game, but the doc never says so and never names what drives long-term play. Meanwhile Gamify v0 is week 6, and "MVP = weeks 1–4. Everything after is stretch" (§12). The MVP therefore ships the loop with no progression at all, so the §9 systems (and the problems in Finding 1) won't be tested against the loop during the course.

**Source.** §9 Estate growth; §12 milestones and MVP line; omission of any onboarding, mid-game, or goal structure.

**Needed.** One paragraph naming the long-term motivator (collection? estate completeness? agent trust ladder?). Pull one XP or growth hook into week 4–5 so the loop and progression get playtested together.

---

## Round 2 — Cross-examination

### Conflicts

**Player Psychologist #3 (visible guilt; "cozy games work because nothing bad happens when you are absent") against my #2 (no scarcity, so no decisions).** The Psychologist's asks are snooze, do-not-disturb, a cap on how many agents come looking for you, and "maximum pressure the screen will ever show." If all of that is added on top of a design that already has no scarce resource, the loop flattens completely. Nothing competes for the player's attention, so "spot who needs you" stops being a choice. I agree that real-life obligations should not turn into in-game punishment. The answer is not to remove pressure. It is to move pressure off the real-life side and onto the agent side. Concretely: no decay or penalty for an unfed axolotl or an unread inbox (the Psychologist is right about those), plus a hard cap on concurrent worker desks per workshop, so the player is always deciding which task gets the next desk. A cap like that produces the throttling the Psychologist wants and the scarcity I want from the same rule. Their "cap how many agents can come looking for you at once" is half of a resource system. It should be designed as one, not added later as a comfort setting.

**Player Psychologist #4 rates the progression/persistence problem MAJOR. I rated it BLOCKING, and so did Narrative #1 and Adversarial QA #2.** The Psychologist's case for MAJOR is reasonable, since §12 puts Gamify at week 6, which is stretch. I still hold BLOCKING, and the reason is a question of timing. The real decision isn't "does XP work." It is "is a worker a persistent character or a disposable process." That decision is due at the Week 2 milestone ("world with mock agents"), because the mock agents need some identity model, and at Week 3, when real Hermes sessions get mapped onto characters. Once the bridge maps one session to one character, persistent crew roles become a refactor. The progression symptom shows up in week 6, but the cause gets locked in by week 3.

**Business Analyst #2 (move "!" and the river into the MVP) against my #5 (move an XP/growth hook into weeks 4–5).** Both of us are asking for the same scarce MVP weeks. The Analyst's claim is stronger: "!" questions are the "spot who needs you" step of the loop, and §9 progression is broken as written (my own #1). Pulling it forward would mean playtesting a system that has no subject. I am withdrawing that half of my #5 recommendation (see Revisions).

### Connections

**Feasibility F5 / Adversarial QA #1 (Michael merges every PR) + my #2 (no scarce resource) + my #3 (latency).** Read together, these show that the document already has a scarce resource. It just isn't designed. Every task waits on one human's merge approval and screenshots, so the binding constraint on the loop is the player's own approval bandwidth, not tokens or agents. The trouble is that it currently acts on the player only as latency (my #3) and as a queue of pacing agents (Psychologist #3). It is never a visible budget the player allocates. Designing it in would be cheap and would hit three findings at once: show the approval queue as a diegetic object (the PR shelf from §7's Done status, which also fixes Psychologist #5's missing "awaiting your approval" state), make it the player's main triage decision, and size the worker cap so the queue can't outgrow one sitting. As written, the doc's real economy is "one human's attention." The doc never models it.

**Adversarial QA #3 (XP-farming exploit) + my #1 fix.** My Round 1 "Needed" said to start the trust ladder below the default so the level-3 unlock means something. Adversarial QA shows the cost of that: once trust actually changes merge rights, "question answered quickly" and "tests passing" become sources the agent can farm, and an agent could buy its way past review by gaming them. My fix without their constraint creates the exploit. Any working trust ladder needs XP sources the agent can't generate for itself, such as merged-without-rework judged after merge, or player-granted XP. The Psychologist's point that "answered quickly" measures the *player's* speed is a second reason to drop that source.

**Adversarial QA #4 (no write-back path for chores) + my #2.** I named real-life chores as the closest thing the doc has to an in-game resource ("lightly"), and said it had no in-game source or sink. QA shows that even the real side can't close: the bridge protocol has no "chore completed" message, and `:consumes` has no owner. So the "resource system, lightly" can't currently complete one transaction. That moves chores from "weak resource" to "not a resource at all," which strengthens #2.

**Feasibility F1 (the `question` event may not be reliably detectable) + my #2.** If Hermes can't emit `question`, then "spot who needs you" loses its signal, and this happens in a loop that also has no scarcity. Both halves of triage, the signal and the stakes, are unspecified. Triage is the only decision the core loop has, so F1 is a threat to the loop's design as well as to the tech plan.

**Narrative #3 (Oracle vs. possession) + Psychologist #5 (Talk and Possess are indistinguishable) + my #2 (possession has no cost, §14 Q3).** Each reviewer sees a different symptom of one systems gap: possession has no mechanical reason to exist. It isn't cheaper, faster, more capable, or more expensive than Talk. Giving it one tradeoff, for example skipping Hal's queue in exchange for tying up the player's approval bandwidth, would answer the Psychologist's "when would I choose this" question and give Narrative a reason to frame it in the fiction.

**Adversarial QA minor note (the Clerk's triage summary needs a model) + my economy math.** That adds to the uncosted items I listed: "Observe = $0" is false whenever the Clerk runs. Combined with Business Analyst #5 (~$150–200/month for a daily non-author player), the hidden-cost stance in §10c holds only for an audience of one. That is the Analyst's #1 showing up in the economy: which scarce resource the loop should expose (tokens vs. desk slots) depends on who the player is. For the v1 author on a flat-rate plan, it should be desk slots and approval bandwidth, not dollars.

### Revisions

- **#1: stays BLOCKING, "Needed" revised.** Add: the XP sources that unlock trust must be ones the agent can't generate itself (per Adversarial QA #3), and drop "question answered quickly" (per Psychologist #4). The decision on persistence is due by Week 2–3, not week 6.
- **#2: stays MAJOR, strengthened.** The chore "resource" can't complete a transaction (Adversarial QA #4). The recommended resource is now specifically worker-desk cap plus a visible approval queue, because the merge bottleneck (Feasibility F5) already exists and only needs to be surfaced. Economy math amended: Clerk triage calls are uncosted.
- **#3: stays MAJOR, strengthened.** Psychologist #2, Feasibility F3, and Adversarial QA #1 reach the same latency from different angles. My distinct claim stands: this isn't only a river/Environment Artist problem. It affects every task, because every payoff waits on §8's default plus §10d's merge rule.
- **#4: stays MAJOR, scope extended.** Adversarial QA #5 raises runaway growth, which I missed and which belongs in my lane: ambient tending is a faucet with no sink and no cap, on an estate that Pillar 6 limits to "one screen." An unbounded source on a bounded board is an economy bug as well as a frame-budget one. Narrative #2 independently reaches my "real vs. procedural signal" point from the theme side, so the visual-signature fix now serves both lenses.
- **#5: stays MINOR, recommendation partly withdrawn.** I withdraw "pull one XP or growth hook into week 4–5." Progression is broken as specified (#1), and Business Analyst #2's case for the "!" loop has a better claim on that time. Replacement: put the desk cap and approval queue into the MVP. They cost little, they sit in the Week 2–4 world already (§6 "Desks = open tasks"), and they give the MVP loop the decision it currently lacks. The ask for a one-paragraph long-term motivator stands.
