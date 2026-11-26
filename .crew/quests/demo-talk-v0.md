# Quest: demo talk without a bridge (TASKS F-5)

**Game:** Prime Meridian. **Asked by:** Michael, Nov 26. **Led by:** Hal.

In the public demo build there is no bridge. Today every line you type in 💭 still tries the
bridge first, so:

1. To the twin: your bubble reads "You → <twin>: put a bench here · not sent: bridge off", and
   then the demo's pretend twin answers and builds it anyway. It reads as a bug.
2. To a crew member: your bubble reads "· not sent: bridge off" and nobody answers.

Fix both, demo only.

## What exists (read first)

- `src/pm/core.cljs` `on-say` (the one say path for typed and spoken text): `talk/crew-say`
  adds your entry as `:sending`, `post-crew!` resolves it via `talk/crew-result` +
  `crew/outcome` (no bridge → `{:result :error :reason "bridge off"}` → `:failed`), then for the
  twin with no bridge: `demo-ask!` in DEMO, else the `:no-bridge` twin line.
- `src/pm/talk.cljs`: `say-states #{:sending :sent :failed}`, `crew-say`, `crew-result`,
  `crew-label` (the "· not sent: …" text), `entry`.
- `src/pm/crew.cljs` `outcome`. `pm.world/twin-says`, `no-bridge-line`, `twin-walker`;
  `pm.jumeaux` (`j/twin`, the twin's name from `:jumeaux`). Members' own lines in 💭 come from
  world events with `--as <member>` (`:say` events, `hal-say`); find how a member's line is
  added to talk and reuse it.
- `resources/estate.edn` `:twin-talk {:no-bridge …}`; `{twin}` placeholders are already used
  (`:today :rows :build`).
- `PLAY.md` lines 71–77 promise "your message to them shows *not sent*".
- Conventions: `.crew/CONVENTIONS.md`, gate `.crew/gate.sh`, `AGENTS.md`; never read
  `assets/private/`, `public/kit/` or `state/`; no npm deps.

## Decided (don't reopen)

- **Only when `DEMO` is true.** The dev build with or without a bridge behaves exactly as
  today (a dev build with the bridge off still shows "not sent: bridge off": that is a real
  failure there).
- **In the demo, no bridge POST at all** for a say: your entry resolves as `:sent` (no
  "not sent" suffix), to the twin and to members alike.
- **To the twin:** unchanged otherwise: `demo-ask!` answers as now.
- **To a crew member:** after your line, that member answers in its own thread with a line
  from data: new estate key `:member-talk {:demo "Ask {twin}, who runs the crew. In the full
  game I can answer you myself."}` (`{twin}` filled from the twin's name via `:jumeaux`; no
  name in code). The answer shows where Michael reads it: the member's 💭 thread (and its
  speech plate if the existing member-line path does that anyway).
- The decision "DEMO + who it is for → which reply" is a **pure function** (in `pm.talk` or a
  small cljc), tested; `on-say` only wires it.
- Everyone's game: no person, pet or member named in code.

## Out of scope

Real agents in the demo; a member answering anything beyond the one pointer line; the dev
build's no-bridge text; new UI.

## Done when

Gate passes with tests for: demo say to twin → `:sent`, no failure text; demo say to a member →
`:sent` plus the member's pointer line with the twin's name filled in (from a fixture with a
different twin name too); non-demo with no bridge → still `:failed · not sent: bridge off`.
The builder leaves `{run}/builder-notes.md` saying how Hal checks it in `dist/demo` headless
(`bb demo-build`, serve, open 💭, message the twin and one member, what to read back).
PLAY.md's lines 71–77 match the new behaviour (le-docteur).
