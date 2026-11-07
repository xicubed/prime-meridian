# Content run: The pretend inbox (me@acme.com)

- request: `content/requests/sample-inbox.edn`
- writer: `claude-sonnet-5`, critic: `claude-opus-5-5`
- GDD sections: §1., §2.4, §2.8, §3., §4.6


## Draft 1

- writer: 4060 tokens out; validator: 1 errors
  - message 3 :question-1: asks for credentials or bank details outside a :suspicious message (§3.2)

## Draft 2

- writer: 1780 tokens out; validator: 1 errors
  - message 3 :question-1: asks for credentials or bank details outside a :suspicious message (§3.2)

## Draft 3

- writer: 1773 tokens out; validator: PASS
- critic: 3 blockers, 8 minor; "Partly: the estate details (blueprint on the shelf, leash on the hook by the porch door, the jar count) place it firmly on the estate, but most lines read as plain status reports, with little of the game's warmth, wryness, or jack-in shorthand, and Hal sounds more like a ticket system than a companion."
  - BLOCKER :chore-1 and :chore-2 :from / :from-name ("Canal Keeper" canal@estate.acme.com, "Porch Keeper" porch@estate.acme.com): The keeper is one of Hal's errands, not a character. These two invent new senders with their own identities, which breaks the rule that scheduled jobs running as Hal are errands and that a character needs its own account, credentials, and memory. [§3 (A character means a separate identity), §3.1 (Hal's errand: keeper)]
  - BLOCKER :pepper-1 :body ("A vendor invoice came in overnight. I've drafted the reply..."): An invoice arriving by mail is untrusted content. Pepper holds keys (the payments account), and her drafting a reply to it means she read raw mail. Agents with keys never read raw untrusted content. Pepper's input is the payments account and business chores, not the inbox. [§3.2 (A reader with no keys), §3.1 (Pepper: takes the payments account and business chores)]
  - BLOCKER :brief-1 :body ("Two workers have questions pinned to the board"): The notice board holds today's calendar and reminders. A worker's question is a red "!" at the worker's desk, raised through ask_player. The day-one inbox teaches the loop by example, so this teaches the wrong place to look. It also contradicts the Clerk's own "2 questions pinned at desks". [§2.4 (Notice board: calendar and reminders), §3.1 (ask_player raises "!"), §1 (core loop: spot who needs you ("!"))]
  - minor :question-1 :body ("Sitting with a card over my head until you answer."): What floats over a waiting worker is the red "!", not a card. Cards belong to Hal's notice board. [§1, §3.1]
  - minor :blueprint-1 :body ("like you asked"), :chore-1 :body ("in the count you reported"): This is a new player's day one in demo mode: they asked for nothing and reported no count. Within the sample this is not a deception, but it reads oddly to someone who just arrived and blurs the pretend/real line the twin rule cares about. [§3 (twin rule), §2.8 (zero accounts to start), §4.6]
  - minor :chore-1 :body ("Flora's been circling the jar this morning."): The keeper takes only the mirror's state and chores and emits events such as due :feed and stock 2. Behavior the state doesn't report is invented, even in a sample. [§3 (a twin shows only what its counterpart's real state says), §3.1 (keeper)]
  - minor :brief-1 (whole card): The brief is meant to cover what waits for approval, but it omits Pepper's signature request. It also never anchors itself as the 8:15 errand. The voice is a flat list with no companion warmth. [§3.1 (morning brief, 8:15), §1 (Hal is your twin, your body in the digital world)]
  - minor :clerk-1 :body: The ask was counts and one-line summaries, but this is counts only, so it adds little over the inbox list itself. [§3.1 (Clerk: a count plus a short triage summary)]
  - minor :from addresses (hal@acme.com, clerk@acme.com vs reed@/pepper@estate.acme.com): The split between two domains for the estate's own cast is arbitrary, and it muddies the spoofing lesson of the bait from accounts@acme.com. [§4.6]
  - minor :question-2 :subject/:body ("Front office welcome button"): The front office is the business (what sold, what's owed, what needs your signature). A UI welcome button there is an odd fit, and the question is generic. [§2.4]
  - minor subjects: "Workshop desk organizer tool finished", "One signature needed: vendor invoice": These read like generic ticket titles rather than voices on the estate. [§1 (design pillars, tone)]

## Draft 4

- writer: 2529 tokens out; validator: PASS
- critic: 3 blockers, 8 minor; "Mostly: Hal's brief, Reed's approval line and the Clerk's flagged bait carry the warm, real-not-simulated loop well, but a few lines invent a live pet's behavior or mislabel the '!', several bodies read as generic workplace mail, and there is no Matrix/TRON flavor anywhere."
  - BLOCKER :chore-1 :body "Flora's been circling the jar this morning.": The keeper reads only a mirror's state and chores and emits events like due :feed and stock 2. It cannot observe a live animal's behavior. Inventing what Flora did breaks the twin rule: a twin shows only what its counterpart's real state says. [§3, §3.1]
  - BLOCKER :blueprint-1 (Reed's canal ripple shader), :blueprint-2 (Marigold's desk organizer): Both blueprints change the game's own code: a shader, and a game tool. On a new player's estate the author is the Oracle for the game itself. The player approves work on their own estate, where each workshop is one of their repos. Asking a new player to approve game-code changes teaches the wrong approval line. It also leaves no example of a workshop doing the player's own project work. [§2.8, §2.4, §3.1]
  - BLOCKER :brief-1 (:minutes-ago 45) vs :question-1 (:minutes-ago 15): The brief says Pike is waiting under a '!', but Pike's question arrives 30 minutes after the brief. The inbox's own timeline contradicts itself. [§3.1]
  - minor :question-1 :body "Sitting with a card over my head until you answer.": Cards are what Hal pins on the notice board. What floats over a waiting worker is the red '!'. This line teaches the wrong visual cue. [§1, §3.1]
  - minor :brief-1: The brief is meant to cover what waits for approval, but it leaves out Pepper's refund and note. The 8:15 errand is also not anchored: :minutes-ago is relative, so the brief can appear at any clock time. [§3.1]
  - minor :clerk-1 :body: The Clerk gives category counts only. The request asked for counts plus one-line summaries. [§3.1, §3.2]
  - minor :chore-1, :chore-2 :body: Each body repeats the subject's 'Keeper check:' prefix. Neither says how to close the loop in real life, which the reverse loop is supposed to teach. [§1 (reverse loop), §3.1 (phone booth)]
  - minor :blueprint-1 :body "like you asked": On day one a new player hasn't asked for anything. The line implies a history they don't have. [§2.8]
  - minor :question-2 :body; :pepper-1 :body: These read as generic office mail: 'Either works, just need a pick before I move on.' and 'A customer refund request came through the ledger overnight.' The front-office 'welcome button' also doesn't match what the front office is (business: sales, money owed, signatures). [§1 (pillars), §2.4]
  - minor :from addresses: Senders mix hal@acme.com and clerk@acme.com with reed@, pepper@ and others at estate.acme.com. There's no visible rule for which agents get which domain. [§4.6]
  - minor whole inbox (voice): There is no Matrix/TRON shorthand anywhere, e.g. jacking in, programs, the grid. [§1 (cast, film-reference note)]

## Draft 5

- writer: 4033 tokens out; validator: PASS
- critic: 1 blockers, 7 minor; "Mostly: the brief's "one word from you and they're back to work," the leash on the hook, "text the booth when she's fed," and Pepper drafting while you sign all feel like the estate, but several lines read as generic work mail, there's no Matrix/TRON shorthand, and two lines credit a brand-new player with things they never did."
  - BLOCKER :chore-1 :body "the count you reported"; :blueprint-1 :body "like you asked": This is a day-one demo inbox. The new player has reported no worm count and asked for no checkout change. These lines present pretend sample state as the player's own history, which is the fake-passed-off-as-real the twin rule forbids. In demo mode the counterpart must be clearly pretend. [§3 (twin rule), §1 pillar 1, §2.8, §4.6]
  - minor :question-1 :body "Sitting with a card over my head": A blocked worker stands under a red "!", not a card. Cards are what Hal pins on the notice board, so this muddles the one signal the player most needs to read. [§1 (executive summary, core loop), §3.1]
  - minor :question-1 and :question-2: Both questions are the same shape: an A-or-B look pick about the estate itself. Pike's question is close to the GDD's own rocks example. Neither shows a worker asking about a real task in a project workshop. Changes to the game's own code are also the author's to approve, not the player's, so the questions shouldn't be about the game's code. [§2.4 (workshops are repos), §2.8 (the author is Oracle only for the game itself)]
  - minor :bait-1 :from "accounts@acme.com": The bait comes from the same domain as hal@acme.com and clerk@acme.com, and the :clerk-note never names the sender as a red flag. That teaches the player that a trusted-looking acme.com sender can be bait, without teaching them how to spot it. [§3.2 (reader with no keys), §3.1 (game notices use a channel no agent can reach)]
  - minor :clerk-1 :body: The ask was counts plus one-line summaries. The triage gives counts only. "2 questions pinned at desks" is also off, since questions aren't pinned; Hal pins cards to the board. [§3.1 (Clerk: a count plus a short triage summary), §3.2]
  - minor :brief-1 :body: The brief is meant to cover what waits for approval, but it leaves out Pepper's refund folder, which is the one item touching money and another person. [§3.1 (morning brief), §3.2]
  - minor :blueprint-1 "Give it a look and say go whenever you're ready"; :pepper-1 "A customer refund request came through the ledger overnight."; :chore-2 "Keeper check: Leash is still on the hook": Generic office-mail voice with no wry estate or jack-in flavor. In :chore-2, "Keeper check:" repeats the subject, and "Leash" is capitalized mid-sentence. [§1 (the cast, film references as shorthand), §2.4]
  - minor :from addresses: Hal and the Clerk use @acme.com, while workers and Pepper use @estate.acme.com, with no reason given. It reads as inconsistent. [§4.6]

**Not installed**: still failing after 5 drafts.