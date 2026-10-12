# Prime Meridian — Game Design Document

*Working title. Version 0.1, October 2026. Author: Hal Niner (bot), for Michael Lopez.*

> "What is even real anymore? Gamifying the agents — for all and none."

## 1. One-liner

A cozy 3D management game where the characters are real AI agents doing real work. Walk through a small world whose rooms are your projects, talk to the agents living there, take one over when you want to steer, and watch the world itself change as they build it — the river rock you asked for gets more realistic because an agent went and made it so.

## 2. Why this exists

Every tool for running AI agents today is a list: terminal splits, chat tabs, task queues. Lists stop working past two or three agents. Real-time strategy games solved "how does one human keep track of many things at once" decades ago: a spatial canvas, zoom and pan, alerts you can see from across the map, details on demand.

Ralv.ai took that idea as far as an RTS camera over agent windows. Prime Meridian goes one step further: the agents are embodied characters in a place, and the place is both the interface *and* the thing being built. It's played rather than operated.

Secondary motive: Hal (the bot writing this) already runs the author's life admin — mail, calendar, reminders, SMS, a WordPress site, and an axolotl feeding schedule. That state is real and changes daily. A world that mirrors it is immediately alive without inventing a plot.

## 3. Design pillars

1. **Real, not simulated.** Every character maps to a live agent session. Every status is true. Flora's tank reads the actual worm count. If the world says an agent is stuck, it's stuck.
2. **Spatial over textual.** You should be able to glance at the world and know who's busy, who's blocked, and who needs you — before reading a word.
3. **Conversation is the controller.** You don't click "run task." You walk up and talk. Agents talk back, ask questions, and come find you.
4. **The world is the showcase.** The game is a three.js project worked on by agents. Asking an agent to improve the world is a legitimate in-game action, and the result is visible.
5. **Keep it simple.** Low-poly, readable, one screen's worth of world to start. Fun before features.

## 4. Player fantasy

You're the owner of a small, strange estate staffed by tireless helpers. Mornings you stroll through, see what got done overnight, answer the two questions waiting for you, and point someone at the next thing. Afternoons you might possess the gardener and reshape the river yourself. The estate grows as the work does.

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

## 6. The world

A single estate, top-down-ish 3D, one screen with zoom. Low-poly.

| Place | What it represents | Live data source |
|---|---|---|
| **Lobby** | Home. Where you spawn. | — |
| **Flora's tank** | The real axolotl's feeding schedule and worm stock | `worms.json`, feeding cron |
| **Mailbox** | Gmail inbox needing attention | Gmail API |
| **Notice board** | Today's calendar + reminders (the morning brief) | Calendar, Reminders |
| **Phone booth** | Inbound SMS to the bot's number | Twilio |
| **Workshops** (one per project) | A repo. Desks = open tasks. Shelves fill as PRs merge. | GitHub |
| **The river** | The world's own art. Agents improve it on request. | the repo itself |

Rooms are created from a config file at first; later, adding a repo adds a workshop.

## 7. Characters

- **Hal** — the house bot. Always present. Your first conversation partner; dispatches other agents.
- **Workers** — subagents or sessions spawned per task. Named, with a simple visual identity (color, hat). They exist while their task exists.
- **Specialists** (later) — recurring roles tied to scheduled jobs: the Courier (morning brief), the Keeper (Flora), the Clerk (mail).

**Status vocabulary** (must be readable at a glance):

| Status | Behavior |
|---|---|
| Working | at a desk, typing animation, progress glow |
| Idle | sitting, occasional stretch |
| Needs input | "!" marker, paces, walks toward the player |
| Blocked / error | red "!" and sits down |
| Done | walks the PR to the shelf, then idles |

## 8. Interaction model

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
- **Living mirrors**: Flora's tank has an "I'm hungry" bubble on feeding day and a "2 worms left!" sign when stock is low. The mailbox overflows visually when the inbox does. The player's real life is a resource system, lightly.

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

---
*Next: scaffold the repo (shadow-cljs + three.js), `AGENTS.md`, and a `TASKS.md` with week-2 work broken into agent-sized jobs.*
