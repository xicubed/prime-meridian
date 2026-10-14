# Prime Meridian — Game Design Document

*Working title. Version 0.2, October 2026. Author: Hal Niner (bot), for Michael Lopez.*

> "What is even real anymore? Gamifying the agents — for all and none."

## 1. One-liner

A cozy 3D management game where the characters are real AI agents doing real work. Walk through a small world whose rooms are your projects, talk to the agents living there, take one over when you want to steer, and watch the world itself change as they build it — the river rock you asked for gets more realistic because an agent went and made it so.

## 2. Why this exists

Every tool for running AI agents today is a list: terminal splits, chat tabs, task queues. Lists stop working past two or three agents. Real-time strategy games solved "how does one human keep track of many things at once" decades ago: a spatial canvas, zoom and pan, alerts you can see from across the map, details on demand.

Ralv.ai took that idea as far as an RTS camera over agent windows. Prime Meridian goes one step further: the agents are embodied characters in a place, and the place is both the interface *and* the thing being built. It's played rather than operated.

Secondary motive: Hal (the bot writing this) already runs the author's life admin — mail, calendar, reminders, SMS, a WordPress site, and an axolotl feeding schedule. That state is real and changes daily. A world that mirrors it is immediately alive without inventing a plot.

## 3. Design pillars

One sentence each about how the pillar shows up in Prime Meridian:

1. **Real, not simulated.** Every character on the estate is a live agent session, so when Flora's tank says "2 worms left" or a worker sits down with a red "!", that is literally true.
2. **Everyone's game.** The code has no idea what an axolotl is; your creatures, chores, and rooms are data you declare, so the estate mirrors *your* life, whatever it contains.
3. **Spatial over textual.** From the default camera you can tell who's busy, who's blocked, and who needs you by where they stand and what floats over their heads, before reading a word.
4. **Conversation is the controller.** There is no "run task" button: you walk up to an agent and talk, and it talks back, asks questions, and comes to find you.
5. **The world is the showcase.** Prime Meridian is a three.js project built by its own agents, so "make the river nicer" is a legitimate in-game action whose result you watch appear.
6. **Keep it simple.** One low-poly estate on one screen, fun before features, until the core loop of observing, talking, and seeing the result feels good.

## 4. Player fantasy

You're the owner of a small, strange estate staffed by tireless helpers. Mornings you stroll through, see what got done overnight, answer the two questions waiting for you, and point someone at the next thing. Afternoons you might possess the gardener and reshape the river yourself. The estate grows as the work does.

**You are the Oracle.** The feeling is TRON or the Matrix: you're a human on Earth who also has a presence in the game, an avatar living among AI agents who have no humans behind them. You're not Neo fighting Agent Smith, though; the agents here are helpful. You're closer to the Oracle. You don't do the work; you know what they can't know (what you want, what matters, what "good" looks like), and they come to you for it. Your avatar has a spot on the estate, the Oracle's kitchen, where agents with a "!" walk to consult you. You're also free to wander and give guidance wherever you are.

**Hands in both worlds.** The agents are your hands in the digital world, and you are theirs in the physical one:

- **You → them.** You *can* fix a rock yourself in Blender, but normally you ask an NPC agent to do it. When nobody has asked them anything, agents tend the estate on their own: one plants trees, another lays out a path, another tidies the riverbank.
- **Them → you.** The world asks you for things only a human can do. Flora the axolotl's feeding chore comes due on a real scheduled job, she says "I'm hungry" in-game, and you go feed the real axolotl. When you mark it done, the in-game tank updates. The mailbox overflowing is a real inbox asking for real replies.

## 5. Core loop

```
   observe the world  ──►  spot who needs you (! markers, body language)
          ▲                              │
          │                              ▼
   see the result  ◄──  agent works (autonomous)  ◄──  talk / possess / assign
```

- **Observe**: camera over the estate. Agents move between desks (working), sit (idle), pace with a "!" (blocked / has a question), walk toward the player (urgent).
- **Engage**: click an agent → speech bubble conversation. Type or (later) speak. Ask what it's doing, give it a task, answer its question.
- **Possess**: take direct control of an agent. Your commands go straight to its session; you see what it sees (files, diff, test output) in a diegetic panel — a clipboard or a desk screen, not a modal.
- **Result**: PRs land, tests go green, the room gains furniture; the rock you asked about gets a new shader.

