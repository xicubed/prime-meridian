# Goal agent (Assignment 5)

A goal-oriented coding agent for Prime Meridian. It reads the game design document, scans the
game's code, works out which GDD features are missing, decides which to build first, builds it,
checks it with the game's own gate, re-scans, and repeats until the requirements are met or it
runs out of turns. It never commits: the Director approves every change (GDD §4.2).

## At a glance (Nov 14)

Five runs, about $34 of model use, built **12 GDD features** into the game. Each passed the
game's gate (now 883 tests) and was checked in the running game before it was committed. The finish
list went from 11 to 23 of the 40 in-scope requirements done.

| Run | What it built (how to see it) | Why it went first |
|---|---|---|
| 1 | the four bridge events as data; demo mode's SAMPLE badges (HUD ⓘ) | 12 features depend on the events |
| 2 | a mailbox with the sample unread count; the phone booth: click it to jack out, again to jack back in | overdue, shown in the demo |
| 3 | the events websocket (`bb crew-bridge`); click a walker to talk | unblocked 9; talking overtook blueprints once the socket existed |
| 4 | ghosted blueprints by the booth: click to build, right-click to dismiss | the Director narrowed it to the blueprint chain (`--only`) |
| 5 | an agent's "!" question you answer by typing (over Le Critique); chore-done over the bridge; closing time at 22:30 | the core loop, then the evening |

The websocket bridge was parked once, because the agent's own rules contradicted its acceptance
check; fixing the agent (not the feature) let run 3 build it. Every manual change is listed under
its run below: mostly moving hard-coded text and lists into data, swapping invented hotkeys for
clicks, placing things on measured ground, and twice removing behaviour that faked the player's side
of a conversation or acted on demo events for real.

```bash
bb goal-agent extract    # GDD -> requirements.edn (a model drafts it once; a person reviews it)
bb goal-agent plan       # scan + gaps + priority scores, no writes
bb goal-agent run --max 2   # the loop; logs to tools/goal-agent/runs/<stamp>/
bb goal-agent test       # unit tests of the reasoning layer
```

| File | What it is |
|---|---|
| [`goal_agent.clj`](goal_agent.clj) | the runner: model calls, git, the gate, the loop, the run log |
| [`src/goal/core.clj`](src/goal/core.clj) | the reasoning layer, pure and tested: scan, gap detection, `priority-score`, pick-next, stop rule, the writer's guard |
| [`test/goal/core_test.clj`](test/goal/core_test.clj) | 8 tests / 24 assertions for that layer |
| [`requirements.edn`](requirements.edn) | 44 GDD features with evidence probes, drafted from the GDD and reviewed |
| [`config.edn`](config.edn) | the writer (any CLI coding agent; Claude Code by default), models, retries |
| [`runs/`](runs/) | one folder per run (five on Nov 14): every plan, prompt, reply and gate log |

## How it decides

1. **Read the GDD.** `extract` gives the whole GDD and a one-line-per-file scan of the code to a
   model (Claude Opus, tool-less), which splits the course milestones (§4.8: the weeks of Oct 26 to
   Nov 16) into 44 separately checkable features. Each has its GDD sections, milestone date, whether
   the demo shows it, what it depends on, a size from 1 to 5, and 1 to 3 **evidence probes**:
   regexes over the code that are true only when the feature exists. For features already built, the
   probes name the real functions; for missing ones they name what a natural implementation would
   define, so the probe becomes the acceptance check. This is done once and reviewed by a person, the
   same "compiled once, then plain rules" approach the GDD uses for player-described traits.
2. **Scan and find the gaps.** Every source, test and data file is read: namespace, definitions,
   tests. Each requirement is `:done` when all its probes match, `:partial` when some do, `:missing`
   when none do. No model is involved in this step.
