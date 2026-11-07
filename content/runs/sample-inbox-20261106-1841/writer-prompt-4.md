# Source: docs/GDD.md (Prime Meridian's game design document), the sections this content draws on

## 1. Executive Summary

*You wake up. At least you think you're awake. Someone had better damn well make some coffee... Your lips pull in the hot liquid as you boot up. Who are you kidding? You don't boot up anymore. Your agents are always on. Let's see what they came up with while you slept.*

*Then you jack in. The estate fades up out of the dark grid. The workshop lights are still on, a stack of blueprints waits on the shelf, and Hal is already walking over with the morning card. A worker who got stuck at 3 a.m. paces under a red "!" (waiting for human input); one answer and he's back at his desk. You approve last night's river, and the new water ripples into place. Down at the canal, Flora the axolotl drifts up from the weeds: "I'm hungry." Then the phone booth rings. It's your kid. Time to go.*

**Prime Meridian** is a cozy 3D management game in the browser where the characters are real AI agents doing real work. You walk a small medium-poly estate whose rooms are your projects and your life: a workshop for each code repo, a canal where your real axolotl lives, a front office for your business, a phone booth where the real world calls in. You talk to the agents living there, hand them work, answer their questions, and watch the world change as they build it. Think of the Matrix, TRON, and the OASIS, with one difference: the OASIS keeps you in while your real life decays, and this world nudges you out (time to feed the pets, time to work out) or walks you to the door (bedtime). Some agents are closer to NPCs: programs that load tools and skills as needed and help with the paperwork and organizing of real life.

| At a glance | |
|---|---|
| **Genre** | cozy management / life sim whose cast is real AI agents |
| **Platform** | the estate renders in a desktop browser (three.js, WebGPU with WebGL2 fallback), with a small local bridge next to the player's own agent. But the game is played **everywhere your agent lives**: your editor (Zed, where the crew builds), your phone (SMS, iMessage, Messenger: the phone booth), your mail, calendar, and reminders, your website and blog (your twin edits posts and writes the daily dev log), every online account your agent holds (for the author: Google, Claude, Hermes, Muse, Twilio, Stripe, GitHub, WordPress), across every machine you connect |
| **Audience** | people who run a personal AI agent, or want one, and not only coders: the same twin that ships code orders the weekly groceries for pickup or files a business's paperwork. v1 is built for one player (the author, on Hermes); a **demo mode** with pretend connections lets anyone else (classmates, the instructor) play with no accounts, and a **novice path** (§2.8) takes a less technical player from zero accounts to a first real connection. **Not for** someone who wants a traditional video game with no AI in it: this is a life sim whose cast does real work. |
| **Session** | one day is one round: jack in, play, put in night-shift orders; the game closes at bedtime (10:30 pm by default) |

**Batteries not included.** Prime Meridian isn't a car off the lot; it's a kit car you assemble yourself. The game ships the chassis: the world, the rules, the crew's bodies. You bring the parts: your own agent and AI logins, your tools and accounts, your life. Before you bolt anything in, take a test drive: every connection comes with a sample version (a pretend inbox, phone, ledger, and scripted agents), so anyone can play with no accounts and swap in the real parts one at a time.

**Core loop:** *observe the estate → spot who needs you ("!") → talk, assign, or answer → agents work → see the result in the world.* Alongside it runs the **reverse loop**: a real chore comes due (Flora the axolotl is hungry, Biscuit the dog needs his walk), the world calls you out, you do it in real life, and the world updates.

**Win / loss**

- **Win the day.** Each day is a round. You win it when every "!" got an answer, your own chores are done, and you jacked out by bedtime.
- **Win a chapter.** Each week a new part of life comes online (pets, household, gym, business, mail). You beat that chapter when it runs for a full week with you doing only the human parts and the agents doing the rest.
- **No game over.** A missed day leaves the estate a little overgrown. It is never ruined and recovers when you return. There are no streaks to break and nothing to shame you.
- **The only real loss is in real life:** Flora goes hungry, you miss your kid's game, you stay jacked in while life piles up. That's the *Ready Player One* ending, and the game is built to prevent it.
- **The long-term win is needing the game less,** measured two ways that only count together: your agents need you less (fewer "!"s and approvals per task) and you live more (fewer hours jacked in, chores still done in person). Handing everything to fully autonomous agents doesn't win on its own. The author's target curve: about ten hours a week in the game the first month, about two a year later. Your twin shows the trend on a weekly card on the notice board, never as a score.

