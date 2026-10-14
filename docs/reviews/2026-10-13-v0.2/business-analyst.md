# Production/Business Analyst — Round 1

Reviewed: `gdd.txt` — *Prime Meridian*, v0.2, October 2026.

## Finding 1 — The document is three products with three different audiences, and it never picks one
**Severity: BLOCKING**

**Problem.** Prime Meridian is pitched as (a) a spatial control surface for developers running many coding agents (§2: "Every tool for running AI agents today is a list... Lists stop working past two or three agents"; it positions itself against Ralv.ai), (b) a cozy life-mirror game for "everyone" (Pillar 2; §9a: "a player without an axolotl has horses, cows, pigs, a greenhouse, a car that needs oil, a sourdough starter"), and (c) a game that builds itself, shown off with a 13-agent studio (§10d, Pillar 5). Each one points at a different player. The horse owner from §9a has no GitHub repos for workshops, no Hermes cron jobs, and no reason to care about PRs landing on shelves. The developer juggling agents has no use for an overflowing barn. The self-building showcase is mostly a demo for the author's class.

**Source.** Pillar 2 "Everyone's game" set against the concrete setup the document actually describes: `~/.hermes/state/worms.json` in the mirror example, "Hermes sessions... already running the author's life" (§10), "Director (Michael, human): ... merges every PR" (§10d), and Gmail and Twilio wired to "the bot's number" (§6). The player that exists today is one person: Michael. Nothing in the document defines a target player, a player persona, or a second user.

**Why it matters.** A design with no stated audience can't decide what to cut. That shows up in Findings 2 through 4. The document should say plainly that v1's audience is one technically fluent person who already runs Hermes, and move "everyone's game" to a later vision section, or else commit to the general audience and pay for onboarding (see Finding 5).

## Finding 2 — The MVP cut leaves out the features that make this different from a dashboard
**Severity: MAJOR**

**Problem.** §12 sets "MVP = weeks 1–4. Everything after is stretch." Weeks 1–4 deliver a scaffold, a world with mock agents, live status on characters, and click-to-talk. Those four are a 3D skin on an agent list, which is exactly what §2 says is wrong with current tools. The load-bearing differentiators all fall in stretch:
- Agents raising "!" and coming to find you (Pillar 4: "it talks back, asks questions, and comes to find you"; the "Oracle" fantasy in §4; the "spot who needs you" step of the core loop) arrive in week 5.
- Possession, one of the four core-loop verbs (§5), arrives in week 5.
- "The world is the showcase" (Pillar 5) and the "wow agent" Environment Artist working on the river (§10d) land in week 7+ ("Polish, the river, sound").
- The reverse loop's "mark it done" step (§5), which closes the Flora mirror, doesn't appear in any milestone.

**Source.** §12 milestone table and the "MVP = weeks 1–4" line, compared with Pillars 4–5, §4, §5, and the §10d "One wow agent" paragraph.

**Why it matters.** If the course ends at the MVP, the delivered build won't show the core vision. "Questions come to you" and "ask for the river to be nicer and watch it change" should move into the MVP. To make room, the talk feature can be thinner (text-only, no history), or Bridge v0 can be narrowed.

## Finding 3 — The 13-agent dev crew is decorative scope that works against "Keep it simple"
**Severity: MAJOR**

**Problem.** §10d sets up a studio hierarchy of 13 roles: Lead Architect, Producer, Game Designer, Writer, three engineers, four art/audio roles, QA, and Asset Scraper. Each has a role spectrum, a model tier, and file ownership. Several of those roles have no work before week 7+: Sound Designer (sound is in week 7+), Character Artist for cosmetic unlocks (week 6), Tech Artist for purchased kits, and Environment Artist (the river is in week 7+). Standing up, prompting, and coordinating the roles, plus the rule that every crew member is "also a visible worker in the Prime Meridian workshop," is overhead the schedule never accounts for. It also contradicts Pillar 6 ("Keep it simple... fun before features") and the scope-creep risk the document itself names in §13.

**Source.** §10d hierarchy and crew table; Pillar 6; §12, where weeks 1–4 need about three of these roles (engine, gameplay, bridge) plus QA.

**Why it matters.** The crew section gets about as much space as the core loop and world sections put together. That is a sign the document is partly a showcase of how the team builds rather than a plan for what it builds. The fix is to staff the MVP with Producer, Engine/Gameplay, Bridge, and QA, and introduce each other role at the milestone that first needs it.

## Finding 4 — The scope-creep cap in §13 is contradicted elsewhere in the document
**Severity: MAJOR**

