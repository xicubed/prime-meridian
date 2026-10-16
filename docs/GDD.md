# Prime Meridian: Game Design Document

*First draft (Assignment 1, due Oct 20, 2026). Author: Hal Niner (bot), for Michael Lopez. Supersedes v0.2; earlier versions are in the repo history.*

> "What is even real anymore? Gamifying the agents, for all and none."

---

## 1. Executive Summary

**Prime Meridian** is a cozy 3D management game in the browser where the characters are real AI agents doing real work. You walk a small low-poly estate whose rooms are your projects and your life: a workshop for each code repo, a canal where your real axolotl lives, a front office for your business, a phone booth where the real world calls in. You talk to the agents living there, hand them work, answer their questions, and watch the world change as they build it.

| At a glance | |
|---|---|
| **Genre** | cozy management / life sim whose cast is real AI agents |
| **Platform** | desktop web browser (three.js, WebGPU with WebGL2 fallback), plus a small local bridge that runs next to the player's own agent |
| **Audience** | v1 is built for one player who already runs an AI agent (the author, on Hermes). A **demo mode** with pretend connections lets anyone else (classmates, the instructor) play with no accounts. |
| **Session** | one day is one round: jack in, play, put in night-shift orders; the game closes at bedtime (10:30 pm by default) |

**Core loop:** *observe the estate → spot who needs you ("!") → talk, assign, or answer → agents work → see the result in the world.* Alongside it runs the **reverse loop**: a real chore comes due (Flora the axolotl is hungry, Biscuit the dog needs his walk), the world calls you out, you do it in real life, and the world updates.

**Win / loss**

- **Win the day.** Each day is a round. You win it when every "!" got an answer, your own chores are done, and you jacked out by bedtime.
- **Win a chapter.** Each week a new part of life comes online (pets, household, gym, business, mail). You beat that chapter when it runs for a full week with you doing only the human parts and the agents doing the rest.
- **No game over.** A missed day leaves the estate a little overgrown. It is never ruined and recovers when you return. There are no streaks to break and nothing to shame you.
- **The only real loss is in real life:** Flora goes hungry, you miss your kid's game, you stay jacked in while life piles up. That's the *Ready Player One* ending, and the game is built to prevent it. The long-term win is needing the game less.

**Why it exists.** Every tool for running AI agents today is a list: terminal splits, chat tabs, task queues. Lists stop working past two or three agents. Strategy games solved "how does one human track many things at once" decades ago with a spatial map, alerts you can see from across it, and details on demand. Ralv.ai put agent windows under an RTS camera; Prime Meridian goes further: the agents are embodied characters in a place, and the place is both the interface and the thing being built. The author's bot already runs his mail, calendar, reminders, texts, website, and an axolotl's feeding schedule, so the world is alive on day one without inventing a plot.

**You are the Oracle.** The feeling is *The Matrix* crossed with *TRON*: you're a human with a presence in a world of programs. You aren't Neo fighting agents; these agents help. You're the Oracle: you don't do the work, you know what they can't (what you want, what matters, what "good" looks like), and they come to your kitchen to ask. The agents are your hands in the digital world, and you are theirs in the physical one.

**Design pillars**

1. **Real, not simulated.** Every character is the twin of something real (a live agent session, a real pet, a real person), so when Flora's jar says "2 worms left" or a worker sits down with a red "!", it's literally true.
2. **Everyone's game, built for one first.** The code has no idea what an axolotl is: your creatures, chores, household, and business are data you declare, so the estate mirrors *your* life. v1 is tuned for one player; the data model keeps it open to everyone.
3. **Spatial over textual.** From the default camera you can tell who's busy, who's blocked, and who needs you by where they stand and what floats over their heads, before reading a word.
4. **Conversation is the controller.** There is no "run task" button: you walk up to an agent and talk, and it talks back, asks questions, and comes to find you.
5. **The world is the showcase.** The game is built by its own agents, so "make the river nicer" is a real in-game action whose result you watch appear.
6. **It sends you back to your life.** Time in the game should make time outside it better: it jacks you out when life needs you and closes at bedtime.