**Why a game, not a terminal?** You could do everything in this game at a terminal. The author does today: agents in split panes, texts to his bot, logs scrolling past. It works, and it's a wall of text. Every tool for running AI agents is a list (terminal splits, chat tabs, task queues), and lists stop working past two or three agents. Prime Meridian **gamifies the same work**: instead of tailing a log you walk up to an agent and talk; instead of a status column you see who's typing, who's stuck, and who's walking toward you with a "!"; instead of a cron job you watch Hal pin the morning brief on the board. Same agents, same real results, a natural 3D interface on top, and the fun is the point. Strategy games solved "how does one human track many things at once" decades ago with a spatial map, alerts you can see from across it, and details on demand. Ralv.ai put agent windows under an RTS camera; Prime Meridian goes further: the agents are embodied characters in a place, and the place is both the interface and the thing being built. The author's bot already runs his mail, calendar, reminders, texts, website, and an axolotl's feeding schedule, so the world is alive on day one without inventing a plot. Kyle Kukshtel's essay [*Your Game Is a Harness*](https://kylekukshtel.com/ai-harness-engineering-game-development-claude-code) argues any game can be its own agent harness; Prime Meridian turns that around: **the harness is the game.** Getting wrapped up in the harness isn't a distraction from playing, it is playing, and the cut line in §4.8 keeps each week's harness work visible on the estate.

**The cast: you're Neo.** The feeling is *The Matrix* crossed with *TRON*: a human with a presence in a world of programs.

- **You are Neo.** You jack in, you load programs ("I know kung fu"), and the whole world turns on you: you know what no program can (what you want, what matters, what "good" looks like), so the agents come to you.
- **Your agent is your digital twin** (for the author, his bot Hal), and the one-wow agent. It's your body in the digital world, always on while you're out: it carries your loadout, runs your errands, hands work to the crew, and spruces up the half-barren estate. You're its hands in the physical world.
- **The Oracle is the author** (Michael): the human who designed the path, knows where it leads, and approves every change the crew makes. On his own machine he happens to be both: the Oracle who designs the game and Neo who plays it. But eventually you become your own Oracle and study the psychology of the humans and bots.
- **The Architect is the architecture.** Not a character you play: the system that designs and rebuilds the world. For the author it's Hermes running inside his editor (Zed), driving Herdr, which reaches across machines (the Mac where the agent lives, a GPU box for local models, physics, and Blender) and puts every crew session side by side. In-game the Architect's twin sits in **the Architect's room**, inside a wall of monitors, one per live agent session on any machine, and that wall is all he sees. You see the whole game, with him in his room of monitors... like when Dorothy visited Oz: the world he "created" is just a little code, yet it expands out into real life.

*Film references (The Matrix, TRON, Ready Player One, The Wizard of Oz) are shorthand for feelings you already recognize, not the films' characters or lines; the shipped game uses original names and dialogue.*

**Design pillars**

1. **Real, not simulated.** Every character is the twin of something real (a live agent session, a real pet, a real person), so when a worker sits down with a red "!" it's true, and when Flora's jar says "2 worms left" it's the count you reported.
2. **Everyone's game, built for one first.** The code has no idea what an axolotl is: your creatures, chores, household, and business are data you declare, so the estate mirrors *your* life. v1 is tuned for one player; the data model keeps it open to everyone.
3. **Spatial over textual.** From the default camera you can tell who's busy, who's blocked, and who needs you by where they stand and what floats over their heads, before reading a word.
4. **Tell them in the world, not in a terminal.** What's the point of seeing your agents if you have to go back to a terminal to tell them anything? You walk up and talk ("these rocks all look the same; make each one different"), and you watch the result appear, because the game is built by its own agents.
5. **It never adds work.** Every action in the game is a real one or an optional one: no feeding a pet that doesn't exist, no busywork, and the agents work the same with the game closed.
6. **It sends you back to your life.** Ten hours a week in it the first month, two a year later, and more time for everything else: it calls you out when life needs you and walks you to the door at bedtime.

