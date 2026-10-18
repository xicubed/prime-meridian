# Player Psychologist — Round 1

Lens: what a real player feels in the first hour, the first week, and the moment they decide whether to come back.

---

## 1. The first hour runs on the sample version, and the switch to "real" has no design. The game's main promise is withheld behind setup work nobody has designed.
**Severity: BLOCKING**

**Source:** §2 "The first hour: the Construct" ("A rack of **every skill and MCP connector out there** scrolls past... The phone rings with a pretend chore"); §1 "Batteries not included" ("every connection comes with a sample version... swap in the real parts one at a time"); Pillar 1 "Real, not simulated... it's literally true"; Audience: "people who already run an AI agent."

**Problem:** The only onboarding in the document is the Construct, and the whole thing uses pretend parts: a practice job, a pretend chore, a scripted worker. The pillar the game is sold on ("it's literally true") doesn't show up until the player connects their own agent, logins, mail, calendar, and machines. The document never describes that moment. It doesn't say when it happens, what prompts it, how long it takes, or what the player sees if an OAuth flow or the local Babashka bridge fails. "Kit car you assemble yourself" names the friction without designing for it. Two effects on the player:
- **Choice overload at the worst moment.** A rack of "every skill and MCP connector out there" is shown to someone who has no context yet for what any of them would do on their estate. "Load one" with no recommendation leads either to freezing up or to a random pick that pays off weakly.
- **A cliff after the tutorial.** "Five minutes in, you've played the whole game" is true for the sample version. The next step is leaving the game to do account plumbing. That is where people quit. The first time they hit friction is right after the tutorial high, and nothing on the other side has been shown to them yet.

