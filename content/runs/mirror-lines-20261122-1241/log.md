# Content run: Mirror bubbles and signs (creatures that mirror real life)

- request: `content/requests/mirror-lines.edn`
- generator: `claude-sonnet-5`, evaluator: `claude-opus-5-5`, refiner: `claude-opus-5-5`
- GDD sections: §1., §2.3, §2.4, §2.5, §3.; style guide: `content/STYLE.md`
- threshold: overall ≥ 8.0, no criterion under 6 (weights A×1.5 B×1.0 C×2.0 D×1.0 S×1.5)


## Draft 1 — Generator

- generator: 2 tokens in, 9728 out, $0.1925; validator: PASS
- evaluator: 2 tokens in, 8395 out, $0.3673
- evaluator: draft 1: 6.8 (A 6 B 8 C 9 D 5 S 5) — under the bar (under 6: D, S)
  - A 6: The creature voices are light and plain ("Leash is right there.", "Jar's looking far away."), and no film lines or job titles appear. Several lines break the twin rule, though. Hal claims the memorial move the player made ("I moved {name} to memorial."), plaques invent traits for any pet ("Slept in every sunny spot."), and some bubbles assert state the estate doesn't track ("Manger's empty.", "Nice day for a nap."). Biscuit's own bubble also speaks to Biscuit ("Walk, Bisco?").
  - B 8: The chores match the mirrors: Flora's worms and jar, Juniper's hay and bales, Biscuit at the door by his leash. The :hungry lines stay one steady, unescalating ask ("Worms, please?", "Hay, please?", "Walk time?"), which respects the pressure ceiling. The memorial notes never tell the player plainly that the pet's chores are off their list, and §2.5 says the twin does say so.
  - C 9: There are no scores, streaks or points. Nothing shames a missed feeding, nobody real is named, and no line claims a purchase or message. No memorial line speaks as if the pet were alive ("{name} doesn't need anything from us now."). One memorial note tells the player how to feel about their loss.
  - D 5: Every line is bubble-length and the {stock}/{bales} placeholders avoid the '1 worms' trap. But the signs are one line reworded five times ("Worms: {stock}", "Worm jar: {stock}", "Jar count: {stock}"), some read as inventory software ("Stock: {stock}", "Low stock: {stock}"), and lines repeat across creatures. Several placeholder lines break for some values or species.
  - S 5: A few details are Prime Meridian's own: the worm jar, the leash by the door, the hay count. Most lines could sit in any cozy pet game ("Full and happy.", "Better now.", "All good here.", "Settled in."). None touch the estate's places (canal, porch, barn), Flora's 5 pm feed, the 12-hour hay round, or the fact that the twin, not the pet, carries the memorial news.
  - violation [A rule 3] :modes :memorial :hal-note line 2 "I moved {name} to memorial..." and line 4 "I've set {name} up in memorial...": GDD §2.5 says only the player moves a pet to memorial, and this note fires when they do. Hal claiming the act is untrue to what really happened.
  - violation [A rule 1] :modes :memorial :hal-note line 4 "I've set {name} up in memorial. Nothing's expected of them anymore, only remembered.": It reads like an administrator closing a ticket, not a companion at a loss. The second sentence is also ungrammatical ("Nothing's expected... only remembered").
  - violation [A rule 3] :modes :memorial :plaque lines 2, 3, 5 ("Slept in every sunny spot.", "Followed everywhere, loyally.", "A steady, gentle friend."): These invent a personality for any memorial pet. In GDD §2.5 personality is the player's own words, declared per creature, and a generic plaque can't know them.
  - violation [A rule 3] :juniper :hungry "Manger's empty." and "Nosing the gate.": The mirror tracks only the 12-hour hay chore and a bale count. An empty manger and a gate are invented state, not the creature's real state (pillar 'real, not simulated').
  - violation [A rule 3] :biscuit :porch "Nice day for a nap.": The sky follows the clock (estate.edn :sky), so this can show at blue hour or night. It claims weather the estate doesn't report.
  - violation [A rule 2] :biscuit :walk-due "Walk, Bisco?": This is Biscuit's own bubble, but it addresses Bisco, so it reads as the player or twin talking to the dog. The bubble should speak to you.
  - violation [B rule 6] :modes :memorial :hal-note (all five): GDD §2.5: a pet moved to memorial stops counting toward 'your own chores done' and the twin says so. The lines only imply it ("No tasks, no tending"). They never tell the player the day no longer waits on that chore.
  - violation [B rule 6] :flora :fed "Worms: delivered.": Feeding is a real chore the player does by hand (the snap moment, GDD §2.2). 'Delivered' frames it as a courier drop-off, not the moment the player just had with Flora.
  - violation [C rule 12] :modes :memorial :hal-note line 2 "They're remembered now, not tended to — and that's right.": 'And that's right' passes judgment on the player's grief and decision. Kind and truthful means stating what changed, not ruling on it.
  - violation [D rule 18] :flora :stock-sign and :juniper :stock-sign (all ten): Every variant is 'label: {count}', which is the same line reworded, not real variety.
  - violation [D rule 18] :flora :low and :juniper :low: The low signs reword each other and the everyday sign. "Hay stock: {bales}" appears in both :low and :stock-sign, so a low pile looks no different from a full one. "Running low — {x} left" is copied across both creatures.
  - violation [D rule 18] :flora :fed "That hit the spot." and :biscuit :walked "That hit the spot.": The same line is reused across two creatures, and as a food idiom it doesn't fit a walk.
  - violation [D rule 18] :biscuit :porch "Watching the yard." and "Keeping watch.": These two variants say the same thing.
  - violation [D rule 17] :flora :stock-sign "Stock: {stock}", :flora :low "Low stock: {stock}", :juniper :stock-sign "On hand: {bales}": These are warehouse labels on what the request calls hand-lettered signs. That is corporate, not the cozy diorama voice.
  - violation [D rule 19] :flora :low "Running low — {stock} left" and :juniper :low "Running low — {bales} left": :low fires at <= 2, which includes 0, and "Running low — 0 left" is wrong: the supply is out, not low.
  - violation [D rule 19] :modes :memorial :plaque line 1 "{name}, {years}. A good dog, missed.": Memorial is a mode for any creature (§2.5). With {name} = an axolotl or a horse, 'a good dog' is wrong.
  - violation [D rule 19] :modes :virtual :idle line 5 "{name}'s content, watching the water.": A virtual creature can live on the porch or in the barn. 'Watching the water' only reads right for one at the canal.
  - violation [D rule 19] :modes :memorial :plaque line 3 "{name}, {years}. Followed everywhere, loyally.": 'Followed everywhere' has no object, so the line doesn't read correctly. It also assumes a following animal.
  - violation [S rule 3] :flora :hungry and :fed (most variants): Generic hunger and fullness lines. Nothing ties them to Flora's canal, her jar on the bank, or her 5 pm feed.
  - violation [S rule 3] :juniper :fed ("That's better.", "Full now.", "Settled in."): These lines could belong to any horse in any game. They don't mention the barn or the 12-hour rhythm.
  - violation [S rule 3] :biscuit :porch "All good here.", :modes :virtual :idle "{name}'s around, no fuss.": These are placeholder-generic idles with no sense of the estate.

