# Review Board Synthesis — *Prime Meridian* GDD (first draft)

Moderator synthesis of six reviews (Round 1 independent review + Round 2 cross-examination).
Date: 2026-10-17. Every point below traces to a file in `reviews/`; the moderator adds no critiques of its own.

**Board at a glance (after Round 2 revisions):** 31 findings: 11 BLOCKING, 17 MAJOR, 3 MINOR.
(Round 1 had 30 findings: 7 BLOCKING, 20 MAJOR, 3 MINOR. Round 2 added adversarial-qa's quota-stall finding and changed several severities.)

---

## 1. Top 5 issues (ranked by severity × confidence)

### #1 — The 4-week MVP doesn't contain the game: no "!", no jack-out/closing time, no progression, and the core systems have no week at all
- **Severity:** BLOCKING
- **Flagged by:** feasibility-lead (F1, BLOCKING), business-analyst (F1, BLOCKING), adversarial-qa (F3, "MVP contradiction" bullet). Systems-designer, narrative-critic and player-psychologist each added a Round 2 consequence.
- **Cross-examination:** **STRENGTHENED.** All six reviewers engaged with it, and none disputed it. QA handed its bullet to Feasibility and BA as "high-confidence BLOCKING." Systems: the MVP has no strategic decision and no progression, so pacing can't be playtested before week 6. Narrative: the MVP contains "zero expression" of the thesis. Psych: the Construct has no build slot and no final script, and MVP players "can only watch." Feasibility added that "I know kung fu" is post-MVP by the document's own definition, since it requires a real account. The two re-cut proposals converged in Round 2. Feasibility's order is: bridge spike, then talk plus a *static* "!" marker, with jack-in/out and closing time moved into the MVP ("I support that part"). BA narrowed its ask to a static "!" plus closing time, with the walking "!" and ragdolls deferred.

### #2 — The human approval gate the safety model depends on can be forged, and the game's own rewards wear it down
- **Severity:** BLOCKING
- **Flagged by:** adversarial-qa (F2 forgeable "!", BLOCKING; F4 autonomy dial vs. "approves every change", MAJOR), player-psychologist (#3 approval fatigue, upgraded to BLOCKING; #5), systems-designer (F4, upgraded to BLOCKING). Connections from narrative-critic and business-analyst (X2, X4).
- **Cross-examination:** **STRENGTHENED.** Psych X1: approvals multiply, most can't be judged visually, and Pillar 2 trains the player to act on "!" without reading, so a habituated player will approve a forged request with the same click as fifty harmless ones. Systems and QA found independently that both win conditions are easiest to meet by maxing every autonomy dial, which removes the blueprint checkpoint. QA X1: the bridge defines no provenance, so any local process or loaded MCP connector could post a "!" or a "fed." QA X2: the Construct's scripted worker breaks "a '!' always means a real question" on the player's first "!". One scoping caveat came from feasibility-lead (C2): the MVP is mock-only, so the forged "!" blocks *connecting real accounts*, not the 7-week plan. Feasibility proposed a gate: no real connector ships until `ask_player` events carry provenance and Clerk output never feeds an agent that holds keycards.

### #3 — No player except the author is designed for, and the switch from sample to real (the payoff of "real, not simulated") has no design
- **Severity:** BLOCKING
- **Flagged by:** player-psychologist (#1, BLOCKING), business-analyst (F3, upgraded to BLOCKING), narrative-critic (F2: who is the Oracle for anyone who isn't Michael), feasibility-lead (F3: "works for Michael" vs. "ships to others"), systems-designer (F3, reframed).
- **Cross-examination:** **STRENGTHENED.** BA X3: "Every approver in the design is Michael." Player, developer, approver and hardware owner are assumed to be the same person, which BA calls the root cause behind Psych #1, Feasibility F3 and Narrative F2. Psych X3: for many players the obvious first connector (Gmail, iMessage) is gated or blocked, so the first real connection should be the player's own agent or local data, never mail. Systems revised its claim: the real loadout track is paced by outside setup friction, not by play. Feasibility moved the opposite way: it downgraded its integration-gate finding (F3) to MINOR *for the 7-week plan*, while keeping it "MAJOR or worse" for any real-connector release.

### #4 — The bridge carries the whole design, yet it is unspecified and gets one week
- **Severity:** BLOCKING
- **Flagged by:** feasibility-lead (F2, BLOCKING). Requirements added by adversarial-qa (X1 provenance, X3 stalled state), with further dependencies traced to player-psychologist #3, systems-designer F1 and adversarial-qa #1, #4 and #5.
- **Cross-examination:** **STRENGTHENED.** Feasibility X3 lists what colleagues now expect the bridge to provide: idempotency keys for chore events, `ask_player` provenance, presence events ("Dinner!", "Biscuit at the door") with no sensor source, a non-visual rendering of code blueprints, cap state, and enforcement of each autonomy-dial setting in every agent runtime. In Feasibility's words, it is "one week of schedule carrying most of the open problems on the board." QA X3 adds that an agent which hits its usage cap can't call `ask_player`, so it shows as busy forever and never returns its keycard. QA asks for a "stalled" state the bridge sets on its own, plus a keycard timeout. No reviewer disputed the finding.

### #5 — A real creature's failure or death has no truthful in-world response; the twin of a dead pet would keep saying "I'm hungry"
- **Severity:** BLOCKING
- **Flagged by:** narrative-critic (F3, upgraded to BLOCKING), adversarial-qa (F5, MAJOR; X5), player-psychologist (X6, "BLOCKING for any release beyond the author"), business-analyst (X5).
- **Cross-examination:** **STRENGTHENED.** Four reviewers reached the dead-pet case independently. QA X5 adds a mechanical consequence: a dead Flora's `:feed` chore still counts toward "your own chores done," so the player can't win the day without faking a feeding or editing data while grieving. Psych X6 combines this with overgrowth, so a grieving player who stays away returns to an overgrown estate and a hungry axolotl that no longer exists. Narrative upgraded because Flora's worm count and the snap are week 3, inside the MVP, and the worm count is self-reported (QA F1). BA changed its ask from "defer memorial pets" to "spec the minimal death/rehome state for live creatures in v1." Narrative's fix separates two kinds of neglect: neglect of the estate is cosmetic and can be tidied without shame, while neglect of a real creature must be told truthfully in the twin's kind voice.

### Just outside the top 5
- **Thesis reversed** (narrative-critic F1, BLOCKING): "needing the game less" is stated but nothing in progression or the endgame delivers it. **SURVIVED.** Narrative corrected its evidence to credit the bedtime lock and jack-out as partial carriers. Systems and BA accepted the diagnosis but disputed the remedy (see Disagreement 3).
- **Chore events unverified, double-counted, no restock** (adversarial-qa F1, BLOCKING): **WEAKENED on severity, kept as a defect.** Systems-designer and feasibility-lead both rated it a cheap, local fix rather than a blocker. Player-psychologist agreed the double count is the real BLOCKING issue and called self-report fraud out of scope. QA itself narrowed the core to duplicate events and unauthenticated senders. Listed below as Quick Win 1.

---

## 2. Unresolved disagreements

### Disagreement 1 — Closing time: enforced lock or honest nudge?
| Keep the lock meaningful | Make it an honest, overridable nudge |
|---|---|
| **business-analyst (C1):** Pillar 4 is the game's only differentiator from free agent dashboards. A bypassable lock plus a one-tap override turns it into "a cosmetic setting." In-fiction negotiation is fine; a free bypass is not. **narrative-critic:** the lock and jack-out are the only mechanics carrying the thesis. An override is acceptable but "never invisibly"; the world must stage it. | **player-psychologist (C1, #4):** a hard lock on a work tool reads as the software blocking your own work. Frame closing time as a nudge the player authored ("you asked me to send you home") with a cheap, explicit escape hatch. **feasibility-lead (X5):** a hard lock can't be built without revoking the player's own agent access, so the override comes for free. |

Also on the table: **systems-designer** proposes making "jacked out by bedtime" a scoring condition only, with no lock. **adversarial-qa (C2)** holds that the current design annoys the player who complies and does nothing to the player who doesn't, and the document must choose before anything else is fixed.
**Decision escalated:** Is *Prime Meridian* primarily a productivity tool or a "sends you back to your life" wellness game (business-analyst F2)? Three lenses (QA, BA, Psych) reached the same unmade decision, and the pitch has to follow whichever answer is chosen.

### Disagreement 2 — Should the game ever suggest raising an agent's autonomy?
| Yes: a visible track record plus a suggestion | No agent may start a trust change |
|---|---|
| **systems-designer (F4):** show each agent's history and "have the twin propose raising the dial and let the player confirm," so trust is still granted by the player but no longer guessed. **player-psychologist (#3, X2):** the game must say whether it ever suggests raising the dial, and careful review needs its own visible payoff. | **adversarial-qa (C1):** a raise proposal arrives as a "!", which makes "give me more autonomy" the most valuable request for a forged "!" to make. Track records can be gamed with trivial clean tasks. Show the history, but if proposals exist at all, they must come from game code over a channel `ask_player` can't reach, and look visibly different. |

**Decision escalated:** Who can start a trust change, and through what channel? The board can't settle it until the document defines what each dial setting actually covers (QA F4).

### Disagreement 3 — What does "needing the game less" measure?
| Agents doing more | The human living more |
|---|---|
| **systems-designer (C vs. Narrative F1):** a dialogue beat can't fix an economy in which every chapter adds more "!"s. The fix has to be a measured quantity designed to fall, such as "!"s per task or minutes jacked in. | **narrative-critic (vs. Systems F4):** a falling "!" rate rewards agents absorbing more of the player's life. A player who hands everything to fully autonomous agents scores perfectly while becoming *more* dependent. It needs a human-side counterpart, such as time spent jacked out or chores done in person. |

**business-analyst (C3)** adds that any engagement-priced business model conflicts with a success metric of declining use, so the thesis must also be written into the business model.
**Decision escalated:** Define the long-term win as an observable measure, and decide whether it counts agent throughput, human time away, or both, together with the business model that would not quietly cut it.

### Disagreement 4 — Write the Construct's original script now, or defer the Construct?
| Write it now | Defer it |
|---|---|
| **narrative-critic (F4):** without the film lines, the Construct and Signature Moment #2 have no script; unscoped redesign of a signature moment is MAJOR. **business-analyst (F5, upgraded to MAJOR):** "Draft the original-IP versions… now, before they are built." | **feasibility-lead (C5):** the Construct is in no week of the schedule, so writing dialogue for an unscheduled feature spends effort before onboarding is buildable. Cut or explicitly defer it and scope the script once it is scheduled. |

**Decision escalated:** Is the Construct v1 onboarding? If it is, the original script becomes a scheduled dependency. If not, it is deferred together with "I know kung fu."

---

## 3. Quick wins

1. **Make chore events idempotent and restock manual.** Allow one completion per chore per due window (`:every "2d"` already defines the window), restock by a manual player event in v1 (no receipt-from-mail ingestion until provenance exists), and accept SMS only from an allowlist of known senders. Reword Pillar 1 from "literally true" to "literally what you reported." *(systems-designer R2, feasibility-lead C1 and X2, adversarial-qa F1 and C3, player-psychologist C2)*
2. **Replace the Construct's "every skill and MCP connector out there" rack with one curated, recommended connector:** the player's own agent or local chore data, never mail. *(business-analyst X2 calls it "the best cost-to-impact cut in the document." It addresses player-psychologist #1, adversarial-qa F2 and systems-designer F3.)*
3. **Commit in one line to kinematic locomotion and a static "!" marker.** Move active ragdolls and the spine swim to post-v1, and remove or explain "GPU box for physics." *(feasibility-lead F4, business-analyst C2, adversarial-qa X4)*

---

## 4. Verdict

This document is not ready to drive production. Reviewers credited real strengths: the moment-to-moment loop is well specified (systems-designer), and the mock-connection test rig and the "world never waits on a model" event design are sound (feasibility-lead). But every lens found that the committed build does not demonstrate the game being pitched. The 4-week MVP has no "!", no jack-out and no progression. The bridge every pillar depends on has one week and no spec. The safety model rests on a "!" that can be forged and on human attention the reward structure wears down. And the design has only ever been written for a single player, its author. The board also leaves one product decision open (productivity tool or wellness game), and it sits underneath the bedtime, autonomy and long-term-win disputes. The single change that matters most is the one all six reviewers converged on: **re-cut the MVP so it closes the full loop against one mock worker, with a written list of what is deferred.** The build order is a bridge spike with a written event schema, then talk with a static "!", then answer, then approve, then the world changes, then closing time. Each of the other top issues either depends on that re-cut or is decided by it.