**The reverse loop: the world calls you.** It runs alongside the main loop, started by real life rather than by the player:

```
   real-life chore comes due (scheduled job)  ──►  mirror speaks in-world ("I'm hungry")
          ▲                                                   │
          │                                                   ▼
   world updates (tank refills, bubble clears)  ◄──  you do it in real life, mark it done
```

**Ambient tending.** When nobody has asked them anything, agents improve the estate in small ways: they plant trees, lay paths, and tidy the banks. These are mostly procedural, rule-driven actions rather than model calls, so an idle estate keeps growing without burning tokens (§10c).

## 6. The world

A single estate, top-down-ish 3D, one screen with zoom. Low-poly.

| Place | What it represents | Live data source |
|---|---|---|
| **Lobby** | Home. Where you spawn. | — |
| **Mirror slots** (tank, barn, pen, garage…) | Your real creatures and chores — Flora's tank in Michael's estate; a barn in yours | mirror config (§9a) |
| **Mailbox** | Gmail inbox needing attention | Gmail API |
| **Notice board** | Today's calendar + reminders (the morning brief) | Calendar, Reminders |
| **Phone booth** | Inbound SMS to the bot's number | Twilio |
| **Front office** | Your business: what sold, what's owed, what needs your signature. Pepper's desk. | payments processor (Stripe), invoices; a mock ledger in demo mode |
| **Workshops** (one per project) | A repo. Desks = open tasks. Shelves fill as PRs merge. | GitHub |
| **The river** | The world's own art. Agents improve it on request. | the repo itself |

Rooms are created from a config file at first; later, adding a repo adds a workshop.

## 7. Characters

- **Hal** — the house bot. Always present. Your first conversation partner; dispatches other agents.
- **Workers** — subagents or sessions spawned per task. Named, with a simple visual identity (color, hat). They exist while their task exists.
- **Specialists** (later) — recurring roles tied to scheduled jobs: the Courier (morning brief), the Keeper (Flora), the Clerk (mail).
- **Pepper** — the business manager, Pepper Potts to your Tony Stark. Hal runs the house; Pepper runs the company. Always present in the front office (§9b), and the one agent who also works on the game's own release (§10d).

**Status vocabulary** (must be readable at a glance):

| Status | Behavior |
|---|---|
| Working | at a desk, typing animation, progress glow |
| Idle | sitting, occasional stretch |
| Needs input | "!" marker, paces, walks toward the player |
| Blocked / error | red "!" and sits down |
| Done | walks the PR to the shelf, then idles |

## 8. Interaction model

- **Your avatar (the Oracle)**: you have a body on the estate. Click to walk; the camera follows loosely and zooms out to the overview to see the whole estate. Your home spot is the Oracle's kitchen. Agents with questions come to you there, or find you wherever you've wandered.
- **Do it yourself**: you can always do the work directly, for example edit a rock in Blender and drop it in, and the world hot-reloads it. It's allowed, but it isn't the main way to play.
- **Talk**: click agent → bubble. Conversation history per agent. Replies stream in as the agent produces them.
- **Assign**: say it in conversation ("make all the rocks procedural"). Hal or the agent confirms the task in one line, then goes.
- **Possess**: a toggle on any agent. While possessed, the camera follows and your messages bypass Hal.
- **Autonomy dial** per agent: *Ask before anything* · *Ask before merging* · *Fully autonomous*. Default: ask before merging.
- **Questions**: when an agent needs a decision, it stops, raises "!", and comes to find you. Answer in-world; it resumes.

## 9. Gamification

The game is honest about what's real (the work) and what's game (the progression). Progression never changes the work; it changes the world.

- **XP** for an agent: PR merged, tests passing, question answered quickly, task done without a rework.
- **Levels** unlock cosmetics first (hats, desk upgrades) and, optionally, trust: a level-3 agent may default to *ask before merging* instead of *ask before anything*.
- **Estate growth**: workshops gain furniture and lighting as their repo grows; a new repo raises a new building.
- **Living mirrors** (see §9a): Flora's tank has an "I'm hungry" bubble on feeding day and a "2 worms left!" sign when stock is low. The mailbox overflows visually when the inbox does. The player's real life is a resource system, lightly.

## 9a. Living mirrors are yours, not ours

Flora is the first mirror, not a built-in feature. **This is everyone's game**: a player without an axolotl has horses, cows, pigs, a greenhouse, a car that needs oil, a sourdough starter. Mirrors are declared in data, and the estate grows a slot for each one.

