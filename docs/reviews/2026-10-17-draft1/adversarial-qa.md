# Adversarial QA — Round 1

Reviewer lens: contradictions, edge cases, exploits, unplanned fail states. Source: `gdd.txt` (Prime Meridian, 5-page first draft).

---

## 1. Marking a chore done is unverified, sent from several channels, and can't be undone, so the worm count stops being "literally true"
**Severity: BLOCKING**

**Problem.** Pillar 1 promises that when "Flora's jar says '2 worms left' ... it's literally true." But the worm count is not measured. It is calculated from what the player reports: `:consumes [:stock 1]` subtracts a worm every time a `:feed` chore is marked done. The document then gives three ways to mark it done ("Reply 'fed' to a text, click the canal, or tell your agent. Every channel sends the same event.") and never says what happens when the same event arrives twice. That leaves several problems:
- **Double counting.** You text "fed" from the kitchen, then click the canal when you jack in to see the snap. Two events, two worms gone, and the jar now undercounts. Nothing in the doc treats repeat events as one.
- **Cheating "Win the day."** Clicking the canal without feeding Flora completes the chore, plays the snap, and counts toward "your own chores done." The single "real loss" the doc names ("Flora goes hungry") is the exact thing the game cannot detect.
- **No restock.** Nothing in the data adds worms back. `:stock` can only go down. After you buy worms, the jar stays at 0 or goes negative, and the "2 worms left!" sign stays up forever. Receipts are mentioned as a data source ("learns your life only from what you connect (receipts, calendar)"), but nothing maps a receipt to `:stock`.
- **Spoofing.** "Reply 'fed' to a text" means the SMS channel accepts input. The doc says only which numbers the bot texts *to* ("the bot texts only the player"). It never says which numbers it accepts texts *from*.

**Source.** §1 Pillar 1; §2 "Mark a chore done"; §2 "The snap"; the Flora data block; §4 Scope week 3 ("Flora's real worm count, **the snap** end to end"), which is inside the MVP.

**Why blocking.** The snap is a signature moment and an MVP deliverable, and it is built on this event. It needs a rule for duplicate events, a restock event, and a rule for who is allowed to send it before week 3 can be finished.

---

## 2. "A '!' always means a real question" can be hijacked: the most trusted signal in the UI can be forged through any input that reaches an agent
**Severity: BLOCKING**

**Problem.** The doc treats "!" as trustworthy because it only appears when an agent calls `ask_player`. But any agent that has read attacker-controlled content can call `ask_player`. "Real question" means "an agent really asked." It does not mean "a legitimate question." Paths the doc opens but does not close:
- The Clerk "holds no keys" but writes the triage summary the player clicks. Nothing says whether that summary is passed to Hal, who *does* hold the loadout and hands out keycards. Removing credentials from the reader does not help if its output goes into an agent that has them.
- The Construct shows "**every skill and MCP connector out there**" and loads them "in seconds." Unvetted third-party MCP servers are the "poisoned tools" the Red Team is supposed to cover. But the Red Team "attacks only pretend services," and the test drive is "also the test rig." The real connectors players will actually load are never tested.
- The guardrail "Purchases and messages to other people always need you" assumes the player is a reliable gate. A forged "!" that walks up to the player with a plausible approval request uses that gate against them. Pillar 2 makes it worse, because the player is trained to act on "!" before reading.

**Source.** §3 "The twin rule" (`ask_player` guarantee); §3 Clerk row and Guardrails; §2 "The first hour: the Construct"; §4 Red Team row; §4 "The test drive is also the test rig."

**Why blocking.** The safety model rests on this one guarantee, and the doc presents it as proven when it isn't. The game handles real mail, real messages to other people, and Stripe.

---

## 3. Closing time is easy to bypass and leaves a lot of work stuck when it triggers, and the MVP doesn't include it at all
**Severity: MAJOR**

