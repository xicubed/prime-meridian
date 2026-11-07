# Content run: Mirror bubbles and signs (creatures that mirror real life)

- request: `content/requests/mirror-lines.edn`
- writer: `claude-sonnet-5`, critic: `claude-opus-5-5`
- GDD sections: §1., §2.3, §2.4, §2.5, §3.


## Draft 1

- writer: 7205 tokens out; validator: PASS
- critic: 3 blockers, 9 minor; "Mostly: it is warm, light and never shames, and the counts come straight from state. But it reads as generic cozy-pet copy with none of the wry voice, the memorial note gets wrong who moves a pet, and two lines claim things the data doesn't hold."
  - BLOCKER :modes :memorial :hal-note, lines 2 and 4 ("I moved {name} to the sunny spot for good...", "I've moved {name} to memorial..."): Hal says he moved the pet to memorial. The GDD says only the player does that, and Hal speaks only after the player acts. Read this way, the twin is deciding about the player's dead pet. [§2.5 ("only you move it to memorial"), §3.2]
  - BLOCKER :modes :memorial :hal-note, all lines: No line says the pet's chores have stopped counting. §2.5 has the twin say exactly that, kindly and truthfully. Without it, the player may wonder whether an old feed or walk reminder still counts against winning the day. [§2.5 ("its chores stop counting toward 'your own chores done', and your twin says so kindly and truthfully"), §1 Win the day]
  - BLOCKER :juniper :hungry "Oats or hay — I'm not picky": Juniper's only chore is :hay, consuming :bales. Oats are not a state or a chore. The bubble suggests a feed the mirror can't track, so the player could do something the world never registers. [§1 pillar 1 (Real, not simulated), §2.5 (rules and chores are declared data)]
  - minor :modes :memorial :hal-note line 5 "{name}'s with us still, just differently now" and line 1 "is resting easy now": Comforting euphemisms that slide away from truthful. "With us still" comes close to talking as if the pet were present and alive. [§2.5 (memorial pets never speak as if they were alive; the twin is kind and truthful)]
  - minor :modes :memorial :hal-note line 3 "I'll keep their place on the porch, always": This note is shared by every memorial creature, but it assumes the porch. A memorial axolotl lives in the canal; a horse lives in the barn. [§2.5 (a mirror's :home is declared per creature)]
  - minor :modes :memorial :plaque "{name} rests here, {years}.": Claims a burial on the estate. The plaque marks a memorial, not a grave, so this is not quite truthful. [§2.5]
  - minor :juniper :hungry "My hay net's empty"; :juniper :fed "Good hay today": Both assert state the mirror doesn't have: no hay net, no hay quality. Small, but the creature should say only what its counterpart's state supports. [§3 twin rule (a twin shows only what its counterpart's real state says)]
  - minor :flora :low, :flora :stock-sign, :juniper :low, :juniper :stock-sign (every line with {stock}/{bales} + a plural noun): With a count of 1 these render as "1 worms" / "1 bales". Flora's low sign fires at 2 or fewer, so it will show 1, and also 0. [§1 pillar 1 (the sign is the count you reported)]
  - minor :juniper :hungry "I'm famished": Stronger than every other :hungry line, so it reads as a step up in urgency. Overdue should be one steady state. [§2.3 Pressure ceiling]
  - minor :flora :low vs :flora :stock-sign; :juniper :low vs :juniper :stock-sign: Little real variety. "{stock} left in the jar" (low) and "{stock} worms on hand" (everyday) read the same, so the low sign doesn't look different from the normal one. Juniper's set copies Flora's line for line ("Only X left", "Running low: X"). [§1 pillar 3 (Spatial over textual: state readable at a glance)]
  - minor voice across the set; generic examples: "That was tasty", "Just chilling here", "Full and happy", "{name}'s having a lovely day": Warm, but it could be from any pet app. There is no wry Prime Meridian touch and no light jack-in or program shorthand anywhere. [§1 (The cast: you're Neo; design pillars)]
  - minor :modes :virtual :idle (all lines): These are speech bubbles over the creature, but every line narrates in third person ("{name}, here simply for company"). That clashes with the creature-voice bubbles everywhere else. [§2.5 (virtual mode), §3.1 (keeper bubble over the creature)]

## Draft 2

- writer: 1745 tokens out; validator: 1 errors
  - :memorial :hal-note[3]: a need or chore (§2.5: memorial/virtual creatures have no needs): "{name}'s feedings and walks have stopped counting. Their place stays warm."

## Draft 3

- writer: 1125 tokens out; validator: PASS
- critic: 2 blockers, 8 minor; "Mostly: the creature bubbles are light and warm, and the signs read like a hand-lettered jar label, but much of it is stock pet-app copy with little of Prime Meridian's wry edge, and Hal's memorial note sounds like a settings confirmation rather than a companion."
  - BLOCKER :flora :low "Only {stock} worms left", "{stock} worms — restock soon", "Running low: {stock} worms"; :juniper :low "Only {bales} bales left", "{bales} bales — order more", "Running low: {bales} bales": The :low sign shows whenever the count is 2 or fewer (estate.edn rule [:<= :stock 2]), so it will regularly display 1 and 0. These lines would read "Only 1 worms left", "1 bales — order more", and "Only 0 worms left". That is broken text on the one sign that has to be read at a glance. The GDD's own sign avoids this with a label form. [§2.5, §1 pillar 3 (spatial over textual)]
  - BLOCKER :modes :memorial :hal-note (all five): The GDD says that when a pet moves to memorial, its chores stop counting toward "your own chores done", and the twin says so kindly and truthfully. Only the last line hints at this ("nothing more asked of them"). None of them tells the player plainly that the reminders have stopped. They also open with clerical wording ("You've moved {name} to memorial") instead of kindness, so Hal sounds like a butler logging a change, not a companion. "Just their sunny spot now, remembered" is also awkward. [§2.5 (memorial mode), §3.2 (your agent as coach, never cold), §3 twin rule]
  - minor :modes :memorial :hal-note "Their sunny spot stays theirs", "in their favorite spot in the sun"; :plaque "{name} rests here, {years}.": These lines state things that may not be true. Not every memorial pet's personality names a sunny spot; that is Rex's example, not a rule. "Rests here" implies the pet is buried on the estate. The twin should say only what is real. [§2.5 (personality, memorial), §3 twin rule]
  - minor :juniper :hungry "I'm famished": The overdue state is one steady state that never escalates. "Famished" reads as a step up from hungry and leans toward guilt. The other hungry lines stay gentle. [§2.3 pressure ceiling]
  - minor :flora :fed "Yum, more please... later"; :juniper :fed "Good hay today": Flora asking for more right after being fed blurs the every-2-days schedule. "Good hay today" claims the hay's quality, which the game has no data for. [§1 pillar 1 (real, not simulated), §2.5]
  - minor :juniper :low "{bales} bales — order more"; :flora :low "{stock} worms — restock soon": A sign giving orders to buy reads like a directive and puts purchase pressure on the player. Purchases are the player's decision, and an agent may only draft them for approval. [§3.2]
  - minor :biscuit :porch "Just chilling here", "Watching the birds", "Good spot for a nap"; :modes :virtual :idle "{name} is enjoying the sun": These are generic, and they assume daylight. The estate goes to night at closing, so a sun or bird line at night would be untrue. "Just chilling here" could fit any app. [§2.3 (closing time / night), §1 voice]
  - minor :modes :virtual :idle "{name}, here simply for company": This describes the game mechanic (virtual = company) instead of being a bubble. The virtual lines are also in third person while every other bubble is first person, which is inconsistent within :say. [§2.5 (virtual mode)]
  - minor :flora :stock-sign, :juniper :stock-sign: There is little real variety: "Worms: {stock}", "Worm jar: {stock}", and "Jar count: {stock}" are the same label reworded. That suits a sign, but five near-duplicates add little. [§2.5]
  - minor :flora :fed "Full and happy"; :juniper :fed "That was tasty", "Thanks for the hay"; :biscuit :walked "Great walk!": Generic thank-you lines. They are pleasant but carry none of the game's wry warmth. [§1 (tone)]

**Not installed**: still failing after 3 drafts.