# Content run: Hal's voice lines, keyed by event

- request: `content/requests/hal-lines.edn`
- generator + refiner: `claude-sonnet-5`, evaluator: `claude-opus-5-5`
- GDD sections: §1., §2.1, §2.3, §2.6, §2.7, §3.; style guide: `content/STYLE.md`
- threshold: overall ≥ 8.0, no criterion under 6 (weights A×1.5 B×1.0 C×2.0 D×1.0 S×1.5)


## Draft 1 — Generator

- generator: 9763 tokens out; validator: 1 errors
  - :weekly-card[2]: a count followed by a plural noun reads wrong at 1 ("1 worms"): put the count after a label ("Worms: {stock}"): "{hours} this week. Check the trend on the board."
- evaluator: draft 1: 6.9 (A 7 B 8 C 8 D 5 S 6) — under the bar (under 6: D)
  - A 7: Most lines sound like a companion: 'This one's real.', 'Go, I've got the desks.' and 'Staying, then. No more asking tonight.' are short, warm and plain. Two groups break the voice. :morning-brief and :blueprint-ready read like dashboard labels ('Card's ready. Items: {count}.'). :memorial-moved claims Hal did what the player did ('I moved {name} to memorial.'). No film quotes and no job titles appear, and :program-needed echoes the GDD's own 'I need a program for this one.'
  - B 8: Most lines keep the world rules. Jack-outs send you to life and the estate waits ('Go, I've got the desks.'). Stalls are plain, with no apology ('Cap ran out on {agent}. Freed the desk.'). :stay-late asks once and accepts ('No more asking tonight.'). However, no :last-call or :closing-time line frames bedtime as the player's own rule, and one :program-loaded line implies a live connection.
  - C 8: Nothing is bought or sent. There are no private details. Memorial lines give the pet no needs and no voice ('{name}'s there, in memorial. Always will be.'). :chore-undone carries no blame ('No worries — {name}'s {chore} is open again.'). Two light slips: :weekly-card names the 'score' frame, and one :coach-habit line can read as doubting the player.
  - D 5: Every line is bubble-length. Several events, though, are one line reworded four times. :morning-brief is always '<verb>. Items: {count}.'. :blueprint-ready is always '<label>: {count}.'. :agent-stalled always says 'stopped/desk freed', and :memorial-moved always says 'moved to memorial'. The 'Items: {count}' readouts are corporate rather than cozy, and lines that open on {chore} or {goal} read badly with real values.
  - S 6: Some lines could only be Prime Meridian: 'let's clear last night's tray', 'How about just one "!" to start?', 'the estate's going to night', 'Cap ran out on {agent}', 'Go, I've got the desks.' Many others could come from any cozy app or reminder tool. Examples: 'It's {bedtime}. Goodnight.', 'Your call. I'll keep things running.', and the :chore-done and :chore-undone sets. :jack-in never mentions overnight work, blueprints, or the estate.
  - violation [A rule 1] :morning-brief, all 4 lines: 'Card's ready. Items: {count}.' and 'Up on the board — items: {count}.' are form-field readouts. Hal isn't speaking as himself, there's nothing warm or wry, and he doesn't say what's next.
  - violation [A rule 1] :blueprint-ready, all 4 lines: 'Waiting for approval: {count}.' and 'Ready for your look: {count}.' are status labels with a number, not a companion telling you what's waiting and what to do next.
  - violation [A rule 1] :next-small-step 'Pick one thing. I'll take it from there.': The event asks Hal to offer the smallest next step. This line hands the choice back to the player instead of naming a step.
  - violation [A rule 3] :memorial-moved 'I moved {name} to memorial.': The player moved the pet, not Hal. The line misstates what really happened.
  - violation [A rule 1] :memorial-moved '{name}'s moved to memorial now.': Reads as a flat system notice for a pet who died. The companion voice should be gentle without performing.
  - violation [A rule 1] :jack-in 'You're in. Let's see what's going on out there.': In this game 'out there' means real life. On jack-in Hal should point at the estate and what changed while you were out.
  - violation [B rule 6] :last-call and :closing-time, all 8 lines: No variant says bedtime is a rule the player set. 'Last call before {bedtime}.' and 'It's {bedtime}. Goodnight.' could read as a house curfew. The GDD's framing is 'you asked me to send you home'.
  - violation [B rule 8] :program-loaded '{program}, online. The {building}'s rising.': Programs load first as sample versions (§4.6). 'online' claims a real, live connection.
  - violation [C rule 11] :weekly-card 'Hours jacked in: {hours}. There's the trend, not a score.': Saying 'not a score' puts the score frame in the player's head. The rule is to never bring scores up.
  - violation [C rule 12] :coach-habit '{habit} — your goal's {goal}. Still on?': 'Still on?' can read as questioning the player's commitment. The GDD model is a plain offer in the player's own words ('One today?').
  - violation [D rule 18] :morning-brief, all 4 lines: One line reworded: 'Morning card's pinned. Items: {count}.' / 'Card's ready. Items: {count}.'
  - violation [D rule 18] :blueprint-ready, all 4 lines: Four labels in front of {count}: 'Blueprints waiting: {count}.' / 'Waiting for approval: {count}.'
  - violation [D rule 17] :morning-brief / :blueprint-ready 'Items: {count}.' 'Waiting for approval: {count}.': Form-field phrasing is the corporate register the diorama voice avoids.
  - violation [D rule 18] :agent-stalled, all 4 lines: Every variant says '{agent} stopped/went quiet/stalled, desk freed'.
  - violation [D rule 18] :memorial-moved, all 4 lines: Four restatements of 'moved to memorial'.
  - violation [D rule 18] :last-call 'Fifteen minutes to {bedtime}. Last call.' vs 'Last call before {bedtime}.': Same line reworded. A variant could carry the real last-call job: night-shift orders.
  - violation [D rule 18] :chore-done, all 4 lines: All four are 'done/logged' with the same two placeholders reordered.
  - violation [D rule 19] :coming-back '{chore}'s done.' / :chore-done '{chore}, for {name}: done.' / :chore-undone '{chore}, {name}: taken back.' / :coach-habit '{goal}, for {habit} — want to log one today?': Opening on a declared value like 'feed' gives 'feed's done.' and 'feed, for Flora: done.' (lowercase, ungrammatical). '{name}'s {chore}' gives 'Flora's feed'. 'log one' assumes the goal is countable.
  - violation [S rule 6] :closing-time 'It's {bedtime}. Goodnight.' / '{bedtime}'s here. Rest up — goodnight.': Generic bedtime lines. The GDD's closing time is specific: the estate goes to night, agents keep working, and results wait as blueprints at breakfast.
  - violation [S rule 9] :jack-in 'Here we are. Anything catch your eye first?' / 'Good to have you back in.': Jack-in is when you see what changed overnight and what waits for approval. These lines would fit any app.
  - violation [S rule 6] :stay-late 'Your call. I'll keep things running.': Generic. The GDD's stay-late is specific: the estate stays at night and the day isn't won, with no other penalty.
  - violation [S rule 3] :chore-undone / :chore-done, all lines: Nothing ties these to the estate or the creature's real state (the canal, the jar, the worm count). A stranger couldn't place them.

## Draft 2 — Refiner

- refiner: 12884 tokens out; validator: 4 errors
  - :morning-brief[0]: a count followed by a plural noun reads wrong at 1 ("1 worms"): put the count after a label ("Worms: {stock}"): "Pinned this morning's card: {count} items. Want the first one now?"
  - :morning-brief[3]: a count followed by a plural noun reads wrong at 1 ("1 worms"): put the count after a label ("Worms: {stock}"): "Morning card's pinned — {count} items, whenever you're ready."
  - :blueprint-ready[0]: a count followed by a plural noun reads wrong at 1 ("1 worms"): put the count after a label ("Worms: {stock}"): "Last night's tray is on the shelf: {count} blueprints, whenever you're ready."
  - :blueprint-ready[3]: a count followed by a plural noun reads wrong at 1 ("1 worms"): put the count after a label ("Worms: {stock}"): "On the shelf tonight: {count} blueprints, before and after."
- evaluator: draft 2: 7.9 (A 8 B 7 C 10 D 6 S 7) — under the bar
  - A 8: Hal mostly sounds like the GDD's companion: "{bedtime}, like you asked. I'll walk you to the door." and "I need {program} for this one." echo his canon lines, and every line speaks to you without quoting the films. Points come off for three clipped lines that read as orders or as sulking rather than warm and wry, such as "{name} needs you out there. Go." and "Staying up, alright. I asked — that's enough."
  - B 7: The world rules mostly hold. Bedtime is framed as yours ("Last call. You asked me to send you home at {bedtime}."), blueprints wait ("Night crew's on; blueprints by breakfast."), and stalls are plain facts ("Cap ran out on {agent} — its keycards are back, desk's open."). Points come off for three lines. One claims reality that may be a sample, one makes closing Hal's call, and one puts the blueprint tray at night.
  - C 10: Nothing forbidden is said. There are no points, levels or streaks, and "That one counts." stays street-cred language. No line claims a purchase or a sent message ("I'll hold things down here."), the undo is blameless ("No worries — {name}'s {chore} is open again."), and memorial lines give the pet no needs ("{name}'s in the memorial now. I'll keep the spot tidy."). The weekly card shows hours and a trend, never a score, and no private details appear.
  - D 6: Every line fits a bubble, the tone stays cozy, and no line sounds corporate. Variety is the weak spot: :weekly-card is one line said four ways ("Hours jacked in: {hours}. The trend's on the board." / "Hours this week: {hours}. Check the trend on the board."). :jack-out-appointment and :coach-habit are reworded the same way, and a few placeholders read awkwardly with real values.
  - S 7: Many lines are unmistakably Prime Meridian: "Last night's tray is on the shelf", "{agent}'s out of juice till it refills", "{program}'s loaded, sample for now. The {building}'s rising." and "Any night-shift orders before {bedtime}?" Others could come from any cozy game, such as "That's handled. Back to what you were doing?", "Good — {chore}'s taken care of." and "Go see {name} — I'll keep an eye on things." The phone booth and hardline never appear in the jack-out lines.
  - violation [A rule 1] :jack-out-creature line 4 "{name} needs you out there. Go.": The bare imperative "Go." reads as a drill sergeant. There is no warmth and no hand-off of what Hal holds while you're out.
  - violation [A rule 1] :stay-late line 1 "Staying up, alright. I asked — that's enough.": "that's enough" sounds tetchy, as if Hal is keeping score of the ask. A companion accepts it plainly.
  - violation [A rule 1] :jack-out-appointment line 1 "Leave by {leave-at} for {event}.": This is a flat calendar notification, not the twin's voice. It has no wry or warm beat and no sense that Hal is with you.
  - violation [B rule 8] :jack-out-creature line 2 "{name}'s calling for you. This one's real.": In demo mode the creature and the twin are SAMPLE connections, so "This one's real" would pass a pretend chore off as real. Rule 8 forbids sample content pretending to be real.
  - violation [B rule 6] :last-call line 4 "{bedtime} comes up fast. Anything else before I call it?": "before I call it" makes closing time Hal's decision. Closing time is a rule the player set, not a lock Hal imposes.
  - violation [B rule 9] :blueprint-ready line 4 "On the shelf tonight: {count} blueprints, before and after.": The grouped tray of last night's blueprints waits at breakfast. "tonight" also contradicts line 1's "Last night's tray".
  - violation [D rule 18] :weekly-card, all 4 lines: All four variants say hours plus 'trend on the board' in different word order. This is the same line reworded, not real variety.
  - violation [D rule 18] :jack-out-appointment, lines 1-3: "Leave by {leave-at} for {event}.", "{event} — out the door by {leave-at}." and "Time to go: {leave-at}, for {event}." carry identical content.
  - violation [D rule 18] :coach-habit, all 4 lines: All four variants restate habit + goal + 'today?' in a different order.
  - violation [D rule 18] :jack-in lines 3 and 4: "Here we are. Let's see what the night crew left on the shelf." and "You're in — the night crew left a few things on the shelf." are the same line.
  - violation [D rule 19] :coach-habit line 1 "You said {habit}: {goal}. Count it today?"; :chore-done lines 1 and 4 "Done: {chore} for {name}.": With declared values these read badly: "You said workout: 3/wk." and "Done: feed for Flora." The lines don't read correctly with any value.
  - violation [S rule 3] :chore-done line 3 "That's handled. Back to what you were doing?": This line is generic and drops both the creature and the chore. It doesn't come from a real pet's state.
  - violation [S rule 7] :coming-back line 2 "Good — {chore}'s taken care of.": This line is generic. It misses the GDD's return beat: the chore counted, the estate waited, and here is the next small step.
  - violation [S rule 7] :jack-out-household, all 4 lines: None of the lines places the call at the phone booth (the hardline), the estate's signature jack-out.
  - violation [S rule 3] :jack-out-creature line 3 "Go see {name} — I'll keep an eye on things.": "keep an eye on things" is generic. Name what Hal actually holds.

## Draft 3 — Refiner

- refiner: 7310 tokens out; validator: 1 errors
  - :weekly-card[2]: a count followed by a plural noun reads wrong at 1 ("1 worms"): put the count after a label ("Worms: {stock}"): "{hours} this week. The rest of the time was yours."
- evaluator: draft 3: 7.7 (A 8 B 8 C 8 D 6 S 8) — under the bar
  - A 8: Hal mostly sounds like the GDD's Hal: "Welcome back.", "You said {goal} for {habit}. One today?" and "I need {program} for this one." are his own lines. Every line addresses you, and no crew job titles appear. Points come off for two invented details (the card "pinned by the coffee", a habit that "fits before lunch") and a repeated butler tic, "whenever you're ready".
  - B 8: The world rules mostly hold. Closing is the player's own rule ("You asked me to send you home at {bedtime}.", "I'll walk you to the door."), jacking out is good ("Go, the estate will wait."), work waits as blueprints ("blueprints by breakfast"), and limits are stated plainly ("{agent}'s out of juice till it refills"). Points come off for one muddled bedtime line, SAMPLE being mentioned in only one :program-loaded variant, a daytime night-crew offer, and a next step that isn't small.
  - C 8: Nothing claims to have sent or bought anything. There are no scores or streaks ("The rest of the time was yours." frames the weekly card as a trend), no shaming ("No worries — {name}'s {chore} is open again."), and no private details. Points come off for the coach guessing a schedule instead of using the player's words, and one memorial line that hints the pet is still present.
  - D 6: Every line fits a bubble (the longest, "Last night's tray is on the shelf — blueprints: {count}, whenever you're ready.", is about 80 characters), and the tone stays cozy. Variety is the main problem: several events offer the same line reworded. The "items: {count}" / "blueprints: {count}" construction reads like a form, and a few placeholders read badly with real values.
  - S 8: A stranger would place most of these lines: the hardline, the shelf and "last night's tray", the night crew and "blueprints by breakfast", juice and keycards, and a bedtime "you asked me" to keep. A few lines are generic cozy filler ("Handled: {chore}. Let's pick back up.", "Can't do this without {program} yet."), and the crew and SAMPLE/REAL barely appear.
  - violation [A rule 3] :weekly-card line 2 "{hours} in the estate this week. The trend card's pinned by the coffee.": Invents where the card is. The GDD puts the weekly card on the notice board, not by the coffee.
  - violation [A rule 3] :coach-habit line 2 "{habit} fits before lunch, if you want it. Your goal: {goal}.": "Fits before lunch" is a scheduling fact Hal has no source for. §3.2 says the twin acts on stated preferences, not guesses.
  - violation [A rule 1] :morning-brief line 4 and :blueprint-ready line 1 "...whenever you're ready.": The same deferential sign-off appears twice. It reads like a butler waiting, not a wry companion saying what's next.
  - violation [B rule 6] :last-call line 4 "{bedtime} comes up fast, like you wanted. Anything else first?": "Like you wanted" says the player wanted bedtime to come fast, which garbles the point that the player set the bedtime.
  - violation [B rule 6] :closing-time line 3 "{bedtime}. Estate's going dark.": "Going dark" sounds like a shutdown. §2.3 says the estate goes to night while the workshop lights stay on for the night crew.
  - violation [B rule 8] :program-loaded lines 1, 2, 4: Every program first loads as a pretend version (§2.7, §4.6), but only line 3 says "sample for now". The other three imply the connection is live.
  - violation [B rule 10] :agent-stalled line 3 "Want the night crew on it?": The night crew takes overnight orders put in by closing (§2.3). Offering it as an instant daytime fix misstates how the limit works.
  - violation [B rule 9] :next-small-step line 1 "One small thing: let's clear last night's tray.": Clearing a whole tray is not the smallest step. "Clear" also blurs approving with rejecting.
  - violation [C rule 12] :coach-habit line 2 "{habit} fits before lunch, if you want it.": As coach, the twin should speak only in the player's own words and goals. Proposing a time slot is the twin's own judgment about the player's day.
  - violation [C rule 14] :memorial-moved line 4 "{name} stays with us, in memorial now.": "Stays with us" edges toward treating a pet who died as still present. A memorial creature should be marked as remembered, not as here.
  - violation [D rule 18] :coming-back lines 1-2 "That's {chore} done. It counts." / "{chore}: done, and it counts.": The same line reworded.
  - violation [D rule 18] :morning-brief lines 1 and 4; :blueprint-ready lines 1 and 4: Each pair says the same thing (card or tray pinned, plus a count).
  - violation [D rule 18] :chore-not-due lines 1, 2, 4: Three variants repeat "last {last-at}, next {next-at}" in a different order.
  - violation [D rule 18] :program-loaded lines 1, 2, 4; :program-needed all lines: The same beat (loaded, building rises; need program, load it) is reworded with no new content.
  - violation [D rule 18] :jack-in lines 2, 4, 5 all open "You're in."; "I've got the desks" in :jack-out-creature 1-3, :jack-out-appointment 4, :jack-out-household 2: Repeated openers and a repeated tic across variants and events.
  - violation [D rule 17] :morning-brief line 1 "items: {count}"; :blueprint-ready lines 1, 4 "blueprints: {count}": Label-colon-number reads like a form, not the warm voice of the estate. Rule 19 explicitly allows "{count} items".
  - violation [D rule 19] :blueprint-ready line 3 "It's all grouped, {count} of them.": Reads wrong when the count is 1 ("1 of them").
  - violation [D rule 19] :chore-done line 1 "{name}'s done: {chore}."; :chore-undone line 2 "{chore} for {name} wasn't done": With real values these read badly: "Flora's done: feed." sounds like Flora is finished, and "feed for Flora" drops the article.
  - violation [S rule 7] :coming-back line 3 "Handled: {chore}. Let's pick back up.": This could come from any to-do app. It doesn't say that the chore was done in real life or that the estate waited.
  - violation [S rule 4] :program-needed line 3 "Can't do this without {program} yet.": Generic. It misses the program and loadout vocabulary that marks Prime Meridian.

## Draft 4 — Refiner

- refiner: 14181 tokens out; validator: 3 errors
  - :morning-brief[0]: a count followed by a plural noun reads wrong at 1 ("1 worms"): put the count after a label ("Worms: {stock}"): "Morning card's pinned: {count} items. Want the first one now?"
  - :blueprint-ready[0]: a count followed by a plural noun reads wrong at 1 ("1 worms"): put the count after a label ("Worms: {stock}"): "Last night's tray is on the shelf: {count} blueprints, before and after."
  - :blueprint-ready[2]: a count followed by a plural noun reads wrong at 1 ("1 worms"): put the count after a label ("Worms: {stock}"): "Want to walk over to the shelf? {count} blueprints, all on one tray."
- evaluator: draft 4: 7.9 (A 7 B 8 C 10 D 5 S 8) — under the bar (under 6: D)
  - A 7: Hal's companion voice mostly lands, warm and plain, and often quotes the GDD's own Hal: "Welcome back.", "I need {program} for this one.", "You said {goal} for {habit}. One today?". Points come off where lines invent state Hal has no data for ("The river one's my favorite on the tray", "Nothing on it needs you before noon"). One :stay-late line goes back to justifying the ask instead of simply accepting the choice.
  - B 8: The world rules are well kept. Closing time is the player's own rule ("Last call. You asked me to send you home at {bedtime}.", "{bedtime}, like you asked. I'll walk you to the door."), jacking out is good ("Go, the estate will wait."), and limits are plain ("Cap ran out on {agent}: its keycards are back, desk's open."). Points come off because :program-loaded hard-codes "sample" in three of four lines, even though the event fires for real loads too.
  - C 10: Nothing forbidden appears. The weekly card is framed as a trend, never a score ("Hours this week: {hours}. The rest of the time was yours."), and the coach lines use the player's words with no pressure ("No pressure: {habit}'s still on your list, {goal}."). Undoing a chore carries no blame ("Easy fix."), memorial lines give no needs ("{name}'s there, in memorial. Always will be."), and no line claims to have sent or bought anything.
  - D 5: Lines are bubble-short and cozy, never corporate. Real variety (rule 18), though, fails in seven events, where three or four variants reword one line, e.g. :jack-out-household "Go, the estate will wait" / "I'll keep an eye on things here" / "I'll hold things down here". One :closing-time line runs to three sentences, and :weekly-card leaves {hours} unlabeled in places.
  - S 8: Most lines could only be Prime Meridian: "The hardline's ringing", "Let's see what the night crew left on the shelf", "{program}'s on my belt now", "out of juice till it refills", "its keycards are back". A few could sit in any cozy game: "Logged — {name}'s all set for now.", "No worries — {name}'s {chore} is open again.", "Okay. Just didn't want you to lose track of time.".
  - violation [A rule 3] :blueprint-ready line 4 "The river one's my favorite on the tray. Start there?": It assumes a river blueprint is on the tray. The event gives only {count}, so the line invents content that may not exist.
  - violation [A rule 3] :morning-brief line 4 "Card's up. Nothing on it needs you before noon.": It claims to know the card's contents and urgency. The event declares only {count}, so the reassurance could be false on a day with a 9 am deadline.
  - violation [A rule 3] :morning-brief line 3 "Today's card is up. The first thing on it can wait for coffee.": It asserts the priority of an item Hal hasn't been given.
  - violation [A rule 1] :stay-late line 3 "Okay. Just didn't want you to lose track of time.": The twin re-explains why he asked, which reads as a soft second nudge. Request note 6 says :stay-late asks once and never nags, and the line also drops the GDD fact that the estate stays at night.
  - violation [A rule 3] :memorial-moved line 4 "{name}'s spot is in the memorial garden now.": "Memorial garden" names a place the provided GDD sections don't declare. That is invented lore rather than the estate's real layout.
  - violation [B rule 8] :program-loaded lines 1, 3, 4 ("sample version", "sample for now", "Sample {program} loaded"): The event declares no sample/real flag, so these lines would mislabel a real login as SAMPLE. The rule needs the label to be true, not merely present.
  - violation [D rule 16] :closing-time line 3 "{bedtime}. Estate's going to night. Night crew's on; blueprints by breakfast.": Three sentences; the limit is one or two.
  - violation [D rule 18] :jack-out-household lines 1-4: All four say the same thing (hardline, quote {text}, go, I'll hold things), only reworded.
  - violation [D rule 18] :jack-out-creature lines 1, 2, 4 ("I've got the desks" / "I'll hold the desks" / "the estate will keep"): Three variants of "{name} needs you, go, I'll mind things".
  - violation [D rule 18] :program-loaded lines 1, 3, 4: "Watch the {building} go up", "The {building}'s rising" and "the {building} just landed" are one line said three ways.
  - violation [D rule 18] :weekly-card lines 1 and 2 ("Trend's on the board" / "The trend's on the notice board"): Near-identical lines.
  - violation [D rule 18] :coach-habit lines 2 and 4 ("{habit}: {goal}, in your own words" / "{habit}, {goal} — your words"): The same line reworded.
  - violation [D rule 18] :memorial-moved lines 2 and 4 ("I'll keep the spot tidy" twice): Two variants are the same thought with the same closing phrase.
  - violation [D rule 18] :stay-late lines 1, 2, 4 ("I asked once" / "I won't ask again" / "No more asking tonight"): Three variants of "I won't ask again".
  - violation [D rule 18] :last-call lines 3 and 4 ("Any night-shift orders before {bedtime}?" / "Anything for the night shift?"): Both ask the same night-shift question.
  - violation [D rule 19] :weekly-card lines 2 and 4 ("{hours} in the estate this week", "Weekly card's up: {hours}."): If {hours} is a bare number, these read "6 in the estate this week" and "Weekly card's up: 6." The line doesn't read correctly with any value.
  - violation [S rule 3] :chore-done line 2 "Logged — {name}'s all set for now.": Generic acknowledgement; nothing ties it to the estate's one-per-window chore rule or street cred.
  - violation [S rule 3] :chore-undone line 3 "No worries — {name}'s {chore} is open again.": Could come from any to-do app.
  - violation [S rule 6] :stay-late line 3 "Okay. Just didn't want you to lose track of time.": A generic line that loses the estate-at-night detail which makes the stay-late beat specific.

**Not installed**: still under the bar after 4 drafts.

## Trajectory

- draft 1: 6.9 (A 7 B 8 C 8 D 5 S 6)
- draft 2: 7.9 (A 8 B 7 C 10 D 6 S 7)
- draft 3: 7.7 (A 8 B 8 C 8 D 6 S 8)
- draft 4: 7.9 (A 7 B 8 C 10 D 5 S 8)