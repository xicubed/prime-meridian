# Content run: Hal's voice lines, keyed by event

- request: `content/requests/hal-lines.edn`
- writer: `claude-sonnet-5`, critic: `claude-opus-5-5`
- GDD sections: §1., §2.1, §2.3, §2.6, §2.7, §3.


## Draft 1

- writer: 4684 tokens out; validator: 2 errors
  - :morning-brief[0]: a count followed by a plural noun reads wrong at 1 ("1 worms"): put the count after a label ("Worms: {stock}"): "Morning card's up: {count} items."
  - :weekly-card[1]: a count followed by a plural noun reads wrong at 1 ("1 worms"): put the count after a label ("Worms: {stock}"): "{hours} this week. Worth noticing, nothing more."

## Draft 2

- writer: 3190 tokens out; validator: PASS
- critic: PASS, 13 minor; "Mostly: the rules are right (closing time framed as the player's own clock, stay-late asks once, no streaks, real life comes first), but the voice is often functional rather than warm and wry, with little Matrix/TRON shorthand outside :program-loaded and :jack-in, and several count lines read like a status readout instead of Hal."
  - minor :program-loaded line 2, "That's {program} on your belt now.": Loading a program hangs the tool on the twin's belt, not the player's. The loadout is the player's, but the twin carries it. [§2.7, §2.6]
  - minor :agent-stalled lines 1 and 3, "Freed the desk." / "I let the desk go rather than hold it.": The bridge marks a worker stalled on its own, and its keycards come back. Hal claims the action as his and never mentions the keycards. 'Ran out of runway' is the only variant that hints at the real cause, a usage cap that refills. [§3.1, §2.3, §2.1 (Check the juice)]
  - minor :coming-back line 1, "That one's back in the ledger.": 'Ledger' is Pepper's business ledger. Applied to chores, it suggests a running tally, which brushes against 'no number on screen.' None of the variants offers the next small step, which the GDD says Hal does on return. [§2.6, §2.3 (Coming back in), §3.1]
  - minor :morning-brief and :blueprint-ready, "Items on it: {count}.", "Count: {count}.", "Blueprints waiting: {count}.", "Something to approve — count: {count}.", "Ready for your eyes. Blueprints waiting: {count}.": These are the same 'label: {count}' line reworded. They read like a log readout, which is the wall of text the game exists to replace. Blueprints come as one grouped tray on the morning card, and no variant uses that picture. [§1 (Why a game, not a terminal?), §2.1 (Approve)]
  - minor :next-small-step, all four lines: The GDD has Hal offer the smallest next step, but every line just says 'one small thing' without offering one. "Let's not overthink it. One small task, and the rest follows." is generic, and "The place could use some attention" leans slightly toward guilt. [§2.6, §1 (No game over)]
  - minor :weekly-card, all lines: The card is meant to show a trend, but every variant shows only {hours}. A bare number is the closest thing to a score. "However you take that." reads as faintly passive-aggressive. The long-term win counts agents needing you less as well as hours, and neither half appears. [§1 (Win / loss: the long-term win)]
  - minor :memorial-moved, all lines: This is for a pet who died, but the lines are administrative ("{name}'s moved to memorial.", "I've placed {name} in memorial."). The second line also credits Hal with something the player did. They correctly avoid any needs. [§3 (The twin rule: memorial creatures show no needs), §1 pillar 1]
  - minor :chore-not-due line 4, "You already did that at {last-at}." and line 2, "{last-at} still holds until around {next-at}.": Hal only knows that a chore-done event arrived. Someone in the household, a text, or an NFC tap could have done it, so 'you did that' can be wrong. Line 2 is awkward. [§2.1 (Mark a chore done), §3 (A twin shows only what its counterpart's real state says)]
  - minor :coach-habit lines 1 and 4, "You said {habit}: {goal}." / "Remember {habit}? {goal} was the plan.": Splitting the player's words into {habit} plus {goal} gives robotic output such as 'You said workout: 3/wk.' 'Remember…? …was the plan' implies the player forgot or fell short, which edges toward shame. "Today's as good a day as any." is generic. [§3.2 (coach uses your own goals and words, never shames), §2.6]
  - minor :stay-late line 2, "Stay as long as you want.": Accepting the choice is right, and so is not nagging. But actively inviting an open-ended stay cuts against the twin's job of sending the player back to life. [§2.3 (Closing time), §1 pillar 6]
  - minor :closing-time line 1, "{bedtime}. Goodnight." and line 4, "{bedtime} came around. Rest well.": These are fine, but only one variant frames closing as the player's own rule, and that framing is the key fact here. A wry 'you asked me to send you home' beat would carry it better. [§2.3 (Closing time: "you asked me to send you home")]
  - minor :jack-out-household line 4, "Hold that thought. \"{text}\"": This doesn't clearly send the player out. Household calls are the top jack-out priority. [§2.3 (jack-out priority table)]
  - minor :chore-undone line 3, "{name}'s {chore} wasn't actually done.": 'Wasn't actually done' faintly implies the player misreported. No variant says the chore shows as due again, which is the useful consequence. [§3.2 (never shames), §2.1 (Mark a chore done)]

**Installed** `resources/content/hal-lines.edn` (draft 2).