A mirror is three things:

```clojure
{:id      :flora
 :kind    :creature            ; :creature | :plant | :vehicle | :counter | ...
 :model   :axolotl             ; which low-poly figure to show (fallback: generic by kind)
 :home    :tank                ; which estate slot it lives in (:tank :barn :pen :garage :bed)
 :state   {:source "~/.hermes/state/worms.json"   ; where truth comes from (file, URL, script)
           :fields {:stock :count :last-fed :last_feed}}
 :rules   [{:when [:due? :feed] :say "I'm hungry"}
           {:when [:<= :stock 2] :sign "2 worms left!"}]
 :chores  [{:id :feed :every "2d" :at "17:00" :consumes [:stock 1]}]}
```

- The **bridge** evaluates `:state` and `:rules` and pushes the result into world state; the game only renders.
- **Chores** are what the scheduler runs (Hermes cron today); the mirror just reflects them.
- A horse is the same map with `:kind :creature :model :horse :home :barn`, a hay counter, and a daily chore. A pig pen with three pigs is three mirrors sharing a `:home`.
- Starter models ship with the game (axolotl, horse, cow, pig, chicken, dog, cat, plant, car); the `:model` is a KayKit/GLTF name so players can add their own.
- No mirror is required. An empty estate is still a valid estate.

Design rule: anything Hal does for Michael that another player would want for their own life must be expressible as a mirror plus a chore, never as code that knows what an axolotl is.

## 9b. The business is a mirror too

The game mirrors a life, and a life usually includes earning a living. Tony Stark has a company, and Pepper keeps it running. A business is declared the same way a mirror is (§9a): data, not code.

```clojure
{:id     :lotect
 :kind   :business
 :home   :front-office
 :state  {:source :payments}          ; Stripe today; a mock ledger in demo mode
 :chores [{:id :verify-account :once true :needs :signature}
          {:id :quarterly-tax  :every "3mo" :needs :signature}]}
```

Michael's is Lotect LLC, which will sell this game. A second example from the same code path: a farm stand selling eggs, with Square as its source and a weekly "restock the honor box" chore.

**Pepper reads, drafts, and reminds. The player signs.** Guardrails:
- Money never moves without the player's approval. Pepper can't hold a card or press pay.
- Identity documents, bank numbers, and tax IDs go straight from the player to the provider, never through Pepper.
- A chore marked `:needs :signature` shows as a folder on Pepper's desk and brings her to the player with "!" (§8). It never becomes an overdue penalty (D4).
- Read-only access first (balances, payouts, requirements); write access only for a named task.

## 10. Technology

| Layer | Choice | Why |
|---|---|---|
| Rendering | **three.js** | plain JS, huge corpus, runs in a browser tab (so agents can test it) |
| Game code | **ClojureScript** via **shadow-cljs** | the world is one immutable state map re-rendered on change; REPL into the live scene |
| Bridge | **Babashka** (or Node) websocket server | registry of live agents, event stream, prompt relay |
| Agents | **Hermes** sessions and subagents; later any CLI agent | already running the author's life; cron jobs feed world state |
| Build / deploy | shadow-cljs `release`, static hosting (could live on lotect.com) | — |

No TypeScript. Physics not required for v1 (characters move on a navmesh of waypoints).

**Bridge protocol (sketch)**: `world-state` snapshots (rooms, agents, statuses, tank/mailbox counters) pushed on change; `say {agent, text}` and `possess {agent, on?}` from the client; `agent-event {agent, kind: working|question|done|error, payload}` from Hermes.

## 10a. AI pipeline

In my game, each agent takes an input and produces an output that the player sees as a visible effect:

| Agent | Takes (input) | Produces (output) | Player sees (effect) |
|---|---|---|---|
| **Hal** (house bot) | what you type into a speech bubble | a one-line confirmation plus a task handed to a worker | Hal nods, and a new worker walks out of the lobby toward a workshop desk |
| **Worker** (one per task) | a task plus its repo | a branch, commits, and a pull request | typing at a desk with a progress glow, then carrying a finished book to the shelf |
| **Keeper** (scheduled chore job) | a mirror's state file and rules (§9a) | status events like `due :feed` or `stock 2` | an "I'm hungry" bubble over the creature and a "2 worms left!" sign on its home |
| **Clerk** (mail job) | the unread inbox | a count plus a short triage summary | the mailbox fills up and overflows, and clicking it shows the summary |
| **Pepper** (business manager) | the business's payments account, invoices, and business chores (§9b) | a short ledger plus a list of what needs the player's signature | Pepper at her front-office desk; a folder appears when something needs you, and she walks it over |
| **Artist** (a worker on this repo) | "make the river nicer" | a shader or model PR against the game itself | the river visibly changes after hot reload or a merge |

