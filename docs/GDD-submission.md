# Prime Meridian

*Game Design Document, final draft (Assignment 2). Michael Lopez, with Hal Niner. October 2026.*
*This is the short version. The full design, the "gold standard" the agents build from, is [`docs/GDD.md`](https://github.com/xicubed/prime-meridian/blob/main/docs/GDD.md). What changed since the first draft, and why, is §5.*

> "What is even real anymore? Gamifying the agents, a game for all and none."

![Blue hour on the estate: the creek and the cottage](screen-01.jpg)
*Blue hour on the estate: the creek and the cottage (work in progress). Scene: [Hiroshi's Island](https://3ditions.drop3.app/watch/hiroshis-island/) by 3DITIONS (Pixel Palms / Cocotofy LLC), used under license.*

## 1. Executive Summary

*You wake up. At least you think you're awake. Someone had better damn well make some coffee... Your lips pull in the hot liquid as you boot up. Who are you kidding? You don't boot up anymore. Your agents are always on. Let's see what they came up with while you slept.*

*Then you jack in. The estate fades up out of the dark grid. The workshop lights are still on, a stack of blueprints waits on the shelf, and Hal is already walking over with the morning card. A worker who got stuck at 3 a.m. paces under a red "!"; one answer and he's back at his desk. You approve last night's river, and the new water ripples into place. Down at the canal, Flora the axolotl drifts up from the weeds: "I'm hungry." Then the phone booth rings. It's your kid. Time to go.*

**Prime Meridian is a cozy 3D management game where the characters are real AI agents doing real work, and the world sends you back to your life.** You walk a small medium-poly estate in your browser. Its rooms are your projects and your life: a workshop for each code repo, a canal where your real axolotl lives, a mailbox that fills with your real mail, a phone booth where the real world calls in. You talk to the agents, hand them work, answer their questions, and watch the world change as they build it.

Think of the Matrix, TRON, and the OASIS, with one difference: **the OASIS keeps you in while your real life decays. This world nudges you out (time to feed the pets, time to work out) or walks you to the door (bedtime).**

**Why a game, not a terminal?** You could do all of this at a terminal: agents in split panes, logs scrolling past, a wall of text. Prime Meridian **gamifies the same work**. Instead of tailing a log, you walk up to an agent and talk; instead of a status column, you see who's typing, who's stuck, and who's walking toward you with a "!". Same agents, same real results, a natural 3D interface on top, and the fun is the point. Kyle Kukshtel argues any game can be its own agent harness ([*Your Game Is a Harness*](https://kylekukshtel.com/ai-harness-engineering-game-development-claude-code)); here it runs the other way: **the harness is the game**, and getting wrapped up in it is playing.

**The cast**

- **You are Neo.** You jack in, load programs (*"I know kung fu"*), and the whole world turns on you: you know what no program can (what you want, what matters, what "good" looks like), so the agents come to you.
- **Your agent is your digital twin** (for the author, his bot Hal). It's your body in the digital world, always on while you're out: it carries your loadout, runs your errands, and spruces up the half-barren estate. You're its hands in the physical one.
- **The Oracle is the author** (Michael), who designed the path and approves every change. Playing on his own machine, he's both Oracle and Neo. But eventually you become your own Oracle and study the psychology of the humans and bots.
- **The Architect is the architecture:** Hermes running inside the editor (Zed), driving Herdr, which reaches across machines (the Mac where the agent lives, a GPU box for local models and physics) to run the whole crew side by side. In-game the Architect's twin sits in **the Architect's room**, inside a wall of monitors, one per live agent session, and that wall is all he sees. You see the whole game, with him in his room of monitors... like when Dorothy visited Oz: the world he "created" is just a little code, yet it expands out into real life.

*Film references (The Matrix, TRON, Ready Player One, The Wizard of Oz) are shorthand for feelings you already recognize, not the films' characters or lines; the shipped game uses original names and dialogue.*

**Genre:** cozy management / life sim whose cast is real AI agents. **Platform:** the estate renders in your browser (three.js, WebGPU), but you play **everywhere your agent lives**: your editor, phone, mail, calendar, blog, and online accounts (Google, Claude, Hermes, Muse, Twilio…), across your machines. **Audience:** people who run a personal AI agent or want one, coders or not (the same twin orders the weekly groceries); **not** for someone who wants a traditional game with no AI in it.

**Batteries not included.** Prime Meridian isn't a car off the lot; it's a kit car you assemble yourself. The game ships the chassis: the world, the rules, the crew's bodies. You bring the parts: your own agent and AI logins, your tools and accounts, your life. Before you bolt anything in, take a test drive: every connection comes with a sample version (pretend inbox, phone, ledger, scripted agents), so anyone can play with no accounts and swap in the real parts one at a time.

- **Core loop:** *observe the estate → spot who needs you ("!") → talk, assign, or answer → agents work → see the result in the world.*
- **Reverse loop:** a real chore comes due (Flora the axolotl is hungry) → the world calls you out → you do it in real life → the world updates.

**Win / loss**

- **Win the day:** every "!" answered, your own chores done, and you jacked out by bedtime.
- **Win a chapter:** each week a part of life comes online (pets, household, gym, business, mail). You beat it when it runs a full week with you doing only the human parts.
- **No game over.** A missed day leaves the estate a little overgrown, never ruined. No streaks, no shame.
- **The only real loss is in real life:** Flora goes hungry, you miss your kid's game. That's the *Ready Player One* ending, and the game exists to prevent it.
- **The long-term win is needing the game less,** measured two ways that only count together: fewer "!"s and approvals per task, and fewer hours jacked in with chores still done in person. Target: about ten hours a week the first month, about two a year later, shown on a weekly card, never a score.

**Design pillars**

1. **Real, not simulated.** When a worker sits down with a red "!" it's true; when Flora's jar says "2 worms left" it's the count you reported.
2. **Spatial over textual.** From the default camera you can tell who's busy, blocked, or waiting on you before you read a word.
3. **Tell them in the world, not in a terminal.** No "run task" button and no going back to a terminal: you walk up to an agent and talk.
4. **It never adds work.** Every action is real or optional: no pet to feed that isn't real, no login rewards, no streaks. The agents work the same with the game closed, so a developer who never opens it loses only the view.
5. **It sends you back to your life.** It calls you out when life needs you and walks you to the door at bedtime.

## 2. Game Mechanics

### What you do

| Action | What you do and see |
|---|---|
| **Walk & observe** | Your avatar has a body on the estate. Agents type at desks (working), sit (idle), pace with "!" (a question), or walk toward you (urgent). |
| **Talk & assign** | Click an agent, a speech bubble opens. "Make all the rocks procedural." It confirms in one line, takes a desk, gets to work. |
| **Answer** | A stuck agent raises "!" and walks to wherever you are. You answer in-world; it resumes. |
| **Approve** | Changes appear first as a **blueprint**, a ghosted version. Approve and it becomes real; reject and it vanishes. Approvals come grouped (one morning tray, a one-line summary each), and reads never ask. |
| **Hand out a keycard** | An agent that needs a permission asks for a keycard. Every key that's out is visible and comes back when the task ends. |
| **Set autonomy** | A dial per agent, from *ask before anything* to *fully autonomous*; money, deletion, security, and messages to people always need you. No agent can start a trust change, so no "!" ever asks for more autonomy. |
| **Check the juice** | A juice meter per AI login on the HUD: how much of the plan's usage window is left and when it refills. Your twin warns you before a cap, so you never check usage in a browser. |
| **Mark a chore done** | Reply "fed" to a text, click the canal, or tell your agent. Every channel sends the same event, and only one counts per feeding window (no double worms); texts count only from known numbers. |

### Signature moments

1. **The snap.** You mark Flora fed. A worm drops from the dock, Flora shoots up from the weeds, *snap*, a swirl of mud, the hungry bubble pops, and the worm jar shows one fewer.
2. **"I know kung fu."** Skills, tools, and MCP servers are Neo's training programs: you load one, your eyes snap open, your twin can suddenly do what it couldn't a moment ago, and its building rises on the estate.

### A day is one round

1. **Jack in.** "Welcome back." See what changed overnight.
2. **Play.** Answer the "!"s, approve blueprints, assign work, tend your creatures.
3. **Get jacked out when life needs you.** The phone booth rings and the world freezes gently. Priority: **household** ("Dinner!") → **creatures** (Biscuit at the door with his leash) → **appointments** (your kid's game at 6, so leave by 5:35) → **deadlines**. Everything else only rings.
4. **Put in night-shift orders** before closing. A few desks run frontier models; a local night crew grinds the rest for free. Choosing what gets the frontier desks is the day's real decision.
5. **Closing time.** A rule you set ("you asked me to send you home"), not a lock: you can stay, but your twin walks you to the door and asks once, and a day you stay late isn't won. Bedtime is declared data, so a night-shift nurse's estate closes at 9 am.

### Your life as data ("everyone's game")

The code has no idea what an axolotl is. Creatures, chores, and businesses are **declared data**, and chores belong to people:

```clojure
{:id :flora :kind :creature :model :axolotl :home :canal
 :rules  [{:when [:due? :feed] :say "I'm hungry"} {:when [:<= :stock 2] :sign "2 worms left!"}]
 :chores [{:id :feed :every "2d" :at "17:00" :who :me :consumes [:stock 1]}]}
```

The same code path runs a horse in a barn with a hay counter. Pets can also be **virtual** (kept around with no needs) or **memorial**: every dog you've ever had can still lie in the sun on the porch, needing nothing. When a live pet dies, only you move it to memorial: its chores stop counting toward your day, and your twin says so kindly and truthfully. You're only jacked out for **your** chores; household members are never agents... in v1. **Someday it goes multiplayer:** each person in the house brings their own twin, and the twins meet, trade chores, and negotiate the shared calendar ("our humans are both free Thursday"), with every human approving.

### Progression: no XP

- **Street cred (yours).** Habits you declared, kept: the estate flourishes and new buildings appear. Slip, and it gets a bit overgrown. No number, no leaderboard, and walking the dog counts.
- **Loadout (yours, carried by your twin).** Every skill or connector you load is a program, and a building.

### The first hour: the Construct

Neo's first day out of the pod. White screen. Your digital twin steps out of nowhere, playing the Morpheus part: *"This is the white room. Nothing in here is real yet."* A rack of programs scrolls past like the film's weapons racks, but your first one is chosen for you: **your own agent**, the red pill that makes the game real. It slams in and your eyes snap open (*"Felt that? That's me, the real me, plugged in."* *"Prove it."* *"Watch."*). The rest of the rack (mail, GitHub, Blender) loads later from a vetted list, each starting as its sample version. Then the sparring match: your twin hands a worker a practice job ("make that rock rounder"), the worker (wearing the sample badge, so even this "!" is honest) runs to you, you answer, a blueprint appears, you approve, and the rock changes. **Five minutes in, you've played the whole game.** The phone rings with a pretend chore; you jack out and back in. The white dissolves into your half-barren estate: *"I can open the door. Walking through it is your part."* Original lines (the film is only the beat sheet), built the week of Nov 16. ([full Construct](https://github.com/xicubed/prime-meridian/blob/main/docs/GDD.md#27-the-first-hour-the-construct))

### New players: zero accounts to your own

On your estate **you are the Oracle**: every approval is yours. Demo mode makes the whole loop playable with zero accounts. The first real program is your own agent (v1: Hermes), and each further connection is a quest your twin runs: it does the paperwork, hands you the links to click, and explains each consent screen in a line (the author's bot filed a phone number's federal texting registration this way). No two twins are alike, so your twin onboards itself; a connection you can't make stays sample with no penalty, and every swap to real is previewed first.

## 3. AI Architecture

**The twin rule.** Every character is the body of something real. Your agent's bubble is its live session; workers are real coding sessions; Flora is a real axolotl. In demo mode the counterpart is clearly pretend, never a fake passed off as real. An agent raises "!" only by calling an explicit `ask_player` tool, and every "!" carries its source (which agent, which session), so **a "!" always means a real question** and one from an unknown process is refused.

| Agent | Player sees (effect on play) |
|---|---|
| **Your agent: Hal** (your digital twin) | You speak; Hal nods, pins a card on the notice board, and a worker walks out to a workshop desk. His scheduled errands are visible too: the 8:15 morning brief pinned on the board, answering the phone booth when you text. |
| **Keeper** (an errand of your agent) | Reads each creature's state: an "I'm hungry" bubble over Flora, a "2 worms left!" sign on the jar. |
| **Workers** (one per task, each a persistent crew persona) | Typing at a desk with a progress glow (*reading → writing → tests passing*), then carrying a finished blueprint to the shelf. |
| **Clerk** (mail reader, holds **no keys**) | The mailbox fills and overflows; click it for a short triage summary. |
| **Pepper** (business manager) | At her front-office desk; when something needs your signature, a folder appears and she walks it over. |
| **Night crew** (local models) | Workshop lights on at night; a stack of blueprints at breakfast. |

**Guardrails that shape play.** Purchases and messages to other people always need you. The agent that reads untrusted mail holds no credentials, and its summaries go to you, never to an agent holding keycards. Keycards grant one permission for one task and time out; money, deletion, and security changes always need you. The game learns your life only from what you connect (receipts, calendar), never from location tracking. Your agent coaches with your own words ("You said three workouts a week. One today?") and never shames or compares.

## 4. Technical Strategy

**Stack.** three.js 0.186 (`WebGPURenderer`, WebGL2 fallback) · ClojureScript via shadow-cljs (one immutable world map, hot reload) · a Babashka websocket bridge on the player's machine · agents on Hermes (your agent and its errands), Claude Code (the dev crew), and local open-weight models (the night crew) · **the Architect:** Hermes in Zed driving Herdr across a Mac and a GPU box; machines are declared data, so one laptop also works. **Motion:** in the MVP characters walk on scripted (kinematic) paths through one motion-controller interface, and "!" is a static marker. Physical motion comes later through the same interface: Rapier active ragdolls that stumble and recover, and Flora swimming as a physical spine.

**The bridge** speaks four events (agent → you `question`, you → agent `answer`, real life → world `chore-done`, life → you `jack-out`), listens only on localhost with a pairing code, and marks an agent **stalled** on its own when it stops reporting (say, its usage cap ran out), so nothing shows busy forever. Notices from the game itself (a trust suggestion, the weekly card) look different from an agent's "!" and use a channel no agent can reach.

### Agent roles: the dev crew

The agents that **build** the game. Each is a persistent persona on a fresh, task-scoped session, visible as a worker in the game's own workshop, editing only the files it owns. **The Director (Michael, human; the Oracle) approves every change.**

| Agent | Development role, in one sentence | Produces (format) |
|---|---|---|
| **The Hero or Heroine (Hal) ★ one wow** | Your twin, the star of the digital world: takes your requests as quests, splits them into tasks, and leads the right crew member into each one. | task cards in `TASKS.md` (Markdown), merges each crew branch into `main`, and the daily dev log `docs/session-<date>.md` |
| **Lead Architect** | Decides how the pieces fit together whenever a change touches more than one area. | the world-state shape and the bridge event schema (EDN), and short decision notes in the GDD (Markdown) |
| **Game Designer** | Turns a feature idea into rules and estate content. | GDD sections (Markdown) and estate data: rooms, mirrors, chores (`resources/estate.edn`) |
| **Writer** | Writes your twin's voice, the status bubbles, and lines like "2 worms left!". | line tables keyed by event (EDN), plus the Construct script (Markdown) |
| **Engine Engineer** | Extends the three.js wrapper, camera, lighting, and physics, and keeps the frame rate steady. | ClojureScript in the one wrapper namespace (`pm.three`), compiling with 0 warnings |
| **Gameplay Engineer** | Codes world rules, movement, and interactions like agents walking over with "!". | pure ClojureScript functions in `pm.world` with unit tests |
| **Bridge Engineer** | Connects real data and agent sessions so statuses match reality. | the Babashka bridge (`bridge.clj`) and the sample (pretend) connections, speaking the four events as JSON over a websocket |
| **Environment Artist** | Reshapes and re-lights the estate when you ask in-game, so you watch that spot change. | shader and scene changes (ClojureScript / TSL) shown as a blueprint with before/after screenshots |
| **Character Artist** | Builds and animates the figures, starting with Flora's snap (with a human 3D artist). | rigged characters (GLB) and motion settings per character (EDN) |
| **Tech Artist** | Slims purchased 3D kits so they look rich without dropping the frame rate. | decimated, level-of-detail models (GLB) and a triangle and frame-time report (Markdown) |
| **Sound Designer** | Places positional audio, like the river getting louder as you walk toward it. | sound emitters placed in the estate (EDN) and audio loops (OGG) with their licenses |
| **QA Tester** | Runs the build, checks the live HUD, and blocks any change that fails. | a pass/fail gate note on every change (Markdown): compile warnings, HUD readout, what it checked |
| **Red Team ("Cypher")** | Tries to turn the crew first (bait mail, poisoned tools) against pretend connections only. | each hole found as a task in `TASKS.md`, then a regression test |
| **Asset Scraper** | Fetches free models and sounds for the crew. | the files plus a license manifest, one entry per asset with source and license (EDN) |
| **Business Manager (Pepper)** | Keeps the company ready to sell the game: payments, store page, policies. | the release checklist and store-page copy (Markdown), and a read-only sales summary |

**One wow: your digital twin (Hal).** You load a program and watch your twin carry it out into the half-barren estate, which gains the building that goes with it. **v1 staffs five:** the Hero, Lead Architect, Gameplay Engineer, Environment Artist, QA. The rest join when their milestone needs them.

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

**Limits are part of the world.** Every multi-agent setup hits caps and quotas, so an agent nearing one parks its step, says so ("almost out of juice, parking this at step 3"), hands back its keycards, and rests: it bows out gracefully instead of failing. The bridge shows each login's usage as the juice meter (estimated from the tokens it sees in v1).

**API constraints:** **Claude plans:** usage caps per window → few frontier desks, a local night crew, short sessions · **Gmail:** OAuth and rate quotas → the Clerk reads on a schedule, never per frame · **SMS / iMessage:** carrier registration covers opted-in people only, and iMessage has no API → the bot texts only the player; a local watcher on the Mac · **Stripe:** live keys move money → read-only for Pepper · **Model output:** non-deterministic → blueprints, keycards, and the QA gate catch a bad path before it's real.

### Scope (one person, through Nov 24)

**One person** (Michael) directs five agents in the evenings and around the Tue/Thu classes. The course ends **Nov 24** and nothing past the scaffold is built yet, so the MVP is the plan. **Done:** GDD + scaffold; two drafts and a review board · **Week of Oct 26:** estate v0 on sample connections (so the MVP is buildable, demoable, and gradable **without a single real account**, and testing burns no quotas): three mock workers on scripted paths, a static "!", HUD · **Nov 2:** bridge + **the snap**: the four events working, your agent's real status, Flora's worm count, stalled state · **Nov 9: the whole loop (MVP):** talk → answer → approve → the world changes, jack in/out, closing time, the juice meter · **Nov 16:** the Construct + demo build · **Nov 24:** final presentation. **Deferred past Nov 24, in writing:** walking "!", street cred, life chapters, real connectors beyond your own agent, physics. **The constraints that set the pace:** his evenings and the plan's usage caps. **Cut line:** harness work counts only when that week's build shows it on the estate.

## 5. What Changed, and Why

The first draft ([`gdd-draft-1`](https://github.com/xicubed/prime-meridian/blob/gdd-draft-1/docs/GDD-submission.md)) went through three reviews. Full tables: [GDD §8](https://github.com/xicubed/prime-meridian/blob/main/docs/GDD.md#8-what-changed-in-the-final-draft-and-why).

**The crew (agent stress test).** Six reviewer agents (systems, narrative, player psychology, feasibility, adversarial QA, business) reviewed the draft, cross-examined each other, and a moderator synthesized **31 findings, 11 BLOCKING** ([synthesis](https://github.com/xicubed/prime-meridian/blob/main/docs/reviews/2026-10-17-draft1/SYNTHESIS.md)). Fixed right after the board: the MVP re-cut to close the whole loop · a forgeable "!" (now `ask_player` only, with a source on every event) · the unspecified bridge (four events, a stalled state, keycards that time out) · a dead pet still saying "I'm hungry" (memorial mode) · double-counted chores · ragdolls in the MVP (kinematic first). The board escalated five questions, decided here:

**Lock or nudge?** A wellness game that runs your work; closing time is a nudge you set, staged in-world · **Can the game suggest more autonomy?** No agent can start a trust change; track records on the board, never a "!" · **What does "needing the game less" measure?** Both: fewer "!"s per task *and* fewer hours jacked in · **Only the author is designed for:** you're the Oracle of your own estate; the novice path · **The Construct now or later?** v1 onboarding the week of Nov 16, one curated first program, original lines.

**Humans (the class Discord).** The agents caught safety and scope problems; the humans caught who the game is *for*.

| Who | What they caught | What changed |
|---|---|---|
| Jaime | How hard is setup for a less technical player? | the novice path: zero accounts to start, your twin runs each connection as a quest |
| David | "Play it less" is a developer's goal; it reads as a gamified agent manager | says plainly who it's for and who it isn't, and that it isn't only for coders |
| David | Show AI usage in the game, not in a browser | the juice meter, in the MVP |
| David | Anything that adds time to real work is a cost | pillar 4: it never adds work |
| Dylan, echoing Joshua | "This guitar is a terrible saxophone": the harness may get built before the game | a cut line in the scope: harness work counts only when it shows on the estate |

**My own calls.** The harness is the game: I like getting wrapped up in it, so I made it the game · the time curve (ten hours a week to two) is the long-term win · everyone's twin is different, so it onboards itself · direct the agents in the world, or the view is just a screensaver · the schedule is re-dated to Nov 24: nothing past the scaffold is built, so the MVP is the plan.