**Problem.** §13 calls the mirror-your-life idea "bottomless" and limits it to "four live data sources max for the course (tank, mailbox, board, one repo)." The rest of the document goes past that cap:
- §6 lists the Phone booth with Twilio SMS as a world place with a live source. Calendar and Reminders, behind the notice board, are two systems, not one.
- §9a specifies a general mirror DSL with an open-ended `:kind` enum (":creature | :plant | :vehicle | :counter | ..."), a rule engine, a chore scheduler, nine starter models, and user-supplied GLTF. That generic system is the bottomless part the risk names. It is load-bearing only for the "everyone" audience, which v1 doesn't have (Finding 1).
- §7 adds Specialists (Courier, Keeper, Clerk), and §14 Q4 is already debating XP for them.

**Source.** §13 "Scope creep" mitigation against §6, §7, §9a, and §14.

**Why it matters.** A cap the design doesn't respect isn't a cap. For the course, Flora can be one hard-coded mirror behind the data interface, without building the full DSL, and the phone booth can be dropped or marked stretch.

## Finding 5 — There's no path from "the author's estate" to anyone else's, and cost is waved away for the wrong audience
**Severity: MAJOR**

**Problem.** The document claims a general audience but leaves out everything that audience would need:
- **Distribution and onboarding.** §10 says the game is a static build hosted on lotect.com. But it depends on a local Babashka bridge, Hermes sessions and cron, local state files, Gmail/Calendar/Twilio credentials, and gitignored licensed kits in `assets/private/`. The document never describes how a second player installs, connects, or configures any of this.
- **Cost.** §10c estimates about $7 per play session, which comes to roughly $150–200/month for a daily player. It then calls cost "a soft constraint" because the author is on flat-rate class and personal plans. That reasoning only holds for the author. It is the main business question for anyone else, and the document doesn't answer it.
- **Privacy.** The world displays a real inbox, SMS, and calendar, and the release target is a public website. The document says nothing about who can see a hosted estate.
- **Positioning.** Ralv.ai is the only comparison given. By late 2026, agent-orchestration UIs are a crowded category, so the document should say why a game metaphor beats them for the user it actually has.

**Source.** §10 build/deploy row, §10c cost paragraph, §6 data sources, §2 positioning; there is no section on onboarding, distribution, or a business model.

**Why it matters.** If this is a personal tool and a class project, say so, and these omissions become acceptable non-goals. If it is "everyone's game," they are the product.

## Round 2 — Cross-examination

### CONFLICTS

**C1. Player Psychologist #1 (no first hour, BLOCKING): I agree with the evidence but not the severity, and I think the wrong player is being onboarded.** The Psychologist's case rests on Pillar 2's horse owner hand-writing EDN. My Finding 1 says that player doesn't exist in v1, and nothing in §12 ships to them. For the actual v1 audience, Michael, the "first hour" is already over, because his Hermes estate is running. The first-session player who does exist, and who the document never mentions, is the **spectator**: classmates and anyone visiting the lotect.com build. That audience doesn't need a mirror-creation wizard. It needs a demo estate with mock or recorded data, which the Week 2 "mock agents" milestone almost provides already. So I would make Psych #1 BLOCKING only if the authors keep "everyone's game" as a v1 goal. Otherwise it is MAJOR, and the fix is a cheap demo/replay mode, not onboarding design. If the board scores it BLOCKING without conditions, it is effectively voting to build product (b) from my Finding 1, and that decision should be made on purpose.

**C2. Adversarial QA #1 and Feasibility F3 vs. my Finding 2's recommendation.** I recommended moving "ask for the river to be nicer and watch it change" into the MVP. AQA and Feasibility both show that the document has no defined path that makes this happen in seconds: the gated path takes hours, and the ungated path can crash the renderer with no rollback. They are right, and my recommendation as written would pull an unspecified, risky feature into the critical weeks. I still disagree with the implied conclusion that the feature should stay in stretch. If the river slips to week 7+, the course ends with the dashboard that §2 criticizes. The cheapest version that keeps the differentiator: a hot reload of a branch on the player's **local** client only, with "reload main" as the rollback and the merge kept as the permanent gate. AQA's sandbox/last-known-good requirement then shrinks to a single reload button rather than a recovery system.

**C3. Narrative Critic #4 (character bible, Writer wraps live output) vs. my Finding 3 (crew is decorative).** The Narrative Critic wants the Writer role to do more, so that live agent replies arrive in character. I want fewer crew roles staffed early. A Hal voice sheet of about a page is cheap and I support it. A layer where a mid-size open-weight Writer rewrites frontier coding output in character is a new runtime pipeline with added latency (worsening Psych #2) and honesty risk (Narrative #2's own concern about framing that softens errors). I'd argue for a system-prompt persona line per agent, written once by a human, and no Writer-in-the-loop for v1.

