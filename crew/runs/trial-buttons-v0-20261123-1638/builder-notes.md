# Builder notes — trial buttons v0 (TASKS T-14)

## What was built

- `resources/estate.edn`: `:blueprint-text` only — `:trial` reworded (the buttons say the rest
  now), new `:make-real` "Make it real", `:undo` "Undo", `:saving` "Saving…".
- `src/pm/blueprint.cljs`: `approve-blueprint` now also records `:trial-order` (unknown id:
  unchanged, no key added). New: `unapprove` (approved → pending; no-op/`identical?` otherwise),
  `card-owner` (latest trial, `:trial-order`-ordered, falling back to the last `:approved` entry),
  `card-lift-m` (0.3), `trial-card` (the card's `{:id :buttons}`, or nil without an owner or
  without all three words).
- `src/pm/pick.cljs`: `screen-of` — NDC point + canvas size → `{:x :y :visible?}`, pure, no DOM.
- `src/pm/three.cljs`: `top-center` (world bbox top-centre), `project-ndc` (`Vector3.project`),
  `unlight-glows!` (reverses `light-glows!`: removes/disposes a built lamp's point light so an
  undone one stops shining).
- `src/pm/scene.cljs`: `css-color` (palette key → `"#rrggbb"`, for the card's colours); the
  `sync-blueprints!` status loop gets a `:pending` branch — only when the *previous* status was
  `:approved` (an Undo) does it `unlight-glows!` then `ghost!` the mounted group; a freshly-drawn
  `:pending` (no previous `:approved`) does nothing, as today.
- `src/pm/ui.cljs`: `set-trial-card!` — `#pm-trial-card`, two buttons rebuilt only when the card
  changes, position/colours set every call, `stopPropagation` on all the listed pointer/mouse/
  wheel/contextmenu events so the card never drives the canvas underneath.
- `src/pm/core.cljs`: `!saving` atom; `make-real!` takes a `done` thunk, called exactly once on
  every path; `real-click!` (guards `@!saving` + status, used by both the item click and the
  card's button) replaces the old direct `make-real!` call in `on-blueprint-click!`'s `:approved`
  branch; `undo-trial!`; `trial-card!`, called every frame right after `scene/update!` when
  `(kit/handle)` — computes the card's screen position from the owning blueprint's group
  (`t/top-center` + `card-lift-m`, `t/project-ndc`, `pick/screen-of` against the renderer canvas's
  `getBoundingClientRect`) and wires its two buttons to `real-click!`/`undo-trial!`.

## Checked by hand

- Ran the gate (`.crew/gate.sh`): **0 warnings**, and the tester's `pm.trial-card-test` (already
  landed) is green along with the rest — 1191 tests, 7385 assertions.
- Hand-traced every case in spec §5 (U1–U8, T1–T3, O1–O9, C1–C6, P1–P8, E1–E3) against the final
  `pm.blueprint`/`pm.pick` code before running the gate; all matched.
- Did not open the compiled page myself this run (no browser here). Hal/Michael, headless or by
  hand:
  - the card: `#pm-trial-card` (hidden via `display:none` when there's no trial or it's off
    screen/behind the camera)
  - Make it real: `#pm-trial-card [data-action="real"]` (`.trial-real`; reads "Saving…" and is
    `:disabled` while the request is in flight)
  - Undo: `#pm-trial-card [data-action="undo"]` (`.trial-undo`)
  - a ghost to click first: the group named `[:blueprint :bench-by-mailbox]` in the kit's scene
    (the existing pick); the card should appear above it within the next frame, following the
    camera; Undo should turn it back to the blue ghost and drop the card; Make it real with no
    bridge running should give the same `:no-bridge` caption as before.

## Known limits (spec §7, not fixed)

Buttons on a pending ghost, the morning tray, a reject button, trials across reloads, and the
demo build's bridge are all unchanged/out of scope, as the spec says.
