# Demo script

*Two videos for the Nov 29 hand-in (below), then the Nov 21 demo-day run order (kept; its shots are
the pool both videos draw from). Everything runs on the **dev build** (`localhost:8080`, `bb
crew-bridge` up, `state/hal-session` pointed at the live Hermes chat) so Hal can speak; the live
site `pm.perstitio.us` (demo build, SAMPLE only, PIN) appears only as the deploy proof. Audio is
all the game's own: soundtrack (Construct tracks, then the sky's tracks), the brook, Hal's voice.
Capture: `screencapture -v -A` (system audio), 1440×900 points; cut with ffmpeg.*

## The two cuts (Nov 29)

**Structure (Michael, Nov 22):** show how Hermes and Herdr do the work today, then the *same
action* in the game, as match cuts, so the advantage of the gamification is visible without
saying it: one place, the world tells you who's waiting, you talk the way you talk on your phone,
and you can leave. Every pair below is a shot in Hermes/Herdr followed by its twin on the estate.

| # | Today (Hermes / Herdr / terminal) | In the game | What it shows |
|---|---|---|---|
| M1 | You type to Hal in the Hermes chat | Click Hal, say it in 💭; he answers on his plate, in his voice, or walks over | the twin is a person in a place, not a prompt box |
| M2 | A Claude member stops on a permission prompt in a Herdr pane; you find the pane, read, answer | A "!" over that walker; click it: before → proposed ghost; Approve | you see *what* you're approving, and *who's* waiting, from anywhere |
| M3 | `herdr pane list`, five panes, busy/idle by reading them | The crew key: dots, names, "!"s; click a row and the camera follows | status is ambient, not polled |
| M4 | `/usage` typed into a pane; read the panel | Hal: "Claude's used 83% till 2 PM"; the juice meter | limits are part of the world, spoken |
| M5 | Flora's 09:00 text from the bot's number; you reply FED | Flora's jar, her worm count, her "!"; tick the chore in her thread | real life mirrored, same loop |
| M6 | A member needs a word while you're away from the laptop: it waits | 📞 on a contact; the call reaches your real phone (T-11/T-12) | the agent can reach you where you are |
| M7 | `bb content mirror-lines` scrolls in a terminal (take 1) | Flora says the new line | content lands in the engine with no hands |
| M8 | Closing the laptop, the crew still running | The hardline rings; jack out; the work waits | the game sends you back to life |

Promo = M1–M8 as fast match cuts (~5 s a pair) framed by S1/S11; the pipeline video = the same
pairs with room, plus S4–S8 in order as the one chain: ask → crew → gate → `main` → live.

**Promo** (45–60 s, the README header and the lotect.com game page): the feeling. **Pipeline**
(3–4 min, A7 deliverable 2): the same shoot, with the you → twin → crew → gate → `main` → live
chain given room, the content loop as one fast-forwarded section with a cost card. One piece can
serve both at different lengths.

| # | Scene | Hal's line (spoken in-game) | Promo | Pipeline | Source |
|---|---|---|---|---|---|
| S1 | Title card → fades into the live estate | — | 5 s | 5 s | key art (to make) + estate |
| S2 | The white room, a glimpse; the twin walks in | "Do you read me?" | 3 s | 10 s | `?construct`, fresh profile |
| S3 | Hal walks to you and speaks (plate + caption + voice) | "Welcome back. Two letters, neither urgent." | 6 s | 10 s | `bb hal-say … --at mailbox` |
| S4 | You ask Hal in 💭; the line lands in Hermes | "On it." | 4 s | 20 s | split screen: estate + Hermes |
| S5 | The crew at work: Herdr panes, walkers go busy | "The crew's on it. I'll gate it." | 6 s | 30 s | film during the T-11 round |
| S6 | A "!" goes up; you answer in-world; before → proposed; approve; the world changes | "Round or jagged?" | 8 s | 20 s | the rock blueprint, or the round's real question |
| S7 | Content loop, fast-forward; Flora says a new line | "Ask her." | 5 s | 30 s + cost card ($1.30, Evaluator the dearest) | **shot** (take 1) |
| S8 | Gate → commit → `bb demo-deploy` → live tab (PIN) → Flora | "Same file, same build, no hands." | — | 25 s | take 1 tail (reshoot the live tab) |
| S9 | Time of day: Afternoon → Golden → Night by the creek, soundtrack shifts | "The sky, the lamps and the music follow your real clock." | 6 s | 10 s | kit's sky row |
| S10 | The hardline rings, the world freezes gently, jack out | "Real life's calling. Go. The work waits." | 6 s | 10 s | booth |
| S11 | End card: title, tagline *What is even real anymore?*, URL | — | 5 s | 5 s | key art |

### Capture checklist

