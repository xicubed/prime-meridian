# Systems Designer — Round 1

Lens: core loop, progression, pacing, difficulty, resource/economy math. The question throughout: do these systems interlock, or do they just sit next to each other on the page?

Overall read: the moment-to-moment loop (observe, "!", answer, blueprint, approve) is well specified and holds together. Above that, things get thin. The document names a "real decision," a win condition, a progression track, and a long-term goal, but most of them have no quantities, no feedback, and no stated link to the systems beside them.

---

## Finding 1 — "The day's real decision" has no numbers, no visible stakes, and a failure case nobody handles
**Severity: MAJOR**

**Problem.** The document makes night-shift allocation the main strategic choice: *"Choosing what gets the frontier desks is the day's real decision"* (§2, A day is one round, step 4). It is never modeled.
- **How many desks?** Only "a few desks run frontier models." There is no number, and nothing says what sets it.
- **What do you give up?** The night crew is "free" ($0 in fees, §4 Token budget). No passage says what the frontier desks do better: quality, speed, or task types the local models can't handle. If the player can't see a quality gap in the world, every choice looks the same. With no visible difference, the obvious play is to send everything to the free crew.
- **What resource is actually spent?** §4 says *"the binding limit is usage caps, not dollars."* But the game never shows usage caps anywhere. The real budget for the core "Assign" verb (about 2.5M tokens per task, about 3 tasks per session) lives outside the game. The player makes the allocation without seeing what they're spending.
- **What happens when a night worker gets stuck?** A "!" can only come from `ask_player` (§3), and at closing time *"the door locks until morning"* (§2 step 5). So a frontier desk that hits a question at 1 a.m. has nowhere to send it. The document never says what happens next. Does it stall all night and waste the scarce desk, skip ahead, or fall back to a guess? That choice decides whether the "real decision" is a decision or a gamble.

**Source.** §2 step 4; §2 step 5; §3 twin rule (`ask_player`); §4 Token budget and the closing paragraph about usage caps.

**Fix direction.** Make capacity an in-world resource with a count (for example, N desks lit, with a gauge that maps to the real cap window). Show the output difference between frontier and local work as blueprints at breakfast. Define what a night worker does when it is blocked, for example "parks with a note on the board."

---

## Finding 2 — Chapter wins are a 7-day streak, which breaks the "No streaks" rule
**Severity: MAJOR**

**Problem.** §1 Win/loss says: *"No game over. A missed day leaves the estate a little overgrown... No streaks, no shame."* The next line up defines the chapter win: *"You beat it when it runs a full week with you doing only the human parts."* That is a streak by definition. The document doesn't say what a missed day does to a chapter in progress. If the week resets, that is streak pressure. If it doesn't, the chapter win means nothing. Chapters are also the only stated macro-progression structure ("each week a part of life comes online"), so this choice controls the pacing of the whole playthrough. The ordering also packs the full-playthrough length at five chapters into five calendar weeks at minimum, with no stated rule for what happens after a slip.

**Source.** §1 Win/loss, the "Win a chapter" and "No game over" bullets; §2 Progression ("Slip, and it gets a bit overgrown").

**Fix direction.** Define chapter completion with tolerance (for example, "5 of 7 days") or as a count of successful cycles rather than consecutive days. Say explicitly what a slip costs and how long recovery takes.

---

## Finding 3 — Progression is front-loaded and runs on two separate tracks that never connect
**Severity: MAJOR**

**Problem.** Two systems both produce "new buildings appear," and nothing defines how they relate:
1. **Street cred:** habits kept → *"the estate flourishes and new buildings appear,"* with *"No number."*
2. **Loadout:** *"Every skill or connector you load is a program, and a building."*

The Construct, in the first hour, shows *"a rack of **every skill and MCP connector out there**"*, and the player loads mail right away (*"load mail, and a mailbox drops out of the white sky"*). So the loadout track can be maxed in hour one, and it is gated only by which accounts the player owns, not by play. It also contradicts the chapter order, where mail is the **last** chapter to "come online" (§1: "pets, household, gym, business, mail"). The document even says *"Five minutes in, you've played the whole game."* The verb set is fine to teach early. But nothing is defined to unlock between minute 5 and chapter 5: no new verbs, no new agent types, no autonomy tier, no escalating demand. Street cred has no number, so it has no thresholds, no decay rate, and no recovery rate. A designer can't tune it, and a player can't read it beyond "a bit overgrown."

**Source.** §2 Progression: no XP; §2 The first hour: the Construct; §1 Win/loss chapter order.

**Fix direction.** Gate connector loading behind chapters, or make chapters *be* the loadout unlocks. Give street cred a hidden internal value with defined decay and recovery so overgrowth is tunable. State which buildings come from which track.

---

