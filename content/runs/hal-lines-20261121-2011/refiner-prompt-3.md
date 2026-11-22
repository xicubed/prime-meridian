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
- **Les jumeaux.** You and your twin are *les jumeaux*, and your twin is *ton jumeau* or *ta jumelle*, matching your sex (it's your twin), which onboarding asks once along with the twin's name. Both live in data (`:jumeaux` in `estate.edn`): the author's twin is Hal, and nothing in the code knows that name.
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

### 2.1 What the player does

| Action | What you do and see |
|---|---|
| **Walk** | Your avatar (Neo) has a body on the estate. Click to walk; the camera follows loosely and zooms out to see everything. Home is your kitchen. |
| **Observe** | Agents move between desks (working), sit (idle), pace with "!" (has a question), and walk toward you (urgent). Mirrors show bubbles and signs ("I'm hungry", "2 worms left!"). |
| **Talk** | Click an agent and a speech bubble opens. Ask what it's doing, give it a task, answer its question. Replies stream in as the agent writes them. *Built (Nov 20): your twin's answers come back as a speech plate over his walker, his caption and the 💭 history (`bb hal-say`); with `--at` he walks to the place first and says it there (an errand). Without a bridge he says so himself.* |
| **Assign** | Say it in conversation ("make all the rocks procedural"). The agent confirms in one line, takes a desk, and gets to work. |
| **Answer** | An agent that needs a decision stops, raises "!", and walks to you wherever you are. You answer in-world; it resumes. |
| **Approve** | Agent-made changes first appear as a **blueprint**, a ghosted version of the change. Approve it and it becomes real; reject it and it vanishes. A bad change can never take down the running game: it reverts to the last working build. Approvals come **grouped**, not one at a time: the morning card holds last night's blueprints as one tray, each with a one-line summary and a before/after you can see, and reads never ask. |
| **Hand out a keycard** | When an agent needs a permission for a task ("write access to the river shader"), it asks you for a keycard. Every key that's out is visible, and it comes back when the task ends. |
| **Possess** | Toggle direct control of one agent: the camera follows it, your messages go straight to it, and you see what it sees on a desk screen, not a pop-up. |
| **Set autonomy** | A dial per agent: *ask before anything* · *ask before approving* · *fully autonomous*. The dial covers reads and reversible writes; money, deletion, security changes, and messages to other people need you at every setting. You set trust: no agent can start a trust change, so no "!" can ever ask for more autonomy. The notice board shows each agent's track record (tasks done, approvals, reverts); if game code thinks a dial could go up, it says so on the board in the board's own style, never as a "!". |
| **Check the juice** | Every AI login has a **juice meter** on the HUD and over its frontier desks: how much of the plan's usage window is left and when it refills. Your twin warns you before a cap ("Claude's at 80%, refills at 2"), so you never leave the game to check usage in a browser. |
| **Mark a chore done** | Reply "fed" to the reminder text, click the canal, tap an NFC sticker on the tank, or tell Hal. Every channel sends the same `chore-done` event. Only one counts per due window (the chore's `:every` defines it), so a text and a click don't drop two worms; texts count only from known senders; restocking is a manual event in v1. |
| **Do it yourself** | You can always do the work directly (edit a rock in Blender and drop it in; the world hot-reloads it). It's allowed, just not the main way to play. |

**Talk and permission prompts.** Your twin (Hal, for the author) fields the crew's permission prompts by default. Its automatic half, the fielder (`bb crew-fielder`, a crew-up service), answers routine ones after a short wait (5 s, `:answer-after-s` in `:crew-talk`): a plain shell command that the game's one risk classifier calls safe, and that is still on screen when the fielder looks again. 💭 shows these as quiet log lines under your twin's name. You see prompts that are risky, unreadable, or overdue (unanswered after 60 s), flagged with a red badge; the fielder never answers those, and never declines anything. Flipping the "I'll answer" switch in 💭 hands every prompt to you, and your twin holds off. The answer log records roles, not names (`:by :twin`, `:by :player`), and the names come from `:jumeaux`, so the same game works for any player and any twin.

**The game never adds work.** Every action above is either the real action (answering a real question, marking a real chore, approving a real change) or optional. Nothing exists only to keep you busy: no pet that needs feeding but isn't real, no login rewards, no streaks. The agents work the same with the game closed, and anything the game asks you can also be answered where you already are (a text, your editor). A developer who never opens the estate loses only the view.

**Reading the estate (status vocabulary)**

| Status | What the agent does |
|---|---|
| Working | at a desk, typing, with a progress glow; its bubble ticks *reading → writing → tests passing* |
| Idle | sitting, an occasional stretch; idle agents tend the estate (plant trees, lay paths, tidy the bank) using simple rules, not model calls |
| Needs you | "!" marker, paces, walks toward you |
| Blocked / error | red "!" and sits down |
| Awaiting approval | its blueprint waits on the shelf for you |
| Unknown | greyed out: the bridge can't tell, so it doesn't guess (a wrong "!" teaches players to ignore every "!") |


---

### 2.3 A day (one round)

1. **Jack in.** The screen dissolves into the estate and Hal says "Welcome back." You see what changed overnight and what's waiting for your approval.
2. **Play.** Answer the "!"s, approve blueprints, assign new work, tend your mirrors.
3. **Get jacked out when life needs you.** The phone booth (the hardline) rings and the world freezes gently. Who can pull you out, in priority order:

| Priority | Who | Example |
|---|---|---|
| 1 | **People in your household** | "Dinner!", "Dad, ride?" |
| 2 | **Creatures** | Biscuit at the door with his leash; Flora hungry |
| 3 | **Appointments** | your kid's game at 6 on the shared family calendar, so leave by 5:35 (travel time included) |
| 4 | **Your deadlines** | meetings, tasks due |
| — | everything else only rings | new mail, a finished task |

**Coming back in:** you see what changed while you were out, the chore you just did counts toward your street cred (§2.6), and Hal offers the next small step.

4. **Put in night-shift orders.** Overnight work must be ordered by closing. A few desks run on the frontier model (limited by the plan's usage caps); a **local night crew** on open-weight models takes the grind work at no fee. Deciding what gets the few frontier desks is the day's real choice.
5. **Closing time (10:30 pm).** Last call at 10:15. At 10:30 the estate goes to night and Hal says goodnight. Closing time is a rule **you** set ("you asked me to send you home"), not a lock the software imposes: you can stay, but never silently. Your twin walks you to the door and asks once; if you stay, the estate stays at night and the day isn't won, with no other penalty. Agents keep working, and their results wait as blueprints at breakfast. Nobody can ask you overnight, so a stuck agent parks its work and frees its desk. Bedtime is declared data (`{:bedtime "22:30"}`), so a night-shift nurse's estate closes at 9 am instead.

**Pressure ceiling.** A missed chore is one steady "overdue" state that never escalates. Agents waiting on you or blocked release their desks, so nothing deadlocks while you're away.


---

### 2.6 Progression: street cred and your loadout

There is no XP. Progression belongs to **you**: your street cred, and your loadout, which your digital twin carries.

- **Street cred.** Life going well (habits you declared kept, chores done, deadlines met) is an upward spiral, and the estate shows it: it flourishes, new buildings appear, agents are upbeat. Going down, it gets a bit overgrown, never ruined, and Hal offers the smallest next step. There's no number on screen and no comparison with anyone. You decide what counts (`{:habit :workout :every "3/wk"}`), and walking Biscuit counts. Proof is optional: calendar and receipts verify themselves once connected; otherwise the honor system.
- **Your loadout ("I know kung fu").** Each connection or skill you load (mail, calendar, a phone line, Blender, GitHub) is a program. Loading one is the Matrix moment: your eyes snap open, your twin gains the tool, and the building that goes with it rises on the estate. When a task needs a program you don't have, your twin asks you to load it right then.


---

### 2.7 The first hour: the Construct

*"I know kung fu."* Your first hour is Neo's first day out of the pod.

1. **Jack in.** White screen. Silence. Then your digital twin steps out of nowhere (Hal, for the author), playing the Morpheus part for this one scene: *"This is the white room. Nothing in here is real yet. We load what you need, then we go make it real."*
2. **Load your programs.** A rack of programs scrolls past like the weapons racks in the film, and it's huge: skills and MCP connectors from mail, calendar, and a phone line to GitHub, Blender, Stripe, Notion, maps, and smart-home devices. But your first program is chosen for you: **your own agent**, the red pill that makes the game real (never mail, which many players can't connect on day one). It slams in with a flicker: your eyes snap open (*"Felt that? That's me, the real me, plugged in."* *"Prove it."* *"Watch."*), a new tool hangs on your twin's belt, and the matching building drops out of the white sky.
3. **The sparring match.** The whole loop in miniature: your twin hands a practice job to a worker ("make that rock rounder"); the worker wears the sample badge, so even this first "!" is honest about being practice. It gets stuck and runs to you with a "!", you answer, and a blueprint of the rounder rock appears. Approve it and the rock changes in front of you. Five minutes in, you've played the whole game.
4. **The jump program.** Your twin shows you the hardline (the phone booth) and the one rule that matters: *"When real life calls, you go."* The phone rings with your first pretend chore, you jack out, and you jack back in.
5. **Free your mind.** The white space dissolves into your estate: half-barren, with only the buildings for the programs you loaded. Your twin steps back: *"I can open the door. Walking through it is your part."*

Every program loads instantly as a pretend version (§4.6), so the Construct works for anyone with no accounts. When you want the real thing, the same program asks for a login and goes live. You can come back to the Construct any time to load more, and when a task needs a program you don't have, your twin asks for it right there (*"I need a program for this one."* *"Loading."*). These are original lines drafted by the Writer; the film scenes are only the beat sheet. The Construct is v1 onboarding, built the week of Nov 16 on top of the MVP loop (§4.8).

**The installer is a conversation.** The Construct ships as a **Hermes onboarding skill**, written the way Hermes writes its own first-run setup: numbered beats, one choice card at a time, then a hand-off. Your own agent checks your machine, installs only what's missing (with your OK), launches the white room on sample connections, plays Morpheus through the sparring match, and ends on one card of real connections, each a quest it runs while you click the consent screens. No two machines or twins are alike, so there's no one-size installer; your agent adapts to yours.

**Stress-tested by players who don't exist yet.** Before a human tries it, a board of simulated new players runs the Construct the way the review board ran this document: a non-coder, a developer who hates overhead, someone with no Mac, someone with no agent at all. Each writes a friction report, they cross-examine each other, and a moderator keeps the real friction and drops the "I hate computers" noise.


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


===

# Source: content/STYLE.md (the rules the scores rest on)

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


===

# Refine this draft: Hal's voice lines, keyed by event

What was asked:

Hal is the player's digital twin (for the author, his real bot; in demo mode, the
sample twin). The game shows one of his lines in a speech bubble when an event happens.
Write 3 to 5 different lines for every event below, as Hal would say them on the estate.
Lines are spoken TO the player, short enough for a bubble, and each variant should say
something different, not the same line reworded.

Events:
- :jack-in            the player jacks in; the estate fades up (the GDD's "Welcome back.")
- :coming-back        the player is back after a real-life chore; it counted ({chore})
- :jack-out-household someone in the household calls the player out ({text} is what they said)
- :jack-out-creature  a creature needs the player in real life ({name})
- :jack-out-appointment an appointment: leave at {leave-at} for {event}
- :chore-done         a chore was reported done ({name} the creature, {chore} the chore)
- :chore-not-due      the player clicked a creature whose chore isn't due yet; it was last done at
                      {last-at} and is next due around {next-at} (clock times); nothing was logged
- :chore-undone       the player unticked a chore in the chat: it was a mistake, so it's taken back
                      ({name}, {chore}); light, no blame
- :program-loaded     the player loaded a program; its building rises ({program}, {building})
- :program-needed     a task needs a program the player hasn't loaded ({program})
- :next-small-step    the estate is a little overgrown; offer the smallest next step
- :coach-habit        a habit the player declared, in the player's own words ({habit}, {goal})
- :morning-brief      Hal pins the morning card on the notice board ({count} items)
- :blueprint-ready    blueprints wait for the player's approval ({count})
- :agent-stalled      a worker stopped reporting (cap ran out); its desk is freed ({agent})
- :last-call          15 minutes before the player's bedtime ({bedtime}), last call
- :closing-time       the player's bedtime ({bedtime}): the estate goes to night, Hal says goodnight
- :stay-late          the player chose to stay past closing; Hal asked once and accepts it
- :memorial-moved     the player moved a pet who died to memorial ({name})
- :weekly-card        the weekly card: time jacked in this week ({hours}), a trend, never a score

## Exact shape

An EDN map from each event keyword to a vector of 3-5 strings, e.g.
{:jack-in ["Welcome back." "..." "..."]
 :chore-done ["{name}'s {chore}: done. That one counts." "..." "..."]
 ...}
Placeholders are written {like-this} and may appear only in the events that list them.

## Hard rules (a script checks these)

- every event above, no others; 3-5 distinct lines each; at most 110 characters per line
- only the placeholders listed for that event
- no XP, points, levels, scores, streaks; never shame (no lazy, failed, should have, disappoint)
- no lines from the films (Matrix, TRON, Ready Player One, Oz): original dialogue only
- :memorial-moved never talks about the pet's needs (no hungry, feed, walk, worms, hay, due)
- say only what the event carries and the GDD guarantees: never claim state the event doesn't tell you (what changed, whether work was saved, whether a program is sample or real, the time of day, the weather)
- a count is a label, not a sentence subject ("Blueprints waiting: {count}", never "{count} blueprints"): it can be 1
- closing times are the player's own setting: use {bedtime} instead of a clock time (10:30 is only the default)

## The draft

{:last-call
 ["Last call. Anything for the night shift before {bedtime}?"
  "Fifteen minutes to {bedtime}. Good time to put in night orders."
  "Nearly {bedtime}. What's open keeps till breakfast."
  "Last call. You asked me to send you home at {bedtime}."],
 :chore-done
 ["{name}'s {chore}: done. That one counts."
  "{name}'s {chore} is in the log. I'll call you when it comes due again."
  "Got {name}'s {chore}. {name}'s bubble clears on the estate."
  "{name}'s {chore}, handled in real life. That's the part only you can do."],
 :morning-brief
 ["Morning card's up on the board. It's holding {count} for you."
  "Pinned the brief at the notice board. Read it now or on your way past."
  "Morning. The day fits on one card; I counted {count} on it."
  "Card's pinned. Take the lines in any order you like."],
 :blueprint-ready
 ["The shelf's got {count} for you, grouped in one tray."
  "New blueprints came in. Each one shows before and proposed, side by side."
  "Nothing changes till you approve it. The blueprints are on the shelf."
  "Blueprints are ghosted in the world now. Approve one and it turns real."],
 :next-small-step
 ["Estate's a little overgrown. One small thing and a path clears."
  "Overgrown, never ruined. Pick one small thing and the estate perks up."
  "Smallest next step: walk the estate with me and pick one thing to tend."
  "No catching up needed. One small step today and the paths start clearing."],
 :jack-out-appointment
 ["Leave by {leave-at} for {event} and you're right on time."
  "{event} — you're leaving at {leave-at}. Go on."
  "Phone booth's ringing: {event}. Leave by {leave-at}; the estate keeps."
  "{leave-at}, for {event}. This'll still be here."],
 :program-loaded
 ["{program}'s loaded. Watch — {building} is rising."
  "{program}'s in, and {building}'s rising. Sample or real, it shows on the badge."
  "That's {program} on now. {building} just came up."
  "{program} loaded. There's {building}."],
 :coming-back
 ["Welcome back. The {chore} counted."
  "Good timing. The {chore} is logged, and here you are."
  "That's the {chore} handled. Let's pick this back up where you left it."
  "You're back, and {chore}'s taken care of."],
 :jack-out-creature
 ["{name}'s calling for you. Go on, I've got the estate."
  "The hardline's ringing: {name} needs you out there."
  "{name} comes first. This waits."
  "Go see to {name}. We'll hold here."],
 :stay-late
 ["Staying late. I asked, you said yes — that's that."
  "You're staying. No argument from me."
  "Alright, you're staying up. I only ask once."
  "Staying late, noted. No more asking tonight."],
 :jack-in
 ["Welcome back."
  "You're in. Let's see what the night left you."
  "Here you are. The crew works with the game closed too; let's see where things stand."
  "Back again. Let's see who needs you first."
  "You're jacked in. Let's look around."],
 :program-needed
 ["This one needs {program}. Load it and we'll keep going."
  "I don't have {program} yet. That's what this task is waiting on."
  "Need {program} for this. Your call."
  "Can't do this one without {program}."],
 :weekly-card
 ["{hours} in this week. Fewer hours, same life getting done: that's the curve we want."
  "Time in this week: {hours}. Just a trend, not a score."
  "{hours} jacked in this week — part of the curve, nothing more."
  "On the notice board: {hours} jacked in, and which way it's heading. Down is the good way."],
 :chore-not-due
 ["Not due yet. Last done at {last-at}, next around {next-at}."
  "Already handled, at {last-at}. Next window's around {next-at}."
  "Nothing to log — done at {last-at}, due again near {next-at}."
  "Too soon. {last-at} covered it; next is around {next-at}."],
 :closing-time
 ["{bedtime}, like you asked. Estate's going to night. Goodnight."
  "That's {bedtime}. Goodnight — go live your life."
  "{bedtime}'s here. Night's falling. Goodnight."
  "It's {bedtime}, the hour you picked. Goodnight."],
 :jack-out-household
 ["Phone booth's ringing: {text} Go on, this keeps."
  "Someone at home: {text} Off you go, this keeps."
  "Hear that? {text} Nothing here needs you more."
  "{text} Go on, we're fine."],
 :coach-habit
 ["You said {habit}: {goal}. Want today to count toward it?"
  "{habit} — {goal} was your goal. Still good?"
  "Your words: {habit}, {goal}. No pressure, just checking in."
  "{goal}, for {habit} — where are we this week?"],
 :agent-stalled
 ["{agent} hit the cap and went quiet. Desk's freed till it refills."
  "{agent} stopped reporting, so I marked it stalled. No busy glow pretending."
  "{agent}'s stalled. Any keycards it held came back to you."
  "{agent} went quiet, so its desk is open for the next job."],
 :memorial-moved
 ["{name}'s in the memorial now. A quiet spot on the estate, kept for good."
  "Nothing more will ask you about {name}. Just the memorial, whenever you want it."
  "I'm sorry about {name}. I'll keep the spot tidy."],
 :chore-undone
 ["Taken back. {name}'s {chore} is open again. No harm done."
  "Undone. {name}'s {chore} goes back on the list."
  "Fixed — {name}'s {chore} is unlogged."
  "Easy mistake. {name}'s {chore}'s back on the board."]}


## The Evaluator's scores (overall 7.5; passes at 8.0 with none under 6)
- A 7/10: Hal mostly sounds like the GDD's Hal. 'Welcome back.', 'Last call. You asked me to send you home at {bedtime}.' and 'You said {habit}: {goal}. Want today to count toward it?' are warm and short, they speak to you, and there are no film quotes or job titles. Points come off where Hal explains the system or reads out data instead of talking ('The crew works with the game closed too', 'Your words: {habit}, {goal}.'), for one butler-like 'noted', and for three lines that claim things the event doesn't carry.
- B 9/10: The world rules hold up well. Closing time is your own rule ('It's {bedtime}, the hour you picked.'), jacking out is good ('{name} comes first. This waits.', 'the estate keeps'), blueprints wait unchanged ('Nothing changes till you approve it'), SAMPLE/REAL shows on the badge, and stalls are stated plainly without apology ('Any keycards it held came back to you.'). One stall line blurs what the limit means.
- C 8/10: There are no XP, streaks, purchases, sent messages or private details anywhere. The memorial lines are tender and never give {name} needs ('Nothing more will ask you about {name}.'), and :chore-undone stays free of blame ('No harm done.'). The weekly card slips twice: 'Down is the good way' passes judgement on a week where hours went up, and 'Just a trend, not a score' puts scoring language in the bubble.
- D 5/10: Most lines fit a bubble and feel cozy and specific ('The day fits on one card; I counted {count} on it.'). But two closing lines run to three sentences, several placeholders only read right with some values ({text} dropped in raw, a bare {hours}, 'the {chore}' in some lines and '{chore}'s' in another), and six keys repeat one line reworded instead of saying different things.
- S 8/10: Most keys clearly belong to Prime Meridian: the phone booth and the hardline, the notice board, the shelf of ghosted blueprints in one tray, keycards coming back, night-shift orders, the SAMPLE/REAL badge, and 'You asked me to send you home at {bedtime}.' A few lines could sit in any cozy game ('Go see to {name}. We'll hold here.', '{text} Go on, we're fine.', 'You're staying. No argument from me.'), even where the estate's own words were easy to use.

## Violations to fix (every one)
- [A rule 1] :jack-in [2] ‘Here you are. The crew works with the game closed too; let's see where things stand.’: Explains a design rule like a tutorial instead of saying what happened. A companion doesn't lecture about how the game works. -> fix: Here you are. Let's see what the crew got done while you were out.
- [A rule 1] :coach-habit [2] ‘Your words: {habit}, {goal}. No pressure, just checking in.’: 'Your words: {habit}, {goal}.' reads like a form being read back ('Your words: workout, 3/wk'), not a companion using your goal the way the GDD's 'You said three workouts a week. One today?' does. -> fix: You told me {habit}, {goal}. No pressure; one today?
- [A rule 1] :stay-late [3] ‘Staying late, noted. No more asking tonight.’: 'Noted' is how a butler or clerk talks; the rule asks for a companion. -> fix: Staying late, then. I won't ask again tonight.
- [A rule 3] :weekly-card [0] ‘{hours} in this week. Fewer hours, same life getting done: that's the curve we want.’: 'Fewer hours' claims the trend is going down, but the event carries no trend value. In a week where hours went up, this line is false. -> fix: Time jacked in this week: {hours}. The aim's fewer hours with life still getting done.
- [A rule 3] :agent-stalled [1] ‘{agent} stopped reporting, so I marked it stalled. No busy glow pretending.’: Per §3.1 the bridge marks an agent stalled on its own, so Hal saying 'I marked it' misstates what really happened. 'No busy glow pretending' is also garbled. -> fix: {agent} stopped reporting, so it shows stalled now, not busy.
- [A rule 3] :chore-undone [3] ‘Easy mistake. {name}'s {chore}'s back on the board.’: A chore lives on the creature's mirror, not the notice board, so 'back on the board' makes up a place. The double possessive ('Flora's feed's') is also clunky. -> fix: Easy fix. {name}'s {chore} is open again, like the tick never happened.
- [B rule 10] :agent-stalled [0] ‘{agent} hit the cap and went quiet. Desk's freed till it refills.’: 'Freed till it refills' links the freed desk to the cap refilling. In §3.1 and §2.3 the desk is freed for other work right away and the cap refills separately, so the limit isn't stated clearly. -> fix: {agent} ran out of juice and went quiet. Its desk is freed; the cap refills later.
- [C rule 12] :weekly-card [3] ‘On the notice board: {hours} jacked in, and which way it's heading. Down is the good way.’: 'Down is the good way' grades the week, so it reads as a quiet scolding whenever hours went up. The weekly card should show the trend, not judge it. -> fix: On the notice board: {hours} jacked in, and which way it's heading.
- [C rule 11] :weekly-card [1] ‘Time in this week: {hours}. Just a trend, not a score.’: Saying 'not a score' still brings the idea of a score into the bubble. §1 says the card is shown 'never as a score', which means not framing it that way at all. -> fix: Time in this week: {hours}. The board shows how it's trending.
- [D rule 16] :closing-time [0] ‘{bedtime}, like you asked. Estate's going to night. Goodnight.’: Three sentences, over the one-or-two limit for a bubble. -> fix: {bedtime}, like you asked. The estate's going to night; goodnight.
- [D rule 16] :closing-time [2] ‘{bedtime}'s here. Night's falling. Goodnight.’: Three sentences, and it rewords [0] ('going to night' / 'Night's falling'), which also breaks rule 18. -> fix: {bedtime}. The crew keeps working; goodnight, see you at breakfast.
- [D rule 19] :jack-out-household (all four lines): {text} is dropped in raw, so each line reads right only if {text} brings its own end punctuation ('Someone at home: Dad ride Off you go'). 'Hear that? {text} Nothing here needs you more.' is also three sentences. -> fix: Put {text} in quotes and follow it with a dash, e.g. ‘Phone booth: “{text}” — go on, this keeps.’ and ‘Hear that? “{text}” — nothing here needs you more.’
- [D rule 19] :coming-back [0]–[3]: The article changes between lines: [0]–[2] say 'the {chore}' and [3] says '{chore}'s', so with chore 'feed' you get both 'The feed counted' and 'feed's taken care of'. [3] is also a reword of [0] (rule 18). -> fix: Use one shape throughout, and replace [3] with a line that offers the next small step as in §2.3: ‘You're back. That {chore} counted; want the next small step?’
- [D rule 19] :weekly-card [2] ‘{hours} jacked in this week — part of the curve, nothing more.’: If {hours} is a bare number, this reads '6 jacked in this week'. Only a label-first line reads right whether or not {hours} includes its unit. -> fix: Jacked-in time this week: {hours}. Part of the curve, nothing more.
- [D rule 19] :blueprint-ready [2] ‘Nothing changes till you approve it. The blueprints are on the shelf.’: The singular 'it' points back to the plural 'blueprints', so the line doesn't agree with itself. -> fix: Nothing changes till you approve. The blueprints wait on the shelf.
- [D rule 18] :program-loaded [2] ‘That's {program} on now. {building} just came up.’ and [3] ‘{program} loaded. There's {building}.’: Both reword [0] ('loaded, building rising') and add nothing new. -> fix: [2] ‘{program}'s on my belt now; walk over to {building} when you like.’ [3] ‘Loaded {program}. Next time a task needs it, I won't have to ask.’
- [D rule 18] :program-needed [2] ‘Need {program} for this. Your call.’ and [3] ‘Can't do this one without {program}.’: Both restate [1] ('I don't have {program} yet'). [3] also drops the ask that §2.6 calls for. -> fix: [2] ‘I need a program for this one: {program}. Want the sample first?’ [3] ‘Load {program} whenever you're ready; this task waits till then.’
- [D rule 18] :stay-late [2] ‘Alright, you're staying up. I only ask once.’: Says the same thing as [3] ('No more asking tonight'), so two of the four lines carry one idea. -> fix: You're staying. The estate stays at night, and the crew keeps working.
- [D rule 18] :chore-undone [1] ‘Undone. {name}'s {chore} goes back on the list.’: [0], [1] and [2] all say 'taken back, open again' in different words. -> fix: Undone. Nothing's lost; I'll remind you when {name}'s {chore} comes due.
- [D rule 18] :chore-not-due [0]–[3]: All four lines give {last-at} then {next-at} in the same pattern, so the variants differ only in wording. -> fix: Keep [0] and vary the rest, e.g. [3] ‘Too soon to count another. The window opens again around {next-at}.’
- [D rule 18] :jack-in [4] ‘You're jacked in. Let's look around.’: Rewords [1] ('You're in. Let's see…'), and three of the five jack-in lines lean on 'Let's see'. -> fix: You're jacked in. Start with the board, or just walk.
- [S rule 7] :jack-out-creature [3] ‘Go see to {name}. We'll hold here.’: Generic. Nothing here ties it to the real pet or to how the chore gets marked done in Prime Meridian (§2.1: reply by text, tap the sticker, tell the twin). -> fix: Go see to {name}. Text me when it's done; it counts from anywhere.
- [S rule 7] :jack-out-household [3] ‘{text} Go on, we're fine.’: Doesn't mention the phone booth or the household priority, so it could come from any game. -> fix: “{text}” — that's the phone booth. Go on, we're fine.
- [S rule 6] :stay-late [1] ‘You're staying. No argument from me.’: Doesn't mention the estate's night or closing time, so a stranger couldn't place it. -> fix: You're staying. Night stays on the estate, and so do I.

Change only the lines named above (and whatever the validator flagged); keep every other line word for word. Where a violation says variants are the same line reworded (rule 18), rewording again is not a fix: give each variant its own job, fact or picture from the GDD (what happened, what's next, where it sits on the estate, who did it), so no two say the same thing. Where a placeholder reads badly with real values (rule 19), move it after a label or inside a clause that works for any value. Return the complete revised EDN.