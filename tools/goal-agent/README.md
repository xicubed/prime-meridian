# Goal agent (Assignment 5)

A goal-oriented coding agent for Prime Meridian. It reads the game design document, scans the
game's code, works out which GDD features are missing, decides which to build first, builds it,
checks it with the game's own gate, re-scans, and repeats until the requirements are met or it
runs out of turns. It never commits: the Director approves every change (GDD §4.2).

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
| [`runs/20261114-1634/`](runs/20261114-1634/) | the run that built the two features below: every plan, prompt, reply and gate log |

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
5. **Check.** The writer may only touch `src/pm/`, `test/pm/` and `resources/`. Then the game's own
   gate (`.crew/gate.sh`: compile with 0 warnings, every test passes) and the requirement's probes
   must pass. A failure goes back to the writer with the errors, up to 3 attempts; after that the
   attempt is stashed (`git stash list`, recoverable) and the gap is parked.
6. **Repeat.** Re-scan, re-score, next gap. It stops when every requirement is met, nothing scores
   above 0, or `--max` iterations are used.

## What it built

One run (`--max 2`, Nov 14, about $3 of model use) built two features into the game. Both pass
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

## Why it picked those

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

## What I changed by hand before accepting it

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
- **Left as found, for a design decision:** GDD §3.1 itself writes two events with `:type` and two
  with `:event`; the agent reproduced that faithfully. And the badges live in the HUD for now; badges
  on the objects in the world would be the next step.