## Finding 4 — The long-term win has no mechanical hook, and the autonomy dial (its only lever) is cut off from feedback
**Severity: MAJOR**

**Problem.** The stated long-term goal is *"needing the game less and agents doing more"* (§1). The only system that moves agents toward doing more is the **autonomy dial**, and it is deliberately closed off: *"You set trust; agents never earn it on their own"* (§2). That is a defensible safety call. But the document then supplies nothing that tells the player when raising trust is justified. There is no track record per agent, no count of approvals versus rejections, and no suggestion from the twin. So the system that defines the endgame has no inputs, no outputs, and no way to measure progress.

The daily win condition also cuts against it. *"Win the day: every '!' answered"* scales with how much work the player assigns and how low the dials are set. A cautious player with every dial at "ask before anything" gets the most "!"s and the hardest days. A player who dials everything to full autonomy wins days trivially. Neither outcome is described as intended, and the difficulty curve depends entirely on this unmentioned interaction.

**Source.** §1 Win/loss (Win the day; "The long-term win..."); §2 What you do, Set autonomy; §3 the `ask_player` twin rule.

**Fix direction.** Show each agent's history (blueprints approved or rejected, keycards returned clean) in a place the player can see. Have the twin propose raising the dial and let the player confirm, so trust is still granted but no longer guessed. Define the long-term win as an observable measure, such as the share of "!"s per task going down over the weeks.

---

## Finding 5 — The worm stock runs down with no refill loop
**Severity: MINOR**

**Problem.** The one fully specified resource is Flora's worm stock: `:consumes [:stock 1]` every 2 days, with a warning at `:<= :stock 2`. The document never defines restocking. Purchases *"always need you"* (§3 Guardrails), so restocking must be a player chore. But no restock chore, purchase event, or receipt-to-stock rule appears in the data example or the mechanics. Without it, the showcase resource only counts down: the warning sign comes up and nothing in the game turns it back into an action the reverse loop can schedule. This is minor because it is a data gap, not a structural one. But it is the reference example that "the same code path runs a horse," so the gap carries over to every declared creature.

**Source.** §2 Your life as data (the Clojure block); §3 Guardrails ("Purchases... always need you"; "learns your life only from what you connect (receipts...)").

**Fix direction.** Add a `:restock` chore triggered by the stock threshold, with a purchase confirmation that goes through the player, and show a receipt-ingest path that increments `:stock`.

---

## Round 2 — Cross-examination

### CONFLICTS

**vs. Narrative Critic F1 (BLOCKING: thesis structurally reversed).** I agree with the diagnosis but not the remedy. The Critic asks for "a narrative endpoint or recurring beat," such as the twin encouraging the player to stay away. A beat can't fix this, because the reversal is in the economy, not the story. Each chapter adds a life domain ("pets, household, gym, business, mail"). Each domain adds chores and agents, and each agent adds `ask_player` calls. The "!" count is the input to "Win the day," so it rises as the game goes on. The progression structure makes the daily obligation load grow every week, and a line of dialogue saying "go outside" won't offset that. The fix has to be mechanical: a measured quantity ("!"s per task, or minutes jacked in per day) that is designed to fall, plus a chapter structure that moves work off the player before it adds new domains. See my F4 fix direction, which I am upgrading below.

**vs. Player Psychologist F4 (bedtime lock needs an override) and Adversarial QA F3 (bedtime is trivially bypassed).** These two findings pull in opposite directions. The Psychologist says the lock is too hard; QA says it's too soft ("Set bedtime to 04:00 and the lock never happens"). From the systems side, both reviewers are treating closing time as enforcement, and the document can't support enforcement: §1 admits "You could do all of this at a terminal," and bedtime is player-authored data. My position is that closing time should be designed as a scoring condition, not a lock. "Jacked out by bedtime" is already one of three "Win the day" clauses. If it lives only there, the Psychologist's override exists for free (you can stay, you just don't win the day), and QA's bypass stops being an exploit because nothing is being enforced. A snooze with no systemic cost, the Psychologist's first suggestion, would quietly delete one of the three win clauses. Any override has to be visible in the win state.

**vs. Adversarial QA F1 (BLOCKING: chore events unverified, double-counted, no restock).** QA independently found my F5 gap and added two real problems: deduplicating events across three channels, and spoofing via inbound SMS. I'm upgrading my finding (below), but I don't accept BLOCKING. The fixes are small and local: a dedupe window on `:feed` events per due period, a `:restock` rule, and a sender allowlist. Each is a few lines of declared data or bridge logic. That makes this a quick win the board should list, not something that should hold up production. The point in QA's F1 about the game being unable to detect that Flora went hungry is a different issue: it's about Pillar 1's truth claim, and no schema change fixes it.