**Problem.**
- **Easy to bypass.** "Bedtime is declared data," and the player writes their own data. Set bedtime to 04:00 and the lock never happens. More basically, the doc says you "play **everywhere your agent lives**: your editor, phone, mail..." and admits "You could do all of this at a terminal." Locking the browser door locks only the 3D view of agents that can still be reached in Zed or by text. Pillar 4 ("locks the door at bedtime") has no real enforcement.
- **What's left hanging at lock time is undefined.** When the door locks, what happens to (a) keycards still out ("comes back when the task ends," but night tasks keep running), (b) blueprints not yet approved, (c) a night-crew worker who hits a question at 3 a.m.? The opening scene shows exactly that worker "stuck at 3 a.m." pacing under "!", so the night desk sits idle until morning. That wastes the "few frontier desks" the doc calls "the day's real decision."
- **"Win the day" can be blocked from outside.** "Every '!' answered" *and* "jacked out by bedtime" both have to be true. An agent that raises "!" at 23:59, or a night-shift order that raises one right after you place it, makes the day unwinnable through no fault of the player.
- **MVP contradiction.** Jack in/out, closing time, and "!" walking to you are all week 5, and "MVP = weeks 1–4." So the MVP ships without Pillar 4, without step 2 of the core loop ("spot who needs you ('!')"), and without the Construct's sparring match ("the worker runs to you with '!'... The phone rings... you jack out and back in"). By the doc's own definition, the MVP cannot run the first hour.

**Source.** §1 Platform line, Pillar 4, "Win the day"; §2 "A day is one round" steps 4–5; §2 "Hand out a keycard"; §2 Construct; §4 Scope.

---

## 4. The autonomy dial contradicts "approves every change"
**Severity: MAJOR**

**Problem.** "Set autonomy: a dial per agent, from *ask before anything* to *fully autonomous*." But "Changes appear first as a **blueprint**... Approve and it becomes real," and "The Director (Michael, human; the Oracle) approves every change." Purchases and messages "always need you." Either:
- full autonomy skips blueprint approval, which contradicts §4 and the Pillar 1 promise that nothing becomes real without you, or
- it doesn't, in which case the dial has nothing left to control and is a fake control.

The doc never says which actions the dial covers. The gap spreads:
- The night crew produces "a stack of blueprints at breakfast." Does a fully autonomous night desk apply them overnight while the door is locked and nobody can review them?
- "Win a chapter" needs a full week "with you doing only the human parts." Approving blueprints and handing out keycards are human parts by definition. So winning a chapter quietly pushes players to turn autonomy up, which works against the guardrails.

**Source.** §2 action table ("Approve," "Set autonomy," "Hand out a keycard"); §1 "Win a chapter"; §3 Night crew row; §4 "The Director... approves every change."

---

## 5. The highest-priority jack-outs have no data source, and creatures have no way to die or be rehomed
**Severity: MAJOR**

**Problem.**
- **Where interrupts come from.** The jack-out order is household ("Dinner!") → creatures ("Biscuit at the door with his leash") → appointments → deadlines. Household is ranked highest, yet "household members are never agents... in v1," and SMS "covers opted-in people only; the bot texts only the player." So nothing produces the "Dinner!" event. "Biscuit at the door" sounds like a sensor reading, but the data model only supports scheduled chores (`:every "2d" :at "17:00"`), not presence. That makes it a timer pretending to be real, which breaks Pillar 1 and the twin rule's "never a fake passed off as real."
- **Collisions.** Nothing covers two interrupts firing at once, or what "the world freezes gently" means for a "!" agent that "walks to wherever you are." Does the walk freeze? Does the question time out?
- **Real pet death.** Flora is a real animal. Nothing in the data handles death or rehoming (no `:status`, no move from active to `:memorial`). The Keeper will keep showing "I'm hungry" over a dead axolotl, and missed feedings will keep the estate "overgrown." That breaks "never shames" at the worst possible time. Memorial pets exist only as a separate kind you add by hand, with no path to get there from a live pet.

**Source.** §2 "A day is one round" step 3; §2 "Your life as data" (Flora block, virtual/memorial pets, "household members are never agents"); §3 twin rule and Guardrails; §4 SMS constraint row.

---

## Round 2 — Cross-examination

### CONFLICTS

