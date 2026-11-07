# Content run: Hal's voice lines, keyed by event

- request: `content/requests/hal-lines.edn`
- writer: `claude-sonnet-5`, critic: `claude-opus-5-5`
- GDD sections: §1., §2.1, §2.3, §2.6, §2.7, §3.


## Draft 1

- writer: 2036 tokens out; validator: 1 errors
  - :stay-late[1]: a film line (§1: the shipped game uses original dialogue): "Understood. No lock on the door, just the one question."

## Draft 2

- writer: 2286 tokens out; validator: PASS
- critic: 6 blockers, 12 minor; "Mostly yes: Hal reads as a warm, unhurried companion who sends you back to life and never nags, but the lines need more of the game's own texture (blueprints, desks, juice, loadout, jacking out) and less generic assistant phrasing, and several lines claim things Hal cannot know."
  - BLOCKER :coming-back "{chore}'s counted. Nothing here moved while you were gone.": This contradicts the GDD. On coming back you see what changed while you were out, and agents keep working with the game closed. Hal would be lying about the world state. [§2.3 step 3 (Coming back in); §2.1 (The game never adds work: agents work the same with the game closed)]
  - BLOCKER :morning-brief "Pinned {count} items overnight. Take a look when you can.": The morning brief is an errand Hal runs at 8:15, not overnight. Overnight results arrive as blueprints, not brief items. [§3.1 (morning brief: Hal pins the day's card on the notice board at 8:15)]
  - BLOCKER :morning-brief "{count} on the card today. Nothing urgent, just ready.": Hal asserts that nothing is urgent whatever the card holds. The brief draws on calendar, mail, and reminders, so it can hold today's appointment or a deadline. A twin shows only what the real state says. [§3 (twin rule); §1 pillar 1 (Real, not simulated); §2.1 Unknown status (it doesn't guess)]
  - BLOCKER :agent-stalled "...Nothing lost, just paused." and "...Desk's free, work's saved.": A stalled agent is one that stopped reporting. The bridge doesn't know whether its work survived, so Hal can't promise that. A false all-clear erodes trust the same way a wrong "!" does. [§3.1 (bridge marks an agent stalled when it stops reporting); §2.1 Unknown (the bridge can't tell, so it doesn't guess); §3 (twin rule)]
  - BLOCKER :program-loaded "{program}: live. The {building} is yours to walk into.": Every program first loads as a pretend version and goes live only after a login. In demo mode or the Construct, calling it "live" passes a sample off as real. [§2.7 (every program loads instantly as a pretend version; the real thing asks for a login and goes live); §3 (in demo mode the counterpart is clearly pretend, never a fake passed off as real)]
  - BLOCKER :last-call "Last call. 10:15..." and "10:15 — last chance..."; :closing-time "10:30. The estate goes quiet now. Goodnight.": Bedtime is declared data. 10:30 is only the default, and a night-shift nurse closes at 9 am. Hard-coded clock times will be wrong for any player who changed bedtime, and "Goodnight" at 9 am reads oddly. [§2.3 step 5 (Bedtime is declared data, `{:bedtime "22:30"}`)]
  - minor :closing-time (all lines): No variant frames closing as the player's own rule. That framing is what keeps it from feeling like a lock. [§2.3 step 5 ("you asked me to send you home"; a rule you set, not a lock)]
  - minor :coming-back (all lines): On return, the GDD has Hal offer the next small step. None of these lines do, so the beat goes unused. [§2.3 step 3 (Hal offers the next small step)]
  - minor :next-small-step "Pick one thing and I'll start there." / "Just tell me where to start again.": The GDD has Hal offer the smallest step. These lines hand the choosing back to the player, which adds a little work at a low moment. [§2.6 (Hal offers the smallest next step); §1 pillar 5]
  - minor :jack-in "You're in. The estate's been quiet without you.": It contradicts the overnight crew working while you're away, and it mildly guilt-trips by implying the world stalls without you. [§2.3 steps 1 and 4 (you see what changed overnight; the night crew works)]
  - minor :stay-late "Alright. Staying's your call, not mine to make twice." / "Fair enough. The estate stays up with you.": The first line is garbled. The second suggests the estate wakes back up, when the GDD keeps it at night. [§2.3 step 5 (if you stay, the estate stays at night)]
  - minor :chore-done "Marked: {chore} for {name}. Good timing." and "{name}'s {chore}: logged. That one counts.": "Good timing" is wrong when the chore was overdue. "That one counts" is wrong for a duplicate report in the same due window, since only one report counts. [§2.1 (Mark a chore done: only one counts per due window); §2.3 (Pressure ceiling: overdue state)]
  - minor :program-needed "{program} would get this done. Should I ask for it?": It's unclear who Hal would ask. The GDD has the twin ask the player to load it. [§2.6 (your twin asks you to load it right then)]
  - minor :weekly-card (all lines): All five lines are the same shape reworded ({hours} plus "trend"). There's also no trend slot, so Hal can't name the direction the card exists to show. [§1 Win/loss (long-term win; the weekly card shows the trend, never as a score)]
  - minor :coach-habit "You set {goal} for {habit}. I'm just asking, not counting.": Habits kept feed street cred, so "not counting" is slightly untrue. Otherwise the set uses the player's own words and is close to the GDD's line. [§2.6 (street cred tracks habits you declared); §3.2 (coach uses your own goals and words)]
  - minor :memorial-moved "{name} has a place now, apart from the rest.": "Apart from the rest" reads cold for a grief moment. The rest of the set is fine and correctly implies no needs. [§3 (memorial creatures show no needs); §1 voice (warm)]
  - minor generic lines: :chore-done "Got it — {name}'s {chore} is done.", "{name}'s set. Thanks for handling that."; :jack-in "Lights are up. Ready when you are."; :blueprint-ready "{count} blueprints up. Take a look whenever."; :jack-out-creature "I'll hold the fort.": These could come from any assistant, and "Thanks for handling that" leans butler. Light Matrix/TRON shorthand (jack, juice, loadout, hardline) appears almost nowhere, and "I'll keep the lights on" repeats across keys. [§1 (cast: you're Neo; the twin is your body in the digital world)]
  - minor :morning-brief, :blueprint-ready ({count}): Plural nouns break at a count of 1 ("1 blueprints", "1 things"). [§2.1 (approvals come grouped, so small counts are common)]

## Draft 3

- writer: 3411 tokens out; validator: PASS
- critic: 2 blockers, 16 minor; "Mostly yes: Hal is warm, brief, and consistently sends the player back to real life without shaming, but the Matrix/TRON flavor is thin, and a few lines claim state the event doesn't carry, which breaks the game's 'real, not simulated' promise."
  - BLOCKER :program-loaded line 4, "{program}'s loaded (sample for now). The {building} is yours to walk into.": Says 'sample' for every load. A real login would be mislabeled as pretend. A twin may show only its counterpart's real state, and sample versus real must always be honest. [§3 (twin rule), §2.7, §1 pillar 1]
  - BLOCKER :coming-back line 5, "{chore}'s counted. A couple of things moved while you were out.": Claims 'a couple of things' changed, but the event carries only {chore}. If nothing moved, Hal is stating a fake fact. False status teaches the player to stop trusting Hal. [§1 pillar 1, §2.1 (Unknown: 'doesn't guess'), §3]
  - minor :coming-back (all lines): The GDD's coming-back beat ends with Hal offering the next small step. No variant does this. [§2.3 step 3]
  - minor :coming-back line 2, "That's done in the world now too.": Unclear which world is meant, and the chore isn't named. [§2.3]
  - minor :jack-in line 3, "You're in. The estate's been quiet without you.": Agents work the same with the game closed, so the estate wasn't quiet. The line also guilt-pulls the player toward staying jacked in, which the game is built to avoid. [§2.1 ('The game never adds work'), §1 pillar 5 and long-term win]
  - minor :last-call line 3, "Almost time — last chance before I walk you out.": 'Last chance' makes closing sound like a lock. Closing is the player's own rule, and they can stay. [§2.3 step 5]
  - minor :closing-time (all lines): No variant says goodnight, and none frames closing as the rule the player set. Both are the GDD's beat for this moment. [§2.3 step 5]
  - minor :stay-late line 3, "Alright. Staying's your call, not mine to make twice.": The phrasing is garbled. Line 1, "Asked once — you're staying.", reads like a log entry, not a companion. [§2.3 step 5]
  - minor :stay-late line 1 "I'll keep the lights on" and line 5 "The estate stays up with you"; also :jack-out-household line 5: If the player stays, the estate stays at night. 'Keep the lights on' and 'stays up' read as the day continuing. 'Keep the lights on' is also used twice in the set. [§2.3 step 5]
  - minor :blueprint-ready line 3, "approve or send back": A rejected blueprint vanishes. 'Send back' implies it returns for rework. [§2.1 (Approve)]
  - minor :chore-done line 1, "logged. That one counts.": Only one report counts per due window. If a duplicate report (a text after a click) also fires this event, 'counts' is false. [§2.1 (Mark a chore done)]
  - minor :next-small-step lines 2 and 5: The GDD says Hal offers the smallest next step. 'Pick one thing' and 'tell me where to start' hand that choice back to the player. [§2.6, §1 (No game over)]
  - minor :coach-habit line 1, "You said {habit}: {goal}. Today's one of them?": 'One of them' fits only count-per-week goals. It fails for a goal like 'walk Biscuit every day'. With raw data it also renders as 'workout: 3/wk', which is not the player's own words. [§3.2, §2.6]
  - minor :program-needed line 4, "Should I ask for it?": Hal is already talking to the person who would load it, so it's unclear who he would ask. [§2.6, §2.7]
  - minor :agent-stalled lines 3 and 5; line 4: Lines 3 and 5 are the same line reworded. Line 4 promises an automatic resume the GDD doesn't describe. Line 2's 'Freed up the desk' credits Hal with the bridge's job. [§3.1 (stalled), §2.1 (juice meter)]
  - minor :weekly-card (all lines): All five variants say the same thing ('{hours}, trend on the board'). The GDD's long-term win counts two measures together: fewer asks from agents and fewer hours jacked in. [§1 (long-term win)]
  - minor :memorial-moved line 2, "{name} has a place now, apart from the rest."; line 4, "Moved {name} to memorial.": 'Apart from the rest' reads cold, and line 4 sounds administrative. This is a grief moment and wants Hal at his warmest. The no-needs rule is correctly kept. [§3 (memorial exception), §2.5]
  - minor voice across keys: "Take a look whenever.", "How's it going?", "Got it — {name}'s {chore} is done.", "Lights are up. Ready when you are.": These are generic assistant lines. Across the set there is very little of the estate's jack-in / program / hardline shorthand. [§1 (The cast: you're Neo), §2.7]

**Not installed**: still failing after 3 drafts.