**vs. Player Psychologist F5 (overgrowth is a shame signal; replace with a tidy-up re-entry).** Overgrowth is the only negative feedback in the entire economy. "No game over," no XP, no number. If it is removed or auto-tidied by the twin, a missed day has no consequence the game can show, and street cred becomes a one-way ratchet with no information in it. I agree the lapsed-player return needs design, but the answer is to cap overgrowth and make recovery fast (one kept habit clears a visible chunk), not to remove it. That requires the internal number I asked for in F3. Without it, the Psychologist's concern and mine can't both be tuned.

### CONNECTIONS

**Max autonomy is the dominant strategy, and it runs against the guardrails (my F4 + QA F4).** QA showed that "Win a chapter" ("doing only the human parts") pushes the player to turn autonomy up. My F4 showed that "Win the day" ("every '!' answered") is easiest with every dial at full autonomy, because fewer `ask_player` calls means fewer "!"s. Taken together, both stated win conditions are optimized by the same move, maxing every dial, and the document provides no counter-pressure: no visible track record, no cost to a bad autonomous change, and QA notes it's undefined whether full autonomy even skips blueprints. A player who follows the rewards ends up removing the human checkpoint that §3 and §4 rely on ("blueprints, keycards, and the QA gate catch a bad path"). This is the clearest case on the board of systems coexisting rather than interlocking.

**"Win the day" has no output.** Rereading the source during cross-examination: street cred is fed by "Habits you declared, kept," not by winning the day. Nothing in §1 or §2 says what winning a day produces: no building, no cred, no chapter credit. Psychologist F5 and QA F3 both argue the day win is unfair (agent-driven, blockable at 23:59). It is also inert. It's a condition with no payout, so the short-term reward the Psychologist says must "carry all the motivation" doesn't exist as a system.

**The MVP has no progression and no strategic decision (my F1/F3 + Feasibility F1 + Business F1).** Feasibility notes that night-shift orders and frontier-desk allocation have no week in the schedule. Business notes that street cred and chapters are week 6 (stretch) and that "I know kung fu" is unscheduled. Combined with my lens: the committed v1 contains none of the three systems I reviewed above the moment-to-moment level. There is no "real decision" (F1), no chapter (F2), no street cred or loadout track (F3), and no "!" to drive day difficulty (F4). The MVP can't be used to playtest pacing or the difficulty curve at all, so every tuning question in this review would first be answerable after week 6, the second-to-last week.

**Loadout progression is the onboarding cliff (my F3 + Psychologist F1).** §4 says: "Swapping in a real part is the 'I know kung fu' moment." So the loadout track *is* the sample-to-real migration the Psychologist found undesigned. The pacing of the game's second progression track is therefore set by OAuth flows, carrier registration, and Mac Full Disk Access (Feasibility F3), not by any designed curve. A player who owns few accounts has a short loadout track; one who hits a Google verification wall has a stalled one.

**Waiting time has no meta layer to absorb it (Psychologist F2 + my F3).** The token budget gives "≈20 talk turns + 3 tasks" per session, so roughly three world-changing payoffs per session, each behind a minutes-long wait. In most management games, idle time is filled by a meta system (spending, upgrading, planning). Here the only candidate, progression, has no number, no thresholds, and nothing to spend. The Psychologist's dead-time problem and my "nothing unlocks between minute 5 and chapter 5" finding are the same hole.

### REVISIONS

- **F4 → upgrade to BLOCKING.** Combined with QA F4 and Narrative F1, this is not just a missing feedback loop. Both win conditions reward the strategy that disables the safety model, and the stated long-term goal has no measurable quantity. A game whose rewards point the opposite way from its guardrails and its thesis can't go to production on tuning alone.
- **F5 → upgrade to MAJOR (not BLOCKING).** QA's double-count and spoofing points show the only fully specified resource is unreliable in the MVP's own signature moment (week 3, the snap). But the fixes are cheap and local, as argued above.
- **F3 → revise one claim, keep MAJOR.** I said the loadout track "can be maxed in hour one." That overstates it. The Construct loads sample connectors, and per §4 the real swap is a separate, later act. The corrected finding is worse for pacing: the real loadout track is paced by external setup friction, not by play, and it still conflicts with the chapter order (mail last).
- **F1 → keep MAJOR, strengthened.** QA F3 points out that the document's own opening scene shows "A worker who got stuck at 3 a.m. pac[ing] under a red '!'". The blocked-night-desk failure case I flagged is shown as atmosphere, not handled as a rule. Feasibility F1 confirms the system isn't scheduled. Recommendation: either cut night-shift allocation from v1 and say the day has no strategic layer yet, or model it with numbers.
- **F2 → keep MAJOR, strengthened.** The Psychologist (F5) independently identified the hidden streak. QA F3 adds that if a chapter week requires won days, an agent raising "!" at 23:59 can break a chapter from outside the player's control.