**C1. Systems Designer F4 and Player Psychologist F3 both recommend that the game suggest raising an agent's autonomy. That suggestion would be the best target for the forged "!" in my F2.**
Systems Designer F4's fix is to "have the twin propose raising the dial and let the player confirm." Player Psychologist F3 also faults the doc for not saying "whether the game ever suggests raising an agent's autonomy dial." Both are fair complaints about missing feedback. My objection is to the specific fix:
- A dial-raise proposal reaches the player as a question, so it arrives as a "!" through `ask_player`. My F2 shows that any agent that has read attacker-controlled content can call `ask_player`. The most valuable thing an injected agent can ask for is *more autonomy for itself*. The proposed feature makes that the normal, expected request.
- The track record Systems Designer wants ("blueprints approved or rejected, keycards returned clean") can be gamed. An agent that does trivial tasks cleanly builds a record that justifies a raise, then spends the higher trust. "You set trust; agents never earn it on their own" (§2) is the only line in the doc that blocks this escalation path. Both colleagues' fixes make it weaker.
- My position: show the track record, but don't let any agent, the twin included, start a trust change. If a proposal is ever allowed, it has to come from the game's own code over a channel `ask_player` can't reach, and it has to look visibly different from an agent's "!". This is not pure disagreement. Both colleagues have found a real gap, and my F4 (the dial has no defined scope) means nobody can yet say what a raise even unlocks.

**C2. Player Psychologist F4 says the bedtime lock is too strong. My F3 says it is too weak. Both are true, and that is the problem.**
Player Psychologist F4 says the lock has "no snooze... no emergency key" and wants "a clear one-tap override." My F3 says the lock does not really exist: agents stay reachable "everywhere your agent lives" (Zed, SMS, mail), and the browser door is the only thing that closes. Taken together, the current design annoys the compliant player, who loses the 3D view of their own work at 23:00, and does nothing to the compulsive player, who switches to the terminal. Adding Player Psychologist's override to a lock that can already be bypassed turns Pillar 4 into a suggestion. That may be the right answer. But the doc has to decide first whether closing time is *enforcement* (and then every channel has to respect it, including SMS replies and agent-raised "!"s) or a *nudge* (and then the override is fine and Pillar 4 should be reworded). Business Analyst F2 raises the same fork from a product angle ("is this a productivity tool (keep working) or a wellness product (lock the door)?"). Three lenses reach the same unmade decision, so I consider it confirmed.

**C3. Systems Designer F5 rates the missing worm restock as MINOR ("a data gap, not structural"). I keep the event problem BLOCKING but agree with them about restock.**
Taken on its own, the missing restock is a data gap, and Systems Designer's `:restock` fix is right. My F1 is BLOCKING for other reasons: the doc has no rule for duplicate events and doesn't say who is allowed to send the "fed" event. Systems Designer's own fix ("show a receipt-ingest path that increments `:stock`") also makes this worse. Receipts come in through mail, mail is read by the Clerk, and my F2 shows the Clerk's output can be poisoned. A crafted "receipt" email could then set Flora's real worm count, which is the exact number Pillar 1 promises is "literally true." The restock path has to be authenticated as carefully as the "fed" path.

### CONNECTIONS

**X1. Feasibility Lead F2 and my F1/F2: the bridge has no stated authentication, so both "!" and "fed" can be forged locally as well as through prompt injection.**
Feasibility Lead F2 points out that the doc never says "how `ask_player` gets injected into each runtime (MCP server? custom tool per harness?)." Combined with my lens, that means the doc defines no *provenance* for any event. The bridge is a websocket that "reaches across machines" (Mac and GPU box). If `ask_player` is an MCP tool, then any MCP connector loaded from the Construct's rack of "every skill and MCP connector out there" runs in the same harness and could register a tool with the same name or post to the same socket. The same goes for "Every channel sends the same event": if channels are just senders to the bridge, any local process can mark Flora fed. The bridge spec Feasibility Lead asks for has to include who is allowed to send each event type. Without that, the twin rule can't be enforced at the transport layer, whatever the agents do.

