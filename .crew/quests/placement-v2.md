# Quest: put it where I'm looking (placement-v2)

**Game:** Prime Meridian. **Asked by:** Michael, Nov 26, after the second bench take. **Led by:** Hal.
**Replaces** the placement rules of placement-v1 for "here" requests.

## The moment

Two takes in a row, the placement logic overruled him: it refused spots he could see were fine
("too steep", "under trees"), moved benches off screen, put one under the cottage and one down in
the canal behind it. His words:

> Change the logic to go in the middle of my view. I am picking the location because I know it
> can go there. I don't need a faulty equation showing me I can't. If it sucks I will just remove
> it, move myself a little and try again.

## What exists (read first)

- The game posts his view to the bridge every few seconds (`state/view.edn`, `bb where`):
  `:center` is the ground point at the middle of his screen (`pm.three/center-hit` on the ground
  meshes), plus the camera.
- `scripts/blueprint_draft.clj` + `src/pm/lot.cljc` (`place`, the ground-map search, refusals,
  the placement-v1 widening and `:moved-m`), `bb blueprint-add`.
- The demo path: `pm.ask-build` + `demo-ask!` in `src/pm/core.cljs` (measures a grid in the
  browser, then `pm.lot/place`; `:here` = `center-hit`).
- `src/pm/blueprint.cljs` `seat` (levels the item at its highest foot, stretches lower feet;
  `:too-steep?` past `max-seat-drop-m`), the `:steep` sign/twin line and the make-real refusal
  for a too-steep ghost (`core.cljs` ~1542, `blueprint.cljs` ~106 and ~482).
- placement-v1's show-me fly-to (`show-arrival!`, `pm.camera/fly-decision`).
- `.crew/roles/builder.md` "Build requests".

## Decided (don't reopen)

1. **"Here" means exactly the middle of his screen.** A request whose place is "here" (or none:
   no place word = here) puts the build's base at `:center` of his current view, unmoved: no
   ground-map check, no search, no gap or route rules, no refusal. Dev: a new `bb
   blueprint-draft <member> <draft> --at here` (reads `state/view.edn`; refuses only when the
   view is missing, older than 30 s, or the centre is sky: "look at the ground and ask again").
   Demo: `demo-ask!`'s `:here` places at `center-hit` the same way, skipping the grid and
   `pm.lot/place`.
2. **Facing:** the item turns to face his camera (its front toward the camera, yaw snapped to
   the nearest 45°), so a bench seat faces him.
3. **No "too steep" refusal for anything.** `seat` still levels the item and stretches its lower
   legs to the ground, but a big stretch no longer stops it: no `:steep` sign, Make it real
   works. (The `:seat-drop` value stays for information.) If it looks bad he removes it.
4. **Named places stay as they are:** "by the mailbox" etc. keep using `pm.lot/place` near the
   landmark (with placement-v1's rules), since he isn't pointing at a spot then.
5. **Show me:** with "here" the build is already in the middle of his screen, so no flight is
   needed; the fly-to stays for named places.
6. **The builder's role card:** for "here" (or no place) the one command is `bb blueprint-draft
   <name> <draft> --at here`; Hal may pass `--at x,z` (exact, same no-checks rule).
7. Everyone's game; pure parts tested (the yaw-to-face-camera fn, the view freshness check, the
   demo's here path); no npm deps.

## Out of scope

Changing the ground sampler; walkers avoiding builds; collision with other builds (he removes it
if it overlaps).

## Done when

Gate passes with tests for: `--at here` from a fresh view (exact `:center`, the facing yaw),
stale/missing/sky view refusals, `--at x,z`, a too-steep seat that still builds (no `:steep`
sign, make-real allowed), the demo's here path placing at the centre with no search, and named
places unchanged. Builder notes: how Hal checks it live (look at a spot, `bb where`, the command,
the ghost exactly there and facing the camera; make it real on a slope).
