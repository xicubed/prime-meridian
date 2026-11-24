# Quest: remove a real build, try-first (TASKS T-15)

**Game:** Prime Meridian. **Asked by:** Michael, Nov 23, after the trial card (T-14): "once its
made real its there for good … should we have the undo or delete come up". He chose a **Remove**
button with the same try-first step as building. This changes the earlier rule that a real build
"can't be dismissed": now it can, but only through a labelled, previewed, bridge-recorded step.
**Led by:** Hal. Michael reviews.

## The moment

Click a real build (the lamp post, the creek bench). The same card as the trial (T-14) appears
above its sign with **Remove…** and **Close**. Remove… previews the loss: the item turns into a
see-through **removal ghost** (warm red fill and edges, clearly not the blue "proposed" ghost)
and its sign reads "will be removed". The card now offers **Remove it** and **Keep it**. Keep it
puts the item back as it was. Remove it records the removal through the bridge; the item vanishes
and stays gone on every reload. No bridge: refused, the twin says why, nothing changes.

## What exists (read first)

- The trial card (round `trial-buttons-v0`, `crew/runs/trial-buttons-v0-*`): `pm.blueprint/
  trial-card`, `card-owner`, `unapprove`; `pm.ui/set-trial-card!` (`#pm-trial-card`, buttons
  `[data-action]`, clicks stopped from reaching the canvas); `pm.core/trial-card!` (placement
  above the sign each frame), `real-click!`, `undo-trial!`, `!saving`, `make-real!`,
  `post-real!`, `on-blueprint-click!` (today `:real` → nil).
- `pm.blueprint`: statuses `#{:pending :approved :real :rejected}`, `apply-real-builds`
  (`state/builds.edn` `{:real [id ...]}`), `make-real`, `reject-blueprint`; `merge-file` and
  `state/blueprints.edn` (round `live-blueprints-v0`).
- `pm.three/ghost!` / `unghost!` (neon blue; stores real colours in userData) and
  `light-glows!` / `unlight-glows!` (a built lamp's light).
- `pm.bridge` (`add-real-build`, `known-build-ids`, `real-id-re`) and
  `scripts/crew_bridge.clj` `make-real!` (`/real`, `:action :real-build`), writing
  `state/builds.edn`. `scripts/blueprint_add.clj` + `pm.blueprint-file`.
- `resources/estate.edn` `:blueprint-text` (all card and sign words are data here).
- Conventions: `.crew/CONVENTIONS.md`; gate `.crew/gate.sh`; `AGENTS.md`. Don't read
  `assets/private/`, `public/kit/`. **Never send keys or prompts to crew members; don't start
  services or the bridge; no whole-disk `find`; scratch files only in your own scratchpad;
  never write `state/` from tests (temp files only).** No npm deps.

## Decided (don't reopen)

- **One card, three modes** (generalise the T-14 card, don't add a second one): `:trial` (Make it
  real · Undo, as today), `:real` (Remove… · Close), `:removing` (Remove it · Keep it). One card
  at a time. Clicking a real build opens its `:real` card; Close, clicking another build, or a
  reload closes it. The card's owner/mode is pure (`pm.blueprint`), tested.
- **The removal preview is a new status `:removing`** (pure fns `start-removal`, `keep-it`:
  real → removing → real; no-op otherwise). Drawn as the removal ghost: add a colour option to
  `ghost!` (warm red, e.g. fill `#ff5a4a`, edges `#ffb0a0`, same opacity as the blue) rather than
  a second ghost fn; a lamp's light goes off while removing and back on with Keep it. Its sign
  shows `:removing` text from `:blueprint-text` ("will be removed").
- **Remove it** posts `/real` with `:action :remove-build` and the id. The bridge (pure rule in
  `pm.bridge`, e.g. `remove-real-build`) drops the id from `:real` and adds it to a new
  `:removed [id ...]` list in `state/builds.edn`, refusing ids that aren't currently real.
  On success the game sets the blueprint `:rejected` (it vanishes, the existing removal path;
  unlight its glow); the twin says `:removed` ("Removed. It's gone for good."). While in flight
  both buttons are disabled and the first reads **Removing…** (reuse `!saving`). Failure or no
  bridge: back to `:real`, the twin says why (existing `:no-bridge` pattern).
- **On load** `apply-real-builds` also reads `:removed`: those ids are `:rejected` and never
  drawn, whether estate or file blueprints, and `:removed` wins over `:real`.
- **`bb blueprint-add` refuses an id that is in `:removed`** (exit 1: "was removed; pick a new
  id"), read from `state/builds.edn` (path overridable like the blueprints file, for tests).
- **A removal is not a delete of the proposal file:** `state/blueprints.edn` is left as it is.
- **Everyone's game:** words are data; nothing names a person, pet, member or blueprint in code.
  No `::k` across namespaces. `now` is an argument in pure fns.

## Out of scope

Removing things that aren't blueprints (the mailbox, creatures, the kit's own props); moving or
editing a build; a bot-drafted removal (later, on top of this); undoing a removal (add it again
with a new id).

## Done when

Gate passes with tests for: the card's owner/mode for each status (trial, real clicked, removing,
none); `start-removal`/`keep-it` transitions and no-ops; `apply-real-builds` with `:removed`
(estate and file ids, `:removed` beats `:real`); the bridge's `remove-real-build` (real id
removed and listed; unknown or not-real id refused; idempotent shape of the file); blueprint-add
refusing a removed id; the new `:blueprint-text` keys exist and are non-blank. The builder leaves
a note in the run directory: what was built, and what Hal should click headless (selectors).