The design needs a scripted first-real-connection moment: one recommended connector (probably the player's own agent), a target time to complete, a failure state handled in the fiction, and a payoff on the estate that is clearly bigger than the sample version.

---

## 2. Waiting is the main thing the player does, and the document doesn't fill it.
**Severity: MAJOR**

**Source:** §4 Constraints ("real agents take seconds to minutes to answer... characters animate thinking, you can walk away, replies arrive as events"); Token budget ("Assign: a small task, through to an approved change ~2.5M in"); Core loop "...agents work → see the result in the world"; Progression "no XP."

**Problem:** The core loop's payoff ("see the result in the world") comes after the longest wait in the game. A small task that goes through 2.5M input tokens takes minutes, not seconds. The design handles latency at the level of how things look ("characters animate thinking") but not how the player spends that time. "You can walk away" assumes there is something to walk to. The document lists nothing to do on the estate between "!"s besides tending creatures. Those are real chores on real schedules (Flora eats every 2 days), so they can't fill gaps whenever needed. With no XP, collecting, or exploring, the player assigns a task and then watches a progress glow. That feels like a terminal with nicer graphics, which is the comparison §1 says the game beats. The Construct's "make that rock rounder" sets the player up to expect quick turnaround that real tasks won't deliver. Players who learn "assign, then wait several minutes" will go do something else, and then the jack-out/jack-in rhythm loses its meaning.

---

## 3. Approval fatigue, and "spatial over textual" doesn't work for most of what gets approved.
**Severity: MAJOR**

**Source:** Mechanics table "Approve: Changes appear first as a blueprint, a ghosted version"; "Set autonomy... You set trust; agents never earn it on their own"; §4 "The Director... approves every change"; Pillar 2 "Spatial over textual"; §1 "a workshop for each code repo."

**Problem:** Every example of a blueprint is visual: a river, a rock, terrain. Most of the real work is not visual: code changes in a repo, mail triage, a Stripe item that needs a signature. A ghosted rock can be judged at a glance. A ghosted refactor can't. The player either rubber-stamps it (which undercuts the safety story in §3 and §4, "blueprints, keycards, and the QA gate catch a bad path") or drops into a diff and reads text, which breaks Pillar 2 and the fantasy along with it. On top of that, "every change" needs approval and trust never grows on its own, so the number of approvals goes up with every task assigned. Lots of repetitive approvals that are hard to evaluate is the textbook setup for habituated clicking and resentment. The document doesn't say what a non-visual blueprint looks like, how approvals get batched, or whether the game ever suggests raising an agent's autonomy dial.

---

## 4. Forced jack-outs and the bedtime lock have no override, so the game feels paternalistic to the exact audience it targets.
**Severity: MAJOR**

**Source:** Pillar 4 "jacks you out when life needs you and locks the door at bedtime"; "A day is one round" step 3 "The phone booth rings and the world freezes gently"; step 5 "The door locks until morning"; §1 "or cuts you off (bedtime)."

**Problem:** The audience is "people who already run an AI agent," and the estate holds their code repos, mail, and business. In practice that makes it a work tool. The document describes freezing the world and locking it with no snooze, no "finish this approval first," no emergency key, and no way to handle an agent that raises an urgent "!" at 10:58 pm. Taking away autonomy is one of the most dependable ways to make players resentful, and adults lose patience fast with software that decides when they're done. The intent is good ("the game exists to prevent" the Ready Player One ending). But a hard lock with no user agency will be read as the game blocking access to their own work, not as care. The likely result is that players turn bedtime off or leave the game. The design needs a negotiable version: snooze with a cost the fiction acknowledges, "finish current blueprint," or a clear one-tap override. It also needs to say what happens to a pending "!" when the door locks.

---

## 5. The win conditions are driven by agent behavior the player doesn't control, and "no shame" contradicts the overgrowth mechanic.
**Severity: MAJOR**

**Source:** Win/loss: "Win the day: every '!' answered, your own chores done, and you jacked out by bedtime"; "Win a chapter... You beat it when it runs a full week with you doing only the human parts"; "No streaks, no shame"; "A missed day leaves the estate a little overgrown"; Street cred "Slip, and it gets a bit overgrown"; "The long-term win is needing the game less."

**Problem:** The reward structure has three psychological faults:
- **"Win the day" depends on agents.** How many "!"s appear depends on how much work the player assigned and how often agents call `ask_player`. A player who uses the game heavily can't clear the queue before bedtime and "loses" the day for being engaged. Assigning more work makes it harder to win.
- **A chapter is a hidden streak.** "Runs a full week with you doing only the human parts" is a seven-day streak in all but name. The document doesn't say whether one missed day resets it. If it does, "No streaks" is false. If it doesn't, the win condition is undefined. The first chapter win also comes a week in, with no intermediate feedback described.
- **Overgrowth is a shame signal.** A returning lapsed player is shown, in the space itself, exactly how long they were gone. That is a guilt cue, and guilt on return is a well-documented reason players don't come back. "Never ruined" softens it but doesn't remove it. The document needs a re-entry design for lapsed players, for example the twin having tidied up or a welcome-back moment, rather than evidence of neglect.

Underneath all three, the stated long-term goal ("needing the game less") means the reward structure is designed to reduce engagement. That's a legitimate goal, but then the short-term rewards have to carry all the motivation, and right now the short-term rewards are clearing a queue the player doesn't control and avoiding overgrowth.

---

## Round 2 — Cross-examination

### Conflicts

**C1. Adversarial QA #3 ("Closing time is easy to bypass") vs. my #4 (bedtime lock is paternalistic).** QA treats it as a defect that a player can set bedtime to 04:00 or reach agents through Zed and text after the browser door locks. I think that bypass is the healthiest part of the design, and making it harder to bypass would be the wrong fix. QA's own evidence backs my side: the document admits "You could do all of this at a terminal" and that you play "everywhere your agent lives." A lock that only covers the 3D view can't be enforced, so if it presents itself as a lock, the player just learns that the game's rules are theater. Adding enforcement (locking data edits, blocking other channels) would make the resentment I described much worse for a work-tool audience. My position: Pillar 4 should be rewritten as a nudge the player authored, not a lock. Bedtime is the player's own declared data, the fiction should frame closing time as "you asked me to send you home," and the escape hatch should be explicit and cheap. QA and I agree that the hanging state at lock time (keycards still out, unapproved blueprints, the 3 a.m. "!") is undefined. Where we differ is that QA wants the lock to hold, and I want it to be honest about being a request.

**C2. Adversarial QA #1 ("Cheating 'Win the day'" by clicking the canal without feeding Flora) — I dispute the framing, not the finding.** From the player's side, someone who marks Flora fed without feeding her isn't exploiting the game. They're lying to themselves on an honor-system tracker, and no verification scheme short of a camera fixes that. Designing against the player as an adversary would push the game toward surveillance, which goes against "never shames." The part of QA #1 I would keep at BLOCKING is the **double count**. A player who honestly texts "fed" and then clicks the canal to watch the snap sees the worm jar go wrong. To that player, the world is visibly lying, and that breaks Pillar 1 in a way they will notice and remember. Being punished for doing the thing the game encourages (jack in to see the snap) is a trust failure. Fix duplicate handling first. Treat self-report fraud as out of scope.

**C3. Systems Designer #3 fix ("give street cred a hidden internal value with defined decay and recovery") vs. my #5 (overgrowth is a shame signal).** I agree a designer needs a value they can tune. But "decay" on absence is exactly the mechanism that turns a return visit into a guilt screen. If street cred decays, the decay needs a cap that is reached fast (a day or two of overgrowth looks the same as a month) and a recovery that is front-loaded, so the first session back restores most of it. Otherwise the tuning knob SD is asking for becomes a dial for how ashamed lapsed players feel. Systems Designer #1's "gauge that maps to the real cap window" has the same tension. Making scarcity visible helps decisions, but a draining meter of the player's real subscription quota will also create anxiety. I'd show it at the moment of allocation, not as a permanent HUD element.

**C4. Business Analyst #2 lists memorial pets as "decoration at this stage."** For scope, fair. But psychologically, memorial pets are one of the few features in the document that gives the player a reason to feel attached to the place itself rather than to the to-do list, and the game's stated long-term goal needs attachment that doesn't depend on work. They should be either cut or designed. Shipping them half-done is the worst option, because Narrative #3 and QA #5 both show the live-to-memorial transition is the place where a dead pet keeps saying "I'm hungry." I'd keep them, but only alongside a player-initiated transition flow.

### Connections

**X1. Approval fatigue (my #3) + forged "!" (Adversarial QA #2) = the safety gate is set up to fail.** QA shows that "!" can be forged by any agent that has read attacker-controlled content. Taken alone, that is a security finding. Combined with my #3, it becomes an exploit the game teaches the player to fall for: approvals multiply with every task, most are non-visual and hard to judge, and Pillar 2 trains players to act on "!" spatially rather than read it. By the second week, a habituated player will approve a well-crafted malicious request with the same click they use on fifty harmless ones. The guardrail "Purchases and messages to other people always need you" relies on the player's attention, and the game's reward structure drains that attention.

**X2. The reward structure pushes players to stop reviewing.** Adversarial QA #4 notes that "Win a chapter" ("you doing only the human parts") quietly pushes autonomy up. Systems Designer #4 notes that low dials make days harder to win. Add my #3 (approvals are tedious) and #5 (win-the-day is hostage to the "!" count), and all three point the same way: every motivational pressure in the design tells the player to raise autonomy and approve faster. Nothing in the design makes caution feel rewarding. SD's fix (the twin proposes raising the dial, with a visible track record) is right, and I'd add that careful review needs its own visible payoff, for example a rejected blueprint that caught something real gets celebrated in the world.

**X3. The first-real-connection cliff (my #1) is steeper than I said: the obvious first connector may be impossible for non-author players.** Feasibility #3 points out that Gmail content scopes require Google app verification and a security assessment before anyone outside test users can connect, and that iMessage needs Full Disk Access against an undocumented schema. Business Analyst #3 shows that the casual "anyone can play" audience will only ever see the sample mode. So for many players, the moment of "swap in the real parts" is either blocked outright or is a system-permissions dialog. My recommendation stands, but more firmly: the first real connection has to be the player's own agent (which they already have, by definition of the core audience) or local chore data like Flora. It must not be mail. Systems Designer #3 found separately that mail is the *last* chapter while the Construct hands it out *first*. That contradiction is also an onboarding bug.

**X4. The onboarding is unscheduled and has lost its script.** Feasibility #1 and Business Analyst #1 both note that the Construct is not on the 7-week schedule. Narrative #4 and Business Analyst #5 note that its dialogue is quoted film lines the document promises to remove. Putting these together with my #1, the first hour is the single most retention-critical stretch of any game, and here it has no build slot, no final script, and no designed hand-off to real play. The MVP also leaves out "!" and jack-out, so whatever is playtested in week 4 can't produce first-hour data. The team will reach launch without ever having watched a non-author player go through onboarding.

**X5. Waiting (my #2) meets usage caps (Business Analyst #4, Systems Designer #1, Feasibility #5).** At about 2.5M tokens per small task on flat-rate plans with caps, the long wait I described can end not with a result but with a cap reached. That is the worst possible payoff for a wait: the player waited minutes and got nothing, for reasons the game never showed them. Hitting a cap has to be a designed in-world event (desks go dark until a stated time), not a silent stall.

**X6. Overgrowth after loss.** Narrative #3 and QA #5 found that a dead real pet keeps generating hungry signals and missed chores. Combined with my #5, a grieving player who stays away is greeted on return by an overgrown estate and a hungry axolotl that no longer exists. That is the single worst emotional moment in the design, and every piece of it follows from rules already in the document. It strengthens Narrative #3, and I would treat that combination as BLOCKING for any release beyond the author.

**X7. The director is the first casualty of approval fatigue.** Feasibility #5 points out that Michael approves every change from a five-agent crew. He is also the reference player ("both Oracle and Neo"). If he burns out on approvals during production, he will build habits like batch-approving and skimming diffs that then get baked into the game's design without anyone examining them. Approvals per day should be measured during production as a direct preview of the player experience.

### Revisions

- **#3 Approval fatigue: upgrade MAJOR to BLOCKING.** Alone it was a frustration problem. Combined with Adversarial QA #2, it is the mechanism that makes the safety model fail in practice. A design that handles real mail, messages, and Stripe can't depend on attention its own loop wears down.
- **#1 First-real-connection cliff: stays BLOCKING, recommendation narrowed.** Given Feasibility #3, the scripted first real connection should be the player's own agent or local data, never an OAuth-gated service. Also adopt Systems Designer #3's gating idea. Chapter-gating connectors solves the choice overload I raised, so drop "every connector out there" from the Construct.
- **#4 Bedtime lock: stays MAJOR, reframed.** Adversarial QA #3 shows the lock can't be enforced anyway. The issue isn't just missing an override; the pillar promises a lock it can't deliver. The fix is to frame it as a nudge the player authored, not to add enforcement.
- **#5 Win conditions: stays MAJOR, strengthened.** The chapter-as-streak point was found independently by Systems Designer #2, and the "!"-count dependency by Systems Designer #4 and Adversarial QA #3. Three lenses landing on the same defect raises my confidence. I withdraw nothing, but the overgrowth sub-point should be read together with X6 above.
- **#2 Waiting: stays MAJOR.** With Feasibility #1 and Business Analyst #1 showing the MVP leaves out the "!" walk-up, the MVP player can't even walk away and be summoned back. They can only watch. The problem is worse in the MVP than in the full design.
