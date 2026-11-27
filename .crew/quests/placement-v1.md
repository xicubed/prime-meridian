# Quest: build where you look, and show me (placement-v1)

**Game:** Prime Meridian. **Asked by:** Michael, Nov 26, shooting the bench scene. **Led by:** Hal.
**Runs after** crew-mirror-v0 (same checkout).

## The moment

Michael looked at the shade under the trees by the lobby and said "make a bench here". The
builder refused: "no level open ground within 6 m: most of it is under trees". Hal had to hand
him a spot; the second ask landed 6 m away, off screen, and Michael went looking for it both
times. His words: "you should be able to put a bench under a tree, that's where benches do
best, in the shade", and "he didn't put the bench in my viewport either time".

## What exists (read first)

- `src/pm/lot.cljc` (pure): `place`, `spot`, `refusal`, `cell-reason`; only kind `"o"` counts
  as buildable today (`spot`: `(= kind "o")`); `:build-rules` in `resources/estate.edn`
  (`:step-m :yaws :search-m 6 :gap-m :route-m :max-spread-m`).
- Ground kinds: `o` open, `T` canopy (cover more than 2 m above the ground: the ground under a
  tree's crown), `B` bush/wall/trunk height (0.35–2 m), `R` rocks, `:` gravel, `~` water.
- `scripts/blueprint_draft.clj` (`bb blueprint-draft <member> <draft> --near …`): refuses with
  one line; `bb blueprint-add` writes `state/blueprints.edn`; the game's 5 s poll seats and draws
  a new ghost and its author says so. The demo build's pretend twin (`pm.ask-build` +
  `demo-ask!` in `pm.core`) places with the same `pm.lot/place` from a grid measured in the
  browser.
- The CREW key's camera flight (`pm.core`/`pm.camera`: clicking a row flies the camera to a
  walker or a creature): reuse it, don't write a second one.
- The builder's role card `.crew/roles/` "Build requests" section.

## Decided (don't reopen)

1. **Shade counts.** A new `:build-rules :open-kinds` (default `["o"]` when missing; the estate
   ships `["o" "T"]`): ground under a tree's crown is buildable like open grass. Bushes, trunks,
   walls, rocks, water and steep ground are still refused with today's reasons.
2. **No dead-end refusals.** When nothing fits within `:search-m`, `place` widens the search in
   steps up to a new `:max-search-m` (estate: 15) and places on the nearest spot that fits; the
   result says how far it is from where the player looked (`:moved-m`). Only nothing within
   `:max-search-m` is a refusal. The line says it plainly: "under the trees there, so I put it
   6 m from where you looked" style, from estate words (`:blueprint-text`), no names in code.
3. **Show me.** When a new ghost from an ask arrives (dev: a new id in `state/blueprints.edn` by a
   member or the twin after page load; demo: `demo-ask!`'s new build), the camera flies to it with
   the CREW flight, framed from the side at about 6–8 m so a canopy doesn't hide it, unless it is
   already on screen and within 10 m of the camera. The player's own clicks never get hijacked:
   no flight while a trial card is open or within 2 s of a player camera move.
4. **One command for build requests.** The builder's role card: a build request is one `bb
   blueprint-draft` (with `--near` the spot Hal gives or the player's view) and one `bb hal-say
   --as` reply; no ground-map scripts, no `python3`/`node` checks: the command reports where it
   went or why not.
5. Pure parts tested, interop thin; everyone's game (no person, pet or member named in code).

## Out of scope

Walkers avoiding builds; changing the ground sampler; new UI controls.

## Done when

Gate passes with tests for: `:open-kinds` (canopy placed with `["o" "T"]`, refused with
`["o"]`), the widening search (`:moved-m`, refusal only past `:max-search-m`), the new line's
words, and the fly-to decision (new ghost off screen → fly; on screen and near → no; trial card
open or a recent player move → no). Builder notes say how Hal checks it live: ask for a bench
under the trees by the lobby, and what to read (`state/blueprints.edn`, the camera, 💭).
