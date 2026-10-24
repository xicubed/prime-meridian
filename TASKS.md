# TASKS

Legend: `[ ]` open · `[~] name` claimed · `[x] #PR` or `[x] <date>` done. Top-most open task is next. Keep tasks agent-sized: one evening's work.

## Week 1 — GDD + scaffold
- [x] #1 Game Design Document
- [x] #2 shadow-cljs + three.js scaffold, spinning cube, AGENTS.md, this file

## Second draft (Assignment 2, due about a week after Oct 20; confirm the date from the assignment PDF)
Plan of record: [`docs/session-2026-10-17.md`](docs/session-2026-10-17.md) §7. Gold standard first (`docs/GDD.md`), then re-condense `docs/GDD-submission.md` (≤ 6 pages).
- [~] D-1 (assignment text pasted in chat; the "Final_GDD - ELVTR" Google Doc still to fetch) Save the Assignment 2 PDF to `assets/private/course/`; gap-check the draft against its rubric.
- [~] D-2 (leans written in as decisions Oct 23, pending Michael's veto) Michael decides open questions A–F (GDD §7); decisions go into the body, the rest stay leans.
- [~] D-3 (Hal drafted pillars from Michael's Discord wording; Writer's original Construct lines in §2.7) Michael rewrites the design pillars in his own voice; the Writer drafts original Construct lines (no film quotes).
- [x] 2026-10-23 D-4 Revise `GDD.md` from the review board's multi-agent findings (forged "!", approval fatigue, stalled agents, curated MCP set, sample-to-real); answer or explain the standard-game findings in one line each.
- [~] D-5 (6 pages; PDF in assets/private/course/ until sign-off) Re-condense `GDD-submission.md`; render the PDF into `docs/`.
- [ ] D-6 Rerun the review kit Rounds 1–4 on the new submission; archive to `docs/reviews/<date>-draft2/`; report BLOCKING 11 → ?.
- [x] 2026-10-19 D-7 Post the draft in the class Discord feedback thread for human feedback.
- [ ] D-8 Diff vs `gdd-draft-1` as a section table; tag `gdd-draft-2`; submit.

## Week 2 — World v0 (estate + mock agents)
> Rewrite these from the re-cut MVP (GDD §4.8) before starting: no XP or tank; add the static "!", the motion-controller interface with kinematic paths, and the four bridge events as mock data.
- [ ] W2-0 Rewrite the W2 tasks below from the current GDD (one evening, do first).
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

## Business (Pepper)
Owner: Business Manager (GDD §9b, §10d). Real-world steps that need ID, bank, or a signature are the Director's; Pepper preps everything around them.
- [ ] B-1 Payments account: finish the provider's business verification before the first sale. Director does the identity/bank steps; Pepper first gets lotect.com review-ready (what we sell, contact info, privacy and refund policies).

## Backlog (weeks 5+)
- Time-of-day switch (afternoon / golden / blue / night): sun angle + color, sky, window glow
- The river: start from three.js `Water2` (examples/webgl_water_flowmap, MIT) stylized to flat-shaded; DOF via `BokehPass` optional; bank; rocks as ONE scaled mesh (on purpose) → then `procedural-rock` (icosahedron + vertex noise + jitter; see offscreencanvas.com "ThreeJS Rocks Demo Explained") as the showcase before/after. Visual reference only: img/ref/hiroshi-island-*.png (closed source).
- River sound, spatial: `AudioListener` on the camera + `PositionalAudio` emitters spread along the water mesh's long axis (refDistance ~2.5, rolloff ~1.6, inverse model), each playing `public/assets/audio/brook.mp3` at a different offset; `pm.audio` already has the loop + synth fallback and a flow→playbackRate mapping (0.3–1.2×, pitch follows). Proven in the kit build (`assets/private/kit/dist/index.html`, the `pm-brook` script) — port that.
- Avatar customizer (KayKit hair/hat/clothes) as the level-up reward
- Possess toggle; autonomy dial; agents walk to the player on `needs-input`
- XP, levels, hats; estate growth tied to repo stats
- The river, and the "make the rocks procedural" demo
- Release build published to lotect.com
