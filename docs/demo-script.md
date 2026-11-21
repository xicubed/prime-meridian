# Demo script (demo day Nov 21; final Nov 24)

*The shot list for the demo video and the live run. Everything below runs in the demo build (`bb demo-build` → `dist/demo/`, pretend data only) unless marked **dev**, which needs the dev build on :8080 with `bb crew-bridge` running.*

## Run order (about 3 minutes)

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