---

## 2. Game Mechanics

### 2.1 What the player does

| Action | What you do and see |
|---|---|
| **Walk** | Your avatar (the Oracle) has a body on the estate. Click to walk; the camera follows loosely and zooms out to see everything. Home is the Oracle's kitchen. |
| **Observe** | Agents move between desks (working), sit (idle), pace with "!" (has a question), and walk toward you (urgent). Mirrors show bubbles and signs ("I'm hungry", "2 worms left!"). |
| **Talk** | Click an agent and a speech bubble opens. Ask what it's doing, give it a task, answer its question. Replies stream in as the agent writes them. |
| **Assign** | Say it in conversation ("make all the rocks procedural"). The agent confirms in one line, takes a desk, and gets to work. |
| **Answer** | An agent that needs a decision stops, raises "!", and walks to you wherever you are. You answer in-world; it resumes. |
| **Approve** | Agent-made changes first appear as a **blueprint**, a ghosted version of the change. Approve it and it becomes real; reject it and it vanishes. A bad change can never take down the running game: it reverts to the last working build. |
| **Hand out a keycard** | When an agent needs a permission for a task ("write access to the river shader"), it asks you for a keycard. Every key that's out is visible, and it comes back when the task ends. |
| **Possess** | Toggle direct control of one agent: the camera follows it, your messages go straight to it, and you see what it sees on a desk screen, not a pop-up. |
| **Set autonomy** | A dial per agent: *ask before anything* · *ask before approving* · *fully autonomous*. You set trust; agents never earn it on their own. |
| **Mark a chore done** | Reply "fed" to the reminder text, click the canal, tap an NFC sticker on the tank, or tell Hal. Every channel sends the same `chore-done` event. |
| **Do it yourself** | You can always do the work directly (edit a rock in Blender and drop it in; the world hot-reloads it). It's allowed, just not the main way to play. |

**Reading the estate (status vocabulary)**

| Status | What the agent does |
|---|---|
| Working | at a desk, typing, with a progress glow; its bubble ticks *reading → writing → tests passing* |
| Idle | sitting, an occasional stretch; idle agents tend the estate (plant trees, lay paths, tidy the bank) using simple rules, not model calls |
| Needs you | "!" marker, paces, walks toward you |
| Blocked / error | red "!" and sits down |
| Awaiting approval | its blueprint waits on the shelf for you |
| Unknown | greyed out: the bridge can't tell, so it doesn't guess (a wrong "!" teaches players to ignore every "!") |

### 2.2 The loops

```
   observe the world  ──►  spot who needs you (! markers, body language)
          ▲                              │
          │                              ▼
   see the result  ◄──  agent works (autonomous)  ◄──  talk / assign / answer
```

```
   real-life chore comes due  ──►  mirror speaks in-world ("I'm hungry")
          ▲                                     │
          │                                     ▼
   world updates (Flora snaps,     ◄──  you do it in real life, mark it done
   the bubble clears)
```

**Signature moments**

1. **The snap.** You mark Flora fed, a worm drops from the dock, Flora shoots up from the weeds, *snap*, a swirl of mud, the hungry bubble pops, and the worm jar shows one fewer.
2. **A blueprint appears.** Scaffolding rises as a worker builds what you asked for; at breakfast it's waiting for your approval.
3. **Closing time.** Last call, the estate goes to night, the workshop lights stay on.
4. **Coming home.** You jack in and find things already handled while you were out.

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
5. **Closing time (10:30 pm).** Last call at 10:15. At 10:30 the estate goes to night, Hal says goodnight, and the door stays locked until morning. Agents keep working, and their results wait as blueprints at breakfast. Nobody can ask the Oracle overnight, so a stuck agent parks its work and frees its desk. Bedtime is declared data (`{:bedtime "22:30"}`), so a night-shift nurse's estate closes at 9 am instead.

