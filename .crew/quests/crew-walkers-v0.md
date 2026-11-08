# Quest: the whole crew on the estate (crew-walkers v0)

**Game:** Prime Meridian. **Asked by:** Michael: "let's get the whole crew in here" (and Le
Docteur, who was off, is back on). **Led by:** Hal. Follows `crew-talk-v0`. TASKS W3-0.

## The moment we're building

Six walkers, one per running Mac member, each with its live status dot and its own patch:
Un Automate and Le Testeur on the west bank (as now), **Le Critique** pacing the woods' edge at the
north end of the west bank, watching them; **Un Architecte** walking slowly on the east bank's north
side, stopping to think; **Le Coureur** doing a longer, quicker loop on the east bank's south side;
**Le Docteur** up on the south plateau by Flora. Each looks different at a glance.

## Data (`resources/estate.edn` `:walkers`; nothing named in code)

Routes Hal measured with `?probe` (top hit is open ground; heights in m):

| walker (`:agent`) | route `[x z]` | ground | notes |
|---|---|---|---|
| `:un-architecte` | `[6 -4] [4 -6] [3.5 -7.5]` | ~1.6-1.7 | `:pause-s 8` (thinking) |
| `:le-coureur` | `[3 -8.5] [5 -10] [3 -12] [2.2 -10]` (a loop) | ~1.5-2.1 | quicker: a `:speed` (default walking pace) of ~1.6× if the motion supports it; else a shorter `:pause-s 1` |
| `:le-critique` | `[-8 -4] [-7.2 -5.2]` | ~2.0 | `:pause-s 6`, faces the west bank walkers while paused if easy |
| `:le-docteur` | `[-6 -17.5] [-6 -20]` | 3.5 | `:pause-s 6`; the rock at `[-5 -17]` is not walkable |

Routes may be nudged where the walkability check says no (ground step, water, rock): keep them in
their patch and say what you changed in your reply. Loops: say how a route closes (back to the first
point) if the motion code needs a flag for it.

**Looks:** Un Automate keeps the Polybot; the rest use NVIDIA's mannequin, each with a `:tint`
(multiplies the mannequin's material colour; keep Le Testeur white/untinted): architect
`0x4a7bd0` blue, runner `0xe8833a` orange, critic `0x8e5cc4` purple, docteur `0x2f9e8f` teal. Tints
are data; cloning materials per walker so they don't share one.

**Names:** a walker's nameplate defaults to its agent's roster `title` (from `state/crew.edn`
`:titles`, falling back to `:name` in the data), so a new member needs no code.

## Cost (measure, then keep it in budget)

MotionBricks runs one shared model; six walkers mean more generations. Keep 60 fps on this Mac:
if generations queue up, stagger them (no frame starts more than one; a walker that waits keeps its
current clip). The HUD's `gen … ms` stays; add `· queue n` when n > 0. Kinematic mode must still
handle six walkers too.

## What exists (read first)

`resources/estate.edn` (`:walkers`, `:motion`), `pm.motion` (routes, plan, pauses), `pm.bricks`
(loading, generation, characters, labels), `pm.characters`, `pm.crew` (dots, titles), `pm.avoid`,
`pm.feet`. Gate `.crew/gate.sh`. Don't read `assets/private/`, `public/kit/`; you may read
`state/crew.edn` (names and states only). Don't edit `public/vendor/` or `resources/content/`.
**Never send keys or prompts to crew members.**

## Out of scope

Robust avoidance for oblivious walkers and bump-and-recover (M-5, M-6, the next round), Hal as a
walker, the omarchy runner, replies in 💭, new character models.

## Done when

Gate passes with tests for: six walkers from the data with their agents, tints and routes; every
route point walkable per the existing ground data the tests use (or the test stubs, as the crew's
earlier walker tests do); the title fallback; the tint on cloned materials; the generation stagger
(at most one start per frame, queue count). Live (Hal): six walkers in their patches, distinct
colours, dots right for each, 60 fps with `?bricks`, `gen` and `queue` in the HUD.
