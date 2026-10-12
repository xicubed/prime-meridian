# TASKS

Legend: `[ ]` open · `[~] name` claimed · `[x] #PR` done. Top-most open task is next. Keep tasks agent-sized: one branch, one PR, one evening.

## Week 1 — GDD + scaffold
- [x] #1 Game Design Document
- [x] #2 shadow-cljs + three.js scaffold, spinning cube, AGENTS.md, this file

## Week 2 — World v0 (estate + mock agents)
- [ ] W2-1 `pm.world`: world-state shape. `{:rooms {id {:name :pos :size}} :agents {id {:name :room :status :pos :target}} :tank {:worms :hungry?} :mailbox {:count}}`. Pure fns: `spawn-agent`, `set-status`, `step` (advance movement toward `:target` by dt). Unit-testable, no three.js.
- [ ] W2-2 `pm.scene`: render rooms from state — flat boxes on the ground with a label sprite; palette map; lobby, tank room, mailbox, one workshop. Camera framed to fit the estate.
- [ ] W2-3 Characters: capsule + hat figure builder in `pm.three`; one color per agent; `pm.scene` syncs agent positions from state each frame (reuse objects, don't rebuild).
- [ ] W2-4 Mock agents: three agents wander between room waypoints; statuses cycle `working → idle → needs-input` on timers so every status is visible.
- [ ] W2-5 Status markers: "!" billboard above `needs-input`, red "!" for `error`, sitting pose for idle, slight bob for working.
- [ ] W2-6 Camera controls: orbit/zoom/pan (OrbitControls via the wrapper), clamped so you can't go under the ground.
- [ ] W2-7 Flora's tank: a glass box with a low-poly axolotl; bubble text "I'm hungry" when `:hungry?`, sign "N worms left" when `:worms <= 2`. Driven from state (mocked this week).
- [ ] W2-8 HUD: agent list on the left with name + status dot; clicking one centers the camera on it.

## Week 3 — Bridge v0 (real state)
- [ ] W3-1 `bb` script `bridge.clj`: websocket server that pushes `world-state` JSON on change. Sources: `~/.hermes/state/worms.json`, Hermes cron job list (`hermes cron list --json` or state db), Gmail unread count via the google-workspace script.
- [ ] W3-2 `pm.bridge`: client connects, merges incoming state into `!world`; reconnects with backoff.
- [ ] W3-3 Map Hermes jobs/sessions → agents with real statuses (scheduled/running/ok/error).
- [ ] W3-4 Tank reads real worm count; mailbox reads real unread count.

## Week 4 — Talk
- [ ] W4-1 Click agent → speech-bubble panel with text input.
- [ ] W4-2 Bridge `say` → spawns/continues a Hermes session for that agent; replies stream back as `agent-event`.
- [ ] W4-3 Conversation history per agent persisted in `localStorage`.

## Backlog (weeks 5+)
- Possess toggle; autonomy dial; agents walk to the player on `needs-input`
- XP, levels, hats; estate growth tied to repo stats
- The river, and the "make the rocks procedural" demo
- Release build published to lotect.com