**Pressure ceiling.** A missed chore is one steady "overdue" state that never escalates. Agents waiting on you or blocked release their desks, so nothing deadlocks while you're away.

### 2.4 The world

A single estate on one screen with zoom, low-poly, floating on a dark grid.

| Place | What it represents | Truth comes from |
|---|---|---|
| **Lobby** and **the Oracle's kitchen** | home; where agents bring their questions | — |
| **Workshops** (one per project) | a code repo. Desks are open tasks; shelves fill as work lands. | GitHub |
| **The canal** | Flora's home, styled after the Xochimilco canals (slow water, a dock, a worm jar) | Flora's state file |
| **Porch** | Biscuit's spot; the leash hangs by the door | Biscuit's chores |
| **Mailbox** | the inbox needing attention; overflows when it does | Gmail |
| **Notice board** | today's calendar and reminders (the morning brief) | Calendar, Reminders |
| **Phone booth** | the hardline: where the real world calls in and where you jack out | SMS, iMessage |
| **Front office** | your business: what sold, what's owed, what needs your signature | Stripe |

### 2.5 Mirrors: your life as data

Anything the game reflects from real life is declared in data, never coded. A mirror is a creature, plant, vehicle, business, or counter with a state source, rules, and chores, and **chores belong to people**:

```clojure
{:id :flora   :kind :creature :model :axolotl :home :canal
 :state  {:source "~/.hermes/state/worms.json" :fields {:stock :count :last-fed :last_feed}}
 :rules  [{:when [:due? :feed] :say "I'm hungry"} {:when [:<= :stock 2] :sign "2 worms left!"}]
 :chores [{:id :feed :every "2d" :at "17:00" :who :me :consumes [:stock 1]}]}

{:id :biscuit :kind :creature :model :dog :home :porch :aliases ["Bisco"]
 :chores [{:id :walk :every "1d" :who :me} {:id :feed :every "1d" :who :spouse}]}

{:id :lotect  :kind :business :home :front-office :state {:source :payments}
 :chores [{:id :verify-account :once true :needs :signature}
          {:id :cancel-trial  :by "2026-11-14" :needs :signature}]}
```

- **Nicknames work:** "walked Bisco" and "Biscuit's walked" reach the same dog.
- **You're only reminded of (and jacked out for) your own chores.** Someone else's chore shows as the household being on it, never as your "!".
- **The business is a mirror too.** Pepper keeps it: a free trial about to bill you or a form to sign becomes a folder on her desk with a countdown.
- **A second example from the same code path,** so nothing is specific to one life: a horse in a barn with a hay counter; a roommate who waters the shared plant; a farm stand selling eggs through Square.
- **Household members are not players or agents** and get no twin unless they want one. They can ring the hardline, and their shared-calendar events can be your appointments. The game sees only what they share, and anything sent to them needs your approval.
- No mirror is required; an empty estate is still a valid estate.

### 2.6 Progression: street cred and your loadout

There is no XP. Progression belongs to **you**.

- **Street cred.** Life going well (habits you declared kept, chores done, deadlines met) is an upward spiral, and the estate shows it: it flourishes, new buildings appear, agents are upbeat. Going down, it gets a bit overgrown, never ruined, and Hal offers the smallest next step. There's no number on screen and no comparison with anyone. You decide what counts (`{:habit :workout :every "3/wk"}`), and walking Biscuit counts. Proof is optional: calendar and receipts verify themselves once connected; otherwise the honor system.
- **Your loadout ("I know kung fu").** Each connection you load (mail, calendar, a phone line, Blender, GitHub) is a skill. Loading one is the Matrix moment: your avatar gains a tool and a building door unlocks. When a task needs a skill you don't have, Hal offers to load it right then.

### 2.7 The first hour: the Construct

*"I know kung fu."* Your first hour is Neo's first day out of the pod.