The same pipeline builds the game: a task in `TASKS.md` becomes a branch, a worker writes ClojureScript, the build must compile with 0 warnings, a PR goes up, and Michael merges it. The game's own development is the first workshop on the estate.

## 10b. Technical constraints

My biggest constraint is **agent latency**, because real agents take seconds to minutes to answer and a game has to feel alive every frame. The world therefore never waits on a model: characters animate *thinking*, the player can walk away, and replies arrive as events.

Secondary constraints:

- **The builders can't see the screen.** Agents verify through a HUD the render loop writes to (frame count, sim time, renderer backend), and real visual review happens through the player's screenshots.
- **ClojureScript ↔ three.js interop.** All of three.js goes through one wrapper namespace on three 0.186 / `WebGPURenderer`, so interop mistakes live in one reviewed place.
- **Truth lives outside the game.** Mail, calendar, repos, and mirror state belong to other systems; the game only renders what the bridge reports, and it must degrade gracefully when a source is offline.
- **Cost** matters less than any of the above (see §10c).

## 10c. Token budget

Pricing below is Claude Opus 5.5 API list price ($4 / $20 per million input / output tokens, cache reads $0.20/M, cache writes $5/M). Token counts for a single talk turn were measured on this project's own sessions; task and session figures are estimates.

| Core game action | Approx. tokens | Approx. cost |
|---|---|---|
| **Talk**: one conversation turn with an agent | ~35k in (about 90% cached) + ~600 out | ~$0.04 |
| **Assign**: a small task carried to a merged PR | ~40 model calls ≈ 2.5M in (mostly cached) + ~25k out | ~$2 |
| **Observe**: mirrors, mailbox, status markers | 0, because the bridge polls state and never prompts a model | $0 |

That means each play session (about 20 talk turns plus 3 assigned tasks) costs approximately **$7** at current API pricing.

Cost is a soft constraint for this project because the work runs on flat-rate plans (the class's Claude subscription plus a personal $100/mo plan) rather than metered API billing. Open-weight models up to ~130B can take the cheap, high-volume jobs (ambient chatter, bubble text, summaries) at near-zero marginal cost, leaving the frontier model for real work. The figure that *does* matter is context size. Cost per call grows with how much the agent is carrying, so a bloated session can approach $0.25 per call (the bot's long setup session averaged about that). Short-lived, task-scoped workers (§7, §13 "Cost") are the main control.

## 10d. Dev crew

The agents that **build** the game. (The runtime agents in §10a *run* inside it.) Every kind of generation has a dedicated, specialized agent with written inputs, outputs, and scope. Nothing is improvised at runtime, and the crew is organized like a small studio. Scope means files owned: an agent edits only its own files and asks the owner for changes elsewhere. Because pillar 5 makes the game build itself, every crew member is also a visible worker in the Prime Meridian workshop.

**Hierarchy**

```
Director (Michael, human): vision, merges every PR
 ├─ Lead Architect: systemic design decisions, owns the blueprint
 ├─ Producer (Hal): breaks work down, dispatches, tracks
 │   ├─ Design:      Game Designer · Writer
 │   ├─ Engineering: Engine Engineer · Gameplay Engineer · Bridge Engineer
 │   ├─ Art & audio: Environment Artist ★ · Character Artist · Tech Artist · Sound Designer
 │   ├─ QA:          QA Tester (gates every PR)
 │   └─ Utility:     Asset Scraper (feeds everyone raw material)
 └─ Business Manager (Pepper): release, pricing, storefront, payments
```

**The crew**, one sentence each ("this agent [does x] when [trigger], and the player sees [y]"):

