# TASKS

Legend: `[ ]` open · `[~] name` claimed · `[x] #PR` or `[x] <date>` done. Top-most open task is next. Keep tasks agent-sized: one evening's work.

## Week 1 — GDD + scaffold
- [x] #1 Game Design Document
- [x] #2 shadow-cljs + three.js scaffold, spinning cube, AGENTS.md, this file

## Second draft (Assignment 2, due about a week after Oct 20; confirm the date from the assignment PDF)
Plan of record: [`docs/session-2026-10-17.md`](docs/session-2026-10-17.md) §7. Gold standard first (`docs/GDD.md`), then re-condense `docs/GDD-submission.md` (≤ 6 pages).
- [x] 2026-10-24 D-1 Save the Assignment 2 PDF to `assets/private/course/`; gap-check the draft against its rubric.
- [x] 2026-10-24 D-2 Michael decides open questions A–F (GDD §7); decisions go into the body, the rest stay leans.
- [x] 2026-10-24 D-3 Michael rewrites the design pillars in his own voice; the Writer drafts original Construct lines (no film quotes).
- [x] 2026-10-23 D-4 Revise `GDD.md` from the review board's multi-agent findings (forged "!", approval fatigue, stalled agents, curated MCP set, sample-to-real); answer or explain the standard-game findings in one line each.
- [x] 2026-10-24 D-5 Re-condense `GDD-submission.md`; render the PDF into `docs/`.
- [ ] D-6 Rerun the review kit Rounds 1–4 on the new submission; archive to `docs/reviews/<date>-draft2/`; report BLOCKING 11 → ?.
- [x] 2026-10-19 D-7 Post the draft in the class Discord feedback thread for human feedback.
- [x] 2026-10-24 D-8 (tagged and pushed; Michael submits) Diff vs `gdd-draft-1` as a section table; tag `gdd-draft-2`; submit.

