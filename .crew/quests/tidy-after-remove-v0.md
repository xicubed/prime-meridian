# Quest: tidy-ups after removal (TASKS T-16)

**Game:** Prime Meridian. **Asked by:** Michael, Nov 23, after T-15 (remove a real build):
"we dont need that sign up by the house do we? … ya just remove the sign", plus the two leftovers
Hal found checking T-15 headless. Three small changes, one round. **Led by:** Hal.

## What exists (read first)

- `resources/estate.edn` `:lobby {:at :yaw :text :look}`: a signpost (post + board) at the
  cottage's steps with the text "Lobby & kitchen — bring your questions here". The lobby is also
  an errand destination (`bb hal-say --at lobby`, `pm.motion`/errand code) and appears in
  `pm.plan` / `pm.scene` as a scene item; grep `src/` and `test/` for `:lobby`.
- The build card (`trial-buttons-v0`, `remove-build-v0` runs): `pm.core/trial-card!` places it
  above the sign each frame through `pm.pick/screen-of` (pure: NDC + size → `{:x :y :visible?}`);
  `pm.ui/set-trial-card!` (`#pm-trial-card`, `transform: translate(-50%,-100%)`, so `:y` is the
  card's bottom edge).
- Blueprint signs: `pm.blueprint` builds each sign's text (summary on the first line, the
  status words from `:blueprint-text` on the second); every shipped and agent blueprint's
  `:summary` reads "proposed: <thing>".
- Conventions: `.crew/CONVENTIONS.md`; gate `.crew/gate.sh`; `AGENTS.md`. Don't read
  `assets/private/`, `public/kit/`. **Never send keys or prompts to crew members; don't start
  services or the bridge; no whole-disk `find`; scratch files only in your own scratchpad;
  never write `state/` from tests.** No npm deps.

## Decided (don't reopen)

1. **The lobby signpost goes; the lobby stays.** Remove the lobby's `:look` and `:text` from
   `estate.edn` so nothing is drawn or labelled there; keep `:lobby {:at :yaw}` as the errand
   destination (`--at lobby` still walks there). Code that drew the lobby item must handle a
   lobby with no `:look`/`:text` by drawing nothing (no empty sign, no error). Tests that counted
   the lobby's scene item or label are updated to match; errand tests keep passing unchanged.
   GDD §2.4's Lobby row stays (the cottage is the lobby); le-docteur notes that it has no sign.
2. **The card stays on screen.** When the card's anchor is above the top of the canvas (camera
   close to a tall item) but the item itself is on screen, the card is pinned at the top edge
   (its bottom edge at `card-height + 8` px from the top) instead of disappearing or hanging off
   screen. Extend `pm.pick/screen-of` with an optional min-y (pure, tested); `pm.core` passes the
   card's measured height. Off the left/right/bottom or behind the camera: hidden, as today.
3. **A real build's sign doesn't say "proposed".** For a `:real` blueprint, the sign's first
   line drops a leading "proposed: " (case-insensitive) from its `:summary`, so "proposed: a
   bench by the creek" reads "a bench by the creek". Pending, trial and removing signs are
   unchanged. Pure, in `pm.blueprint`, tested (with and without the prefix).
- **Everyone's game:** words are data; nothing names a person, pet, member or blueprint in code.
  No `::k` across namespaces.

## Out of scope

Making agents actually walk to the lobby with questions; any other sign; the kit's cottage itself.

## Done when

Gate passes with tests for: no lobby scene item or label when the lobby has no `:look`/`:text`
(and the lobby still resolves as an errand spot); `screen-of` with min-y (anchor above the top
pinned; inside unchanged; off the sides/bottom/behind still hidden); the real-sign summary with
and without the prefix, and unchanged for pending/trial/removing. Builder note in the run dir
says what Hal should look at headless.