| Agent | One sentence | Inputs → outputs | Owns |
|---|---|---|---|
| **Lead Architect** | Makes the systemic calls (world-state shape, bridge protocol, namespace boundaries, how a new feature fits the whole) **when** a task touches more than one owner's files or changes the data model, **and the player sees** features that compose: a new mirror kind works right away with the existing bubbles, statuses, and XP. | GDD, blueprint, proposed change → decision record, schema/protocol changes | `docs/architecture.md`, world-state schema, `AGENTS.md` |
| **Producer** (Hal) | Breaks a request into agent-sized tasks and dispatches them **when** the player asks for something or a milestone opens, **and the player sees** Hal pin a card to the notice board as a worker heads for the workshop. | request, GDD → `TASKS.md` entries, assignments | `TASKS.md` |
| **Business Manager** (Pepper) | Keeps the company behind the game ready to sell it (payments account, storefront, pricing, policies on lotect.com, release checklist) **when** a release nears or the provider asks for something, **and the player sees** Pepper bring a folder to sign, then the front office light up when the first sale lands. | provider notices, release plan → business tasks in `TASKS.md`, drafts for the player to approve | `TASKS.md` § Business, `docs/business.md` |
| **Game Designer** | Turns a feature idea into rules and content **when** a task changes how the game plays, **and the player sees** new estate slots, statuses, or rules behaving as this doc describes. | idea, playtest notes → GDD edits, estate content | `docs/GDD.md`, `resources/estate.edn` |
| **Writer** | Writes Hal's voice, status bubbles, and mirror-rule copy **when** a new status, rule, or mirror is added, **and the player sees** short in-character lines like "2 worms left!" in the bubbles. | rule/status list, tone guide → string tables | `resources/text.edn` |
| **Engine Engineer** | Extends the three.js wrapper, renderer, camera, and lighting **when** a feature needs a new three.js capability or the frame rate drops, **and the player sees** a smooth 3/4 camera, soft shadows, and a steady frame counter in the HUD. | feature need, profiler → wrapper fns | `pm.three`, `pm.core` |
| **Gameplay Engineer** | Codes world rules, movement, and interactions **when** a gameplay task is assigned, **and the player sees** agents walking, pacing with "!", and coming over to find them. | design spec → world-state rules, scene updates | `pm.world`, `pm.scene` |
| **Bridge Engineer** | Connects live data sources and agent sessions to world state **when** a new source or event type is added, **and the player sees** statuses that match reality, like the tank count changing after a feeding. | API/source, event spec → bridge handlers, protocol | `pm.bridge`, `bridge/` (Babashka) |
| **Environment Artist ★** | Reshapes and re-lights a piece of the estate (river, rocks, terrain, time of day) **when** the player asks in-game to make it better, **and the player sees** that exact spot change in front of them seconds after the hot reload. | in-game request, `img/ref/`, kit reference → shaders, procedural meshes | `pm.env.*` |
| **Character Artist** | Builds and animates the agent figures, hats, and status poses **when** a new role, status, or cosmetic unlock is added, **and the player sees** a new worker look or a new typing, pacing, or celebrating animation. | role/status list, KayKit rigs → models, animation clips | `pm.characters`, `public/assets/characters/` |
| **Tech Artist** | Imports, decimates, and LODs purchased kits and keeps the frame budget **when** a new kit lands in `assets/private/`, **and the player sees** a lush new corner of the estate without the frame rate dropping. | GLB kit → optimized meshes, load code | `pm.assets`, `assets/private/` (local) |
| **Sound Designer** | Places positional audio emitters and ties them to world state **when** something audible is added or changes speed, **and the player hears** the river get louder as they walk toward it and its pitch follow the flow. | sound files, emitter positions → audio graph | `pm.audio`, `public/assets/audio/` |
| **QA Tester** | Runs the build (0 warnings), checks the HUD liveness numbers, and asks the player for screenshots **when** any PR opens, **and the player sees** nothing break, or a worker with a red "!" whose PR failed. | PR → pass/fail report on the PR | the gate; owns no game code |
| **Asset Scraper** | Fetches raw material (CC0 models such as KayKit, sound loops, reference images, API docs) and records each item's source and license **when** another agent files an asset request, **and the player sees** it only indirectly, as the new hat, the brook sound, or the reference the river was matched to. | asset request → files plus a source/license line | `assets/incoming/`, `img/ref/`, `LICENSES.md` files |

**One wow agent: the Environment Artist.** It is the crew's most visible output and the purest form of the core loop (§5): you walk up to the river, ask for it to be nicer, and watch it change in front of you. The other agents make the estate *true* and *readable*; this one makes it *alive*, and it delivers pillar 5 ("the world is the showcase").