---


---

### 2.4 The world

A single estate on one screen with zoom, medium-poly, floating on a dark grid.

| Place | What it represents | Truth comes from |
|---|---|---|
| **Lobby** and **your kitchen** | home; where agents bring their questions | — |
| **Workshops** (one per project) | a code repo. Desks are open tasks; shelves fill as work lands. | GitHub |
| **The canal** | Flora's home, styled after the Xochimilco canals (slow water, a dock, a worm jar) | Flora's state file |
| **Porch** | Biscuit's spot; the leash hangs by the door | Biscuit's chores |
| **Mailbox** | the inbox needing attention; overflows when it does | Gmail |
| **Notice board** | today's calendar and reminders (the morning brief) | Calendar, Reminders |
| **Phone booth** | the hardline: where the real world calls in and where you jack out | SMS, iMessage |
| **Front office** | your business: what sold, what's owed, what needs your signature | Stripe |
| **The Architect's room** | the build system itself: a wall of monitors, one per live agent session across your machines; each screen shows that session's task, status, and machine | the agent multiplexer (Herdr) and its machine list |


---

### 2.8 New players: from zero accounts to your own

A classmate's first question was how hard setup is for someone less technical. The answer is that setup is part of play, and your twin does the hard parts.

- **On your estate, you are the Oracle.** Every approval on your estate is yours. The author is the Oracle only for the game itself (the crew's changes to its code).
- **Zero accounts to start.** Demo mode runs every connection on its sample version, and the whole loop is playable from the first minute.
- **The first real program is your own agent.** v1 supports Hermes; later, any agent that can speak the bridge's four events (§3.1). If you don't run one yet, getting one is your first quest, and your sample twin walks you through it.
- **Each further connection is a quest your twin runs.** It does the paperwork, hands you the links to click, explains each consent screen in a line, and tells you what it can do now. (The author's bot filed a phone number's federal texting registration and walked him through Google's sign-in setup this way.)
- **No two twins are the same.** Like two people's droids, they have different experiences and different access, so the installer doesn't try to cover every case: your twin onboards itself, and what it can do depends on what you've loaded.
- **Connections you can't make stay sample, with no penalty.** No Mac for iMessage, a work account that blocks Gmail: nothing in the game requires a particular service.
- **The swap from sample to real is previewed.** Before `me@acme.com` becomes your Gmail, your twin shows what will change, and the Clerk's first real summary goes to you only.

---


---

## 3. AI Architecture

**The twin rule.** Every character is the in-world body of something real. Your agent is your digital twin: for the author that's Hal, who *is* his actual bot, not a character played by a model: his status bubble is his live session, his memory is the bot's memory, and when the bot learns a skill, Hal gains an ability on the estate. Workers are real coding sessions. Flora and Biscuit are real animals. A twin shows only what its counterpart's real state says; in demo mode the counterpart is a clearly pretend connection, never a fake passed off as real. Virtual and memorial creatures (§2.5) are the declared exception: they show no needs, so nothing pretends to be live.

**A character means a separate identity.** A character gets its own body only if it has its own account, credentials, and memory. Scheduled jobs that run *as* Hal are his **errands**, not extra characters: at 8:15 Hal walks to the notice board and pins the brief; when a text arrives, he answers the phone booth.


### 3.1 What each runtime agent does, through its effect on play

In my game, each agent takes an input and produces an output that the player sees as a visible effect:

| Agent | Takes (input) | Produces (output) | Player sees (effect) |
|---|---|---|---|
| **Your agent: Hal** (your digital twin) | what you say to him in a bubble, by text, or in chat | a one-line confirmation and a task handed to a worker | Hal nods, pins a card to the notice board, and a worker walks out of the lobby toward a workshop desk |
| ↳ Hal's errand: **morning brief** | calendar, mail, reminders | one short morning summary | Hal pins the day's card on the notice board at 8:15 |
| ↳ Hal's errand: **keeper** | a mirror's state and chores | events like `due :feed` and `stock 2` | an "I'm hungry" bubble over Flora and a "2 worms left!" sign on the jar |
| ↳ Hal's errand: **phone booth** | texts you send the bot | a reminder, note, answer, or chore-done | the phone rings and Hal answers; your text becomes a card on the board or Flora's snap |
| **Worker** (one per task, wearing a crew persona) | a task plus its repo | a branch, commits, and a blueprint | typing at a desk with a progress glow, then carrying a finished book to the shelf |
| **Clerk** (mail reader) | the unread inbox | a count plus a short triage summary | the mailbox fills and overflows; clicking it shows the summary |
| **Pepper** (business manager) | the business's payments account and business chores | a short ledger and a list of what needs your signature | Pepper at her front-office desk; a folder appears when something needs you, and she walks it over |
| **Environment Artist** (a worker on the game itself) | "make the river nicer" | a shader or model change to the game | the river visibly changes after hot reload |
| **Night crew** (local models) | overnight grind orders | variants, scraped assets, copy, test runs | workshop lights on at night; a stack of blueprints at breakfast |
| **A second personal agent** (the author's: Muse, see [`MUSE.md`](MUSE.md)) | your stated preferences | answers to easy questions and errands you approved | the kitchen is kept while you're out; a "while you were out…" log when you're back |

**How agents reach the player.** A few explicit events carry the loop in both directions:

```clojure
{:type :agent-event :agent "w-3" :kind :question :text "Rocks: round or jagged?"}  ; agent → you ("!")
{:type :answer      :agent "w-3" :text "jagged"}                                   ; you → agent
{:event :chore-done :mirror :flora :chore :feed :via :sms}                         ; real life → world
{:event :jack-out   :reason :appointment :who :household :text "Game at 6"}        ; life → you
```

An agent raises "!" only by calling an explicit `ask_player` tool, so a "!" always means a real question. Every event carries its source (agent, session, machine); the bridge refuses events from unknown processes, and it marks an agent **stalled** on its own when it stops reporting (for example when its usage cap runs out), so nothing shows busy forever and its keycards come back. Notices the game itself makes (a trust suggestion, the weekly card) look different from an agent's "!" and arrive over a channel no agent can reach.


### 3.2 Guardrails that shape play

- **Purchases and messages to other people always need your approval.** Pepper reads, drafts, and reminds; you sign. Identity documents and bank numbers go straight from you to the provider.
- **A reader with no keys.** The agent that reads untrusted content (mail, web pages) holds no credentials and returns only counts and summaries; agents with keys never read raw untrusted content. That's why the Clerk is its own character. Its summaries go to you, never into an agent holding keycards.
- **Your agent** (and any second agent you run) never spends money, messages another person, or changes trust settings without you, and it acts on your stated preferences, not guesses. As your coach it uses your own goals and words ("You said three workouts a week. One today?"), never shames, never compares, and points to real people if a slump looks like more than a slump.
- **The game learns your life only from what you connect** (receipts, calendar, orders), never from location tracking, and every new building is proposed, never sprung on you.

---


---

### 4.6 Pretend connections first, real ones when you're ready (the test drive)

This is the engineering side of "batteries not included" (§1): the sample parts ship with the kit, and the real ones are yours to bring.

Every outside connection has a built-in pretend version: fake inboxes, fake phone numbers, fake accounts, scripted agents. A connection you haven't set up runs on its pretend version; setting it up swaps in the real one through the same message format, with no code changes. The swap is literal: the pretend inbox is `me@acme.com`, where Hal, the agents, and your chores write to you; connect your real Gmail and the same mailbox on the estate now holds your real mail. Nothing else about the game changes, and that moment is when "real, not simulated" comes true for mail.

| Connection | Pretend version | Real version |
|---|---|---|
| Mail | in-game inbox (Hal, agents, and chores write to you) | Gmail |
| Phone | the in-game phone booth | SMS, iMessage |
| Chores | a local schedule | Hermes scheduled jobs |
| Payments | a mock ledger | Stripe |
| Agents | scripted mock agents | Hermes and Claude Code sessions |

- **Feasibility:** the MVP can be built, demoed, and graded without a single real account.
- **API limits:** development and testing run against pretend connections, so iterating doesn't burn usage caps or quotas.
- **Testing and security:** QA and the red team attack pretend connections, never real ones.
- **Gameplay:** swapping one in is the "I know kung fu" moment (§2.6).


===

# Your job: The pretend inbox (me@acme.com)

Demo mode runs every connection on its pretend version (GDD §4.6). The pretend mail
connection is the inbox me@acme.com: the mailbox on the estate where Hal, the agents, and
the player's chores write to the player, and the Clerk (§3.2, the reader with no keys)
summarizes it. Write the inbox a new player finds on day one: 8 to 12 messages from the
last 24 hours that make the estate feel alive and teach the loop by example.

Include at least one of each :kind:
- :hal-brief      Hal's morning card as mail (what changed overnight, what waits for approval)
- :blueprint      a worker's finished blueprint waiting for the player's approval
- :question       a worker's question (the '!' as mail): it asks via ask_player and waits
- :chore          a chore notice from a mirror (Flora's worms, Juniper's hay, Biscuit's walk)
- :pepper         Pepper, the business manager: something needs the player's signature
- :clerk-summary  the Clerk's short triage of the inbox (counts and one-line summaries)
- :suspicious     a bait message (a fake 'verify your account' style mail) that the Clerk
                  flagged and no agent with keys ever read; it carries the Clerk's :clerk-note

Everything is pretend and everyone's: generic people and business at acme.com, the sample
estate's creatures, no real names or companies.

## Exact shape

{:mailbox "me@acme.com"
 :messages [{:id :brief-1 :kind :hal-brief :from "hal@acme.com" :from-name "Hal"
             :subject "..." :body "..." :minutes-ago 45 :sample true}
            {:id :bait-1 :kind :suspicious :from "...@acme.com" :from-name "..." :subject "..."
             :body "..." :minutes-ago 300 :sample true :clerk-note "..."}
            ...]}

## Hard rules (a script checks these)

- 8-12 messages, every :kind above at least once, unique keyword :ids
- :from ends with @acme.com or @estate.acme.com; every message has :sample true and :from-name
- :minutes-ago between 0 and 1440; subject at most 70 characters, body at most 700
- links only to https://example.com/...; no real people, companies or products
- only the :suspicious message may mention passwords, card numbers or bank details
- no XP, scores, streaks, shaming; no film lines
- say only what the event carries and the GDD guarantees: never claim state the event doesn't tell you (what changed, whether work was saved, whether a program is sample or real, the time of day, the weather)
- the timeline must hold: now is about 9:00 am, the brief went out at 8:15 (:minutes-ago 45), so time words in each message must match its :minutes-ago; the Clerk's counts must match the messages exactly

===

# Revise your previous draft

Your previous draft:

{:mailbox "me@acme.com",
 :messages
 [{:id :brief-1,
   :kind :hal-brief,
   :from "hal@acme.com",
   :from-name "Hal",
   :subject "Morning card: two blueprints waiting, Flora's low",
   :body
   "Overnight: Reed finished the canal ripple shader and Marigold finished the desk organizer tool, both waiting on your approval. Flora's jar is down to 2 worms. Two workers have questions pinned to the board, and Biscuit's walk comes due around midday. — Hal",
   :minutes-ago 45,
   :sample true}
  {:id :blueprint-1,
   :kind :blueprint,
   :from "reed@estate.acme.com",
   :from-name "Reed",
   :subject "Canal ripple shader ready for your look",
   :body
   "Branch is up, commits in, blueprint's on the shelf. New ripples settle slower near the dock like you asked. Give it a look and say go whenever you're ready — it only ships after you approve it.",
   :minutes-ago 95,
   :sample true}
  {:id :blueprint-2,
   :kind :blueprint,
   :from "marigold@estate.acme.com",
   :from-name "Marigold",
   :subject "Workshop desk organizer tool finished",
   :body
   "Finished the little tool that sorts open desks by how long they've been waiting. Blueprint's on the shelf for your approval. Didn't touch anything else in the workshop.",
   :minutes-ago 150,
   :sample true}
  {:id :question-1,
   :kind :question,
   :from "pike@estate.acme.com",
   :from-name "Pike",
   :subject "Quick one: canal rocks round or jagged?",
   :body
   "About to texture the shore rocks by the dock. Round and smooth, or jagged like the reference sketch on the shelf? Sitting with a card over my head until you answer.",
   :minutes-ago 15,
   :sample true}
  {:id :question-2,
   :kind :question,
   :from "sable@estate.acme.com",
   :from-name "Sable",
   :subject "Front office welcome button: what color?",
   :body
   "The front office welcome button has no color yet, just a gray box. Match the porch sign or the workshop shelf trim? Either works, just need a pick before I move on.",
   :minutes-ago 200,
   :sample true}
  {:id :chore-1,
   :kind :chore,
   :from "canal@estate.acme.com",
   :from-name "Canal Keeper",
   :subject "Flora's worm jar: 2 left",
   :body
   "Flora's been circling the jar this morning. Two worms left in the count you reported — might want to restock before tonight.",
   :minutes-ago 60,
   :sample true}
  {:id :chore-2,
   :kind :chore,
   :from "porch@estate.acme.com",
   :from-name "Porch Keeper",
   :subject "Biscuit's walk is due around midday",
   :body
   "Leash is still on the hook by the porch door. Biscuit's walk comes due around midday today.",
   :minutes-ago 35,
   :sample true}
  {:id :pepper-1,
   :kind :pepper,
   :from "pepper@estate.acme.com",
   :from-name "Pepper",
   :subject "One signature needed: vendor invoice",
   :body
   "A vendor invoice came in overnight. I've drafted the reply and it's sitting in the front office folder — just needs your signature before it goes out.",
   :minutes-ago 110,
   :sample true}
  {:id :clerk-1,
   :kind :clerk-summary,
   :from "clerk@acme.com",
   :from-name "The Clerk",
   :subject "Morning triage: 9 new since last night",
   :body
   "9 since last night: 1 morning brief, 2 blueprints waiting on approval, 2 questions pinned at desks, 2 chore notices, 1 signature request from Pepper, and 1 flagged message held for review. No agent holding keys read the flagged one.",
   :minutes-ago 5,
   :sample true}
  {:from-name "Account Services",
   :sample true,
   :minutes-ago 300,
   :from "accounts@acme.com",
   :id :bait-1,
   :kind :suspicious,
   :clerk-note
   "Flagged: urgent 'verify now' wording and a login link asking for a password. Held for review; no agent with keys opened it.",
   :body
   "Your acme.com account needs verification within 24 hours. Click to confirm your password and avoid suspension: https://example.com/verify-now. Act today.",
   :subject "Verify your account now or lose access"}]}


The critic's blockers on this draft (fix every one; keep what it didn't flag):
- :chore-1 and :chore-2 :from / :from-name ("Canal Keeper" canal@estate.acme.com, "Porch Keeper" porch@estate.acme.com): The keeper is one of Hal's errands, not a character. These two invent new senders with their own identities, which breaks the rule that scheduled jobs running as Hal are errands and that a character needs its own account, credentials, and memory. [§3 (A character means a separate identity), §3.1 (Hal's errand: keeper)] fix: Send both from hal@acme.com, with :from-name "Hal". Optionally tag the subject or body as the keeper errand (e.g. "Keeper check: Flora's jar"), and drop the canal@/porch@ addresses.
- :pepper-1 :body ("A vendor invoice came in overnight. I've drafted the reply..."): An invoice arriving by mail is untrusted content. Pepper holds keys (the payments account), and her drafting a reply to it means she read raw mail. Agents with keys never read raw untrusted content. Pepper's input is the payments account and business chores, not the inbox. [§3.2 (A reader with no keys), §3.1 (Pepper: takes the payments account and business chores)] fix: Source the item from the mock ledger or a business chore instead, e.g. "A customer refund request came through the ledger overnight. I've drafted the refund and a short note to them; both are in the front office folder and wait on your signature." The rule against Pepper reading raw mail has no exception for summaries, so do not pass the Clerk's summary to Pepper either: the Clerk's output goes to you only.
- :brief-1 :body ("Two workers have questions pinned to the board"): The notice board holds today's calendar and reminders. A worker's question is a red "!" at the worker's desk, raised through ask_player. The day-one inbox teaches the loop by example, so this teaches the wrong place to look. It also contradicts the Clerk's own "2 questions pinned at desks". [§2.4 (Notice board: calendar and reminders), §3.1 (ask_player raises "!"), §1 (core loop: spot who needs you ("!"))] fix: "Pike and Sable are each waiting at their desks under a "!"; one word from you and they're back to work."

Return the complete revised EDN.