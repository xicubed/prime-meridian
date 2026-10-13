# TASKS

Legend: `[ ]` open · `[~] name` claimed · `[x] #PR` done. Top-most open task is next. Keep tasks agent-sized: one branch, one PR, one evening.

## Week 1 — GDD + scaffold
- [x] #1 Game Design Document
- [x] #2 shadow-cljs + three.js scaffold, spinning cube, AGENTS.md, this file

## Week 2 — World v0 (estate + mock agents)
- [ ] W2-1 `pm.world`: world-state shape. `{:rooms {id {:name :pos :size}} :agents {id {:name :room :status :pos :target}} :tank {:worms :hungry?} :mailbox {:count}}`. Pure fns: `spawn-agent`, `set-status`, `step` (advance movement toward `:target` by dt). Unit-testable, no three.js.
- [ ] W2-2 `pm.scene`: render rooms from state — cutaway room boxes (floor + 2 back walls) floating on a dark grid void, Ralv-style; colored floor per room; label sprite under each; lobby, tank room, mailbox, one workshop. Elevated 3/4 camera framed to the estate; sun shadow frustum sized to the estate.
- [ ] W2-3 Characters: import KayKit character GLTF (CC0; vendor the files under `public/assets/kaykit/` with its license) via GLTFLoader in the wrapper; idle + walk animation clips; one color/hat variant per agent; `pm.scene` syncs agent positions from state each frame (reuse objects, don't rebuild).
- [ ] W2-4 Mock agents: three agents wander between room waypoints; statuses cycle `working → idle → needs-input` on timers so every status is visible.
- [ ] W2-5 Status bubbles: Ralv-format billboard above each agent — task name · status + elapsed · branch · model; "!" variant for `needs-input`, red for `error`; sitting pose for idle, typing for working.
- [ ] W2-6 Camera controls: orbit/zoom/pan (OrbitControls via the wrapper), clamped so you can't go under the ground.
- [ ] W2-7 Mirrors v0: `pm.world` reads a `mirrors` vector (GDD §9a shape) from `resources/estate.edn`; `pm.scene` renders each in its `:home` slot with a placeholder figure by `:kind`; `:say`/`:sign` rules render as bubble/sign. Ship `estate.edn` with Flora (axolotl, tank) AND a second example (horse, barn) so nothing is axolotl-specific. State mocked this week.
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
- Time-of-day switch (afternoon / golden / blue / night): sun angle + color, sky, window glow
- The river: water shader, bank, rocks as ONE scaled mesh (on purpose) → then `procedural-rock` (icosahedron + vertex noise + jitter) as the showcase before/after
- Avatar customizer (KayKit hair/hat/clothes) as the level-up reward
- Possess toggle; autonomy dial; agents walk to the player on `needs-input`
- XP, levels, hats; estate growth tied to repo stats
- The river, and the "make the rocks procedural" demo
- Release build published to lotect.com
