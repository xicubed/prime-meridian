# Quest: a trial build gets labelled buttons (TASKS T-14)

**Game:** Prime Meridian. **Asked by:** Michael, Nov 23, after making the first agent-drawn
bench real: "i didnt realize u needed to click it twice … that would be helpful especially for
new people and the demo." His standing rule: **every action on a screen is a labelled button**
("give buttons to everything"), never faint hint text. **Led by:** Hal. Michael reviews; the
demo's S6 scene (`docs/demo-script.md`) uses it.

## The moment

You click a blue ghost: it builds as a **trial** (real colours, a reload clears it). Today the
only way on is the sign's hint "trial · click to make it real · right-click to undo", which he
missed. After this round, the trial shows a small card **above the built item** with two
labelled buttons: **Make it real** and **Undo**. Make it real = today's second click. Undo = the
trial goes back to its blue ghost (pending), so you can try it again later. The card follows the
item on screen as the camera moves and hides when the item is off screen or behind the camera.

## What exists (read first)

- `src/pm/core.cljs`: `on-blueprint-click!` (pending → `apply-approved!` = trial; approved →
  `make-real!`, which posts to the bridge or has the twin say why not), the right-click handler
  (`bp/reject-blueprint`), `say!`, the per-frame tick, `revert-to-last-good!`.
- `src/pm/blueprint.cljs` (pure, tested): statuses `#{:pending :approved :real :rejected}`,
  `approve-blueprint`, `make-real`, `reject-blueprint`, `status`.
- `resources/estate.edn` `:blueprint-text` (`:trial`, `:real`, `:no-bridge`, `:steep`,
  `:failed`): the sign/twin words; button labels go here too (data, not code).
- `src/pm/ui.cljs`: the shared `.pm-btn` style and how other labelled buttons are built
  (`pm-btn back`, `pm-btn call`); the HUD/corner rules.
- `src/pm/scene.cljs` / `pm.three`: the blueprint groups are named `[:blueprint <id>]` in the
  kit's scene; `window.hiroshi.camera` is the camera.
- Conventions: `.crew/CONVENTIONS.md`; gate `.crew/gate.sh`; `AGENTS.md`. Don't read
  `assets/private/`, `public/kit/`. **Never send keys or prompts to crew members; don't start
  services or the bridge; no whole-disk `find`; scratch files only in your own scratchpad;
  never write `state/` from tests.** No npm deps.

## Decided (don't reopen)

- **One card at a time:** the card belongs to the most recent trial (status `:approved`). It
  disappears when that blueprint becomes real, goes back to pending, or is rejected. A page
  reload clears trials, so no card after a reload.
- **Make it real** calls exactly the existing `make-real!` path (bridge, twin lines, no-bridge
  refusal unchanged). While the bridge request is in flight the button reads **Saving…** and is
  disabled, so a double click can't post twice.
- **Undo** sets the blueprint back to `:pending` with a new pure fn in `pm.blueprint`
  (`unapprove`; no-op unless the id is `:approved`) and redraws it as the ghost (the same
  un-ghost/ghost path `sync-blueprints!` already uses). It is not a reject. Right-click keeps
  today's behaviour.
- **Placement:** the card's screen position is computed each frame from the blueprint group's
  top (bounding box max y plus 0.3 m) projected through the camera; a pure fn takes the
  projected NDC point and the viewport size and returns `{:x :y :visible?}` (hidden when
  `|x|>1`, `|y|>1` or behind the camera, `z>1`). Card centred on x, bottom edge at y. No
  `js/Date` or DOM in that fn; tested.
- **Look:** buttons use `.pm-btn` (same size as the phone's buttons), the card uses the
  in-world plate's colours (light, outlined, readable on grass), 8 px between buttons.
  The trial's sign text drops the click hints: `:trial` becomes "trial: try it out" (or
  similar wording from `:blueprint-text`), since the buttons now say it.
- **Clicks on the card never reach the canvas** (no camera pan, no pick on the ghost behind
  it): stop propagation on the card.
- **Everyone's game:** labels are data in `:blueprint-text`; nothing names a person, pet,
  member or specific blueprint in code. No `::k` across namespaces.

## Out of scope

Buttons on a pending ghost (clicking the ghost still makes the trial); the morning approval
tray; a reject button; persisting trials across reloads; the demo build's bridge (unchanged).

## Done when

Gate passes with tests for: `unapprove` (approved → pending; pending/real/rejected unchanged;
unknown id no-op); the placement fn (on screen, off each edge, behind the camera); which
blueprint owns the card (the latest trial; none when no `:approved` blueprint); the new
`:blueprint-text` keys exist and are non-blank. The builder leaves a note in the run directory:
what was built and what Hal should click headless (selectors for the card and its two buttons).