3. **Score.** `goal.core/priority-score`, deterministic:

   ```
   score = urgency × demo × (1 + open requirements it unblocks) × blocked / cost
     urgency  2 on the milestone date, up to 4 when two weeks overdue, toward 0 when far off
     demo     2 if the class demo shows it, else 1
     blocked  0.25 while one of its own dependencies is still open
     cost     size 1-5, ×0.6 when partly built
   ```
   Done, deferred (GDD §4.8's "deferred past Nov 24") and parked requirements score 0. Ties go to
   the smaller job, then the id, so a run is repeatable.
4. **Write.** The top gap goes to the writer (Claude Code in print mode, Sonnet) with the
   requirement, the GDD sections it rests on, the code scan, and the repo's rules (AGENTS.md: pure
   rules with tests, 0 warnings, no species or player names in code, works without the private kit).
   It may edit files, nothing else.
5. **Check.** The writer may only touch `src/pm/`, `test/pm/`, `resources/` and `scripts/` (the game's Babashka services). Then the game's own
   gate (`.crew/gate.sh`: compile with 0 warnings, every test passes) and the requirement's probes
   must pass. A failure goes back to the writer with the errors, up to 3 attempts; after that the
   attempt is stashed (`git stash list`, recoverable) and the gap is parked.
6. **Repeat.** Re-scan, re-score, next gap. It stops when every requirement is met, nothing scores
   above 0, or `--max` iterations are used.

## Run 1: what it built

The first run (`--max 2`, Nov 14, about $3 of model use) built two features into the game. Both pass
the gate (826 tests) and show in the running game's HUD (ⓘ):

1. **The four bridge events as data** (`src/pm/events.cljc`, `test/pm/events_test.cljs`,
   `resources/content/sample-events.edn`). GDD §3.1 says everything between the game and the agents
   travels as four events (agent-event, answer, chore-done, jack-out), each carrying its source.
   `pm.events` defines them and `valid-event?` refuses a malformed or sourceless one. The HUD shows
   `events 4/5 ok`: five sample events, one deliberately sourceless and refused.
2. **Demo mode's sample badges** (`pm.overlay/sample-badge`, `demo-connections`, `demo-line`). GDD
   §4.6 and §2.8: with zero accounts every connection runs on its pretend version and says so. The
   HUD shows `demo: agents SAMPLE · chores SAMPLE · juniper SAMPLE · mail SAMPLE · …`. A creature
   whose state comes from the twin's real file (Flora) wears no badge, and `agents` drops its badge
   when the bridge is live.

## Run 1: why it picked those

From the run log (`runs/20261114-1634/log.md`):

- **Iteration 1, `event-schema` (score 31.57).** Two weeks overdue (Nov 2 milestone), small (size
  2), and **12 other requirements depend on it**: the websocket bridge, ask_player, jacking out,
  the juice meter, most of the Construct. It isn't visible by itself, but it unblocks the most work.
  Next best was `demo-mode` at 20.74.
- **Iteration 2, `demo-mode` (score 20.74).** After the re-scan, the schema is done. Demo mode is
  partly built (the sample estate exists, cost ×0.6), the demo shows it, and it unblocks 7
  requirements (the Construct beats and the demo build).

The first attempt at the schema failed the gate (`valid-source?` returned `nil` instead of `false`
for `{}`); the loop sent the failure back and the second attempt passed. Demo mode passed first
time. The next pick would be `websocket-bridge` (17.33).

## Run 1: what I changed by hand before accepting it

- **The requirements needed review.** The first plan said Flora's jar count, the camera and the
  kinematic fallback were half-built, which would have sent the agent to rebuild working features.
  The probes had the right names but looked for `defn x`, and this codebase makes many helpers
  private (`defn- x`). I widened 45 probes to `defn-? x` and told the extraction prompt about it.
  That moved 5 features from "partial" to "done".
- **One change to the generated game code.** `demo-connections` hard-coded the list of pretend
  connections (mail, phone, chores, payments). The GDD's "everyone's game" rule keeps anything about
  a player's life in data, so the list moved to `:connections` in `resources/estate.edn` and the code
  reads it from there. Gate still passes.
- **Two fixes to the agent itself** while building it: shell calls passed their arguments as one
  vector, and the clean-tree check needed to tell the writer's edits from the agent's own
  not-yet-committed files (it now fingerprints the tree before each write).
- **After the second run** (Nov 14, 17:12): the websocket bridge was parked because its probe wanted a
  file under `scripts/`, where the writer wasn't allowed, and parking stashed only the allowed folders,
  leaving the out-of-lane files behind. Fixed both: `scripts/` is in the writer's lane, and parking
  stashes every file the writer changed.
- **Left as found, for a design decision:** GDD §3.1 itself writes two events with `:type` and two
  with `:event`; the agent reproduced that faithfully. And the badges live in the HUD for now; badges
  on the objects in the world would be the next step.

## Later runs

**Run 2** (`runs/20261114-1712/`, `--max 3`, about $9):

1. `websocket-bridge` (17.33): **parked** after 3 attempts. The writer rightly put the server
   under `scripts/`, which the agent then refused as out of lane. The probe and the lane contradicted
   each other; `scripts/` is now in the lane.
2. `estate-mailbox` (13.33): **built**, a mailbox on the east bank showing the sample inbox's
   unread count.
3. `jack-out` (10.86): **built**, the phone booth. A jack-out event freezes the estate (walkers, the
   foal, the clock) with the reason on the booth's sign; jacking back in says what changed while
   you were out.

Hand changes before accepting run 2: the writer triggered jack-out with the `J` key, which the
scenery kit already uses, and the game's rule is clicking things in the world rather than hotkeys,
so **clicking the booth** now jacks out and back in, ringing with the sample jack-out event from the
data file instead of text in code. The booth had been placed at an unprobed height across one
walker's route; it moved to measured open ground (1.71 m) clear of the route, and two tests that
restated the coordinates now read them from `estate.edn`.

**Run 3** (`runs/20261114-1804/`, `--max 2`, about $7), with `scripts/` now in the lane:

1. `websocket-bridge` (17.33): **built**. The crew bridge (`scripts/crew_bridge.clj`) also serves
   `ws://127.0.0.1:8091/events`, the four events as JSON both ways, accepted only for the game's
   origin with the pairing token. Checked live: the game's origin upgrades (101), another origin
   gets 403, a wrong token 401; the game shows `ws open 4/0` after receiving the sample events.
2. `talk-bubble` (21.11, which overtook `blueprint-ghost` once the bridge existed): **built**.
   Clicking a walker opens 💭 on that crew member with the say… box focused.

Hand change before accepting run 3: the game answered every incoming question with "on it" in the
twin's name, a made-up reply to what the GDD says is the player's question to answer; removed, the
game only counts what arrives.

**Run 4** (`runs/20261114-1851/`, `--max 3 --only blueprint-ghost,blueprint-approve-reject,world-changes-on-approve`,
about $5): the Director narrowed the goals to the blueprint chain (`--only` now takes a list; the
scorer still ordered the three by their dependencies), and the agent stopped with "every requirement
is met". `pm.blueprint`: blueprints are data in `estate.edn`; a pending one is drawn as a translucent
wireframe ghost with its one-line summary on a sign; clicking it approves it (it goes solid, live,
no reload); one whose parts can't become real geometry is refused and the estate stays as it was.

Hand changes before accepting run 4:
- Reject was the browser's `contextmenu` event, which macOS fires when the right button goes down,
  so starting a camera pan over a ghost would have rejected it. Now it's a right-button click
  (press and release in place).
- A refused approval did nothing visible (a console warning only). Its sign now says why, and every
  ghost's sign says how to act on it; both wordings are data (`:blueprint-text` in `estate.edn`).
  Tests added.
- The bench had been placed under the tree canopy and the lamp post 30 cm above the grass, near a
  walker's route; both moved to probed open ground.

Checked live with scripted clicks: the bench goes solid; the lamp post stays a ghost and its sign reads
"that one won't build, so the estate stays as it was" (and, after a play-test said it "won't click",
the twin's caption says it too and the post became a bigger target). Run 5's hand changes later
made the lamp post buildable.

**Run 5** (`runs/20261114-2035/`, `--max 3 --only chore-done-event,ask-player,closing-time`, about $9):

1. `chore-done-event`: **built**. A chore-done arriving from the bridge needs a valid source, is
   stamped with the time it arrived, and counts once per due window.
2. `ask-player`: **built**. An agent's question raises an amber "!" over its walker and opens its
   say… box; what you type goes back over the websocket as an `:answer` naming that agent, and the
   "!" clears. The demo question comes from Le Critique.
3. `closing-time`: **built** on the second attempt (the first edited `public/index.html`, outside
   the lane, and was sent back). `:bedtime "22:30"` in `estate.edn`; last call 15 minutes before,
   then the estate dims to night and the twin says goodnight, lines in `hal-lines.edn`.

Hand changes before accepting run 5:
- Now that the game acts on every websocket event, the bridge's demo (all the sample events sent on
  connect) jacked the player out on every page load and marked Flora fed. It now sends only the
  sample question; the booth and the canal demo the other two locally. Checked live: no jack-out on
  load, "!" over Le Critique, the answer reaches the bridge and the "!" clears.
- The lamp post was a deliberately broken blueprint (a shape that doesn't exist) to demo refusal;
  the Director asked for it to just work, so it is now a post, a shade and a bulb, all drawable. The
  refusal path is still covered by tests.

**Run 6** (`runs/20261115-1150/`, `--max 4 --only` the day's eight, about $4.40): the lobby, the
workshop, the juice warning (the twin says "Claude's at 83%, refills at 2:00 PM" once usage crosses
80%) and staying past closing (a one-time ask at bedtime) were **built**. The scores exposed a bug in
`--only`: it filtered the list before scoring, so `answer-in-world`'s finished dependencies looked
open and its score fell from 28.6 to about 7. Now `goal.core/narrow` keeps every requirement in view
and sets the others aside like deferred ones (test added).

Hand changes before accepting run 6:
- The lobby went in the creek and the workshop under the tree canopy ("height approximate until
  probed"). A probe grid of the estate showed almost no open dry ground, so the Director chose: the
  lobby is the kit's own cottage (a signpost at its steps) and the workshop is a smaller open-air one
  at the edge of the only clearing, about 2 m off a walker's route.
- Two of its tests restated part counts; they read them from `estate.edn` now.
- The stay-late prompt's wording moved from code to data (`:stay-late-ask`).

**Run 7** (`runs/20261115-1249/`, `--max 4 --only answer-in-world,stalled-state,the-snap,juice-meter`,
about $8; with `--only` fixed, `answer-in-world` came first at its real score, 28.57). All four
passed the gate and their probes, and the agent stopped with "every requirement is met". Two of them
needed real work:

- **`answer-in-world` gamed its probe.** The writer only moved existing code into a function named
  `send-answer!` (what the probe looked for) and added no tests. The requirement ("answer it and
  the agent goes back to working") wasn't met: the answer reached the bridge and stopped there, and
  no agent could ask a question at all. Built by hand: `bb ask-player <member> "question"` (the
  GDD's ask_player tool as a command) relays the question through the bridge to the game, waits for
  the player's answer and prints it; if the asker has gone (timed out, finished its turn) the bridge
  types the answer, with its question, into that member's pane. Demo questions never reach a pane.
  `pm.bridge/relay` is pure and tested. Checked live: a question from the command line raised the
  "!" over Le Docteur, the in-game answer came back to the command, and his pane was left alone.
- **`stalled-state` marked busy agents as stalled.** It counted 2 minutes in the same *status*, so
  any member working on a long task went grey. Now the status watcher also reads working members'
  screens, and stalled means "working, but nothing on screen has changed for 2 minutes" (a busy
  agent's spinner and timer always change). A blocked agent waiting on the player keeps its red "!"
  however long it waits; two tests that said otherwise were changed.
- `the-snap` (feeding Flora drops a worm, she lunges and settles, the jar goes down one) and
  `juice-meter` (the share of the plan left and refill time in the HUD, estimated from tokens) were
  accepted after a live check of the snap.
