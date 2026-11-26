# Quest: ask the demo's twin to build something (final project, due Dec 6)

**Game:** Prime Meridian. **Asked by:** Michael, Nov 25. The final project's win includes "build
something by talking to your twin (chat or voice)" (round win-the-day-v0 adds the goal). On
Michael's own machine the twin is a real agent (Hal) who routes the request to the crew; on the
public demo there is no agent and no bridge, and today the demo's twin only answers "Out here I
only hear you through the hardline…". This quest gives the demo its **pretend version** (GDD
rule: every connection has an in-game pretend version): you type or say "put a bench here" to
the twin in 💭, he answers in-world, and a neon-blue ghost appears on level ground at the centre
of your screen; you try it and make it real, and it stays real in that browser. **Led by:** Hal.

## What exists (read first)

- `src/pm/lot.cljc` (pure, T-13): `place` finds the closest level, open spot near a landmark or
  an `[x z]` spot on a **ground map** `{:origin :step :cols :rows :kinds [...] :y [...]}`; kinds
  `o` open, `T` canopy, `B` bush/wall, `R` rocks, `:` gravel, `~` water, space none. `feet`,
  `look-radius`, `refusal` (readable reasons), `blueprint`, `landmarks`.
- `src/pm/three.cljs` `center-hit` (screen-centre ray, T-19) and `drop-ray`; ground meshes are
  found by name prefix (`:motion :ground` in `estate.edn`, `t/find-meshes`, `m/ground-name?` in
  `pm.core/view-ground-meshes!`). The kit's cover meshes are named `…Leaves`, `…CanopyMass`,
  `…WoodlandNear`, `…TreeWood`, `…Rocks`, `…Gravel`, the water surface's name contains `creek`.
  How Hal classified cells from two rays (ground hit + first hit of the cover set; cover more
  than 2 m above ground = `T`, 0.35–2 m = `B`): the sampler recipe is summarised in
  `state/ground.edn`'s header comment (read the comment only).
- `src/pm/core.cljs`: `say-entry!` (the 💭 send path; the demo twin's no-bridge reply is there),
  `apply-blueprint-file!` (merges a blueprints "file" map into the world, applies real builds,
  seats the new ones, adds the author's line: reuse it), `make-real!` / `real-click!` /
  `remove-click!` (bridge-only today; refused with the `:no-bridge` words when there's none),
  `DEMO`, `bridge-on?`. `pm.call` routes 🎙 speech to the same say path.
- `pm.world/twin-says`, `no-bridge-line`; `resources/estate.edn` `:twin-talk`, `:blueprint-text`,
  `:build-rules`, the blueprint look format (comment under `:blueprints`).
- Round win-the-day-v0 (just landed): goal 2 ticks when any blueprint turns `:real`.
- Conventions as before: `.crew/CONVENTIONS.md`, gate `.crew/gate.sh`, `AGENTS.md`; never read
  `assets/private/`, `public/kit/` or `state/` (except `state/ground.edn`'s comment); no npm deps.

## Decided (don't reopen)

- **Only in the demo build** (`DEMO` true). With a bridge, the words go to the real twin as
  today; the dev build without a bridge also keeps today's no-bridge answer.
- **The request parser is pure** (new `src/pm/ask_build.cljc`): text → `{:look <key> :near
  :here | <landmark-key>}` or `{:help true}`. Words come from data, not code: a new estate block
  `:demo-builds {:looks {<key> {:summary "…" :look {:parts [...]}}} :words {"bench" :bench "seat"
  :bench "lamp" :lamp-post …} :places {"here" :here "mailbox" :mailbox "creek" :creek "booth"
  :phone-booth …} :lines {:placed "…" :refused "…" :help "…" :made-real "…"}}`. Ship at least
  four looks (bench, lamp post, table, planter), each grounded on y 0 with feet, ≥ 0.08 m parts,
  `:brown`/`:tin`/`:gold` palette keys. No place word = `:here`. A request with no look word gets
  the help line ("I can build a bench, a lamp post, a table or a planter. Try: put a bench here.").
- **The ground map is measured in the browser**, never shipped (the kit's licence forbids
  extracted datasets): a grid of `step-m` cells covering `search-m` around the target, two rays
  per cell (ground meshes; ground + cover set), classified like Hal's sampler, built into the same
  map shape `pm.lot/place` takes. Interop in `pm.three` (one fn returning the map), logic in pure
  code. Keep it under ~50 ms (one-off per request; the step and size from `:build-rules`).
- **"Here"** is `center-hit` of the screen centre; nil (sky) → the twin says so and asks you to
  look at the ground.
- **The build:** a new id (`<look>-<n>`, unique in the world and the demo's saved list), summary
  from the look, `:by` the twin's agent id, placed by `pm.lot/place` with the estate's
  `:build-rules`; the result is merged with the existing `apply-blueprint-file!` path from a demo
  "file" kept in localStorage `pm.demo-builds.v1` (`{:as-of ms :blueprints [...] :real [...]}`),
  so it seats, draws, and announces itself with no new drawing code. The twin then says the
  `:placed` line in-world (and in 💭). A refusal: the twin says `:refused` plus `pm.lot`'s reason.
- **Make it real and Remove in the demo** record into the same localStorage key instead of the
  bridge (demo only: everything there is SAMPLE). Estate sample blueprints count too. Reload keeps
  them; `?fresh` clears the key. The dev build without a bridge keeps refusing, as now.
- **Pure parts tested**, interop thin. Everyone's game: no person, pet or member named in code.

## Out of scope

Real agents in the demo; any language model in the browser; new UI controls (the existing 💭
composer, 🎙 and the trial card are the whole interface); walkers avoiding builds.

## Done when

Gate passes with tests for the parser (look words, place words, help, default here), id choice,
the demo file merge (new blueprint added once, real list applied, remove), and the localStorage
shape round trip. The builder leaves `{run}/builder-notes.md` with how Hal checks it in the demo
build headless: `bb demo-build`, serve `dist/demo`, type "put a bench here" into the twin's
thread, and what to read back (the world's blueprints, the twin's line, `pm.demo-builds.v1`).