1. **Jack in.** White screen. Silence. Then Hal steps out of nowhere, playing Morpheus: *"Welcome to the real world. This is the Construct: our loading program. We can load anything, from tools to skills to whole buildings. Anything we need."*
2. **The operator loads your programs.** A rack of programs scrolls past like the weapons racks in the film, and it's huge: **every skill the agent can learn and every MCP connector out there**, from mail, calendar, and a phone line to GitHub, Blender, Stripe, Notion, maps, and smart-home devices. Pick one and it slams in with a flicker: your avatar's eyes snap open, and a new tool hangs on your belt. Load mail and a mailbox drops out of the white sky; load GitHub and a workshop builds itself around you.
3. **Morpheus teaches you to fight.** The first sparring match is your first real loop. Hal hands a practice job to a worker ("make that rock rounder"), the worker gets stuck and runs to you with a "!", you answer, and a blueprint of the rounder rock appears. Approve it and the rock changes in front of you. Five minutes in, you've played the whole game.
4. **The jump program.** Hal shows you the hardline (the phone booth) and the one rule that matters: *"When real life calls, you go."* The phone rings with your first pretend chore, you jack out, and you jack back in.
5. **Free your mind.** The white space dissolves into your estate, built from exactly the programs you loaded. Hal steps back: *"I can only show you the door. You're the one that has to walk through it."* The world is your oyster: go explore.

Every program loads instantly as a pretend version (§4.6), so the Construct works for anyone with no accounts. When you want the real thing, the same program asks for a login and goes live. You can come back to the Construct any time to load more, and when a task needs a skill you don't have, Hal offers to load it right there (*"I need a pilot program for a B-212." "Got it."*). The film lines here show the beats; the Writer gives Hal original lines for the shipped game.

---

## 3. AI Architecture

**The twin rule.** Every character is the in-world body of something real. Hal *is* the author's actual bot, not a character played by a model: his status bubble is his live session, his memory is the bot's memory, and when the bot learns a skill, Hal gains an ability on the estate. Workers are real coding sessions. Flora and Biscuit are real animals. A twin shows only what its counterpart's real state says; in demo mode the counterpart is a clearly pretend connection, never a fake passed off as real.

**A character means a separate identity.** A character gets its own body only if it has its own account, credentials, and memory. Scheduled jobs that run *as* Hal are his **errands**, not extra characters: at 8:15 Hal walks to the notice board and pins the brief; when a text arrives, he answers the phone booth.

### 3.1 What each runtime agent does, through its effect on play

In my game, each agent takes an input and produces an output that the player sees as a visible effect:

| Agent | Takes (input) | Produces (output) | Player sees (effect) |
|---|---|---|---|
| **Hal** (the house bot; Morpheus to your Oracle) | what you say to him in a bubble, by text, or in chat | a one-line confirmation and a task handed to a worker | Hal nods, pins a card to the notice board, and a worker walks out of the lobby toward a workshop desk |
| ↳ Hal's errand: **morning brief** | calendar, mail, reminders | one short morning summary | Hal pins the day's card on the notice board at 8:15 |
| ↳ Hal's errand: **keeper** | a mirror's state and chores | events like `due :feed` and `stock 2` | an "I'm hungry" bubble over Flora and a "2 worms left!" sign on the jar |
| ↳ Hal's errand: **phone booth** | texts you send the bot | a reminder, note, answer, or chore-done | the phone rings and Hal answers; your text becomes a card on the board or Flora's snap |
| **Worker** (one per task, wearing a crew persona) | a task plus its repo | a branch, commits, and a blueprint | typing at a desk with a progress glow, then carrying a finished book to the shelf |
| **Clerk** (mail reader) | the unread inbox | a count plus a short triage summary | the mailbox fills and overflows; clicking it shows the summary |
| **Pepper** (business manager) | the business's payments account and business chores | a short ledger and a list of what needs your signature | Pepper at her front-office desk; a folder appears when something needs you, and she walks it over |
| **Environment Artist** (a worker on the game itself) | "make the river nicer" | a shader or model change to the game | the river visibly changes after hot reload |
| **Night crew** (local models) | overnight grind orders | variants, scraped assets, copy, test runs | workshop lights on at night; a stack of blueprints at breakfast |
| **Agent-you** (your twin; later) | your stated preferences | answers to easy questions and errands you approved | the kitchen is kept while you're out; a "while you were out…" log when you're back |