## Draft 2 — Refiner

- refiner: 2 tokens in, 6986 out, $0.3659; validator: PASS
- evaluator: 2 tokens in, 8819 out, $0.3759
- evaluator: draft 2: 8.0 (A 8 B 9 C 10 D 6 S 6) — over the bar
  - A 8: Most creature lines come from real chores: "It's my five o'clock worm." matches Flora's 17:00 feed, "Seven o'clock hay round?" matches Juniper's 07:00/19:00 hay, and "Leash is right there." matches the leash by the door. The memorial hal-note "I'm sorry about {name}. Their chores are off your list; the day no longer waits on them." is warm and truthful to §2.5. Points come off for two :fed lines that promise a fixed interval the clock-anchored schedule doesn't keep, and for one hal-note that reads like a status change rather than a companion.
  - B 9: The :hungry lines stay one steady, unescalating ask, as the §2.3 pressure ceiling requires ("Worms, please?", "Hay time, no?"). None of them carries a "!", so the declared needs-you mark can lead the bubble. Biscuit's lines cover only his walk, his own chore, and never his feeding, which is :who :spouse. One :hungry line implies a rising state.
  - C 10: Nothing here is forbidden: no points, streaks or scores, no shame ("Thanks for that.", "Tired, in a good way."), and no claim that anything was bought or sent. The memorial plaques stay out of the pet's voice ("{name}, {years}. Loved."), and the hal-notes never make a gone pet speak or need anything ("{name} doesn't need anything from us now.").
  - D 6: Every line fits bubble and plate length, and the voice is cozy, never corporate. Several variant sets, though, are the same line reworded: "Worms left: {stock}" / "Worms: {stock}, getting low", and three hal-notes ending on a spot in the sun. Two sign lines also read wrong at some of the values they will show.
  - S 6: Flora's lines are clearly Prime Meridian: "It's my five o'clock worm.", "Up from the weeds. Worm?" (the GDD opening), "Snap. Got it." (her :fx :snap moment), and the canal-side jar signs. Juniper's set is largely Flora's template with the noun swapped ("Hay, please?", "Mm, hay.", "Back to the barn, full."). Biscuit's and the virtual creature's lines ("Good walk.", "Back and happy.", "Just resting here.", "{name}'s content, just watching.") could come from any cozy game.
  - violation [A rule 3] :juniper :fed "See you in twelve hours.": The hay chore is anchored at 07:00 with :every "12h". If the player hays late, the next one is due sooner than twelve hours, so the line can state something untrue (request note 6, pillar 'real, not simulated').
  - violation [A rule 3] :flora :fed "Two days till the next one.": Flora's feed is anchored at 17:00 every 2d. After a late feed the next one is less than two days away, so the bubble can misreport the real schedule.
  - violation [A rule 1] :memorial :hal-note "{name}'s in memorial. No tasks, no tending — just a place for them in the sun.": "{name}'s in memorial. No tasks" reads as a system status change, not the twin talking to you as a companion. It also nearly repeats hal-note #1.
  - violation [B rule 6] :juniper :hungry "Getting hungry.": "Getting" implies hunger that keeps rising. An overdue chore is one steady state that never escalates (§2.3 pressure ceiling, request note 6), and this line still says "getting" hours into the overdue state.
  - violation [D rule 18] :flora :low "Worms: {stock}, getting low" (vs "Worms left: {stock}"): This is the same line as variant #1, reworded.
  - violation [D rule 19] :flora :low "Worms: {stock}, getting low": The sign shows at stock ≤ 2, including 0, and "Worms: 0, getting low" reads wrong.
  - violation [D rule 18] :juniper :low "Low on hay: {bales}" (vs "Bales left: {bales}"): This is the same line as variant #1, reworded.
  - violation [D rule 19] :juniper :low "Last of the hay · {bales}": The sign shows at bales ≤ 3. "Last of the hay · 3" overstates the shortage, and "Last of the hay · 0" contradicts itself.
  - violation [D rule 18] :memorial :hal-note #1, #3, #5 ("a quiet spot in the sun" / "a place for them in the sun" / "just a spot to remember them by"): Three of the five variants end on the same image and say the same thing.
  - violation [D rule 18] :virtual :idle "{name}'s here for the company." (vs "{name}'s just here, keeping company."): This is the same line as variant #1, reworded.
  - violation [S rule 17] :juniper :hungry "Hay, please?" / :fed "Mm, hay." / :fed "Back to the barn, full.": These copy Flora's "Worms, please?" / "Mm, worms." / "Back to the weeds, full." with the noun swapped, so Juniper has no voice of her own (the GDD gives her a lower, slower voice).
  - violation [S rule 17] :biscuit :walked "Good walk." / "Back and happy."; :porch "Just resting here.": These are generic and use none of Biscuit's declared specifics (the leash by the door, the porch, the daily walk).
  - violation [S rule 17] :virtual :idle "{name}'s content, just watching." / "{name}'s happy right where they are.": Nothing ties these to the estate. They could float over a creature in any game.

**Installed** `resources/content/mirror-lines.edn` (draft 2, 8.0).

## Trajectory

- draft 1: 6.8 (A 6 B 8 C 9 D 5 S 5)
- draft 2: 8.0 (A 8 B 9 C 10 D 6 S 6)

## Cost

- total: $1.3015 over 4 model calls (API-equivalent, as reported by `claude -p`)
- evaluator: 2 calls, 4 in / 17214 out, $0.7431
- refiner: 1 call, 2 in / 6986 out, $0.3659
- generator: 1 call, 2 in / 9728 out, $0.1925
- most expensive step: evaluator on draft 2 (claude-opus-5-5), $0.3759