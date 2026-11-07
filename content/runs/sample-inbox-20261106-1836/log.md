# Content run: The pretend inbox (me@acme.com)

- request: `content/requests/sample-inbox.edn`
- writer: `claude-sonnet-5`, critic: `claude-opus-5-5`
- GDD sections: §1., §2.4, §2.8, §3., §4.6


## Draft 1

- writer: 5913 tokens out; validator: PASS
- critic: 8 blockers, 4 minor; "Partly: the estate places (shelf, porch leash, worm jar, front-office folder) and the '!' framing feel like Prime Meridian, but the voice is mostly a polite office status report with none of the jack-in/jack-out shorthand, and several facts break the GDD."
  - BLOCKER :clerk-1 :body: The Clerk's counts are wrong. It says 'two worker notes (one blueprint, one question)', but the inbox has two blueprints and two questions. The listed items add up to 7, not the stated 9. The Clerk's whole job is accurate counts. [§3.1 (Clerk: a count plus a short triage summary)]
  - BLOCKER :minutes-ago across :brief-1, :clerk-1, :question-1, :question-2, :chore-1, :chore-2: The timeline doesn't hold together. The brief is 45 minutes old, so now is about 9:00 (the brief pins at 8:15). That puts Sable's question (180) at about 6:00, but the brief says the worker has been stuck 'since 3am'. Vale (20) says 'before I wrap tonight' in the morning. Flora's jar says 'no rush tonight' in the morning. The Clerk (50) counts a brief that arrived after it (45) and misses Vale (20). Biscuit's 'walk hasn't happened yet today' arrives at about 5:00. [§3.1 (morning brief at 8:15); §1 (worker stuck at 3 a.m.)]
  - BLOCKER :brief-1 :body 'stalled around 3am': 'Stalled' is a defined bridge state: an agent that stopped reporting, whose keycards come back. A worker waiting on ask_player under a red '!' is waiting on the player, not stalled. Mixing the two teaches the wrong rule. [§3.1 (ask_player; bridge marks agents stalled when they stop reporting)]
  - BLOCKER :brief-1 :subject/:body 'three things want you': The brief misreports what is waiting. It names one blueprint and one stuck worker. It leaves out Fenn's blueprint, Vale's question, and Pepper's signature. That's at least five approvals or answers, plus two chores. The brief's job is to say what waits for approval. [§3.1 (morning brief: one short morning summary); §1 (win the day: every '!' answered)]
  - BLOCKER :blueprint-1 (Rook, 'canal workshop', canal water shader): Two problems. First, workshops are one per code repo, and the canal is Flora's home, not a repo. Second, a canal shader is a change to the game's own code, which the author approves as Oracle, not a new player. On their estate a new player approves their own projects. [§2.4 (workshops = repos; canal = Flora's home); §2.8 (author is Oracle only for the game itself)]
  - BLOCKER :blueprint-2 (Fenn) 'Closed out last month's numbers': Closing the books is Pepper's front-office work, not a worker's task. Workers take a task plus a repo and produce a branch, commits, and a blueprint. [§3.1 (Worker vs Pepper rows)]
  - BLOCKER :chore-1 'drifting up to the surface more than usual' / :chore-2 'He's been pacing a little... just waiting on you': Both notices invent behavior from the real animals that no state reports. The keeper emits only events like 'due :feed' and 'stock 2'. 'Just waiting on you' also leans on guilt. [§3 (twin rule: a twin shows only what its counterpart's real state says); Pillar 1; §3.2 (never shames)]
  - BLOCKER :pepper-1 'A refund request came in from a customer': Pepper holds the payments keys. If she read a customer's request from mail, she read raw untrusted content, which only the no-keys Clerk may do. The Clerk's summaries go to the player, never to a keyholder. [§3.2 (a reader with no keys)]
  - minor :bait-1 :from 'security@acme.com' and :clerk-note: The bait comes from the estate's own domain, which looks the same as Hal's and Pepper's real addresses. The Clerk's note also misses a chance to teach the rule. 'Then archiving it' implies a mailbox action from a reader meant only to return counts and summaries. [§3.2 (reader with no keys; identity documents and bank numbers go straight to the provider)]
  - minor :question-1 and :question-2: Both questions come from the storefront workshop, and both are near-identical A-or-B picks with the same 'either's a small change' line. Both blueprints also end on 'nod and it goes live'. There's little variety for teaching the loop. [§2.4; §1 core loop]
  - minor voice: :brief-1 'Nothing urgent burning... before you log off today', 'the canal water finished rendering'; :pepper-1 'Short one today'; :blueprint-2 'Quick look and a nod': These are generic office-mail phrasings with no warmth or wry Matrix/TRON shorthand. 'Log off' and 'rendering' aren't the game's words: players jack out, and the change is a shader. [§1 (cast: you're Neo; jack in/out)]
  - minor :pepper-1 'Folder's on my desk... whenever you pass by'; :question-1 'Standing by at my desk': These lines don't match the in-world effects. Pepper walks the folder over. A worker with a '!' paces and walks toward you. [§3.1 (Pepper row); §1 (worker paces under a red '!')]

## Draft 2

- writer: 9900 tokens out; validator: 1 errors
  - message 1 :blueprint-1 :body: shaming (§3.2: never shames): "Finished the lazy-loading pass on the photo gallery you asked for: images now load as you scroll instead of all at once. Blueprint's on the shelf in the garden-site workshop. Say the word and it goes live; say 'redo' and I'll take another pass tonight."

## Draft 3

- writer: 1945 tokens out; validator: 1 errors
  - message 1 :blueprint-1 :body: shaming (§3.2: never shames): "Finished the lazy-loading pass on the photo gallery: images now load as you scroll instead of all at once. Blueprint's on the shelf in the garden-site workshop. Give it a nod and it goes live; point me somewhere else and I'll take another pass tonight."

**Not installed**: still failing after 3 drafts.