- [x] S7 content loop, one take, narrated (`assets/private/video/pipeline-take1.mov`, 13:20; beats in `pipeline-take1.log`; dead air 6:40–10:30 is a pager stall, cut it)
- [ ] Key art render (S1, S11): dusk estate, Hal foreground, Flora's jar lit, a "!" up
- [ ] S2 white room glimpse (fresh Chrome profile, `?construct`)
- [ ] S3 errand to the mailbox
- [ ] S4 💭 ask → Hermes (split screen; `state/hal-session` set first)
- [ ] S5 Herdr + busy walkers (during the T-11 round)
- [ ] S6 "!" answered in-world, blueprint approve
- [ ] S8 live-tab tail (you type the PIN, off-camera or masked)
- [ ] S9 sky row Afternoon → Golden → Night, by the creek
- [ ] S10 booth rings, jack out
- [ ] Promo cut (45–60 s) → `assets/private/video/promo.mp4`, then unlisted YouTube
- [ ] Pipeline cut (3–4 min) → `assets/private/video/pipeline.mp4`, then unlisted YouTube
- [ ] Links into README (top block) and `docs/assignment-7-pipeline.md`

### Recording rules (learned Nov 22)

- Shoot on localhost; the live site can't hear `hal-say` (DEMO build has no bridge).
- Hermes may be on screen as "your agent"; keep Herdr's Terminal window unmoved and never close
  a Terminal window by script (one Cancel dialog cost a take).
- Terminal commands in a take get `--no-pager` (`git diff` stalled the deploy four minutes).
- Record before 22:15 local or the closing-time card is on screen.

## Demo-day run order (Nov 21; about 3 minutes)

| # | Shot | What to say | How |
|---|------|-------------|-----|
| 1 | **The white room** | "A new player, no accounts. Your twin meets you in the Construct." | fresh browser profile (or `?construct`); Jack in; voice on |
| 2 | **Program rack → the estate** | "Every connection loads as a pretend version first. Here's the estate." | click through the rack; the house, the creek, the crew wandering |
| 3 | **The "!"** | "A worker needs a word. That's the only way an agent can interrupt you." | the SAMPLE worker's "!" over its head and in the crew key; click it |
| 4 | **Before → proposed, approve** | "You see what you're approving before you approve it." | the rock blueprint: NOW spiky → PROPOSED round ghost; Approve; the rock changes |
| 5 | **The booth: jack out, jack in** | "Real life rings. Jack out; nothing breaks; the work waits." | the phone booth rings; Jack out; Jack back in |
| 6 | **The crew key** | "Who's on the estate, what they're doing, where. Click to follow." | click two rows; the camera flies and follows |
| 7 | **Talk to your twin** | "Click Hal. In the demo he answers that he's only reachable from a real game." | click Hal → say… → his answer on his plate and the caption |
| 8 | **Hal talks back** (**dev**) | "In a real game that box reaches his chat, and he answers on the estate." | `bb hal-say "Felt that? That's me."` → plate + caption + 💭; his dot amber while he thinks |
| 9 | **An errand** (**dev**) | "Or he walks over and tells you there." | `bb hal-say "Two letters, neither urgent." --at mailbox` → he walks to the mailbox and says it |
| 10 | **Mirrors: SAMPLE ↔ REAL** | "Flora's worm count is the real one from my bot. Everything else is a sample until you plug yours in." | ⓘ panel: the toggles; Flora (Hal) on the HUD |
| 11 | **Time of day** | "The sky follows the clock; the brook gets louder by the water." | the kit's Afternoon → Golden → Night row; walk to the creek |
| 12 | **Closing time** | "A day is one round. At bedtime the estate closes." | `?hour=` can't fake this: the real clock; show the Staying past closing? card if it's late, else say it |

## Things to show (running list)

- The twin's speech plate, the caption and the 💭 history are the same line in three places (T-1).
- The twin's dot: hollow → amber from a say until he answers (T-3).
- The demo twin speaks a SAMPLE line every 45 s so the estate has a voice with nothing real behind it (T-4).
- Errands: a straight leg to 1.2 m off the place, said there, then back to wandering (T-2).

## Cut from v1 (shown as "next", not demoed)

- A night music track (T-6); re-probing wander spots after a build (T-7); jack-out priority and the Construct as an onboarding skill (T-8); living objects (T-9, v2).
- Physics walkers, creature training, the shared world: deferred in the GDD since draft 2.

## Recording notes

- Record the demo build from `dist/demo` served at a site root (`python3 -m http.server 8095` in `dist/demo`), in a real Chrome window, 1600×1000, sound on (the brook and the music are part of the pitch).
- The dev shots (8, 9) need: `npx shadow-cljs watch app`, `bb crew-bridge`, and `state/hal-session` for the relay; run the `bb hal-say` lines from a second terminal.
- Record before 22:15 local or the closing-time card is on screen (bedtime is `22:30` in `estate.edn`).