**C4. Feasibility F4 and AQA #4 (security) vs. treating it as engineering work.** Both reviewers want an auth/network model for a public client that reaches a private bridge, and AQA flags shared `:source` scripts as remote code execution. Both findings hold. But building auth is the expensive answer. The cheap answer is a scope decision: if v1 is single-user (my Finding 1), the bridge binds to localhost, the public lotect.com build runs only in the demo mode from C1, and shared mirror configs are cut. That removes the attack surface instead of defending it. I'd ask the board to record the security issue as "blocked on the audience decision," not as an independent workstream.

### CONNECTIONS

**X1. The persistent-cast problem has a ready-made answer in the section I called decorative.** Systems #1, Narrative #1, Psych #4, and AQA #2 all independently conclude that XP, hats, and trust have no persistent subject, because workers are task-scoped. §10d already defines 13 named, persistent roles with file ownership, and says each is "also a visible worker in the Prime Meridian workshop." Those roles are the persistent cast the game is missing: a task-scoped session *incarnates* a standing role (the Environment Artist, for example), and XP accrues to the role. Systems #1 points toward this ("a persistent named role, e.g. the §10d crew roles"). Seen through my lens, this changes the crew from decoration into the content of the progression system. It also gives a scope rule: **the MVP crew is the MVP cast.** Staff four roles, and the estate has four characters to grow attached to.

**X2. Adding crew adds queue, not speed.** Feasibility F5 identifies Michael as the single merger and screenshot provider on a Tue/Thu schedule. Combined with my Finding 3, the 13-role crew is worse than overhead. Every extra role that produces PRs lengthens a human queue that is already the bottleneck, which also stretches the "minutes to hours" payoff latency that Systems #3 and Psych #2 flag. Staffing roles only when their milestone starts is therefore a throughput fix, not just a simplicity preference.

**X3. Scarcity resolves the cost question I left open.** Systems #2 proposes concurrent worker slots ("desks per workshop") as the game's scarce resource. My Finding 5 said cost is the main business question for any player who isn't on the author's flat-rate plans. Desk slots answer both: they create a real triage decision, and they cap concurrent sessions, which bounds spend per player. One mechanic does design work and business work at once, so it should be in the MVP.

**X4. The guilt problem is a symptom of the audience problem.** Psych #3 argues that mirroring an overflowing inbox and an unfed pet turns a "cozy" game into a guilt surface. For product (a), a developer's control surface, visible pressure is the point. For product (b), cozy "everyone's game", it is a defect. The same feature is load-bearing for one audience and harmful for the other. That is the most concrete evidence for my Finding 1: the board cannot rule on Psych #3 until the document says who the player is.

**X5. "Questions come to you" is the hardest event to get.** Feasibility F1 shows that the `question` event is the least specified and hardest part of the Hermes contract. My Finding 2 asks for that exact feature in the MVP. These fit together only if week 3 starts with a spike to determine whether `question` can be detected reliably. If it can't, the core differentiator of the whole pitch (Pillar 4) is at risk, and that business risk should appear in §13.

### REVISIONS

- **Finding 1: stays BLOCKING, strengthened.** Five colleague findings change severity or fix depending on the audience decision: Psych #1, Psych #3, Feasibility F4, AQA #4 (shared-script exploit), and Feasibility F5 (generic mirror engine as debt). It is upstream of more of the board's findings than any other single issue.
- **Finding 2: stays MAJOR, recommendation revised.** I withdraw "move the river into MVP" as written. The replacement: (1) a week-3 spike on `question` detection (X5); (2) the river as a local branch-preview hot reload with reload-main rollback (C2); (3) desk slots from Systems #2 (X3).
- **Finding 3: stays MAJOR, reframed.** I withdraw "decorative" as the core claim. Per X1, the crew roles are the best candidate for the persistent cast the progression system needs. The finding is now that the crew is **staffed too early and all at once**, which overloads the single human merger (X2), not that it shouldn't exist.
- **Finding 4: unchanged, corroborated.** Feasibility F4 independently counted six or seven integrations against the four-source cap and noted that each OAuth integration costs about a week. That turns my scope observation into a schedule cost.
- **Finding 5: the cost bullet is upgraded.** I said cost was only a problem for players other than the author. Systems #2 shows that the binding limit on flat-rate plans is usage caps (about 7.5M input tokens per session), and that a bloated task can cost five times the stated figure. AQA's note that the Clerk's triage summary needs a model breaks the "Observe = $0" claim. So cost is not "soft" even for Michael. The privacy bullet is handed off to Feasibility F4 and AQA #4, which cover it in more depth; my addition is the scope-based fix in C4.
