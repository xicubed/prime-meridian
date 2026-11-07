# Content run: The pretend inbox (me@acme.com)

- request: `content/requests/sample-inbox.edn`
- writer: `claude-sonnet-5`, critic: `claude-opus-5-5`
- GDD sections: §1., §2.4, §2.8, §3., §4.6


## Draft 1

- writer: 9478 tokens out; validator: 1 errors
  - message 8 :clerk-1: asks for credentials or bank details outside a :suspicious message (§3.2)

## Draft 2

- writer: 1685 tokens out; validator: PASS
- critic: 1 blockers, 7 minor; "Partly: the rules are right (Hal's errands state only due and stock, workers wait on ask_player, Pepper reads the payments account, the Clerk holds the bait back from keyed agents), but most lines read like a generic project-management inbox with none of the estate's warmth, wryness, or jack-in shorthand."
  - BLOCKER :brief-1 :subject and :body: Hal's card misstates what is in the inbox. It says 'a blueprint ready', but :blueprint-1 (95 min) and :blueprint-2 (130 min) both arrived before the brief (45 min). It says 'a question waiting', but there are two. It reports Pepper's invoice 10 minutes before :pepper-1 (35 min) exists. A twin shows only what its counterpart's real state says, and the first card a new player reads should not miscount the estate. [§3 (twin rule), §1 pillar 1, §3.1 (morning brief: calendar, mail, reminders → one short summary)]
  - minor :brief-1 :body: The ask was for 'what changed overnight, what waits for approval'. The card is a flat to-do list with nothing overnight in it: no night-crew lights, no stack of blueprints at breakfast, no pointer to today's calendar on the notice board. It reads like a butler's checklist, not a companion's morning card. [§1 (opening: night shift, stack of blueprints), §2.4 (notice board holds calendar and reminders), §3.1 (night crew)]
  - minor :blueprint-1 :body vs :question-2 :body: Finch says the finished garden-club homepage already includes 'an event list'. Briar is 'building the events list now' and asks how it should behave. The same repo contradicts itself, which muddles the example of the loop. [§2.4 (desks are open tasks; shelves fill as work lands)]
  - minor :clerk-1 :body: The Clerk gives category counts only. The brief asked for counts plus one-line summaries, so the triage has nothing a player could act on from the mailbox click. [§3.1 (Clerk: a count plus a short triage summary), §3.2]
  - minor :question-1 :subject, :blueprint-2 :body, :pepper-1 :body: Generic lines: 'Quick question: recipe app units, metric or imperial?', 'ready whenever you want to approve it', 'The rest of the ledger is caught up.' These could come from any ticket tracker. The four workers also sound alike, so the crew personas add no variety. [§1 (cast and feeling: programs in a world, the human they turn to)]
  - minor :blueprint-1, :blueprint-2, :question-1, :question-2 :from: Workers send from worker-*@acme.com, while Hal, Pepper, and the Clerk send from @estate.acme.com. Estate characters using the same domain as the outside world (billing@acme.com) blurs what the Clerk is supposed to distinguish. [§3.2 (reader with no keys; untrusted content is separate), §4.6]
  - minor :chore-2 and the inbox as a whole: Nothing in the inbox sends the player back to real life. Biscuit's walk is the natural place for the reverse loop, but it is just a status line. [§1 (reverse loop; pillar 6)]
  - minor :pepper-1 :body: 'Ready for your signature' leaves unclear whether this is a bill to pay (a purchase) or an invoice to send (a message to another person). Both need approval, but the player should know which they are signing. [§3.2 (purchases and messages to other people need your approval; Pepper drafts, you sign)]

## Draft 3

- writer: 4792 tokens out; validator: PASS
- critic: PASS, 7 minor; "Partly: the estate is there (shelves, desks, the worm jar, the front office, the reader with no keys) and the facts and guardrails hold, but most lines read like a plain work inbox, with none of the warm, wry Matrix/TRON-tinted voice and nothing that walks a first-day player back out to life."
  - minor :clerk-1 :subject and :body: The count can be read two ways. The subject 'Inbox triage: 9 messages, 1 held back' can mean 9 including the held one or 9 plus 1. The body lists 8 and then 'One more'. A count is the Clerk's whole output, so it must be unambiguous. The body is also counts by category, not the one-line summaries the brief asked for. [§3.1 Clerk (a count plus a short triage summary), §3.2 reader with no keys]
  - minor :blueprint-1 vs :question-2: The two garden-club items overlap. Finch's finished homepage already includes 'header, event list, and a contact section'. Briar is 'Building the events list now' and waiting 'before I lock in the layout'. That reads as two workers on one task, and Finch's layout as both finished and not finished. [§3.1 Worker (one per task), §2.4 Workshops (desks are open tasks)]
  - minor :brief-1 :body: The brief reads as a list of counts, and it drops two things the GDD gives the morning brief. It has no calendar or reminder line, though the notice board holds the calendar and reminders. It has no first-day welcome and no nudge back to real life. 'Two blueprints landed on the shelves overnight' also sits oddly with timestamps that put them at roughly 6:50 and 7:40 if the brief is the 8:15 errand. [§2.4 Notice board, §3.1 morning brief (calendar, mail, reminders), §1 pillar 6]
  - minor :question-1, :question-2, :pepper-1, :chore-2 (voice): Several lines are generic office mail: 'Quick question: recipe app units, metric or imperial?', 'Building the events list now.', 'The rest of the ledger is caught up.', 'Biscuit's chore list shows his walk is due.' The game is about embodiment, but nothing places the sender on the estate or ties the mail to the red '!'. Each line could come from any project tool. [§1 Design pillars 3-4 (spatial over textual, tell them in the world), §3.1 ask_player]
  - minor :pepper-1: Paying a hosting invoice is an outgoing bill. That fits the payments account and the front office awkwardly, since the front office covers what sold and what's owed. It also ties Pepper's business to a garden-club repo that a worker owns. It is still correctly an approval: Pepper prepares, you sign. [§2.4 Front office (Stripe), §3.2 Pepper reads, drafts, and reminds; you sign]
  - minor :chore-1, :chore-2 (demo framing): On day one in demo mode, Flora and Biscuit are sample mirrors. Only the :sample flag says so. A new player with no axolotl could read 'Biscuit: walk due' as a real request. [§3 twin rule (in demo mode the counterpart is a clearly pretend connection), §2.8 Zero accounts to start]
  - minor :from addresses: Hal, Pepper and the Clerk send from estate.acme.com, while workers send from acme.com, the same domain as the bait's billing@acme.com. The split looks accidental, and it blurs the one place where a sender's domain could teach something. [§4.6 pretend inbox me@acme.com]

**Installed** `resources/content/sample-inbox.edn` (draft 3).