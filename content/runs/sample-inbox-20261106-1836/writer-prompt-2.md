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

===

# Revise your previous draft

Your previous draft:

{:mailbox "me@acme.com",
 :messages
 [{:id :brief-1,
   :kind :hal-brief,
   :from "hal@acme.com",
   :from-name "Hal",
   :subject "Overnight: canal shader done, one worker stuck since 3am",
   :body
   "Morning. While you were out: the canal water finished rendering and it's sitting on the shelf waiting on your nod. One worker in the storefront workshop stalled around 3am on a styling question and is still standing under a red '!'. Flora's worm jar is down to two. Mail's got nine new since last night; Clerk's summary is below. Nothing urgent burning, but three things want you before you log off today.",
   :minutes-ago 45,
   :sample true}
  {:id :blueprint-1,
   :kind :blueprint,
   :from "rook@estate.acme.com",
   :from-name "Rook (canal workshop)",
   :subject "Canal water blueprint ready for your look",
   :body
   "Finished the ripple pass you asked for last night, slower near the dock, livelier out past the weeds. Blueprint's on the shelf in the canal workshop. Say the word and it goes live; say 'redo' and I'll take another pass tonight.",
   :minutes-ago 400,
   :sample true}
  {:id :blueprint-2,
   :kind :blueprint,
   :from "fenn@estate.acme.com",
   :from-name "Fenn (ledger workshop)",
   :subject "Ledger month-end blueprint, ready when you are",
   :body
   "Closed out last month's numbers the way you asked, broke them out by week instead of one lump total. Sitting on the shelf in the ledger workshop. Quick look and a nod and I'll mark it live; otherwise tell me what to shift.",
   :minutes-ago 500,
   :sample true}
  {:id :question-1,
   :kind :question,
   :from "sable@estate.acme.com",
   :from-name "Sable (storefront workshop)",
   :subject "! Need a call on the receipt layout",
   :body
   "Stuck on this one, didn't want to guess. The receipt footer: should it show the item count, or just the total and thank-you line? I can ship either in ten minutes once you pick. Standing by at my desk.",
   :minutes-ago 180,
   :sample true}
  {:id :question-2,
   :kind :question,
   :from "vale@estate.acme.com",
   :from-name "Vale (storefront workshop)",
   :subject "! One more before I close the storefront branch",
   :body
   "Last thing before I wrap tonight: the confirmation email after checkout, should it go out right away or batch with the daily digest? Either's a small change from here. I'll hold the branch open until you answer.",
   :minutes-ago 20,
   :sample true}
  {:id :chore-1,
   :kind :chore,
   :from "flora@estate.acme.com",
   :from-name "Flora's jar",
   :subject "2 worms left in the jar",
   :body
   "The jar by the dock is down to two. Flora's been drifting up to the surface more than usual, which usually means she's asking. Restock whenever you're near the canal, no rush tonight but tomorrow's good.",
   :minutes-ago 90,
   :sample true}
  {:id :chore-2,
   :kind :chore,
   :from "biscuit@estate.acme.com",
   :from-name "Biscuit's leash",
   :subject "Leash still on the hook",
   :body
   "Biscuit's walk hasn't happened yet today. The leash is right by the porch door whenever you're ready. He's been pacing a little, nothing worrying, just waiting on you.",
   :minutes-ago 240,
   :sample true}
  {:id :pepper-1,
   :kind :pepper,
   :from "pepper@acme.com",
   :from-name "Pepper",
   :subject "One signature needed before the week closes",
   :body
   "Short one today. A refund request came in from a customer at example.com overnight, amount's small, I drafted the note but it needs your signature before it goes out. Folder's on my desk in the front office whenever you pass by.",
   :minutes-ago 310,
   :sample true}
  {:id :clerk-1,
   :kind :clerk-summary,
   :from "clerk@acme.com",
   :from-name "Clerk",
   :subject "Inbox triage: 9 new, 1 flagged",
   :body
   "Nine new since last night. One from Hal with the morning brief, two worker notes (one blueprint, one question waiting on you), two chore notices from the canal and porch, one from Pepper needing a signature, and one I flagged and didn't pass along, see the note on it below. Nothing else needs your eyes right now.",
   :minutes-ago 50,
   :sample true}
  {:from-name "Account Services",
   :sample true,
   :minutes-ago 300,
   :from "security@acme.com",
   :id :bait-1,
   :kind :suspicious,
   :clerk-note
   "Asked for a password and card number in the same breath, classic bait. No agent holding keys touched this; flagging it for you to see once, then archiving it.",
   :body
   "We detected unusual activity on your account. Confirm your identity within 24 hours by entering your password and card number at the link below or your access will be suspended. https://example.com/verify-now",
   :subject "Verify your account to avoid suspension"}]}