**X2. Player Psychologist F1 and my F2: the Construct breaks the twin rule on purpose, and the sample-to-real switch makes "!" ambiguous.**
Player Psychologist F1 notes the first hour runs on "scripted agents" and a "pretend chore." In the Construct, "the worker runs to you with '!'." That worker is scripted, so its "!" does not come from a real question. The first "!" a player ever sees breaks "a '!' always means a real question" and "never a fake passed off as real." It gets worse after the tutorial. "Swap in the real parts one at a time" means that for days or weeks the estate holds both sample and real agents. The doc gives no visual difference between a sample "!" and a real one. A player who has learned to dismiss pretend "!"s in a mixed estate will dismiss a real one, and a player who treats them all as real can be fooled by any sample-shaped prompt. Player Psychologist's "first-real-connection moment" needs a matching rule: sample entities must be visibly marked, and the mark must disappear only after the switch to real.

**X3. Systems Designer F1, Feasibility Lead F5, and Business Analyst F4 (usage caps) and my lens: an agent that runs out of quota can't ask for help, so it looks busy forever.**
All three note that the real limit is "usage caps, not dollars." The twin rule says "!" appears *only* when an agent calls `ask_player`. An agent whose plan hits its cap mid-task can't call any tool, `ask_player` included. Under the doc's own rules it never raises "!" and keeps its progress glow, so Pillar 2's promise to show "who's busy, blocked, or waiting on you" fails without anyone noticing. The keycard it holds "comes back when the task ends," which never happens. So keycards stay out indefinitely, which is a credential leak, and a scarce frontier desk stays occupied. The bridge needs a "stalled" state that it sets on its own, separate from `ask_player`, plus a keycard timeout.

**X4. Feasibility Lead F4 (ragdoll locomotion) and my F5 (interrupt collisions): a "!" that has to walk to you can fail to arrive.**
If "!" agents "walk to wherever you are" through active ragdoll physics, a worker can fall, get stuck, or fail to find a path. The doc doesn't say whether a "!" that never reaches the player still counts as pending for "Win the day." Feasibility Lead's recommendation of kinematic locomotion fixes this. If it is not adopted, delivering a "!" needs a fallback that doesn't depend on a body arriving, such as a HUD indicator after N seconds.

**X5. Narrative Critic F3 and my F5 (pet death): agreement, plus a mechanical consequence.**
Narrative Critic covers the tone. I add the mechanics: under my F1, a dead Flora's `:feed` chore still counts toward "your own chores done." The player can't win the day without either faking a feeding or editing data by hand during grief. Two independent lenses reach the same missing state transition, so I'd ask the moderator to treat it as one strengthened finding.

### REVISIONS

- **F1: stays BLOCKING, with a narrower core.** Following Systems Designer F5, the "No restock" bullet on its own is MINOR. The blocking parts are duplicate events and unauthenticated senders. X1 (no provenance at the bridge) and C3 (receipt ingestion as an attack path) strengthen it.
- **F2: stays BLOCKING, strengthened.** Feasibility Lead F2 confirms the `ask_player` transport is unspecified. X2 shows the doc's own onboarding breaks the guarantee. C1 shows two colleagues' proposed fixes would expand the attack surface.
- **F3: stays MAJOR, with one point downgraded and one handed off.** I withdraw "set bedtime to 04:00" as an exploit. Player Psychologist F4 persuades me that the player choosing their own bedtime is player agency, not cheating, in a single-player game. The real issue is that the lock is incoherent across channels (C2). I also hand the "MVP contradiction" bullet to Feasibility Lead F1 and Business Analyst F1, who cover it more fully. It has now been raised independently by three reviewers, so I'd rate it high-confidence BLOCKING under their findings rather than as a sub-point of mine.
- **F4: stays MAJOR, scope widened.** Systems Designer F4 and Player Psychologist F5 independently found that "Win the day" depends on dial settings ("a player who dials everything to full autonomy wins days trivially"). Together with my point that "Win a chapter" pushes autonomy up, the win conditions consistently reward weakening the guardrails. That is a systems problem and a safety problem at once.
- **F5: stays MAJOR.** The pet-death part is strengthened by Narrative Critic F3 (X5). The interrupt-collision part gains the delivery-failure case from Feasibility Lead F4 (X4).
- **New, from X3: a quota-stalled agent has no visible state and its keycard is never returned. Severity: MAJOR.** No single Round 1 review found this. It follows from combining the twin rule's "only `ask_player`" with the usage-cap constraint three colleagues raised.
