# Player Psychologist — Round 1

Lens: what a real player experiences in the first session and the first week. Where they get confused, frustrated, or bored and quit, and where the rewards fail to motivate them.

---

## 1. There is no first hour. A new player starts with an empty estate and a config file.
**Severity: BLOCKING**

**Problem.** The document describes only a veteran's routine ("Mornings you stroll through, see what got done overnight, answer the two questions waiting for you", §4). It never describes the first session. Based on what the document does say, a new player's first hour looks like this:
- "No mirror is required. An empty estate is still a valid estate." (§9a). It is valid, but it is also an empty room with nothing to observe, and observing is step one of the core loop (§5).
- Mirrors are added by writing Clojure EDN maps that point at state files on disk (`:source "~/.hermes/state/worms.json"`, §9a). "Rooms are created from a config file at first" (§6).
- Getting a live estate requires a running Hermes install, cron jobs, and Gmail, Calendar, Reminders, Twilio, and GitHub hookups (§6, §10).

Pillar 2 ("Everyone's game") promises "a player without an axolotl has horses, cows, pigs...". The person who owns horses and a sourdough starter is the person least likely to hand-write `{:when [:<= :stock 2] :sign ...}`. Nothing covers a first-run flow, a tutorial, a guided "add your first mirror" step, sample or mock data for a new install, or Hal's first conversation (§7 says Hal is "your first conversation partner" but not what he says). The game's appeal is that it "mirrors your life", but that only works after setup, and setup has no design at all. That is exactly the point where a new player quits.

