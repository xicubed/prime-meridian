# Quest: walkers only react to what they can see or hear (senses v0)

**Game:** Prime Meridian. **Asked by:** Michael: "sometimes the bot moves if something's coming
behind him, let's restrict his vision to in front of him? or it could be sound, something he
hears." **Led by:** Hal. Follows `avoid-v0` (planner in `pm.motion`/`pm.avoid`).

## The moment we're building

Le Testeur comes up behind Un Automate on the bank. Un Automate keeps walking: he can't see
behind him. Le Testeur sees him and goes around. If Le Testeur gets close enough to be heard,
Un Automate notices (after a beat) and only then may step aside. Walking toward each other,
both see each other and both keep right, as now.

## Rules (pure, tested)

- **Sight:** another walker is seen when it is within `:sight-m` (8 m) and inside the cone of
  `:sight-deg` total (110°, so ±55°) around the walker's heading.
- **Hearing:** it is heard when within `:hear-m` (1.2 m), scaled up by its speed relative to
  walking pace (`hear-m × max(1, speed / 1.2)`), from any direction.
- **Noticing takes time:** a walker that wasn't perceived becomes noticed only after it has been
  continuously seen or heard for `:react-s` (0.4 s); it's forgotten after `:forget-s` (1.5 s)
  without being perceived. The planner's "others" are only the noticed walkers.
- Static footprints and `:obstacles` are always known (you remember the rock).
- A walker that notices someone only by hearing gets a `:glance` flag (debug/HUD for now; the
  head turn comes later).
- Data in `resources/estate.edn` under `:motion :senses {:sight-m 8 :sight-deg 110 :hear-m 1.2
  :react-s 0.4 :forget-s 1.5}`; per-walker overrides allowed. Nothing names a walker in code.
- `?paths` also draws each walker's sight cone (thin lines) and hearing circle.

## What exists (read first)

`pm.motion` (`plan`, walkers, `:heading`, `:vel`), `pm.avoid`, `pm.bricks` (where `plan` is
called with `others`), `?paths` drawing. Gate `.crew/gate.sh`. Don't read `assets/private/`,
`public/kit/`, `public/state/`; don't edit `public/vendor/` or `resources/content/`.

## Out of scope

Head turning, bump physics (M-5), sounds, perception of Flora/Juniper/the player.

## Done when

The gate passes with tests for: seen in front, not seen behind or outside the cone or beyond
range; heard behind when close, louder when fast; the reaction delay and forgetting; an
overtake (the one behind detours, the one ahead keeps its line); head-on still both keep right;
the glance flag. Live (Hal checks with `?paths`): overtaking from behind, only the follower
steps aside; the HUD and 60 fps unchanged.