## Crew + kit (Assignment 3, due Nov 1)
- [x] 2026-10-31 C-1 Agent crew: `.crew/` roster, role cards, quests, rounds, gate; `crew/README.md` with the Mermaid diagram; run logs in `crew/runs/`.
- [x] 2026-10-31 C-2 Feed-Flora loop (crew round): `resources/estate.edn` (Flora + Juniper), `pm.world` rules, 32 tests, HUD + F key.
- [x] 2026-10-31 C-3 The kit is the world: `pm.kit` loads the local kit copy (`public/kit/`, gitignored) into the game page; `pm.brook` spatial brook in CLJS; scaffold fallback.
- [x] 2026-10-31 C-4 `pm.three` builds objects with the kit's THREE when the kit is present (one three.js per page). First step of W2-7.
- [x] 2026-10-31 C-6 Mirrors v0 (crew round `mirrors-v0`): stand-ins for Flora, her worm jar and Juniper placed in the kit's world from `:placed` data, with bubble/sign labels from their rules; pure `pm.plan` (22 tests); `pm.scene` builds once, updates labels only.
- [x] 2026-10-31 C-7 Mirrors v1 (crew round `mirrors-v1-looks`): looks as data (`:parts`, per-item `:scale` and `:yaw`); Flora as an axolotl of 15 parts at display size (~1.5 m, the size of a mid-size rock), Juniper as a horse of 13; display-scale rule in `estate.edn`.
- [x] 2026-11-06 C-9 Real state from Hal (crew rounds `real-state-v0`, `real-state-v1-cache`): the game reads `/state/mirrors.edn` (private, gitignored, kept by Hal from his Flora reminder and what Michael tells him), polls it every 5 s, and caches Hal's last word in the browser so it stays right while Hal is offline.
- [ ] C-10 Write-back: the F key / a feed card updates Hal's file and completes his reminder (needs a small local helper; the browser can't write files). Then the feed card UI (worm count you can type in).
- [x] 2026-11-08 C-11 `le-coureur-omy` (runner on omarchy): first gate on Linux once the box is up (box clone needs `npm install`; ask Michael first). **Done Nov 8:** gate PASS on Linux at `d4a6b33` (0 warnings, 627 tests, OpenJDK 27).
- [x] 2026-11-08 C-12 Round `crew-fielder-v0` (quest `.crew/quests/crew-fielder-v0.md`): the twin's prompt-fielding as a `bb crew-fielder` service (replaces Hal's private driver scripts), `:jumeaux` in `estate.edn`, no "Hal" in code, log keys `:by :twin`/`:player` (crew-up 0.2.4 contract).
- [x] 2026-11-07 C-8 Tried NVIDIA MotionBricks (round `motion-bricks-v0`: two SOMA mannequins on the creek bank with `?bricks`, one shared model, ~60 fps). Before shipping: host the model pack ourselves (gitignored). Was: Idea: try NVIDIA MotionBricks (`three-motionbricks`, Apache-2.0; weights under NVIDIA's Open Model Agreement) as the reference motion for one KayKit agent behind the motion-controller interface, after the MVP. Humanoid only (nothing for Flora). 190-760 MB model download.
- [ ] C-5 Submit Assignment 3: repo link to `crew/README.md` + crew-up link.

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

- [x] 2026-11-07 M-1 (round `foot-ik-v0`) Foot IK on slopes (Michael, Nov 7: "how do their feet know where the ground is?"): today only the whole body follows the ground (one ray under the root); after MotionBricks poses a walker, ray under each foot and bend that leg so the foot lands on the real height and tilts with the slope, pelvis dropping to the lower foot. Same tool keeps a rider's feet in stirrups.
- [ ] M-2 Take a seat: our own seated target pose (from the model's own standing pose, hips and knees bent, pelvis at seat height) wrapped as a 'sit' primitive, so the sit-down and stand-up are generated without NVIDIA's evaluation-only clips.
- [ ] M-3 Riding Juniper (Michael, Nov 7): rider parented to the saddle, foot IK to stirrup points, pelvis on a spring following his back, torso sway with his gait; mounting/dismounting as generated transitions from our own target poses. Needs Juniper to walk first (procedural quadruped legs).
- [ ] M-5 Bump and recover (Michael, Nov 7: "they aren't looking and collide, a force pushes each apart, and they recover naturally"): on overlap, an impulse on each root along the contact normal (by closing speed, decaying), a spring jolt on spine/shoulders added over the generated pose, then a MotionBricks "catch step" target (a balanced stance half a step along the push) so the stagger and recovery are generated; a glance back and an optional "sorry!" line. `?distracted` turns off one walker's avoidance to test it. Before the full ragdoll (M-4).
- [ ] M-6 Personalities (Michael: "I love the awkward reactions almost better"): per-walker `:senses` (the builder slow to notice, the tester quick), which `senses-v0` already supports. Tried Nov 7: with the builder at react 0.55 s / hear 1.0 m, the crew's lap simulations (avoid t65, characters t17) had the attentive walker still walk into him, so one-sided avoidance needs to be robust to a walker that barely reacts (pass wider, slow down behind, yield), and M-5 should catch the rest. Then set the personalities.
- [ ] M-7 Flora moves like a real axolotl (Michael, Nov 7: "natural motion into the animals as well?"): procedural spine wave for swimming, diagonal salamander walk on the bottom, gill flutter, a behaviour picker (rest / walk / swim / gulp air) with real dwell times. MotionBricks can't do animals (human and robot skeletons only). Details: `docs/ideas-animal-motion.md`.
- [ ] M-8 Juniper, practical route: a CC0 rigged horse with walk/trot/graze/idle clips blended by speed, hoof IK on the slope (pm.feet), head bob and tail swish, a behaviour layer (wander, hay when hungry, graze, doze). Details: `docs/ideas-animal-motion.md`.
- [ ] M-9 Juniper learns to walk (Michael, Nov 7: "at first it's unstable, it falls a lot, sits down, naps, but slowly over time it learns to coordinate its limbs"): physics-based RL, a simulated body with joint motors and a small policy network trained on the Omarchy GPU (MJX/Brax/Isaac, PPO), checkpoints played by Juniper's age so he visibly grows steadier over real days, a behaviour layer for sitting and napping, imitation of horse mocap later for a natural gait. First a spike: a brainless capsule pony in browser physics. Needs Omarchy online and Michael's OK for installs. Details: `docs/ideas-animal-motion.md`.
- [~] M-10 Creature pipeline step 1: GPU smoke test on the box (`cd creatures && uv sync && uv run python smoke.py`): **PASS on omarchy, Nov 8** (RTX 5070 Ti); then **browser spike PASS Nov 8** (`?pony`, `?pony=dog`; round `pony-spike-v0`); next: the MuJoCo-in-the-browser capsule pony spike. Plan: `docs/ideas-animal-motion.md` (Nov 8).
- [x] 2026-11-08 M-11 Head reflex (round `head-reflex-v0`): a lying foal lifts its head and holds it level (Michael, Nov 8); species `:reflexes`, Jacobian-transpose, same for the dog. Then: roll onto the chest, then stand (training).
- [ ] M-12 Effort (Michael: "head up, just stationary, not like it's struggling to raise"): a weak neck, a tremor that fades as he steadies, fatigue (head sinks, rests, tries again); all species data.
- [x] 2026-11-08 U-1 Remember the camera across page reloads (Michael, Nov 8: "leave the camera where it was between runs"): save the kit camera's pose to localStorage, restore when the kit is ready; probe the kit's camera controller first.
- [ ] M-4 Physics later: a Rapier ragdoll that follows MotionBricks' motion and reacts to bumps (the "reference motion" pattern); a navigation mesh (recast-navigation, MIT) once there are buildings and doors.
## Week 3 — Bridge v0 (real state)
- [ ] W3-0 The crew in the game (Michael, Nov 6): each real crew member (Herdr workspace `crew`: architect, builder, tester, runner, critic) is an avatar on the estate, its status from `herdr agent get` (working / idle / done / blocked). A member waiting at a permission prompt is **blocked** and wears the GDD's red "!" (§1 opening, §3.1 `ask_player`); clicking it shows the prompt and answers it (Yes / No), like `herdr agent send-keys`. Source: Herdr's API through the bridge (W3-1), not mock timers. This is the GDD's "snap", and tonight it would have saved three stuck rounds.
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
- O-1 Onboarding (the Construct, GDD §2.7): ask the player's sex once and name the twin; fills `:jumeaux` (`{:player {:name :sex} :twin {:name}}`). The twin is *jumeau* or *jumelle* by the player's sex (it's their twin), the pair *jumeaux* / *jumelles*; not the crew's cloud/local rule.
- Time-of-day switch (afternoon / golden / blue / night): sun angle + color, sky, window glow
- The river: start from three.js `Water2` (examples/webgl_water_flowmap, MIT) stylized to flat-shaded; DOF via `BokehPass` optional; bank; rocks as ONE scaled mesh (on purpose) → then `procedural-rock` (icosahedron + vertex noise + jitter; see offscreencanvas.com "ThreeJS Rocks Demo Explained") as the showcase before/after. Visual reference only: img/ref/hiroshi-island-*.png (closed source).
- River sound, spatial: `AudioListener` on the camera + `PositionalAudio` emitters spread along the water mesh's long axis (refDistance ~2.5, rolloff ~1.6, inverse model), each playing `public/assets/audio/brook.mp3` at a different offset; `pm.audio` already has the loop + synth fallback and a flow→playbackRate mapping (0.3–1.2×, pitch follows). Proven in the kit build (`assets/private/kit/dist/index.html`, the `pm-brook` script) — port that.
- Avatar customizer (KayKit hair/hat/clothes) as the level-up reward
- Possess toggle; autonomy dial; agents walk to the player on `needs-input`
- XP, levels, hats; estate growth tied to repo stats
- The river, and the "make the rocks procedural" demo
- Release build published to lotect.com
