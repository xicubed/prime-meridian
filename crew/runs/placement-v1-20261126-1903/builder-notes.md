# Builder notes: placement-v1

Touched (per spec §6): `src/pm/lot.cljc`, `src/pm/ask_build.cljc`, `src/pm/camera.cljs`,
`src/pm/blueprint.cljs`, `src/pm/core.cljs`, `scripts/blueprint_draft.clj`,
`resources/estate.edn` (`:build-rules` whole value, `:blueprint-text :moved`). Nothing under
`test/`, no other estate key, no `.crew/`, `PLAY.md` or `docs/`.

Gate: `0 warnings`; `Ran 1688 tests containing 8855 assertions. 0 failures, 0 errors.` (includes
`pm.placement-test` and the two changed `pm.ask-build-test` cases, t29 and t33).

## How Hal checks it live

**Dev build** (`npx shadow-cljs watch app`, kit copied into `public/kit/`):

1. Stand the camera on the shade under the trees by the lobby (a `T` cell), then from another
   pane run `bb blueprint-draft <member> <draft.edn> --near here` (or ask the crew for "a bench
   here" — their role card now does one `blueprint-draft` + one `hal-say`).
2. Read:
   - `state/blueprints.edn` for the new id and its `:at` — canopy now counts as buildable, so a
     spot right under the crown should stick instead of refusing.
   - The `blueprint-draft` stdout: one line always (`placed … m from …`), a second line only when
     it moved (`Not there: … so I put it … m from …`) — ask for a spot that's all bush/rock/water
     within 15 m to see a plain refusal instead (nothing fits that far out).
   - The camera: it should swing to frame the new ghost from the side (so the canopy doesn't
     hide it) unless it was already on screen and within 10 m, or you'd just dragged/scrolled/
     clicked the camera in the last 2 s, or a trial card was open — in each of those cases it
     stays put.
   - The browser console: `Prime Meridian: new ghost at [...] camera: <decision>` — `<decision>`
     is one of `:fly :in-view :player-moving :card-open :no-ghost`, so you can see which rule
     fired without guessing from the camera alone.
   - 💭: the member's `--as` line (the moved reason, or the refusal + a landmark to try).

**Demo build** (no kit, or `?demo`/whatever flag runs the scaffold's pretend twin): ask the twin
in 💭 for something under a tree or in a spot that needs the wider search — same two lines
(moved sentence + placed sentence) land in 💭, and the console logs `Prime Meridian: ground grid
<cols> x <rows> in <ms> ms` once for the near grid (29×29 with the estate's rules) and, only when
the near grid refuses for lack of ground, a second time for the wide grid (65×65 — 5× the cells
of the near grid, 4225 vs 841).

I don't have a GUI browser in this pane (headless Herdr session, no `playwright`/`puppeteer` in
the toolchain and no display), so I couldn't drive the kit myself to capture a real wall-clock
figure for that second grid line — the gate's pure tests cover the grid's *size* (t29/t32/t33 in
`ask_build_test.cljs`, both 29×29 and 65×65), but the actual ms is a property of the running
browser's raycaster, not something the pure code can report. When you do the live check above,
that console line is already part of what you're reading — note what it says and we'll know if
65×65 is fine or needs trimming.

## Notes on the implementation

- `pm.lot/place`'s widening is two passes over one candidate list bounded by `:max-search-m`:
  pass 1 (`d <= search-m`) unchanged in output shape; pass 2 only runs when pass 1 finds nothing,
  and only adds `:moved-m`/`:why` keys — existing `lot_test.cljs` expectations are untouched
  because every rule there has `:max-search-m` absent (so the second pass never widens anything).
- `pm.camera/flight` is the CREW key's old inline `fly!` maths moved out verbatim; `crew-key-tick!`
  now calls through it, same behaviour, one flight shared with the new ghost path.
- `show-arrival!` reads `@!camera-moved-at`, which `wire-pointer!` now stamps on `pointerdown`,
  `pointermove` (while dragging) and `wheel` — the player's own look-around never gets hijacked.