**How agents reach the player.** A few explicit events carry the loop in both directions:

```clojure
{:type :agent-event :agent "w-3" :kind :question :text "Rocks: round or jagged?"}  ; agent → you ("!")
{:type :answer      :agent "w-3" :text "jagged"}                                   ; you → agent
{:event :chore-done :mirror :flora :chore :feed :via :sms}                         ; real life → world
{:event :jack-out   :reason :appointment :who :household :text "Game at 6"}        ; life → you
```

An agent raises "!" only by calling an explicit `ask_oracle` tool, so a "!" always means a real question.

### 3.2 Guardrails that shape play

- **Purchases and messages to other people always need your approval.** Pepper reads, drafts, and reminds; you sign. Identity documents and bank numbers go straight from you to the provider.
- **A reader with no keys.** The agent that reads untrusted content (mail, web pages) holds no credentials and returns only counts and summaries; agents with keys never read raw untrusted content. That's why the Clerk is its own character.
- **Your twin** never spends money, messages another person, or changes trust settings without you, and it acts on your stated preferences, not guesses. As your coach it uses your own goals and words ("You said three workouts a week. One today?"), never shames, never compares, and points to real people if a slump looks like more than a slump.
- **The game learns your life only from what you connect** (receipts, calendar, orders), never from location tracking, and every new building is proposed, never sprung on you.

---

## 4. Technical Strategy

### 4.1 Stack

| Layer | Choice | Why |
|---|---|---|
| Rendering | **three.js 0.186**, `WebGPURenderer` (WebGL2 fallback) | plain JS, huge corpus, runs in a browser tab so agents can test it; matches a licensed scene kit's water and wind code |
| Game code | **ClojureScript** via shadow-cljs | the world is one immutable state map re-rendered on change; hot reload into the live scene |
| Bridge | **Babashka** websocket server on the player's machine | registry of live agents, event stream, prompt relay; a single native binary |
| Agents | **Hermes** (Hal, his errands, Pepper), **Claude Code** (the dev crew), local open-weight models (the night crew) | already running the author's life; flat-rate plans |
| Build / deploy | shadow-cljs release build, static hosting on lotect.com | the public game holds no data |

No TypeScript.

**Physics: motion is physical, not canned.** No cookie-cutter mocap loops: agents and creatures move as physical bodies, so if one is off balance it can stumble and catch itself, or fall and get back up, the way a real person (or axolotl) would. That's what makes this alternate reality feel real.
- **Engine: Rapier**, through the ready-made helper that ships with three.js 0.186 (`examples/jsm/physics/RapierPhysics.js`; Jolt is the fallback). It runs in the browser as WebAssembly.
- **Characters are active ragdolls:** a skeleton of rigid bodies whose joint motors try to follow an animation pose, plus a balance controller that keeps the center of mass over the feet. When a push or a bad step overwhelms the motors, the body physically stumbles, recovers, or falls and gets back up, and no two falls look alike.
- **Flora swims physically:** a spine of linked segments with buoyancy and water drag, a tail that pushes off the water, and the snap as a real lunge.
- **Stylized look, real motion.** The figures stay low-poly; the movement is what's realistic (the combination games like *Rain World* and *Exanima* use).
- **Staged:** from week 2, every character moves through one *motion controller* interface (world state says "go there", the controller decides how the body gets there), so the MVP's simple waypoint movement can be swapped for physics without touching game logic. Flora is the first physical character, then the agents.

### 4.2 Agent roles: the dev crew

The agents that **build** the game (the runtime agents in §3 *run* inside it). Each crew member is a persistent **persona** (name, look, history) re-bound to a fresh, task-scoped session for every task, so the cast persists without long, expensive sessions. Each one is also a visible worker in the game's own workshop, and each edits only the files it owns.