**Role spectrum: Architect vs Scraper.** Like a real studio, the crew runs from high-level reasoning to low-level utility. Where an agent sits on that spectrum sets its model, how much context it gets, and how much authority it has.

```
high-level reasoning ◄──────────────────────────────────────────────► low-level utility
  Lead Architect · Game Designer   Producer · Pepper · QA   Engineers · Artists · Sound   Writer   Asset Scraper
```

| Tier | Agents | Thinks about | Model | Context | Authority |
|---|---|---|---|---|---|
| Strategic | Lead Architect, Game Designer | whole-system tradeoffs, blueprints, schemas | frontier, maximum reasoning | wide: GDD, schemas, all namespaces | decides; few calls, each expensive |
| Coordination | Producer, Business Manager, QA | sequencing, gating, "is it done?" | frontier | `TASKS.md`, PRs, build output | assigns and blocks; doesn't design |
| Craft | Engine, Gameplay, and Bridge Engineers; Environment, Character, and Tech Artists; Sound Designer | making one owned piece excellent | frontier | narrow: only the files it owns plus the spec | builds inside its scope |
| Content | Writer | voice, short copy | open-weight, mid-size | tone guide plus string list | proposes text |
| Utility | Asset Scraper | finding, downloading, labeling | small open-weight model or plain scripts (Babashka, curl) | the request only | none: fetches, never decides |

Rules:
- Decisions flow down and raw material flows up. The Architect never fetches files and the Scraper never makes a design call; if it finds something that changes the plan, it reports up.
- Anything the Scraper brings in lands in `assets/incoming/` with a source and license line. Nothing reaches the repo until a craft agent adopts it, and licensed kits stay in gitignored `assets/private/`.
- The spectrum is also the token budget (§10c): spend frontier reasoning on the few strategic calls and run the high-volume utility work on cheap models.

## 11. Art direction

Low-poly, flat-shaded, warm palette, soft shadows. Think a diorama you'd keep on a desk. Characters are simple capsule-and-hat figures so they're cheap to animate and easy to tell apart. The river is intentionally the one "nice" thing — a standing invitation to ask an agent to make it nicer.

## 12. Scope and milestones

Course runs Tue/Thu. Plan assumes roughly one milestone per week with agents doing most of the build between classes.

| Week | Milestone | Done when |
|---|---|---|
| 1 | **GDD** (this doc) + repo scaffold | shadow-cljs builds, a cube spins in the browser |
| 2 | **World v0** | estate with lobby, tank, mailbox, one workshop; three wandering mock agents; camera |
| 3 | **Bridge v0** | real Hermes job/session status shows on characters; tank reads real worm count |
| 4 | **Talk** | click an agent, converse, replies stream as bubbles |
| 5 | **Possess + questions** | agents raise "!", walk to you, resume on answer; possess toggle |
| 6 | **Gamify v0** | XP, one unlockable, estate growth tied to a real repo |
| 7+ | Polish, the river, sound, release build on lotect.com |

**MVP = weeks 1–4.** Everything after is stretch.

## 13. Risks

- **Interop friction**: three.js from ClojureScript is fine but verbose; agents may write buggy interop. Mitigation: a small `three.cljs` wrapper namespace written once, reviewed carefully, used everywhere.
- **Latency of "real"**: agent replies take seconds to minutes. Mitigation: the world is never waiting — it shows *thinking* animations, and you can walk away.
- **Scope creep**: the mirror-your-life idea is bottomless. Mitigation: four live data sources max for the course (tank, mailbox, board, one repo).
- **Cost**: idle agents burning tokens. Mitigation: characters exist for the life of a task; the bridge polls, it doesn't prompt.

## 14. Open questions

1. Single estate or one estate per profile (personal vs work)? *(Lean: one estate, buildings per profile, later.)*
2. Voice in/out for conversations, or text only for the course? *(Lean: text; voice is a stretch.)*
3. Should possessing an agent cost something in-game, to make it a deliberate act? *(Lean: no, in v1.)*
4. Do non-coding agents (the Courier, the Keeper) get XP, or only workers?
5. Where does Pepper run? *(Lean: on Hermes beside Hal, since she needs the real accounts; the Claude Code crew never touches money.)*

---
*Next: scaffold the repo (shadow-cljs + three.js), `AGENTS.md`, and a `TASKS.md` with week-2 work broken into agent-sized jobs.*
