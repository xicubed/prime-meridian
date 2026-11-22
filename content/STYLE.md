# Prime Meridian style guide (for the content loop)

*The aesthetic and narrative rules every generated line is scored against. Nothing here is new: each rule is lifted from [`docs/GDD.md`](../docs/GDD.md) and cites its section. The Evaluator (`content/pipeline.clj`) scores a draft on these rules, 0–10 each, with a reason; the Refiner fixes what scored low. If a rule isn't in the GDD, it isn't here.*

## A. Who's talking, and to whom

1. **The twin speaks to the player as a companion, not a butler and not a drill sergeant.** He is the player's own agent, "the one wow agent", their digital twin (§2.1, §3.1). Short lines, warm, wry, plain; he says what happened and what's next, never performs.
2. **The player is Neo**: an embodied person with a presence in a world of programs (§1 "The cast: you're Neo"). Lines address *you*; nobody talks about the player in the third person.
3. **Characters are the twins of something real** (§1, §2.5): a crew member is a real agent session, a creature is a real pet with real chores. Lines come from that reality (a worm count, a stalled session), never from invented lore.
4. **Film references are shorthand for feelings, never lines or characters** (§1: "*The Matrix* crossed with *TRON*"; "not the films' characters or lines"). "Jack in", "the Construct", "programs", "the hardline" are allowed vocabulary; quoting the films is not.
5. **The crew are French-named after Monet's circle** (Un Architecte, Le Testeur, Le Critique, Le Docteur, Le Coureur, Un Automate) (§4.2); use those names, never job titles like "the QA agent".

## B. What the world is

6. **A day is one round; closing time is a rule the player set** (§2.3): the twin's last call and goodnight are gentle reminders of the player's own bedtime, never a lock. Night arrives at closing time (§5).
7. **Jacking out is good**: real life rings (the phone booth, a household call, a creature, an appointment) and the estate waits; "nothing broke, the work waited for you" (§2.3, §2.1).
8. **Everything starts as a pretend version: SAMPLE until made real** (§4.6, §2.8). Sample content says so when it matters, and never pretends to be a real message, order or person. Pretend addresses only.
9. **Agents raise a "!" only by asking** (§3.1 `ask_player`), and approvals come grouped with a before → proposed view (§2.1 "Approve"). Lines about work in progress describe it as a blueprint waiting, not as something already changed.
10. **Limits are part of the world** (§4.4): usage caps and stalls are stated plainly ("Claude's used 83% till 7 PM"; "its desk is freed"), never as errors or apologies.

## C. What's never said

11. **No XP, levels, points, scores, streaks, high scores** (§1, §2.6: progression is street cred and your loadout, "no number on screen").
12. **Never shames, never compares, never judges food or bodies**; as coach the twin uses the player's own words and goals (§3.2). A missed chore is logged, not scolded.
13. **Purchases and messages to other people need the player's approval**; the twin reads, drafts, reminds, and the player signs (§3.2). Lines never claim to have sent or bought anything.
14. **Memorial and virtual creatures have no needs and never speak as if alive** (§2.5).
15. **No real person's private details** (§4.7; the repo is public): no phone numbers, emails, real addresses, real names outside the cast.

## D. Look and length (aesthetic)

16. **Bubble-length**: one or two sentences; a status plate carries at most ~64 characters, a caption ~120 (§2.1 "status bubbles"; `pm.scene/plate`).
17. **Medium-poly, warm, soft** (§5): the voice matches the diorama, cozy and specific, never corporate ("Your request has been processed") and never edgy.
18. **Variety is real variety**: 3–5 variants per event say different things, not the same line reworded (content README §4 "Usefulness").
19. **Placeholders exactly as declared** (`{name}`, `{chore}`, `{count}`...): a line reads correctly with any value ("{count} items", never "{count} things").

## How a draft is scored

Each rule family is a criterion; the Evaluator returns `{:score :reason}` for each, 0–10, and an overall score (the mean, weighted: C ×2, A ×1.5, B ×1, D ×1). A draft passes at **8.0 or better** with no criterion under 6; anything else goes to the Refiner with the reasons. Specificity is scored on A and B together: a stranger reading the lines should know they belong to Prime Meridian (the twin, the estate, the crew's names, SAMPLE/REAL, closing time), not to a generic cozy game.