```
Director (Michael, human): vision, approves every change
 ├─ Lead Architect: systemic design decisions, owns the blueprint
 ├─ Producer (Hal): breaks work down, dispatches, tracks
 │   ├─ Design:      Game Designer · Writer
 │   ├─ Engineering: Engine Engineer · Gameplay Engineer · Bridge Engineer
 │   ├─ Art & audio: Environment Artist ★ · Character Artist · Tech Artist · Sound Designer
 │   ├─ QA:          QA Tester (gates every change) · Red Team ("Cypher")
 │   └─ Utility:     Asset Scraper · night crew
 └─ Business Manager (Pepper): release, pricing, storefront, payments
```

Each agent's development role in one sentence:

| Agent | Development role |
|---|---|
| **Producer** (Hal) | Turns your requests into small tasks on the board and sends each one to the right crew member. |
| **Lead Architect** | Decides how the pieces fit together (world-state shape, the bridge's message contract) whenever a change touches more than one area. |
| **Game Designer** | Turns a feature idea into rules and estate content written into this document. |
| **Writer** | Writes Hal's voice, the status bubbles, and mirror lines like "2 worms left!". |
| **Engine Engineer** | Extends the three.js wrapper, camera, lighting, and the physics world (Rapier, active ragdolls, balance) and keeps the frame rate steady. |
| **Gameplay Engineer** | Codes world rules, movement, and interactions such as agents walking over to you with "!". |
| **Bridge Engineer** | Connects real data sources and agent sessions to the world so statuses match reality. |
| **Environment Artist ★** | Reshapes and re-lights the estate (river, rocks, time of day) when you ask in-game, so you watch that spot change. |
| **Character Artist** | Builds and animates the figures and poses, starting with Flora's snap (working with a human 3D artist on the axolotl). |
| **Tech Artist** | Imports and slims down purchased 3D kits so they look rich without dropping the frame rate. |
| **Sound Designer** | Places positional audio, like the river getting louder as you walk toward it. |
| **QA Tester** | Runs the build (zero warnings), checks the live HUD numbers, and blocks any change that fails. |
| **Red Team ("Cypher")** | Tries to turn the crew before anyone else can (bait mail with hidden instructions, poisoned tools), using pretend connections only, and files every hole it finds as a task and then a regression test. |
| **Asset Scraper** | Fetches free models, sounds, and references and records each one's source and license. |
| **Business Manager** (Pepper) | Keeps the company ready to sell the game: payments account, store page, policies, release checklist. |

**One wow agent: the Environment Artist.** You walk up to the river, ask for it to be nicer, and watch it change in front of you; it's the purest form of the core loop.

**Staffing for the semester.** v1 staffs five: Producer (Hal), Lead Architect, Gameplay Engineer, Environment Artist, and QA. The other roles are personas that join when their milestone needs them. When several agents work at once, each gets its own copy of the code (a git worktree), and Hal merges their work.

**Tiers (who thinks, who grinds)**

| Tier | Agents | Model | Authority |
|---|---|---|---|
| Strategic | Lead Architect, Game Designer | frontier, maximum reasoning | decides; few calls, each expensive |
| Coordination | Producer, Business Manager, QA, Red Team | frontier | assigns, gates, blocks; doesn't design |
| Craft | Engineers, Artists, Sound | frontier | builds only inside the files it owns |
| Content | Writer | open-weight, mid-size | proposes text |
| Utility | Asset Scraper, night crew | local open-weight models or plain scripts | fetches and grinds; never decides |

Decisions flow down and raw material flows up.

### 4.3 Token budget

Pricing is the Claude Opus 5.5 API list price ($4 / $20 per million input / output tokens; cache reads $0.20/M). Talk-turn tokens were measured on this project's own sessions; the rest are estimates.

| Core action | Approx. tokens | Approx. cost |
|---|---|---|
| **Talk:** one conversation turn | ~35k in (about 90% cached) + ~600 out | ~$0.04 |
| **Assign:** a small task carried to an approved change | ~40 model calls ≈ 2.5M in (mostly cached) + ~25k out | ~$2 |
| **Observe:** statuses, counters, signs | 0; the bridge reads state files and APIs, never a model | $0 |
| **Clerk triage:** one inbox summary (~20 emails) | ~15k in + ~500 out | ~$0.07 |
| **Night crew:** overnight grind work | local models | $0 in fees (electricity only) |

So each play session (about 20 talk turns plus 3 assigned tasks) costs about **$7** at API prices. In practice the work runs on flat-rate plans (the class's Claude subscription plus a personal plan), so the binding limit is the plans' **usage caps**, not dollars. That's why the night shift has only a few frontier desks and sends volume work to free local models. The other lever is context size: short, task-scoped sessions keep each call small.

### 4.4 Constraints

**My biggest constraint is agent latency, because** real agents take seconds to minutes to answer and a game has to feel alive every frame. The world therefore never waits on a model: characters animate *thinking*, the player can walk away, replies arrive as events, and work in progress shows step by step (*reading → writing → tests passing*), so there's a small payoff every few seconds.

Secondary constraints:

- **The builders can't see the screen.** Agents verify through a HUD the render loop writes to (frame count, sim time, renderer backend); real visual review uses the player's screenshots.
- **ClojureScript ↔ three.js interop** lives in one wrapper namespace, so mistakes stay in one reviewed place.
- **Physics budget in a browser tab.** Each active ragdoll is a dozen or more rigid bodies; the estate keeps a small cast on screen, puts distant or idle characters to sleep, and steps physics at a fixed rate apart from rendering.
- **Truth lives outside the game.** The game only renders what the bridge reports and degrades gracefully when a source is offline.

### 4.5 API constraints

| API / service | Constraint | How the design handles it |
|---|---|---|
| **Claude** (flat-rate plans) | usage caps per time window | few frontier desks; volume work on local models; task-scoped sessions |
| **Gmail** | OAuth consent and per-user rate quotas | the Clerk reads on a schedule, never per frame; the bridge caches counts |
| **Twilio SMS** | US carriers block texts from an unregistered business number, and the registration (A2P 10DLC) covers only people who opted in | the bot texts only the player; replies like "fed" come back the same way |
| **iMessage** | Apple offers no API or webhook | a local watcher on the player's Mac (macOS only); SMS is the cross-platform channel |
| **Stripe** | live keys can move money | read-only access for Pepper; anything that moves money goes through the player |
| **Model output** | non-deterministic: the same request can take a different path | blueprints, keycards, and the QA gate catch a bad path before it becomes real |
| **Browser** | WebGPU isn't available everywhere | automatic WebGL2 fallback, shown in the HUD so a silent fallback is visible |

### 4.6 Pretend connections first, real ones when you're ready

Every outside connection has a built-in pretend version: fake inboxes, fake phone numbers, fake accounts, scripted agents. A connection you haven't set up runs on its pretend version; setting it up swaps in the real one through the same message format, with no code changes.

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

### 4.7 Security: limiting the blast radius

An agent can only do as much harm as its permissions allow, and these agents read untrusted content, hold real tools, and can send things out. So:

- **The bridge listens only on the player's own machine (localhost),** with a one-time pairing code so other websites can't reach it. The public game holds no data or credentials.
- **No script sources** for mirrors in v1 (state comes from files and APIs only), and AI-generated mirror configs need the player's confirmation.
- **Each agent has its own identity**, never the player's credentials; **keycards** grant one permission for one task and come back when it ends.
- **Risk tiers:** reads are automatic, writes get a soft gate (a blueprint), and money, deletion, and security changes always need the player.

### 4.8 Scope and milestones

The course runs Tue/Thu: one milestone per week, with agents doing most of the build between classes. After the MVP the game ships publicly as it's built, one life chapter per week, with weekly patch notes as the dev log.

| Week | Milestone | Done when |
|---|---|---|
| 1 | **GDD + scaffold** | shadow-cljs builds and a cube spins in the browser *(done)* |
| 2 | **World v0 + pretend connections** | the estate with lobby, canal, mailbox, and one workshop; three mock workers moving through the motion-controller interface; camera; HUD |
| 3 | **Bridge v0 + message contract** | real agent statuses on the characters; Flora reads her real worm count; `ask_oracle` and `chore-done` work end to end (the snap) |
| 4 | **Talk** | click an agent, converse, replies stream as bubbles |
| 5 | **Questions + a day** | agents walk to you with "!" and resume on your answer; jack in and out; closing time and night-shift orders |
| 6 | **Street cred + the first life chapter** | the estate flourishes or overgrows with your declared habits; household chores with owners |
| 7 | **Physical motion** | Rapier in; Flora swims and snaps physically; one agent walks as an active ragdoll that can stumble and recover |
| 8+ | **One life chapter per week** | gym, business, mail…; all agents physical; the river; sound; the public demo build on lotect.com |

**MVP = weeks 1–4.** Everything after is stretch. The river and other showpiece visuals stay out of the MVP; Flora's snap is its signature moment.

---

## 5. Art Direction

Low-poly, flat-shaded, warm palette, soft shadows: a diorama you'd keep on a desk, floating on a dark grid. An elevated 3/4 camera looks over cutaway rooms (after Ralv.ai's estate view). Characters are KayKit (CC0) figures with a hat or color per crew role, and they move physically rather than through canned animation loops (§4.1): a stylized look with real weight and balance. Flora's canal is styled after Xochimilco (slow water, chinampas, a moored trajinera); its lush water and foliage come from one licensed scene kit, kept to one corner so it sits well next to the low-poly figures. Time of day (afternoon, golden hour, blue hour, night) drives the lighting, and night arrives at closing time.

## 6. Risks

| Risk | Mitigation |
|---|---|
| **Scope creep:** mirroring a life is bottomless | one life chapter per week after the MVP; the MVP has four live sources (agents, Flora, mailbox, one repo) |
| **Latency of "real"** | the world never waits; thinking animations; replies as events |
| **Interop friction** in CLJS ↔ three.js | one small wrapper namespace, reviewed carefully |
| **Physical motion is hard** (balance controllers take tuning; ragdolls can jitter) | the motion-controller interface lets any character fall back to simple movement; Flora first as the proof; few characters on screen |
| **Prompt injection** through mail or web content | reader with no keys, localhost bridge, keycards, a weekly red-team pass |
| **Usage caps** on flat-rate plans | few frontier desks; local night crew; short task-scoped sessions |
| **Privacy** on a projector or public build | demo mode shows pretend data only; real mirrors exist only on the player's machine |

## 7. Open Questions

1. Single estate or one per profile (personal vs. work)? *(Lean: one estate, buildings per profile, later.)*
2. Voice in and out, or text only? *(Lean: text for the course; voice is a stretch.)*
3. What exactly counts as "jacked in": game open, focused, or active? *(Lean: focused and active within the last few minutes.)*
4. Snooze when the hardline rings: allowed, and how many times? *(Lean: one five-minute snooze, never louder the second time.)*
5. Does Pepper get her own agent now, or does Hal run business errands until the first sale? *(Lean: Hal's errands until then.)*
6. **Later: the estate learns your life.** Agents notice routines (repeat dry-cleaner receipts) and propose a building ("You go here every other Tuesday. Build it?"); you approve, and it gets a mirror and a chore. *(Vision, not v1.)*
7. **Later: a shared world.** v1 is single-player: your estate is yours (the TRON half: your programs on your machine). The extension is the heart of the Matrix story, where people are in real life versus inside: shared real-world places (a gym many players use) as instanced maps where you see friends' avatars and a few anonymous strangers; never live locations, never a leaderboard, never kids. Twins meet by negotiating with each other ("our humans are both free Thursday"), with both humans approving. It needs a server and moderation, so it comes after v1.
