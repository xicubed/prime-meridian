# Production/Business Analyst — Round 1

Document reviewed: *Prime Meridian*, GDD first draft (Assignment 1), October 2026.

Lens: feature creep, which features carry the core vision and which are decoration, market positioning, audience clarity, and whether this is one game or several.

---

## Finding 1 — The 4-week MVP does not contain the core loop, the win condition, or the main pillar

**Severity: BLOCKING**

**Problem.** The document defines the core loop as "observe the estate → spot who needs you ("!") → talk, assign, or answer → agents work → see the result in the world." It then declares "**MVP = weeks 1–4;** everything after is stretch." Here is what the schedule leaves out of that MVP:

- **"!" walks to you** is week 5, so it is stretch. The "!" is the signal the core loop depends on ("spot who needs you"), and it is the subject of Pillar 2 ("who's busy, blocked, or waiting on you").
- **Jack in/out and closing time** are week 5, so they are stretch. That is Pillar 4, "It sends you back to your life," which the Executive Summary calls the game's key difference ("the OASIS keeps you in… This world nudges you out"). It is also two of the three "Win the day" conditions.
- **Street cred and the first life chapter** are week 6, so they are stretch. That covers all progression and the "Win a chapter" condition.
- **Not scheduled at all:** the Construct (first hour/onboarding), "I know kung fu" (Signature Moment #2, the twin loading programs and buildings rising, which the document calls the "One wow"), keycards, the autonomy dial, and the blueprint approve/reject flow as a separate deliverable.

So the MVP is an estate with mock workers, a working snap, and chat bubbles. It has no reason-to-play signal, no win state, and no jack-out. In other words, it is the terminal-with-a-skin the document sets out to beat ("Why a game, not a terminal?").

**Source.** §4 "Scope (7 weeks)" read against §1 "Core loop," "Win / loss," and "Design pillars," and §2 "Signature moments" and "The first hour."

**Ask.** Re-cut the MVP around the loop. Move "!" and jack-out/closing time ahead of streaming chat polish. Name one signature moment as must-ship. Cut or explicitly defer the Construct.

---

## Finding 2 — The document is several products at once

**Severity: MAJOR**

**Problem.** At least five separate products share one pitch, and each has its own audience and success metric:

1. **An agent-orchestration front end for developers.** Workshops are code repos, workers are Claude Code sessions, and there are blueprints, keycards, and an autonomy dial.
2. **A life-management / habit app.** Chores, gym, household, calendar, and mail triage. Chapters are integrations ("pets, household, gym, business, mail"), and you "beat" one by running it "a full week."
3. **A pet / creature sim.** Flora, Biscuit, horses with hay counters, virtual pets, and **memorial pets** with personality lines.
4. **A household coordination platform.** "Someday it goes multiplayer: twins… negotiate the shared calendar."
5. **A self-building dev pipeline shown as a game.** §4's 15-role dev crew "builds the game," and each role appears as a worker "in the game's own workshop."

Only #1 and the creature half of #3 (the snap) are load-bearing for the stated vision. #2 is half load-bearing: the "reverse loop" needs some real chore, but it does not need five life domains. Memorial pets, multiplayer twins, and the 15-role crew are decoration at this stage. Each one also brings its own integration, privacy, and content load. The document never says which of these it is when they conflict. For example, if a player's agents are running a business task at bedtime, is this a productivity tool (keep working) or a wellness product (lock the door)?

**Source.** §1 "Win a chapter," §2 "Your life as data" (memorial pets, `:personality`, multiplayer), §3 cast table (Pepper, Clerk), §4 "Agent roles: the dev crew."

**Ask.** Write one sentence that ranks these: "Prime Meridian is primarily X; Y is a supporting system; Z is out of scope for v1."

---

## Finding 3 — The audience is undefined and contradicts itself

**Severity: MAJOR**

**Problem.** The document gives at least three incompatible audiences:

- "**Audience:** people who already run an AI agent." This is a narrow, technical early-adopter group.
- "a game for all and none," "everyone's game," and "anyone can play with no accounts." This is broad and casual.
- The author himself: "Playing on his own machine, he's both Oracle and Neo." The cast is literally named after the author's bot (Hal) and pet (Flora). The reference setup is Hermes in Zed driving Herdr across a Mac and a GPU box, with a Babashka bridge and a local iMessage watcher on a Mac.

The "kit car" framing ("Batteries not included… you bring the parts: your own agent and AI logins, your tools and accounts, your life") puts the whole integration burden on the player. That only fits audience #1, and arguably only the subset who run this exact toolchain. Meanwhile, the "cozy" genre label and the casual-sounding pitch aim at audience #2, who would only ever see the sample/demo mode. That makes the demo a fake version of "Real, not simulated," the first pillar. Nothing describes a player who is not the author: what they own, what they set up, and how long that takes before the first snap.

**Source.** §1 epigraph, "The cast" (Oracle), "Genre/Platform/Audience" line, "Batteries not included"; §2 "everyone's game"; §4 "Stack."

**Ask.** Name one primary player persona who is not Michael, list their minimum required accounts and hardware, and check that the first hour works for them.

---

## Finding 4 — There is no business model, yet the document plans to sell and puts real running costs on the player

**Severity: MAJOR**

**Problem.** The dev crew includes a Business Manager whose job is to keep "the company ready to sell the game: payments, store page, policies." But the document never says what is sold, to whom, or for how much. Its own cost analysis works against a simple sale:

- "One play session… is about **$7** at API prices." A daily-play cozy game at that rate costs the player roughly $200 a month in model fees on top of any purchase price.
- The answer given is "In practice it runs on flat-rate plans, so the binding limit is usage caps." That makes the game's playability depend on the player's own third-party subscriptions and those vendors' caps and terms, none of which the studio controls.
- The kit-car model means the product is mostly the player's own accounts. It is unclear what a customer pays for that they couldn't get from the open repo (the GDD links a public GitHub).

This is a positioning question as much as a pricing one. Comparable 2026 products are free or subscription agent dashboards, plus cozy games sold at a premium with zero running costs. Prime Meridian has the cost profile of the first and the pitch of the second.

**Source.** §4 "Token budget" (the $7 session and the flat-rate note), dev crew row "Business Manager (Pepper)," §1 "Batteries not included," and the absence of any pricing, distribution, or competitor section.

**Ask.** Add a short section covering what is sold (the chassis? a hosted bridge? content packs?), who pays the model costs, and the nearest comparables.

---

## Finding 5 — The hook and the first hour are built on licensed-film IP the document says it will remove

**Severity: MINOR** (upgrade to MAJOR if the shipped game is commercial)

**Problem.** The disclaimer says "the shipped game uses original names and dialogue." But the design vocabulary is the Matrix vocabulary: "You are Neo," the Oracle, the Architect, Morpheus, "jack in," "I know kung fu" (a named signature moment), "Welcome to the Construct," the weapons-rack scene, and "I can only show you the door." The whole first hour is a scene-by-scene reenactment. The document gives no original replacements. Once the references come out, the onboarding beat, the second signature moment, and much of the tone have to be redesigned rather than renamed, and that work is not scoped. The document also leans on the OASIS/*Ready Player One* comparison for its main differentiator ("the OASIS keeps you in"). That positions the game against a film rather than against products the audience actually uses, such as agent dashboards and habit trackers.

**Source.** §1 "The cast" and the film-reference disclaimer, §2 Signature Moment #2, §2 "The first hour: the Construct."

**Ask.** Draft the original-IP versions of the cast roles and the Construct now, before they are built, and position the game against real competing products.

---

## Round 2 — Cross-examination

### CONFLICTS

**C1. Player Psychologist #4 (bedtime lock needs a one-tap override) vs. my F1/F2: an overridable, bypassable lock means the game has no differentiator.**
The psychologist's reading of the audience is right: for people whose repos, mail, and business live on the estate, a hard lock reads as the software blocking their work. But the proposed fix ("snooze," "finish current blueprint," "a clear one-tap override") isn't a neutral UX tweak. Pillar 4 is the only thing the document offers that separates Prime Meridian from the free agent dashboards it would compete with (§1: "the OASIS keeps you in… This world nudges you out"). Adversarial QA #3 has already shown the lock can be bypassed by editing your own bedtime data or by reaching agents through Zed or text. Add a one-tap override on top of that, and Pillar 4 becomes a cosmetic setting. What's left is an agent dashboard with a cozy skin, which is the "terminal" comparison §1 claims to beat. My position is that this can't be settled at the UX level. It's the productivity-vs-wellness decision my F2 asked the document to make. If the product is primarily a dev tool, take the psychologist's override and drop Pillar 4 as the headline differentiator. If it's primarily the "sends you back to your life" game, the lock has to stay meaningful: negotiation inside the fiction is fine, but a free bypass is not, and the document has to accept that it will lose some of the work-tool audience. Whichever way it goes, the pitch has to change to match. **The board should escalate this as a product decision, not settle it as a usability fix.**

**C2. Feasibility Lead F1 (MVP should close the loop against one mock worker) vs. my F1 (MVP should include jack-out/closing time).**
We agree the cut line is BLOCKING, but the fixes differ. Feasibility's proposed MVP (talk → assign → work → "!" → answer → approve → world changes) closes the agent loop and leaves Pillar 4 out. That MVP would show that Michael built an agent orchestration UI. It wouldn't show that he built the game in the pitch. Taking Feasibility F2 (the bridge is the critical-path risk) and F4 (ragdoll locomotion) into account, I'll narrow my ask instead of dropping it. The "!" can ship in the MVP as a static marker above the worker instead of a character walking to you, which removes the locomotion and pathfinding dependency. Closing time is cheap: a clock, a world-state change, and a locked door scene. It doesn't touch the bridge. Pulling it into the MVP costs little and is the only way to test the differentiator inside the 7 weeks. Cutting the cheapest pillar to protect the most expensive one is backwards prioritization.

**C3. Narrative Critic #1 (thesis structurally reversed): partial disagreement about the remedy.**
The critic asks for a narrative endpoint for "needing the game less." I agree the gap is real, but I'd push back on treating it as only a narrative fix. A product whose stated success metric is declining use clashes with any engagement-priced business model, such as a subscription, a hosted bridge, or content packs, which are the options my F4 listed. Adding a "world that runs itself" ending without settling how the game makes money means that if it's ever monetized, someone will quietly cut the ending. The thesis needs to be written into the business model (a one-time purchase, player-paid tokens, no engagement KPIs) as well as into the story.

### CONNECTIONS

**X1. Two progression tracks exist because they belong to two different products (Systems Designer #3 + my F2).**
Systems found that loadout and street cred both "make buildings appear" and never connect, and that mail can be loaded in hour one even though it's the last chapter. From my lens, the cause is clear. Loadout is the dev-tool product's progression (connectors and skills). Chapters and street cred are the life-management product's progression (domains and habits). They don't interlock because they were designed for different players. Systems' fix (gate connectors behind chapters) only works after the document picks a primary product. Otherwise the gate gets in the way of one of the two audiences.

**X2. "Every skill and MCP connector out there" is the most dangerous piece of feature creep in the document (Player Psychologist #1 + Adversarial QA #2 + Systems Designer #3 + my F1).**
Three reviewers each flagged the Construct's connector rack for a different reason: choice overload at onboarding (Psych), unvetted third-party tools that can forge a "!" (QA), and a progression track that maxes out in hour one (Systems). None of them framed it as scope. A rack of every connector is an open-ended commitment to support, test, and secure an ecosystem the studio doesn't control, and the Construct isn't scheduled at all (my F1). Cutting it to one curated, recommended connector fixes all three problems at once. It's the best cost-to-impact cut in the document.

**X3. Every approver in the design is Michael (Narrative Critic #2 + Feasibility Lead F5 + my F3).**
Narrative showed that the Oracle role ("approves every change") only makes sense on the author's machine. Feasibility showed that Michael's approval bandwidth caps dev throughput. Put those together with my audience finding and the pattern is clear. The design assumes the player, the developer, the approver, and the reference hardware owner are the same person. That's why the audience reads as incoherent: the document has only ever been written for one user. It's the root cause behind several findings on the board, not just a narrative or production detail.

**X4. Selling the game carries recurring compliance costs and a liability exposure that no business model covers (Feasibility Lead F3 + Adversarial QA #2 + my F4).**
Feasibility pointed out that Gmail restricted scopes need Google verification and a third-party security assessment, and that SMS needs 10DLC registration. These are recurring costs and lead times, not one-time engineering. QA showed that the safety guarantee behind real mail, messages to other people, and Stripe can be forged. For a commercial product, that combination is a liability question: who is responsible when a forged "!" gets a player to approve a payment? Under the kit-car model, the realistic way to avoid the Google costs is to make players register their own OAuth clients. That narrows the audience even further to the technical audience (#1 in my F3). Pepper's "ready to sell the game" can't be planned until the document settles who holds the integrations and the liability.

**X5. Real pet death is a decorative feature turning into a support burden (Narrative Critic #3 + Adversarial QA #5 + my F2).**
I listed memorial pets as decoration. Both colleagues show the cost is worse than decoration. Once memorial pets exist, the design has implicitly promised a live-to-memorial flow, and nothing specifies it. In a shipped product, a twin saying "I'm hungry" over a dead animal becomes a support ticket and a review-score problem, not just a tonal misstep. My recommendation changes from "defer memorial pets" to "defer memorial pets, but spec the minimal death/rehome state for live creatures in v1," because the snap's MVP creature needs it either way.

### REVISIONS

- **F5 (film IP): upgrade MINOR → MAJOR.** My own condition was "upgrade if the shipped game is commercial." The document already meets it: the Business Manager's job is to keep the company "ready to sell the game," as I cited in F4. Narrative Critic #4 adds that the replacement isn't a find-and-replace, because the emotional peaks are written entirely in quoted lines with nothing replacing them. Unscoped redesign of the onboarding and a signature moment is MAJOR.
- **F3 (audience): upgrade MAJOR → BLOCKING.** X3 shows the missing persona is the root cause behind Player Psychologist #1 (BLOCKING: the first real connection isn't designed), Feasibility F3 ("works for Michael" vs. "ships to others"), and Narrative #2 (who the Oracle is for a stranger). None of those can be fixed until the document says who the non-Michael player is and what they own.
- **F1 (MVP): stays BLOCKING, with a narrower ask** (see C2): a static "!" marker plus closing time in the MVP; walking "!" and ragdolls deferred.
- **F4 (business model): stays MAJOR, with one correction to my own figure.** My "~$200/month" extrapolated from the $7 session, but Systems #1 and Feasibility F5 both note that only Talk turns were measured and "the rest are estimates." The monthly figure is illustrative, not measured. The finding stands because it's about the missing model, not the exact number. Per X4, it becomes BLOCKING the moment Pepper's sales work starts.
- **F2 (several products): stays MAJOR,** strengthened by X1 and X5. C1 shows it's the decision underneath the Pillar 4 dispute.