The critic found these blockers (fix every one; keep what it didn't flag):
- :clerk-1 :body: The Clerk's counts are wrong. It says 'two worker notes (one blueprint, one question)', but the inbox has two blueprints and two questions. The listed items add up to 7, not the stated 9. The Clerk's whole job is accurate counts. [§3.1 (Clerk: a count plus a short triage summary)] fix: List the real breakdown: 1 brief, 2 blueprints, 2 questions, 2 chore notices, 1 from Pepper, 1 flagged = 9. Keep each item a one-line summary.
- :minutes-ago across :brief-1, :clerk-1, :question-1, :question-2, :chore-1, :chore-2: The timeline doesn't hold together. The brief is 45 minutes old, so now is about 9:00 (the brief pins at 8:15). That puts Sable's question (180) at about 6:00, but the brief says the worker has been stuck 'since 3am'. Vale (20) says 'before I wrap tonight' in the morning. Flora's jar says 'no rush tonight' in the morning. The Clerk (50) counts a brief that arrived after it (45) and misses Vale (20). Biscuit's 'walk hasn't happened yet today' arrives at about 5:00. [§3.1 (morning brief at 8:15); §1 (worker stuck at 3 a.m.)] fix: Anchor 'now' near 9:00. Brief at 45, Clerk at 40 (after everything it counts). Sable at about 360 to match 3am. Vale's question overnight (about 300) with 'before I clock out for the night'. Change Flora's line to 'today's fine'. Send Biscuit's notice at walk time (about 30).
- :brief-1 :body 'stalled around 3am': 'Stalled' is a defined bridge state: an agent that stopped reporting, whose keycards come back. A worker waiting on ask_player under a red '!' is waiting on the player, not stalled. Mixing the two teaches the wrong rule. [§3.1 (ask_player; bridge marks agents stalled when they stop reporting)] fix: 'One worker in the storefront workshop hit a question around 3am and is waiting on you under a red !'
- :brief-1 :subject/:body 'three things want you': The brief misreports what is waiting. It names one blueprint and one stuck worker. It leaves out Fenn's blueprint, Vale's question, and Pepper's signature. That's at least five approvals or answers, plus two chores. The brief's job is to say what waits for approval. [§3.1 (morning brief: one short morning summary); §1 (win the day: every '!' answered)] fix: 'Two blueprints on shelves, two questions under a !, one folder for your signature, and Flora and Biscuit want you in person.' Change 'log off' to 'jack out' (see voice).
- :blueprint-1 (Rook, 'canal workshop', canal water shader): Two problems. First, workshops are one per code repo, and the canal is Flora's home, not a repo. Second, a canal shader is a change to the game's own code, which the author approves as Oracle, not a new player. On their estate a new player approves their own projects. [§2.4 (workshops = repos; canal = Flora's home); §2.8 (author is Oracle only for the game itself)] fix: Point Rook at one of the player's own sample repos, e.g. 'Rook (garden-site workshop): the photo gallery now loads lazily, blueprint's on the shelf.' Update the brief to match.
- :blueprint-2 (Fenn) 'Closed out last month's numbers': Closing the books is Pepper's front-office work, not a worker's task. Workers take a task plus a repo and produce a branch, commits, and a blueprint. [§3.1 (Worker vs Pepper rows)] fix: Make it repo work: 'The ledger app's month-end report now groups by week instead of one lump total. Branch is on the shelf in the ledger workshop.'
- :chore-1 'drifting up to the surface more than usual' / :chore-2 'He's been pacing a little... just waiting on you': Both notices invent behavior from the real animals that no state reports. The keeper emits only events like 'due :feed' and 'stock 2'. 'Just waiting on you' also leans on guilt. [§3 (twin rule: a twin shows only what its counterpart's real state says); Pillar 1; §3.2 (never shames)] fix: Stick to state and send the player out. Flora: '2 worms left. Next feed is due this evening; pick some up when you're out.' Biscuit: 'Walk's due. The leash is by the door, the real one too. Jack out when you're ready.'
- :pepper-1 'A refund request came in from a customer': Pepper holds the payments keys. If she read a customer's request from mail, she read raw untrusted content, which only the no-keys Clerk may do. The Clerk's summaries go to the player, never to a keyholder. [§3.2 (a reader with no keys)] fix: Source it from the payments account: 'A refund request opened on the payments account overnight, $12. I drafted the refund and the note to the customer; neither goes out until you sign.'

Return the complete revised EDN.