**Ask.** Write the first 15 minutes beat by beat. Decide whether a new player sees a demo estate (Michael's, or mock agents as in the Week 2 milestone) before their own data is connected. Give mirror creation an in-world or conversational path ("Hal, I have three chickens"). The config file should not be the only way in.

---

## 2. The pitch promises instant payoff, but the pipeline delivers it in minutes to days
**Severity: MAJOR**

**Problem.** The emotional hook is immediate cause and effect: "watch the world itself change as they build it" (§1); for the Environment Artist, "the player sees that exact spot change in front of them seconds after the hot reload" (§10d); "you walk up to the river, ask for it to be nicer, and watch it change in front of you" (§10d). The rest of the document contradicts this:
- An assigned task takes "~40 model calls" to reach a merged PR (§10c).
- "QA Tester (gates every PR)" and "Director (Michael, human): vision, merges every PR" (§10d). The default autonomy is "Ask before merging" (§8).
- §10b admits replies take "seconds to minutes", and the mitigation is "the player can walk away" (§10b, §13).

From the player's side, the flagship moment is: ask the river to be nicer, then wait, then come back later to a "!" asking you to approve a merge, and only then does the river change. "You can walk away" is not engagement design. It is the moment the player closes the tab. Nothing describes what the player does in the meantime, how the game marks progress on long tasks beyond a "progress glow", or how the payoff is staged when the player returns so it still feels earned (for example, a reveal or replay of the change).

**Ask.** Either pick a fast path for the wow moment (for example, a preview/hot-reload on a branch before merge, which §10a hints at with "after hot reload or a merge") and say so explicitly, or reset the expectation in the pitch. Design the "while you wait" and "welcome back" experiences.

---

## 3. A "cozy" game that turns your real obligations into visible guilt
**Severity: MAJOR**

**Problem.** The reverse loop (§5) and living mirrors (§9) put real-life pressure on screen: "The mailbox overflowing is a real inbox asking for real replies" (§4); "The mailbox overflows visually when the inbox does" (§9); agents with questions "come to find you" "wherever you've wandered" (§8); urgent agents "walk toward the player" (§5); blocked workers sit with a red "!" (§7). The document calls itself a "cozy 3D management game" (§1) and says "The player's real life is a resource system, lightly" (§9).

Nothing in the document makes it light. Nothing covers:
- what happens when the player ignores a chore or an inbox for a week (does the estate decay? does the axolotl look sad? does the mailbox spill across the lawn?)
- muting, snoozing, quiet hours, or capping how many agents can come looking for you at once
- what the player finds after being away: if three tasks hit "ask before merging" overnight, the morning is a queue of pacing agents, which is a to-do list wearing hats

Cozy games work because nothing bad happens when you are absent. This design deliberately mirrors things that do go bad when you are absent (unanswered mail, an unfed pet). Without explicit rules for decay, failure states, and throttling, the likely result is that the game becomes another notification surface the player learns to avoid. That is the failure mode of the "list" tools §2 says it is replacing.

**Ask.** Add a section on absence and neglect: what changes visually when things are overdue, the maximum pressure the screen will ever show, and player controls (snooze, "do not disturb", batch "office hours" at the Oracle's kitchen).

---

## 4. Progression rewards the wrong entity, attaches to characters that get deleted, and its one real unlock is a no-op
**Severity: MAJOR**

**Problem.** Every progression system in §9 belongs to the agents, not the player:
- "XP for an agent: PR merged, tests passing, question answered quickly..." The player has no XP, no level, and no unlocks of their own. Their only growth is "estate growth", which follows repo size, not anything they chose to do.
- **The XP goes to characters that disappear.** "Workers — subagents or sessions spawned per task... They exist while their task exists" (§7), and §13 repeats "characters exist for the life of a task." So a worker levels up, earns a hat, and is gone when its PR merges. The player cannot get attached to a character that resets every task, and cosmetic unlocks on throwaway characters mean nothing.
- **The trust unlock is already the default.** §9: "a level-3 agent may default to *ask before merging* instead of *ask before anything*." §8: "Default: ask before merging." The one non-cosmetic reward gives the agent a setting it already had.
- "Question answered quickly" grants agent XP for the *player's* response speed, which is a quiet penalty for being away and makes Finding 3 worse.
- Open question 4 (do Courier and Keeper get XP?) leaves the only persistent characters possibly outside the progression system altogether.

The overall result is that the player has no reason to care about XP. The real reward is the finished work, and §9 admits the progression "never changes the work".

**Ask.** Decide who progresses. Either make workers persistent, named crew members who are re-used across tasks (so their XP and hats accumulate), or move progression to the player and the estate. Fix the trust-unlock contradiction. Drop "answered quickly" as an XP source, or reframe it so it does not punish absence.

---

## 5. The at-a-glance status language is not distinguishable at a glance
**Severity: MINOR**

**Problem.** Pillar 3 promises you can tell "who's busy, who's blocked, and who needs you... before reading a word", and §7 says the status vocabulary "must be readable at a glance". But the table in §7 relies on small differences between "simple capsule-and-hat figures" (§11) seen from an overview camera:
- *Idle* = "sitting"; *Blocked/error* = "red '!' and sits down". The only difference between "fine" and "broken" is the color of a small glyph on a seated capsule.
- *Needs input* and *Blocked* both use "!", told apart only by color and by pacing vs. sitting.
- *Done* = "walks the PR to the shelf, then idles". After the short walk, a finished worker looks exactly like an idle one, so "done, waiting for your merge" (the default autonomy) has no lasting signal of its own.

Relying on red alone is also a color-vision accessibility issue. The player will need to click agents to check their state, which breaks the pillar's promise.

Related confusion: "Talk" and "Possess" (§5, §8) are hard for a player to tell apart. Both send typed messages to an agent. The only stated differences are that possess "bypass[es] Hal" and the camera follows. Open question 3 hints that the designers are unsure why a player would choose one over the other.

**Ask.** Give each status a distinct silhouette or pose, not just a color. Add a persistent "awaiting your approval" state. State in one line when a player should possess an agent instead of talking to it.

---

## Round 2 — Cross-examination

### CONFLICTS

**vs. Systems Designer F2 (no scarce resource, so add one).** I agree the loop has no real decisions. I disagree with one of the proposed fixes. Systems suggests "a per-day context/token allowance shown as something diegetic." From the player's side, that turns an operator cost the document deliberately hides (§10c) into visible meter anxiety, and it sits on top of the real-life pressure the mirrors already put on screen (my F3). The player already has a scarce resource: their own real-world attention, and the mirrors spend it. The game should not add a second scarcity that punishes asking. Of Systems' two options, **desk slots** (concurrent workers, tied to §6 "Desks = open tasks") is the right one because it limits what the player *queues*, not how fast they have to *respond*. Any scarcity added to the loop should be the kind a player can plan around, never the kind that grows while they are away.

**vs. Systems Designer F5 ("no failure state ... may be on purpose for a cozy game").** I disagree that the document has no failure state. It has no *mechanical* one, but it does have an emotional one: an overflowing mailbox, a hungry Flora, agents pacing toward you with "!" (§4, §7, §9). From the player's side, a visible pile of unmet obligations works as a failure state whether or not a number goes down. "No in-game consequence" is not the same as "nothing bad happens when you are absent," and the second is the one cozy depends on. That is why my F3 asks for a pressure *ceiling*, not just a statement that there are no penalties.

**vs. Adversarial QA F4 (missed chores need an "overdue or escalation state").** QA is right that the missed-chore state is undefined, and it has to be specified. My objection is to the word *escalation*. If the overdue state is a ladder (bubble, then a sad creature, then a spilling mailbox, then an agent walking to you), the game punishes absence on purpose. I'd argue the spec should define overdue as **one capped, steady visual state**: noticeable, but never louder the longer you stay away. QA's zero-stock case ("can't feed, no worms") should be presented as information, not alarm. On the facts we agree. Where we differ is how loud the state should be, and the document needs to choose deliberately.

**vs. Systems Designer F1, Adversarial QA F2, Narrative Critic F1 (BLOCKING on progression and persistence).** Three colleagues rate the disappearing-worker problem BLOCKING. I rated my version MAJOR, and I'm keeping that for the **XP system specifically**. Systems' own F5 notes that progression is Week 6, outside the MVP, so no player of the course build ever meets the broken XP loop. A broken system that isn't in the build can't block the player experience. Narrative's framing is the one I'd back as BLOCKING: the loss of a **persistent cast**. That affects the MVP directly. The §4 "morning stroll" past "familiar helpers" fails from Week 3 onward, when workers start appearing and disappearing per task, whether or not XP exists. The board should treat persistence as the root issue and XP as a symptom, not rate them as the same thing.

### CONNECTIONS

**Feasibility F1 + my F5: misreported status is worse than unreadable status.** I flagged that the status poses are hard to tell apart. Feasibility shows that `question` may not be reliably detectable, because a waiting CLI session "usually just stops producing output." Combined, players will see agents that look idle but are actually waiting on them, or a "!" on agents that are just thinking. A signal that is hard to read makes players squint. A signal that is *wrong* teaches them to stop trusting it after a few false alarms. Once that happens, Pillar 3 is gone, and the player is back to clicking every agent, which is the "list" behavior §2 rejects. (Upgrade below.)

**Adversarial QA F3 (XP farming) + my F3 and F4: the reward system pays agents to interrupt you.** I noted that "question answered quickly" penalizes absence. QA shows agents can farm XP by asking trivial questions. Combined, the progression system gives agents a reason to send the player *more* interruptions, and those interruptions are exactly what makes a cozy game feel like a job. In player terms, the reward structure works directly against the absence/neglect controls I asked for.

**Narrative F3 (Oracle vs. servant) + my F3 (guilt).** Narrative points out that the reverse loop casts the player as "a servant answering a summons." That is the identity version of my guilt finding. The player was promised "you are the Oracle, they come to you for it," and in practice gets a fish asking to be fed and a mailbox demanding replies. A fantasy that flips without warning reads as a bait-and-switch, which is a known source of early churn. Fixing the pressure ceiling (my F3) and framing the reverse loop in-fiction (Narrative F3) is one piece of design work, not two.

**Narrative F2 + Systems F4 + my F5: three requests for the same visual grammar.** Narrative wants a marker for real versus procedural activity, Systems wants a visual signature for real work that ambient growth never uses, and I want distinct silhouettes for each status. These are one spec: a single, small visual language for (a) real session versus estate life, and (b) status within real sessions. If they are designed separately, the overview camera ends up with three overlapping signaling systems on capsule-and-hat figures. That overload is how my F5 gets worse.

**Business F5 + Feasibility F4 (privacy, public hosting) + my F3.** The overflowing mailbox is a real inbox, and the target is a public site, possibly shown on a classroom projector. Visible guilt becomes visible *embarrassment*. A player won't open a game that shows the room how far behind they are on email. Before any security model exists, the player experience already needs a "presentation mode" that hides mirror contents.

**Business F2 + my F1 and F2: the MVP's first hour has no hook.** Business shows that weeks 1–4 are "a 3D skin on an agent list." With "!" arriving in Week 5 and the river in Week 7+ (Feasibility F3), a first-time viewer of the MVP never sees the world come to them and never sees it change on request. My F1 (no first hour) and F2 (the payoff is delayed) are both worse for the actual deliverable than I wrote in Round 1.

### REVISIONS

- **F5 (status readability): upgrade MINOR to MAJOR.** Given Feasibility F1, this is no longer only a legibility problem. The status signal may be inaccurate, and Pillar 3 depends on players trusting it. The ask expands: the spec must say what the character shows when the bridge *doesn't know* the state, rather than guessing a pose.
- **F1 (no first hour): keep BLOCKING, made conditional.** Business F1 is right that the only real player today is Michael. If the document adopts Business's recommendation (v1 audience = one Hermes user, "everyone's game" moved to a vision section), I'd downgrade F1 to MINOR. Even then, a demo or mock estate stays necessary for classmates and anyone watching. As long as Pillar 2 stays a pillar, F1 is BLOCKING.
- **F4 (progression): keep MAJOR, narrowed.** I give the trust-unlock no-op point to Systems F1 and QA F3, which cover it more thoroughly. I'm sharpening the "answered quickly" sub-point into the XP-farming/interruption connection above. That part belongs to my lens, and it holds no matter how the persistence question is resolved.
- **F2 (instant payoff): keep MAJOR, strengthened.** Feasibility F3, QA F1, and Systems F3 all independently reach the same "seconds versus minutes to hours" contradiction. QA adds a player-facing worst case I missed: on the ungated path, the wow moment can crash the renderer while the player is watching. A broken world during the signature moment is the worst possible first impression. I'm not upgrading to BLOCKING, because the river is stretch scope and the MVP player won't reach it.
- **F3 (guilt): keep MAJOR, strengthened** by QA's unscored minor note on several "Needs input" agents walking to the player at once with no queueing. QA left that unscored. From the player's side it's the clearest picture of the overwhelm problem and should be counted under F3.
