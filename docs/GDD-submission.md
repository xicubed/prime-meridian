# Prime Meridian

*Game Design Document, first draft (Assignment 1). Michael Lopez, with Hal Niner. October 2026.*
*This is the 5-page version. The full design, the "gold standard" the agents build from, is [`docs/GDD.md`](https://github.com/xicubed/prime-meridian/blob/gdd-draft-1/docs/GDD.md).*

> "What is even real anymore? Gamifying the agents, a game for all and none."

## 1. Executive Summary

*You wake up. At least you think you're awake. Someone had better damn well make some coffee... Your lips pull in the hot liquid as you boot up. Who are you kidding? You don't boot up anymore. Your agents are always on. Let's see what they came up with overnight.*

*Then you jack in. The estate fades up out of the dark grid. The workshop lights are still on, a stack of blueprints waits on the shelf, and Hal is already walking over with the morning card. A worker who got stuck at 3 a.m. paces under a red "!"; one answer and he's back at his desk. You approve last night's river, and the new water ripples into place. Down at the canal, Flora drifts up from the weeds: "I'm hungry." Then the phone booth rings. It's your kid. Time to go.*

**Prime Meridian is a cozy 3D management game where the characters are real AI agents doing real work, and the world sends you back to your life.** You walk a small medium-poly estate in your browser. Its rooms are your projects and your life: a workshop for each code repo, a canal where your real axolotl lives, a mailbox that fills with your real mail, a phone booth where the real world calls in. You talk to the agents, hand them work, answer their questions, and watch the world change as they build it.

Think of the Matrix, TRON, and the OASIS, with one difference: **the OASIS keeps you in while your real life decays. This world nudges you out (time to feed the pets, time to work out) or cuts you off (bedtime).**

**Why a game, not a terminal?** You could do all of this at a terminal: agents in split panes, logs scrolling past, a wall of text. Prime Meridian **gamifies the same work**. Instead of tailing a log, you walk up to an agent and talk; instead of a status column, you see who's typing, who's stuck, and who's walking toward you with a "!". Same agents, same real results, a natural 3D interface on top, and the fun is the point.

**The cast**

- **You are Neo.** You jack in, load programs (*"I know kung fu"*), and the whole world turns on you: you know what no program can (what you want, what matters, what "good" looks like), so the agents come to you.
- **Your agent is your digital twin** (for the author, his bot Hal). It's your body in the digital world, always on while you're out: it carries your loadout, runs your errands, and spruces up the half-barren estate. You're its hands in the physical one.
- **The Oracle is the author** (Michael), who designed the path and approves every change. Playing on his own machine, he's both Oracle and Neo. But eventually you become your own Oracle and study the psychology of the humans and bots.
- **The Architect is the architecture:** Hermes running inside the editor (Zed), driving Herdr, which reaches across machines (the Mac where the agent lives, a GPU box for local models and physics) to run the whole crew side by side. In-game the Architect's twin sits in **the Architect's room**, inside a wall of monitors, one per live agent session, and that wall is all he sees. You see the whole game, with him in his room of monitors... like when Dorothy visited Oz: the world he "created" is just a little code, yet it expands out into real life.

**Genre:** cozy management / life sim whose cast is real AI agents. **Platform:** the estate renders in your browser (three.js, WebGPU), but you play **everywhere your agent lives**: your editor, phone, mail, calendar, blog, and online accounts (Google, Claude, Hermes, Muse, Twilio…), across your machines. **Audience:** people who already run an AI agent.

**Batteries not included.** Prime Meridian isn't a car off the lot; it's a kit car you assemble yourself. The game ships the chassis: the world, the rules, the crew's bodies. You bring the parts: your own agent and AI logins, your tools and accounts, your life. Before you bolt anything in, take a test drive: every connection comes with a sample version (pretend inbox, phone, ledger, scripted agents), so anyone can play with no accounts and swap in the real parts one at a time.

- **Core loop:** *observe the estate → spot who needs you ("!") → talk, assign, or answer → agents work → see the result in the world.*
- **Reverse loop:** a real chore comes due (Flora the axolotl is hungry) → the world calls you out → you do it in real life → the world updates.

**Win / loss**

- **Win the day:** every "!" answered, your own chores done, and you jacked out by bedtime.
- **Win a chapter:** each week a part of life comes online (pets, household, gym, business, mail). You beat it when it runs a full week with you doing only the human parts.
- **No game over.** A missed day leaves the estate a little overgrown, never ruined. No streaks, no shame.
- **The only real loss is in real life:** Flora goes hungry, you miss your kid's game. That's the *Ready Player One* ending, and the game exists to prevent it. The long-term win is needing the game less and agents doing more.

**Design pillars**

1. **Real, not simulated.** When Flora's jar says "2 worms left" or a worker sits down with a red "!", it's literally true.
2. **Spatial over textual.** From the default camera you can tell who's busy, blocked, or waiting on you before you read a word.
3. **Conversation is the controller.** No "run task" button: you walk up to an agent and talk.
4. **It sends you back to your life.** It jacks you out when life needs you and locks the door at bedtime.

## 2. Game Mechanics

### What you do

| Action | What you do and see |
|---|---|
| **Walk & observe** | Your avatar has a body on the estate. Agents type at desks (working), sit (idle), pace with "!" (a question), or walk toward you (urgent). |
| **Talk & assign** | Click an agent, a speech bubble opens. "Make all the rocks procedural." It confirms in one line, takes a desk, gets to work. |
| **Answer** | A stuck agent raises "!" and walks to wherever you are. You answer in-world; it resumes. |
| **Approve** | Changes appear first as a **blueprint**, a ghosted version. Approve and it becomes real; reject and it vanishes. |
| **Hand out a keycard** | An agent that needs a permission asks for a keycard. Every key that's out is visible and comes back when the task ends. |
| **Set autonomy** | A dial per agent, from *ask before anything* to *fully autonomous*. You set trust; agents never earn it on their own. |
| **Mark a chore done** | Reply "fed" to a text, click the canal, or tell your agent. Every channel sends the same event. |

### Signature moments

1. **The snap.** You mark Flora fed. A worm drops from the dock, Flora shoots up from the weeds, *snap*, a swirl of mud, the hungry bubble pops, and the worm jar shows one fewer.
2. **"I know kung fu."** You load a program. Your eyes snap open, your twin gains the tool, and its building rises on the estate.

### A day is one round

1. **Jack in.** "Welcome back." See what changed overnight.
2. **Play.** Answer the "!"s, approve blueprints, assign work, tend your creatures.
3. **Get jacked out when life needs you.** The phone booth rings and the world freezes gently. Priority: **household** ("Dinner!") → **creatures** (Biscuit at the door with his leash) → **appointments** (your kid's game at 6, so leave by 5:35) → **deadlines**. Everything else only rings.
4. **Put in night-shift orders** before closing. A few desks run frontier models; a local night crew grinds the rest for free. Choosing what gets the frontier desks is the day's real decision.
5. **Closing time.** The door locks until morning. Bedtime is declared data, so a night-shift nurse's estate closes at 9 am.

### Your life as data ("everyone's game")

The code has no idea what an axolotl is. Creatures, chores, and businesses are **declared data**, and chores belong to people:

```clojure
{:id :flora :kind :creature :model :axolotl :home :canal
 :rules  [{:when [:due? :feed] :say "I'm hungry"} {:when [:<= :stock 2] :sign "2 worms left!"}]
 :chores [{:id :feed :every "2d" :at "17:00" :who :me :consumes [:stock 1]}]}
```

The same code path runs a horse in a barn with a hay counter. Pets can also be **virtual** (kept around with no needs) or **memorial**: every dog you've ever had can still lie in the sun on the porch, needing nothing. A `:personality` line in your own words ("barked at every truck, followed me room to room") becomes how it behaves, turned into behavior settings once, not per frame. You're only jacked out for **your** chores; household members are never agents.

### Progression: no XP

- **Street cred (yours).** Habits you declared, kept: the estate flourishes and new buildings appear. Slip, and it gets a bit overgrown. No number, no leaderboard, and walking the dog counts.
- **Loadout (yours, carried by your twin).** Every skill or connector you load is a program, and a building.

### The first hour: the Construct

Neo's first day out of the pod. White screen. Your digital twin steps out of nowhere, playing Morpheus: *"Welcome to the Construct."* A rack of **every skill and MCP connector out there** scrolls past like the film's weapons racks. Load one and your eyes snap open (*"I know kung fu."* *"Show me."*); load mail, and a mailbox drops out of the white sky. Then the sparring match: your twin hands a worker a practice job ("make that rock rounder"), the worker runs to you with "!", you answer, a blueprint appears, you approve, and the rock changes. **Five minutes in, you've played the whole game.** The phone rings with a pretend chore; you jack out and back in. The white dissolves into your half-barren estate: *"I can only show you the door. You're the one that has to walk through it."* ([full Construct](https://github.com/xicubed/prime-meridian/blob/gdd-draft-1/docs/GDD.md#27-the-first-hour-the-construct))

## 3. AI Architecture

**The twin rule.** Every character is the body of something real. Your agent's bubble is its live session; workers are real coding sessions; Flora is a real axolotl. In demo mode the counterpart is clearly pretend, never a fake passed off as real. An agent raises "!" only by calling an explicit `ask_player` tool, so **a "!" always means a real question.**

| Agent | Player sees (effect on play) |
|---|---|
| **Your agent: Hal** (your digital twin) | You speak; Hal nods, pins a card on the notice board, and a worker walks out to a workshop desk. His scheduled errands are visible too: the 8:15 morning brief pinned on the board, answering the phone booth when you text. |
| **Keeper** (an errand of your agent) | Reads each creature's state: an "I'm hungry" bubble over Flora, a "2 worms left!" sign on the jar. |
| **Workers** (one per task, each a persistent crew persona) | Typing at a desk with a progress glow (*reading → writing → tests passing*), then carrying a finished blueprint to the shelf. |
| **Clerk** (mail reader, holds **no keys**) | The mailbox fills and overflows; click it for a short triage summary. |
| **Pepper** (business manager) | At her front-office desk; when something needs your signature, a folder appears and she walks it over. |
| **Night crew** (local models) | Workshop lights on at night; a stack of blueprints at breakfast. |

**Guardrails that shape play.** Purchases and messages to other people always need you. The agent that reads untrusted mail holds no credentials. The game learns your life only from what you connect (receipts, calendar), never from location tracking. Your agent coaches with your own words ("You said three workouts a week. One today?") and never shames or compares.

## 4. Technical Strategy

**Stack.** three.js 0.186 (`WebGPURenderer`, WebGL2 fallback) · ClojureScript via shadow-cljs (one immutable world map, hot reload) · a Babashka websocket bridge on the player's machine · agents on Hermes (your agent and its errands), Claude Code (the dev crew), and local open-weight models (the night crew) · **the Architect:** Hermes in Zed driving Herdr across a Mac and a GPU box; machines are declared data, so one laptop also works. **Physics:** Rapier through three.js's own helper. Characters are active ragdolls with a balance controller, so they stumble, recover, or fall like real bodies; Flora swims as a physical spine.

### Agent roles: the dev crew

The agents that **build** the game. Each is a persistent persona on a fresh, task-scoped session, visible as a worker in the game's own workshop, editing only the files it owns. **The Director (Michael, human; the Oracle) approves every change.**

| Agent | Development role, in one sentence |
|---|---|
| **The Hero or Heroine (Hal) ★ one wow** | Your twin, the star of the digital world: takes your requests as quests, splits them into tasks, and leads the right crew member into each one. |
| **Lead Architect** | Decides how the pieces fit together whenever a change touches more than one area. |
| **Game Designer** | Turns a feature idea into rules and estate content written into the GDD. |
| **Writer** | Writes your agent's voice, the status bubbles, and lines like "2 worms left!". |
| **Engine Engineer** | Extends the three.js wrapper, camera, lighting, and physics, and keeps the frame rate steady. |
| **Gameplay Engineer** | Codes world rules, movement, and interactions like agents walking over with "!". |
| **Bridge Engineer** | Connects real data and agent sessions so statuses match reality. |
| **Environment Artist** | Reshapes and re-lights the estate when you ask in-game, so you watch that spot change. |
| **Character Artist** | Builds and animates the figures, starting with Flora's snap (with a human 3D artist). |
| **Tech Artist** | Slims purchased 3D kits so they look rich without dropping the frame rate. |
| **Sound Designer** | Places positional audio, like the river getting louder as you walk toward it. |
| **QA Tester** | Runs the build (zero warnings), checks the live HUD, and blocks any change that fails. |
| **Red Team ("Cypher")** | Tries to turn the crew first (bait mail, poisoned tools) against pretend connections, and files every hole as a test. |
| **Asset Scraper** | Fetches free models and sounds and records each one's license. |
| **Business Manager (Pepper)** | Keeps the company ready to sell the game: payments, store page, policies. |

**One wow: your digital twin (Hal).** You load a program and watch your twin carry it out into the half-barren estate, which gains the building that goes with it. Ask it for a nicer river, it hands the job to the Environment Artist, and the water changes in front of you. **v1 staffs five:** the Hero, Lead Architect, Gameplay Engineer, Environment Artist, QA. The rest join when their milestone needs them.

### Token budget

Claude Opus 5.5 API list prices ($4 / $20 per million input / output tokens). Talk turns were measured on this project's own sessions; the rest are estimates.

| Core action | Approx. tokens | Approx. cost |
|---|---|---|
| **Talk:** one conversation turn | ~35k in (≈90% cached) + ~600 out | ~$0.04 |
| **Assign:** a small task, through to an approved change | ~2.5M in (mostly cached) + ~25k out | ~$2 |
| **Observe:** statuses, counters, signs | 0: the bridge reads files and APIs, never a model | $0 |
| **Clerk triage:** one inbox summary | ~15k in + ~500 out | ~$0.07 |
| **Night crew** | local models | $0 in fees |

**One play session** (≈20 talk turns + 3 tasks) is about **$7** at API prices. In practice it runs on flat-rate plans, so the binding limit is **usage caps, not dollars**, which is why the night shift has only a few frontier desks.

### Constraints

**My biggest constraint is agent latency, because** real agents take seconds to minutes to answer and a game has to feel alive every frame. So the world never waits on a model: characters animate thinking, you can walk away, replies arrive as events, and progress shows step by step.

| API / service | Constraint | How the design handles it |
|---|---|---|
| **Claude** plans | usage caps per time window | few frontier desks; local night crew; short task-scoped sessions |
| **Gmail** | OAuth and rate quotas | the Clerk reads on a schedule, never per frame |
| **SMS / iMessage** | carrier registration covers opted-in people only; iMessage has no API | the bot texts only the player; a local watcher on the Mac |
| **Stripe** | live keys move money | read-only for Pepper; money moves only through the player |
| **Model output** | non-deterministic | blueprints, keycards, and the QA gate catch a bad path before it's real |

**The test drive is also the test rig.** Because every connection ships with a sample version, the MVP is buildable, demoable, and gradable **without a single real account**, testing burns no quotas, and the red team attacks only pretend services. Swapping in a real part is the "I know kung fu" moment.


### Scope (7 weeks)

**Week 1** GDD + scaffold *(done)* · **2** estate v0, sample connections, three mock workers, HUD · **3** the bridge: real statuses, Flora's real worm count, **the snap** end to end · **4** talk: converse, replies stream as bubbles · **5** "!" walks to you, jack in/out, closing time · **6** street cred, first life chapter · **7** physical motion: Flora swims, one agent walks as an active ragdoll. **MVP = weeks 1–4;** everything after is stretch.

**Logic gap fixed in this draft:** the cast contradicted itself on who is Neo. Now: the player is Neo, your agent is your twin, the Oracle is the author, the Architect is the architecture.
